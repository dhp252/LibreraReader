package com.foobnix.ui2.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.util.Pair;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.foobnix.android.utils.LOG;
import com.foobnix.dao2.FileMeta;
import com.foobnix.model.AppProfile;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.TintUtil;
import com.foobnix.pdf.info.view.MyProgressBar;
import com.foobnix.pdf.info.wrapper.UITab;
import com.foobnix.pdf.search.activity.msg.MessageSync;
import com.foobnix.ui2.FileMetaCore;
import com.foobnix.ui2.MainTabs2;
import com.foobnix.ui2.adapter.DefaultListeners;
import com.foobnix.ui2.adapter.FileMetaAdapter;
import com.foobnix.webdav.FolderSync;
import com.foobnix.webdav.WebDav;
import com.foobnix.webdav.WebDavClient;
import com.foobnix.webdav.WebDavCoverMaker;
import com.foobnix.webdav.WebDavCovers;
import com.foobnix.webdav.WebDavDialog;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The books on the WebDAV server (its Books folder), shown like the local ones: covers in the
 * Folders tab's grid or list. A book is downloaded into Librera/Books when first opened; one already
 * on this device is shown and opened as the local book it is. Covers of server-only books come
 * from the server ({@link WebDavCovers}); a book with none gets one made while it is shown
 * ({@link WebDavCoverMaker}).
 */
public class WebDavFragment2 extends UIFragment<FileMeta> {

    public static final Pair<Integer, Integer> PAIR = new Pair<Integer, Integer>(R.string.webdav_tab,
                                                                                 R.drawable.glyphicons_544_cloud);

    private interface Job {
        void run() throws Exception;
    }

    private FileMetaAdapter adapter;
    private View pathContainer;
    private View onBack;
    private TextView pathView;
    private TextView status;
    private volatile String folder = WebDav.BOOKS;
    /** The folder the items shown were listed from. */
    private volatile String listedFolder;
    /** What the last listing found wrong, shown in the status line; null when it went fine. */
    private volatile String error;
    /** The server entry behind each item shown, by the item's path (item path or local path). */
    private final Map<String, WebDavClient.Entry> entries = new ConcurrentHashMap<>();
    private volatile boolean downloading;
    private int mode = -1;
    private final WebDavCoverMaker coverMaker = new WebDavCoverMaker();
    /** The tab is on screen (selected and resumed): covers are made only then. */
    private volatile boolean shown;

    @Override
    public Pair<Integer, Integer> getNameAndIconRes() {
        return PAIR;
    }

    /** Settings > "Books on WebDAV": the tab, shown first if it was hidden. */
    public static void open(Activity a) {
        if (!WebDav.isEnabled(a)) {
            WebDavDialog.show(a, null);
            return;
        }
        if (!(a instanceof MainTabs2) || !isTabShown()) {
            showTab(a);
            return;
        }
        try {
            final DrawerLayout drawer = a.findViewById(R.id.drawer_layout);
            if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
                drawer.closeDrawer(GravityCompat.START);
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        LocalBroadcastManager.getInstance(a).sendBroadcast(new Intent(UIFragment.INTENT_TINT_CHANGE)
                .putExtra(MainTabs2.EXTRA_PAGE_NUMBER, UITab.getCurrentTabIndex(UITab.WebDavFragment)));
    }

    /** Puts the tab back in the tab bar and restarts the main screen on it, as the tab settings do. */
    public static void showTab(Activity a) {
        UITab.show(UITab.WebDavFragment);
        if (a instanceof MainTabs2) {
            restartMainScreen(a, UITab.getCurrentTabIndex(UITab.WebDavFragment));
        }
    }

    /** WebDAV sync switched on or off: the tab comes or goes with it. */
    public static void onEnabledChanged(Activity a) {
        if (WebDav.isEnabled(a)) {
            showTab(a);
        } else if (a instanceof MainTabs2) {
            restartMainScreen(a, 0);
        }
    }

    private static void restartMainScreen(Activity a, int tab) {
        AppProfile.save(a);
        AppProfile.clear();
        a.finish();
        MainTabs2.startActivity(a, tab);
    }

    private static boolean isTabShown() {
        for (UITab tab : UITab.getOrdered()) {
            if (tab == UITab.WebDavFragment) {
                return tab.isVisible();
            }
        }
        return false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_webdav, container, false);

        pathContainer = view.findViewById(R.id.pathContainer);
        onBack = view.findViewById(R.id.onBack);
        pathView = view.findViewById(R.id.webdavPath);
        status = view.findViewById(R.id.webdavStatus);
        recyclerView = view.findViewById(R.id.recyclerView);

        MyProgressBar = view.findViewById(R.id.MyProgressBarWebDav);
        MyProgressBar.setVisibility(View.GONE);
        TintUtil.setDrawableTint(MyProgressBar.getIndeterminateDrawable().getCurrent(), Color.WHITE);

        adapter = new FileMetaAdapter();
        bindAdapter(adapter);
        bindAuthorsSeriesAdapter(adapter);
        adapter.setOnItemClickListener(item -> {
            onClick(item);
            return false;
        });
        adapter.setOnItemLongClickListener(item -> {
            onLongClick(item);
            return true;
        });
        adapter.setOnMenuClickListener(item -> {
            onLongClick(item);
            return true;
        });
        // favorites are for books on this device; server-only items have no heart
        final com.foobnix.android.utils.ResultResponse2<FileMeta, FileMetaAdapter> star = DefaultListeners.getOnStarClick(getActivity());
        adapter.setOnStarClickListener((item, a) -> !WebDavCovers.isItem(item.getPath()) && star.onResultRecive(item, a));

        onBack.setOnClickListener(v -> up());
        status.setOnClickListener(v -> {
            if (!WebDav.isEnabled(getActivity())) {
                WebDavDialog.show(getActivity(), null);
            }
        });

        applyMode();
        onTintChanged();
        showFolder();
        populate();
        return view;
    }

