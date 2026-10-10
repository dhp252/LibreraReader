package com.foobnix.webdav;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.R;
import com.foobnix.ui2.FileMetaCore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The books on the server (the Books folder): browse folders, open a book (downloaded into
 * Librera/Books the first time), free its space on this device, or delete it everywhere.
 */
public class WebDavBooksDialog {

    private static final WebDavClient.Entry UP = new WebDavClient.Entry("..", true, 0, 0, null);

    private interface Job {
        void run() throws Exception;
    }

    private final Activity a;
    private final WebDavClient client;
    private final FolderSync books;
    private final List<WebDavClient.Entry> entries = new ArrayList<>();
    private ArrayAdapter<WebDavClient.Entry> adapter;
    private AlertDialog dialog;
    private TextView status;
    private String folder = WebDav.BOOKS;

    public static void show(Activity a) {
        if (!WebDav.isEnabled(a)) {
            WebDavDialog.show(a, null);
            return;
        }
        new WebDavBooksDialog(a).open();
    }

    private WebDavBooksDialog(Activity a) {
        this.a = a;
        this.client = WebDav.client(a);
        this.books = WebDav.books(a, client);
    }

    private void open() {
        final LinearLayout layout = new LinearLayout(a);
        layout.setOrientation(LinearLayout.VERTICAL);
        final int padding = Dips.dpToPx(8);
        layout.setPadding(padding, padding, padding, 0);

        status = new TextView(a);
        status.setPadding(padding, 0, padding, padding);
        layout.addView(status);

        final ListView list = new ListView(a);
        adapter = new ArrayAdapter<WebDavClient.Entry>(a, android.R.layout.simple_list_item_2, android.R.id.text1, entries) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final View view = super.getView(position, convertView, parent);
                final WebDavClient.Entry entry = getItem(position);
                final TextView name = view.findViewById(android.R.id.text1);
                final TextView info = view.findViewById(android.R.id.text2);
                if (entry == UP) {
                    name.setText("← ..");
                    info.setText("");
                } else if (entry.directory) {
                    name.setText("📁 " + entry.getName());
                    info.setText("");
                } else {
                    name.setText(entry.getName());
                    final String size = ExtUtils.readableFileSize(entry.size);
                    info.setText(isOnDevice(entry) ? size + " · " + a.getString(R.string.webdav_on_device) : size);
                }
                return view;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> onClick(adapter.getItem(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            onLongClick(adapter.getItem(position));
            return true;
        });
        layout.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Dips.dpToPx(360)));

        dialog = new AlertDialog.Builder(a)
                .setTitle(R.string.webdav_books)
                .setView(layout)
                .setNegativeButton(R.string.close, null)
                .create();
        dialog.show();
        load(WebDav.BOOKS);
    }

    private boolean isOnDevice(WebDavClient.Entry entry) {
        final File local = books.localFileOf(entry);
        return local != null && local.isFile();
    }

    private void load(final String path) {
        folder = path;
        status.setText(R.string.please_wait);
        background(() -> {
            List<WebDavClient.Entry> listed;
            try {
                listed = client.list(path);
            } catch (WebDavClient.WebDavException e) {
                if (e.code != 404) {
                    throw e;
                }
                listed = new ArrayList<>();
            }
            // folders first, then by name
            Collections.sort(listed, (x, y) -> x.directory != y.directory
                    ? (x.directory ? -1 : 1)
                    : x.getName().compareToIgnoreCase(y.getName()));
            final List<WebDavClient.Entry> result = listed;
            ui(() -> {
                entries.clear();
                if (!WebDav.BOOKS.equals(folder)) {
                    entries.add(UP);
                }
                entries.addAll(result);
                adapter.notifyDataSetChanged();
                if (result.isEmpty()) {
                    status.setText(R.string.webdav_empty);
                } else {
                    status.setText(folder);
                }
            });
        });
    }

    private void onClick(WebDavClient.Entry entry) {
        if (entry == UP) {
            load(WebDavClient.parentOf(folder));
        } else if (entry.directory) {
            load(entry.path);
        } else {
            openBook(entry);
        }
    }

    private void onLongClick(final WebDavClient.Entry entry) {
        if (entry == UP || entry.directory) {
            return;
        }
        final boolean onDevice = isOnDevice(entry);
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
                        final File local = books.localFileOf(entry);
                        if (local != null && !local.delete()) {
                            LOG.d("WebDAV cannot delete", local);
                        }
                        adapter.notifyDataSetChanged();
                    } else {
                        confirmDelete(entry);
                    }
                })
                .show();
    }

    private void confirmDelete(final WebDavClient.Entry entry) {
        new AlertDialog.Builder(a)
                .setMessage(a.getString(R.string.webdav_delete_confirm, entry.getName()))
                .setPositiveButton(R.string.delete, (d, w) -> background(() -> {
                    client.delete(entry.path);
                    final File local = books.localFileOf(entry);
                    if (local != null && local.isFile() && !local.delete()) {
                        LOG.d("WebDAV cannot delete", local);
                    }
                    ui(() -> load(folder));
                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Opens the local copy, downloading it first when this device has none yet. */
    private void openBook(final WebDavClient.Entry entry) {
        final File local = books.localFileOf(entry);
        if (local != null && local.isFile()) {
            dialog.dismiss();
            ExtUtils.showDocumentWithoutDialog(a, local, null);
            return;
        }
        status.setText(a.getString(R.string.download) + ": " + entry.getName() + " …");
        background(() -> {
            final File downloaded = books.downloadOnDemand(entry);
            FileMetaCore.createMetaIfNeed(downloaded.getPath(), true);
            ui(() -> {
                dialog.dismiss();
                ExtUtils.showDocumentWithoutDialog(a, downloaded, null);
            });
        });
    }

    private void ui(Runnable r) {
        a.runOnUiThread(() -> {
            if (!a.isFinishing() && dialog != null && dialog.isShowing()) {
                r.run();
            }
        });
    }

    private void background(final Job job) {
        new Thread(() -> {
            try {
                job.run();
            } catch (Exception e) {
                LOG.e(e);
                ui(() -> status.setText(a.getString(R.string.fail) + ": " + e.getMessage()));
            }
        }, "@T WebDAV books").start();
    }
}
