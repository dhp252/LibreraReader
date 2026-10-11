package com.foobnix.ui2.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.core.util.Pair;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

import com.foobnix.android.utils.LOG;
import com.foobnix.dao2.FileMeta;
import com.foobnix.model.AppProfile;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.TintUtil;
import com.foobnix.pdf.info.view.MyPopupMenu;
import com.foobnix.pdf.info.view.MyProgressBar;
import com.foobnix.pdf.info.wrapper.UITab;
import com.foobnix.pdf.search.activity.msg.MessageSync;
import com.foobnix.ui2.FileMetaCore;
import com.foobnix.ui2.MainTabs2;
import com.foobnix.ui2.adapter.DefaultListeners;
import com.foobnix.ui2.adapter.FileMetaAdapter;
import com.foobnix.webdav.FolderSync;
import com.foobnix.webdav.WebDav;
import com.foobnix.webdav.WebDavBookMeta;
import com.foobnix.webdav.WebDavClient;
import com.foobnix.webdav.WebDavCoverMaker;
import com.foobnix.webdav.WebDavCovers;
import com.foobnix.webdav.WebDavDialog;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

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

    /** WebDAV tab sorts (AppState.webdavSortBy). */
    public static final int SORT_NAME = 0;
    public static final int SORT_DATE = 1;
    public static final int SORT_SIZE = 2;

    /** Covers are made only after the folder has been looked at this long: browsing is not slowed by a book download. */
    private static final long COVERS_AFTER_MS = 1500;
    /** Metadata files fetched at once when a folder is first seen. */
    private static final int META_FETCHES = 4;

    private FileMetaAdapter adapter;
    private View header;
    private View pathContainer;
    private ImageView onBack;
    private View secondTopPanel;
    private EditText filterLine;
    private TextView countBooks;
    private ImageView cleanFilter, sortBy, onGridList, menu2;
    /** Server-only books, by server path: their cover entry (if any) and metadata JSON (if any). */
    private final Map<String, WebDavClient.Entry> coverOfBook = new ConcurrentHashMap<>();
    private final Map<String, String> metaOfBook = new ConcurrentHashMap<>();
    /** Everything listed in the folder; the adapter shows it filtered and sorted. */
    private final List<FileMeta> allItems = new ArrayList<>();
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
    /** Each folder seen, as last listed: shown at once when it is opened again, then refreshed. */
    private final Map<String, List<FileMeta>> listedItems = new HashMap<>();
    /** Where each folder left was scrolled to, so going back returns there. */
    private final Map<String, Parcelable> scrollOf = new HashMap<>();
    /** The folder the list shows now (null before the first one). */
    private String displayedFolder;
    /** Server-only books of the last listing whose metadata this device has not kept yet: {book, metadata file}. */
    private volatile List<WebDavClient.Entry[]> metaToFetch = new ArrayList<>();
    private final Runnable startCovers = this::makeMissingCovers;

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

        header = view.findViewById(R.id.webdavHeader);
        pathContainer = view.findViewById(R.id.pathContainer);
        onBack = view.findViewById(R.id.onBack);
        pathView = view.findViewById(R.id.webdavPath);
        status = view.findViewById(R.id.webdavStatus);
        recyclerView = view.findViewById(R.id.recyclerView);

        MyProgressBar = view.findViewById(R.id.MyProgressBarWebDav);
        MyProgressBar.setVisibility(View.GONE);
        TintUtil.setDrawableTint(MyProgressBar.getIndeterminateDrawable().getCurrent(), TintUtil.color);

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

        secondTopPanel = view.findViewById(R.id.secondTopPanel);
        filterLine = view.findViewById(R.id.filterLine);
        countBooks = view.findViewById(R.id.countBooks);
        cleanFilter = view.findViewById(R.id.cleanFilter);
        sortBy = view.findViewById(R.id.sortBy);
        onGridList = view.findViewById(R.id.onGridList);
        menu2 = view.findViewById(R.id.menu2);

        menu2.setOnClickListener(v -> {
            final View drawerButton = v.getRootView().findViewById(R.id.imageMenu1);
            if (drawerButton != null) {
                drawerButton.performClick();
            }
        });
        filterLine.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                showItems();
            }
        });
        cleanFilter.setOnClickListener(v -> filterLine.setText(""));
        sortBy.setOnClickListener(this::sortPopup);
        // as in the Library tab: the button shows which way round, a long press turns it round
        sortBy.setOnLongClickListener(v -> {
            AppState.get().webdavSortAsc = !AppState.get().webdavSortAsc;
            showItems();
            return true;
        });
        onGridList.setOnClickListener(v -> modePopup());

        onBack.setOnClickListener(v -> up());
        status.setOnClickListener(v -> {
            if (!WebDav.isEnabled(getActivity())) {
                WebDavDialog.show(getActivity(), null);
            }
        });

        // the folder row sits above the first row and scrolls away with the list; the room for
        // it is kept by the padding UIFragment gives the list (listHeadRoom)
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                placeHeader();
            }
        });
        recyclerView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> placeHeader());

        applyMode();
        onTintChanged();
        showFolder();
        populate();
        return view;
    }

    @Override
    protected int listHeadRoom() {
        return header == null ? 0 : header.getHeight();
    }

    /**
     * Keeps the folder row just above the first row of the list. The list is pulled up under the
     * top bar (UIFragment), so at rest the row stands right below the bar; scrolled, it goes
     * under the bar with the books.
     */
    private void placeHeader() {
        if (header == null || recyclerView == null) {
            return;
        }
        final int height = header.getHeight();
        final int rest = recyclerView.getPaddingTop() - height;
        int y = rest;
        if (adapter != null && adapter.getItemCount() > 0) {
            final RecyclerView.ViewHolder first = recyclerView.findViewHolderForAdapterPosition(0);
            y = first == null ? -height : Math.min(rest, first.itemView.getTop() - height);
        }
        if (header.getTranslationY() != y) {
            header.setTranslationY(y);
        }
    }

    private void applyMode() {
        if (mode != AppState.get().webdavMode) {
            mode = AppState.get().webdavMode;
            onGridList(mode, onGridList, adapter, null);
        }
    }

    private void modePopup() {
        final MyPopupMenu p = new MyPopupMenu(getActivity(), onGridList);
        final int[] names = {R.string.list, R.string.compact, R.string.grid, R.string.cover};
        final int[] icons = {R.drawable.my_glyphicons_114_paragraph_justify, R.drawable.my_glyphicons_114_justify_compact,
                R.drawable.glyphicons_157_thumbnails, R.drawable.glyphicons_158_thumbnails_small};
        final int[] modes = {AppState.MODE_LIST, AppState.MODE_LIST_COMPACT, AppState.MODE_GRID, AppState.MODE_COVERS};
        for (int i = 0; i < names.length; i++) {
            final int chosen = modes[i];
            p.getMenu().add(names[i]).setIcon(icons[i]).setOnMenuItemClickListener((MenuItem item) -> {
                AppState.get().webdavMode = chosen;
                applyMode();
                return false;
            });
        }
        p.show();
    }

    private void sortPopup(View anchor) {
        final MyPopupMenu p = new MyPopupMenu(getActivity(), anchor);
        final int[] names = {R.string.by_file_name, R.string.by_date, R.string.by_size};
        final int[] sorts = {SORT_NAME, SORT_DATE, SORT_SIZE};
        for (int i = 0; i < names.length; i++) {
            final int chosen = sorts[i];
            p.getMenu().add(names[i])
                    .setIconSmall(AppState.get().webdavSortBy == chosen ? TintUtil.SORT_CURRENT_ICON : 0)
                    .setOnMenuItemClickListener((MenuItem item) -> {
                        AppState.get().webdavSortBy = chosen;
                        showItems();
                        return false;
                    });
        }
        p.show();
    }

    /** The folder's items, filtered by the text in the bar and sorted, folders first. */
    private void showItems() {
        if (adapter == null) {
            return;
        }
        final String filter = filterLine == null ? "" : filterLine.getText().toString().trim().toLowerCase();
        final List<FileMeta> shown = new ArrayList<>();
        int books = 0;
        for (FileMeta item : allItems) {
            if (!filter.isEmpty() && !matches(item, filter)) {
                continue;
            }
            shown.add(item);
            if (!WebDavCovers.isFolderItem(item.getPath())) {
                books++;
            }
        }
        Collections.sort(shown, comparator());
        adapter.getItemsList().clear();
        adapter.getItemsList().addAll(shown);
        adapter.notifyDataSetChanged();

        if (countBooks != null) {
            countBooks.setText(String.valueOf(books));
        }
        if (cleanFilter != null) {
            cleanFilter.setVisibility(filter.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (sortBy != null) {
            sortBy.setImageResource(AppState.get().webdavSortAsc ? R.drawable.glyphicons_476_sort_attributes
                    : R.drawable.glyphicons_477_sort_attributes_alt);
        }
    }

    /** The filter looks in the file name, the title and the author, as the Library's does. */
    private boolean matches(FileMeta item, String filter) {
        return nameOf(item).toLowerCase().contains(filter)
                || (item.getTitle() != null && item.getTitle().toLowerCase().contains(filter))
                || (item.getAuthor() != null && item.getAuthor().toLowerCase().contains(filter));
    }

    private String nameOf(FileMeta item) {
        final WebDavClient.Entry entry = entries.get(item.getPath());
        return entry != null ? entry.getName() : (item.getPathTxt() != null ? item.getPathTxt() : item.getPath());
    }

    private Comparator<FileMeta> comparator() {
        final int by = AppState.get().webdavSortBy;
        final boolean asc = AppState.get().webdavSortAsc;
        return (a, b) -> {
            final boolean folderA = WebDavCovers.isFolderItem(a.getPath());
            final boolean folderB = WebDavCovers.isFolderItem(b.getPath());
            if (folderA != folderB) {
                return folderA ? -1 : 1;
            }
            final WebDavClient.Entry ea = entries.get(a.getPath());
            final WebDavClient.Entry eb = entries.get(b.getPath());
            int result = 0;
            if (by == SORT_DATE && ea != null && eb != null) {
                result = Long.compare(ea.lastModified, eb.lastModified);
            } else if (by == SORT_SIZE && ea != null && eb != null) {
                result = Long.compare(ea.size, eb.size);
            }
            if (result == 0) {
                result = nameOf(a).compareToIgnoreCase(nameOf(b));
            }
            return asc ? result : -result;
        };
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
            // one client for the tab: its connections and login stay open between folders
            final WebDavClient client = WebDavCovers.sharedClient(c);
            final FolderSync books = WebDav.books(c, client);
            // the folder and its covers folder are listed at the same time
            final FutureTask<List<WebDavClient.Entry>> coversListing =
                    new FutureTask<>(() -> listOrEmpty(client, WebDavCovers.coversFolderOf(path)));
            new Thread(coversListing, "@T WebDAV covers list").start();
            final List<WebDavClient.Entry> listed = listOrEmpty(client, path);
            final Map<String, WebDavClient.Entry> covers = new HashMap<>();
            try {
                for (WebDavClient.Entry cover : coversListing.get()) {
                    covers.put(cover.getName(), cover);
                }
            } catch (ExecutionException e) {
                throw e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
            }
            // what each book has is read afresh: a file replaced on the server needs both again
            coverOfBook.clear();
            metaOfBook.clear();
            final List<WebDavClient.Entry[]> fetchMeta = new ArrayList<>();
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
                        final WebDavClient.Entry cover = covers.get(WebDavCovers.coverNameOf(entry.getName()));
                        final WebDavClient.Entry metaEntry = covers.get(WebDavBookMeta.metaNameOf(entry.getName()));
                        // only what this device keeps: the rest is fetched once the folder shows
                        final String meta = metaEntry == null ? null : WebDavCovers.cachedMetaJson(c, metaEntry);
                        if (metaEntry != null && meta == null) {
                            fetchMeta.add(new WebDavClient.Entry[]{entry, metaEntry});
                        }
                        if (cover != null) {
                            coverOfBook.put(entry.path, cover);
                        }
                        if (meta != null) {
                            metaOfBook.put(entry.path, meta);
                        }
                        item = remoteBook(entry, cover, meta);
                    }
                }
                entries.put(item.getPath(), entry);
                items.add(item);
            }
            metaToFetch = fetchMeta;
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

    /** A server-only book as an item: file details, then its shared metadata when there is some. */
    private static FileMeta remoteBook(WebDavClient.Entry entry, WebDavClient.Entry cover, String meta) {
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
        if (meta != null) {
            WebDavBookMeta.apply(meta, item);
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
        if (error == null) {
            listedItems.put(folder, new ArrayList<>(items));
        }
        display(items);
        fetchMissingMeta();
        if (error != null) {
            setStatus(error);
        } else if (items.isEmpty()) {
            setStatus(getString(WebDav.BOOKS.equals(folder) ? R.string.webdav_empty : R.string.webdav_folder_empty));
        } else {
            setStatus(null);
        }
    }

    /** Shows the items of {@link #folder}, scrolled where it was left (at the top when new). */
    private void display(List<FileMeta> items) {
        final RecyclerView.LayoutManager layout = recyclerView == null ? null : recyclerView.getLayoutManager();
        final boolean other = !folder.equals(displayedFolder);
        if (other && displayedFolder != null && layout != null) {
            scrollOf.put(displayedFolder, layout.onSaveInstanceState());
        }
        allItems.clear();
        allItems.addAll(items);
        showItems();
        showFolder();
        if (other && layout != null) {
            final Parcelable scroll = scrollOf.get(folder);
            if (scroll != null) {
                layout.onRestoreInstanceState(scroll);
            } else {
                recyclerView.scrollToPosition(0);
            }
        }
        displayedFolder = folder;
        recyclerView.post(this::placeHeader);
    }

    /**
     * Fetches the metadata files of the listing that this device has not kept, a few at a time,
     * each book updated as its file comes; covers are started after them.
     */
    private void fetchMissingMeta() {
        final List<WebDavClient.Entry[]> todo = metaToFetch;
        metaToFetch = new ArrayList<>();
        final Activity a = getActivity();
        if (todo.isEmpty() || a == null) {
            scheduleCovers();
            return;
        }
        final Context c = a.getApplicationContext();
        final String forFolder = folder;
        final ExecutorService pool = Executors.newFixedThreadPool(META_FETCHES);
        for (WebDavClient.Entry[] pair : todo) {
            pool.execute(() -> {
                if (!forFolder.equals(folder)) {
                    return;
                }
                final String json = WebDavCovers.metaJson(c, pair[1]);
                if (json != null) {
                    ui(() -> {
                        if (forFolder.equals(folder)) {
                            onMade(pair[0], null, json);
                        }
                    });
                }
            });
        }
        pool.shutdown();
        new Thread(() -> {
            try {
                pool.awaitTermination(2, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                LOG.d("WebDAV metadata", e.getMessage());
            }
            ui(() -> {
                if (forFolder.equals(folder)) {
                    scheduleCovers();
                }
            });
        }, "@T WebDAV metadata").start();
    }

    /** Covers are made once the folder has stayed on screen a moment, not while browsing through it. */
    private void scheduleCovers() {
        if (recyclerView == null) {
            return;
        }
        recyclerView.removeCallbacks(startCovers);
        recyclerView.postDelayed(startCovers, COVERS_AFTER_MS);
    }

    /** Starts making the covers and metadata the books shown lack (replacing a run for another folder). */
    private void makeMissingCovers() {
        final Activity a = getActivity();
        if (a == null || adapter == null || !shown || !WebDav.isEnabled(a)) {
            coverMaker.stop();
            return;
        }
        final List<WebDavCoverMaker.Need> todo = new ArrayList<>();
        for (FileMeta item : adapter.getItemsList()) {
            if (!WebDavCovers.isItem(item.getPath()) || WebDavCovers.isFolderItem(item.getPath())) {
                continue;
            }
            final WebDavClient.Entry entry = entries.get(item.getPath());
            if (entry == null) {
                continue;
            }
            final boolean cover = !WebDavCovers.hasCover(item.getPath());
            final boolean meta = !metaOfBook.containsKey(entry.path);
            if (cover || meta) {
                todo.add(new WebDavCoverMaker.Need(entry, cover, meta));
            }
        }
        if (todo.isEmpty()) {
            coverMaker.stop();
            return;
        }
        final String madeFor = folder;
        coverMaker.start(a, todo, () -> shown && madeFor.equals(folder), (book, cover, meta) -> ui(() -> onMade(book, cover, meta)));
    }

    /**
     * A cover and/or metadata was made: the book's item is rebuilt with them (a new cover tag, so
     * Glide fetches the cover).
     */
    private void onMade(WebDavClient.Entry book, WebDavClient.Entry madeCover, String madeMeta) {
        if (madeCover != null) {
            coverOfBook.put(book.path, madeCover);
        }
        if (madeMeta != null) {
            metaOfBook.put(book.path, madeMeta);
        }
        final FileMeta item = remoteBook(book, coverOfBook.get(book.path), metaOfBook.get(book.path));
        entries.put(item.getPath(), book);
        final List<FileMeta> items = adapter.getItemsList();
        for (int i = 0; i < items.size(); i++) {
            if (isItemOf(items.get(i), book)) {
                items.set(i, item);
                adapter.notifyItemChanged(i);
            }
        }
        for (int i = 0; i < allItems.size(); i++) {
            if (isItemOf(allItems.get(i), book)) {
                allItems.set(i, item);
            }
        }
        listedItems.put(folder, new ArrayList<>(allItems));
    }

    private static boolean isItemOf(FileMeta item, WebDavClient.Entry book) {
        final String path = item.getPath();
        return WebDavCovers.isItem(path) && !WebDavCovers.isFolderItem(path) && book.path.equals(WebDavCovers.serverPathOf(path));
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
        coverMaker.stop();
        if (recyclerView != null) {
            recyclerView.removeCallbacks(startCovers);
        }
        final List<FileMeta> known = listedItems.get(path);
        if (known != null) {
            display(known);
            setStatus(null);
        } else {
            showFolder();
        }
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
        final File local = WebDav.books(a, WebDavCovers.sharedClient(a)).localFileOf(entry);
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
                    final WebDavClient client = WebDavCovers.sharedClient(c);
                    client.delete(entry.path);
                    final String cover = WebDavCovers.coverOf(entry.path);
                    if (cover != null) {
                        client.delete(cover);
                    }
                    final String meta = WebDavBookMeta.metaOf(entry.path);
                    if (meta != null) {
                        try {
                            client.delete(meta);
                        } catch (Exception e) {
                            LOG.d("WebDAV metadata", e.getMessage());
                        }
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
        final FolderSync books = WebDav.books(a, WebDavCovers.sharedClient(a));
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
        if (secondTopPanel == null) {
            return;
        }
        // as the Library tab draws its bar
        TintUtil.setBackgroundFillColor(secondTopPanel, TintUtil.color);
        final boolean dark = AppState.get().appTheme == AppState.THEME_DARK_OLED || AppState.get().appTheme == AppState.THEME_DARK;
        TintUtil.setTintImageNoAlpha(menu2, dark ? Color.WHITE : TintUtil.color);
        final int colorTheme = ColorUtils.setAlphaComponent(TintUtil.getColorInDayNighth(), 230);
        TintUtil.setUITextColor(countBooks, colorTheme);
        TintUtil.setTintImageNoAlpha(cleanFilter, colorTheme);
        TintUtil.setTintImageNoAlpha(onBack, colorTheme);
        TintUtil.setUITextColor(pathView, colorTheme);
        if (AppState.get().appTheme == AppState.THEME_DARK_OLED || (AppState.get().appTheme == AppState.THEME_DARK && TintUtil.color == Color.BLACK)) {
            filterLine.setBackgroundResource(R.drawable.bg_search_edit_night);
            menu2.setBackgroundResource(R.drawable.bg_search_edit_night);
        } else {
            filterLine.setBackgroundResource(R.drawable.bg_search_edit);
            menu2.setBackgroundResource(R.drawable.bg_search_edit);
        }
        TintUtil.setStrokeColor(filterLine, TintUtil.color);
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