    private void applyMode() {
        if (mode != AppState.get().broseMode) {
            mode = AppState.get().broseMode;
            onGridList(mode, null, adapter, null);
        }
    }

    @Override
    public List<FileMeta> prepareDataInBackground() {
        final Context c = getActivity() == null ? null : getActivity().getApplicationContext();
        final String path = folder;
        listedFolder = path;
        final List<FileMeta> items = new ArrayList<>();
        if (c == null || !WebDav.isEnabled(c)) {
            error = c == null ? null : c.getString(R.string.webdav_tab_off);
            return items;
        }
        try {
            final WebDavClient client = WebDav.client(c);
            final FolderSync books = WebDav.books(c, client);
            final List<WebDavClient.Entry> listed = listOrEmpty(client, path);
            final Map<String, WebDavClient.Entry> covers = new HashMap<>();
            for (WebDavClient.Entry cover : listOrEmpty(client, WebDavCovers.coversFolderOf(path))) {
                covers.put(cover.getName(), cover);
            }
            // folders first, then by name
            Collections.sort(listed, (x, y) -> x.directory != y.directory
                    ? (x.directory ? -1 : 1)
                    : x.getName().compareToIgnoreCase(y.getName()));

            for (WebDavClient.Entry entry : listed) {
                if (entry.getName().startsWith(".")) {
                    continue;
                }
                final FileMeta item;
                if (entry.directory) {
                    item = new FileMeta(WebDavCovers.folderItem(entry.path));
                    item.setCusType(FileMetaAdapter.DISPLAY_TYPE_DIRECTORY);
                    item.setTitle(entry.getName());
                    item.setPathTxt(entry.getName());
                } else {
                    final File local = books.localFileOf(entry);
                    if (local != null && local.isFile()) {
                        // a book on this device: the local book, with its cover, progress and heart
                        item = FileMetaCore.createMetaIfNeed(local.getPath(), false);
                    } else {
                        item = remoteBook(entry, covers.get(WebDavCovers.coverNameOf(entry.getName())));
                    }
                }
                entries.put(item.getPath(), entry);
                items.add(item);
            }
            error = null;
        } catch (Exception e) {
            LOG.e(e);
            error = (getActivity() == null ? "" : getActivity().getString(R.string.fail) + ": ") + e.getMessage();
        }
        return items;
    }

    private static List<WebDavClient.Entry> listOrEmpty(WebDavClient client, String path) throws Exception {
        try {
            return client.list(path);
        } catch (WebDavClient.WebDavException e) {
            if (e.code != 404) {
                throw e;
            }
            return new ArrayList<>();
        }
    }

    private static FileMeta remoteBook(WebDavClient.Entry entry, WebDavClient.Entry cover) {
        final FileMeta item = new FileMeta(WebDavCovers.bookItem(entry.path, cover));
        item.setCusType(FileMetaAdapter.DISPLAY_TYPE_FILE);
        item.setTitle(entry.getName());
        item.setPathTxt(entry.getName());
        item.setExt(ExtUtils.getFileExtension(entry.getName()));
        item.setSize(entry.size);
        item.setSizeTxt(ExtUtils.readableFileSize(entry.size));
        if (entry.lastModified > 0) {
            item.setDate(entry.lastModified);
            item.setDateTxt(ExtUtils.getDateFormat(entry.lastModified));
        }
        return item;
    }

    @Override
    public void populateDataInUI(List<FileMeta> items) {
        if (adapter == null) {
            return;
        }
        if (!folder.equals(listedFolder)) {
            // another folder was opened while this one was being listed
            populate();
            return;
        }
        adapter.getItemsList().clear();
        adapter.getItemsList().addAll(items);
        adapter.notifyDataSetChanged();
        showFolder();
        makeMissingCovers();
        if (error != null) {
            setStatus(error);
        } else if (items.isEmpty()) {
            setStatus(getString(WebDav.BOOKS.equals(folder) ? R.string.webdav_empty : R.string.webdav_folder_empty));
        } else {
            setStatus(null);
        }
    }

    /** Starts making the covers the books shown lack (replacing a run for another folder). */
    private void makeMissingCovers() {
        final Activity a = getActivity();
        if (a == null || adapter == null || !shown || !WebDav.isEnabled(a)) {
            coverMaker.stop();
            return;
        }
        final List<WebDavClient.Entry> todo = new ArrayList<>();
        for (FileMeta item : adapter.getItemsList()) {
            if (WebDavCovers.isItem(item.getPath()) && !WebDavCovers.isFolderItem(item.getPath())
                    && !WebDavCovers.hasCover(item.getPath())) {
                final WebDavClient.Entry entry = entries.get(item.getPath());
                if (entry != null) {
                    todo.add(entry);
                }
            }
        }
        if (todo.isEmpty()) {
            coverMaker.stop();
            return;
        }
        final String madeFor = folder;
        coverMaker.start(a, todo, () -> shown && madeFor.equals(folder), (book, cover) -> ui(() -> onCoverMade(book, cover)));
    }

    /** A cover was made: the book's item gets the new cover tag, so Glide fetches the cover. */
    private void onCoverMade(WebDavClient.Entry book, WebDavClient.Entry cover) {
        final List<FileMeta> items = adapter.getItemsList();
        for (int i = 0; i < items.size(); i++) {
            final String path = items.get(i).getPath();
            if (WebDavCovers.isItem(path) && !WebDavCovers.isFolderItem(path)
                    && book.path.equals(WebDavCovers.serverPathOf(path))) {
                final FileMeta item = remoteBook(book, cover);
                entries.remove(path);
                entries.put(item.getPath(), book);
                items.set(i, item);
                adapter.notifyItemChanged(i);
                return;
            }
        }
    }

    @Override
    public void onTabShown(boolean selected) {
        setShown(selected && isResumed());
    }

    @Override
    public void onResume() {
        super.onResume();
        setShown(getActivity() instanceof MainTabs2 && ((MainTabs2) getActivity()).isCurrentTab(this));
    }

    @Override
    public void onPause() {
        super.onPause();
        setShown(false);
    }

    private void setShown(boolean now) {
        final boolean was = shown;
        shown = now;
        if (now && !was) {
            makeMissingCovers();
        } else if (!now) {
            coverMaker.stop();
        }
    }

    private void setStatus(String text) {
        if (status == null) {
            return;
        }
        status.setText(text);
        status.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    private void showFolder() {
        if (pathView == null) {
            return;
        }
        pathView.setText(folder.replace("/", " / "));
        onBack.setVisibility(WebDav.BOOKS.equals(folder) ? View.INVISIBLE : View.VISIBLE);
    }

    private void load(String path) {
        folder = path;
        showFolder();
        populate();
    }

    private void up() {
        if (!WebDav.BOOKS.equals(folder)) {
            load(WebDavClient.parentOf(folder));
        }
    }

    @Override
    public boolean isBackPressed() {
        if (WebDav.BOOKS.equals(folder)) {
            return false;
        }
        up();
        return true;
    }

    private void onClick(FileMeta item) {
        if (WebDavCovers.isFolderItem(item.getPath())) {
            load(WebDavCovers.serverPathOf(item.getPath()));
            return;
        }
        final WebDavClient.Entry entry = entries.get(item.getPath());
        if (entry == null) {
            if (new File(item.getPath()).isFile()) {
                ExtUtils.showDocumentWithoutDialog(getActivity(), new File(item.getPath()), null);
            }
            return;
        }
        openBook(entry);
    }

    private void onLongClick(final FileMeta item) {
        final WebDavClient.Entry entry = entries.get(item.getPath());
        if (entry == null || entry.directory || getActivity() == null) {
            return;
        }
        final Activity a = getActivity();
        final File local = WebDav.books(a, WebDav.client(a)).localFileOf(entry);
        final boolean onDevice = local != null && local.isFile();
        final List<String> actions = new ArrayList<>();
        actions.add(a.getString(R.string.webdav_open));
        if (onDevice) {
            actions.add(a.getString(R.string.webdav_remove_local));
        }
        actions.add(a.getString(R.string.webdav_delete_server));

        new AlertDialog.Builder(a)
                .setTitle(entry.getName())
                .setItems(actions.toArray(new String[0]), (d, which) -> {
                    final String action = actions.get(which);
                    if (action.equals(a.getString(R.string.webdav_open))) {
                        openBook(entry);
                    } else if (action.equals(a.getString(R.string.webdav_remove_local))) {
                        // only the downloaded copy: the next sync keeps the server file (on demand)
                        if (!local.delete()) {
                            LOG.d("WebDAV cannot delete", local);
                        }
                        populate();
                    } else {
                        confirmDelete(entry);
                    }
                })
                .show();
    }

    private void confirmDelete(final WebDavClient.Entry entry) {
        new AlertDialog.Builder(getActivity())
                .setMessage(getString(R.string.webdav_delete_confirm, entry.getName()))
                .setPositiveButton(R.string.delete, (d, w) -> background(() -> {
                    final Context c = getActivity().getApplicationContext();
                    final WebDavClient client = WebDav.client(c);
                    client.delete(entry.path);
                    final String cover = WebDavCovers.coverOf(entry.path);
                    if (cover != null) {
                        client.delete(cover);
                    }
                    final File local = WebDav.books(c, client).localFileOf(entry);
                    if (local != null && local.isFile() && !local.delete()) {
                        LOG.d("WebDAV cannot delete", local);
                    }
                    ui(this::populate);
                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Opens the local copy, downloading it first when this device has none yet. */
    private void openBook(final WebDavClient.Entry entry) {
        final Activity a = getActivity();
        if (a == null || downloading) {
            return;
        }
        final FolderSync books = WebDav.books(a, WebDav.client(a));
        final File local = books.localFileOf(entry);
        if (local != null && local.isFile()) {
            ExtUtils.showDocumentWithoutDialog(a, local, null);
            return;
        }
        downloading = true;
        setStatus(getString(R.string.download) + ": " + entry.getName() + " …");
        MyProgressBar.setVisibility(View.VISIBLE);
        background(() -> {
            try {
                final File downloaded = books.downloadOnDemand(entry);
                FileMetaCore.createMetaIfNeed(downloaded.getPath(), true);
                ui(() -> {
                    setStatus(null);
                    populate();
                    ExtUtils.showDocumentWithoutDialog(getActivity(), downloaded, null);
                });
            } finally {
                downloading = false;
                ui(() -> MyProgressBar.setVisibility(View.GONE));
            }
        });
    }

    private void ui(Runnable r) {
        final Activity a = getActivity();
        if (a != null) {
            a.runOnUiThread(() -> {
                if (isAdded()) {
                    r.run();
                }
            });
        }
    }

    private void background(final Job job) {
        new Thread(() -> {
            try {
                job.run();
            } catch (Exception e) {
                LOG.e(e);
                ui(() -> setStatus(getString(R.string.fail) + ": " + e.getMessage()));
            }
        }, "@T WebDAV tab").start();
    }

    /** A sync has finished (pull to refresh, app start, a book closed): the folder may have changed. */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSync(MessageSync msg) {
        if (msg.state == MessageSync.STATE_SUCCESS || msg.state == MessageSync.STATE_FAILE) {
            populate();
        }
    }

    @Override
    public void onTintChanged() {
        if (pathContainer != null) {
            TintUtil.setBackgroundFillColor(pathContainer, TintUtil.color);
        }
    }

    @Override
    public void notifyFragment() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void resetFragment() {
        applyMode();
        populate();
    }
}
