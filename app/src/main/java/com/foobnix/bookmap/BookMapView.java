package com.foobnix.bookmap;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiJump;
import com.foobnix.ai.AiProgress;
import com.foobnix.ai.AiSettings;
import com.foobnix.ai.AiSettingsDialog;
import com.foobnix.ai.AiTask;
import com.foobnix.ai.AiWork;
import com.foobnix.ai.BookChapters;
import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.TintUtil;
import com.foobnix.pdf.info.view.MyPopupMenu;
import com.foobnix.pdf.info.wrapper.DocumentController;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * The book map, full screen (F-013): a native top bar (what the map is doing, Build / Continue
 * building / Update map, more actions) over the map's page in a WebView (bundled, no network), or
 * the start and partial screens before the map exists. Closed during a build, the build goes on.
 */
public final class BookMapView {

    private final Activity a;
    private final DocumentController dc;
    private final AiJobs.Target target;
    private final boolean dark;
    private final boolean studied;
    private Dialog dialog;
    private FrameLayout content;
    private TextView status;
    private TextView action;
    private TextView close;
    private ProgressBar progress;
    private WebView web;
    private boolean pageLoaded;
    private String pendingData;
    private MapBook book = new MapBook();
    /** The map's task (F-004 design-2026-10-25.md): it goes on with the map or the book closed. */
    private final String key;
    private boolean building;

    private BookMapView(Activity a, DocumentController dc) {
        this.a = a;
        this.dc = dc;
        this.target = AiJobs.Target.of(dc);
        this.dark = !AppState.get().isDayNotInvert;
        final com.foobnix.study.StudySession s = com.foobnix.study.StudySession.of(dc);
        this.studied = s != null && s.book().on && s.book().hasPreview();
        this.key = AiTask.key(target.name, AiTask.MAP);
    }

    public static void show(Activity a, DocumentController dc) {
        if (dc == null || dc.getCurrentBook() == null) {
            return;
        }
        new BookMapView(a, dc).open();
    }

    private interface Screen extends MapJob.Screen, AiProgress.TaskScreen {
    }

    private final Screen screen = new Screen() {
        @Override
        public void onStatus(String text) {
            if (building) {
                status.setText(text);
                status.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void onWaiting(String text) {
            onStatus(text);
        }

        @Override
        public void onDone(String message) {
            if (!building) {
                return;
            }
            building = false;
            Toast.makeText(a, message, Toast.LENGTH_LONG).show();
            reload(message);
        }

        @Override
        public void onError(String message) {
            building = false;
            reload(message);
        }

        @Override
        public void onEnded(int state, String message) {
            if (state == AiProgress.DONE && message != null) {
                onDone(message);
            } else if (state == AiProgress.FAILED || state == AiProgress.CANCELLED) {
                onError(message == null ? "" : message);
            }
        }

        @Override
        public void onNeedSettings() {
            building = false;
            refresh("");
            AiSettingsDialog.show(a, null);
        }
    };

    private void open() {
        dialog = new Dialog(a, dark ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(dark ? 0xff1e1e1e : Color.WHITE);

        // top bar: title and what is going on, the main action, more, close
        final LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        final int pad = Dips.dpToPx(12);
        bar.setPadding(pad, Dips.dpToPx(6), Dips.dpToPx(4), Dips.dpToPx(6));
        final LinearLayout titles = new LinearLayout(a);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text(a.getString(R.string.book_map), 17, true));
        status = text("", 13, false);
        status.setAlpha(0.75f);
        status.setMaxLines(3);
        titles.addView(status);
        bar.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        progress = new ProgressBar(a);
        progress.setVisibility(View.GONE);
        bar.addView(progress, new LinearLayout.LayoutParams(Dips.dpToPx(22), Dips.dpToPx(22)));
        action = button("");
        bar.addView(action);
        final TextView more = text("⋮", 22, true);
        more.setPadding(Dips.dpToPx(10), Dips.dpToPx(4), Dips.dpToPx(10), Dips.dpToPx(4));
        ripple(more);
        more.setOnClickListener(this::more);
        bar.addView(more);
        close = text("✕", 20, false);
        close.setPadding(pad, Dips.dpToPx(6), pad, Dips.dpToPx(6));
        close.setContentDescription(a.getString(R.string.close));
        ripple(close);
        close.setOnClickListener(v -> dialog.dismiss());
        bar.addView(close);
        page.addView(bar);

        content = new FrameLayout(a);
        page.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        dialog.setContentView(page);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        com.foobnix.android.utils.SafeArea.fit(dialog, page, dark);
        dialog.setOnDismissListener(d -> {
            AiProgress.detach(key, screen);
            if (building) {
                // the build goes on and says when it is done
                Toast.makeText(a, R.string.map_hidden, Toast.LENGTH_LONG).show();
            }
            if (web != null) {
                web.destroy();
                web = null;
            }
        });
        dialog.show();

        final int state = AiProgress.state(key);
        building = state == AiProgress.RUNNING || state == AiProgress.WAITING;
        AiProgress.attach(key, a, screen);
        reload(building ? AiProgress.status(key) : "");
    }

    /** Reads the map again (off the main thread) and shows it. */
    private void reload(String message) {
        new Thread(() -> {
            final MapBook loaded = MapStore.get().load(target.name);
            a.runOnUiThread(() -> {
                if (dialog == null || !dialog.isShowing()) {
                    return;
                }
                book = loaded;
                refresh(message);
            });
        }, "@T Book map").start();
    }

    /** The bar and the content for where the map stands. */
    private void refresh(String message) {
        progress.setVisibility(building ? View.VISIBLE : View.GONE);
        // while it builds, closing hides it: the build goes on
        close.setText(building ? a.getString(R.string.ai_task_hide) : "✕");
        close.setTextSize(building ? 15 : 20);
        close.setContentDescription(a.getString(building ? R.string.ai_task_hide : R.string.close));
        final int mapped = mapped();
        final boolean needsUpdate = MapData.needsUpdate(book);
        String line = message;
        if (!building && line.isEmpty()) {
            if (needsUpdate) {
                line = a.getString(R.string.map_needs_update);
            } else if (book.hasBook()) {
                line = target.title;
            }
        }
        status.setText(line);
        status.setVisibility(line.isEmpty() ? View.GONE : View.VISIBLE);

        if (building) {
            action.setText(R.string.cancel);
            action.setOnClickListener(v -> {
                AiWork.cancel(a, key);
                building = false;
                reload(a.getString(R.string.ai_task_cancelled));
            });
        } else if (book.hasBook()) {
            action.setText(needsUpdate ? R.string.map_update : R.string.map_continue);
            action.setVisibility(needsUpdate ? View.VISIBLE : View.GONE);
            action.setOnClickListener(v -> build(needsUpdate && mapped > 0));
        } else {
            action.setText(mapped > 0 ? R.string.map_continue : R.string.map_build);
            action.setOnClickListener(v -> build(false));
        }
        if (building || !book.hasBook()) {
            action.setVisibility(View.VISIBLE);
        }

        if (book.hasBook()) {
            showMap();
        } else {
            showStart(mapped);
        }
    }

    /** Chapters with moments saved. */
    private int mapped() {
        int n = 0;
        for (MapBook.Chapter c : book.chapters.values()) {
            if (!c.moments.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    private void build(boolean bookOnly) {
        if (building) {
            return;
        }
        if (!AiSettings.load(a).isReady()) {
            AiSettingsDialog.show(a, null);
            return;
        }
        AiWork.enqueue(a, AiTask.map(target, bookOnly));
        building = true;
        AiProgress.attach(key, a, screen);
        refresh(a.getString(R.string.ai_task_queued));
    }

    private void more(View anchor) {
        final MyPopupMenu menu = new MyPopupMenu(a, anchor);
        final boolean idle = !building;
        if (idle && book.hasBook()) {
            menu.getMenu(R.drawable.ic_book_map, R.string.map_continue, () -> build(false));
        }
        if (idle && mapped() > 0) {
            menu.getMenu(R.drawable.glyphicons_82_refresh, R.string.map_rebuild, () -> {
                if (book.hasBook()) {
                    com.foobnix.ai.AiMenu.confirmRedo(a, R.string.map_rebuild_confirm, () -> build(true));
                } else {
                    build(true);
                }
            });
        }
        if (idle && !book.isEmpty()) {
            menu.getMenu(R.drawable.glyphicons_17_bin, R.string.map_delete, this::delete);
        }
        menu.getMenu(R.drawable.glyphicons_137_cogwheel, R.string.ai_settings, () -> AiSettingsDialog.show(a, null));
        menu.show();
    }

    private void delete() {
        new Thread(() -> {
            try {
                MapStore.get().update(target.name, MapBook::clear);
            } catch (Exception e) {
                LOG.e(e);
            }
            a.runOnUiThread(() -> {
                Toast.makeText(a, R.string.map_deleted, Toast.LENGTH_SHORT).show();
                reload("");
            });
        }, "@T Book map").start();
    }

    // --- before the map exists ---

    private void showStart(int mapped) {
        content.removeAllViews();
        web = destroy(web);
        final ScrollView scroll = new ScrollView(a);
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        final int pad = Dips.dpToPx(18);
        list.setPadding(pad, pad, pad, pad);
        scroll.addView(list);
        list.addView(text(a.getString(R.string.map_intro), 16, false));
        final TextView more = text(mapped > 0
                ? a.getString(R.string.map_partial, mapped)
                : a.getString(R.string.map_estimate, MapData.estimate(dc.getPageCount(), MapJob.PART_CHARS)), 14, false);
        more.setPadding(0, Dips.dpToPx(12), 0, 0);
        more.setAlpha(0.75f);
        list.addView(more);
        content.addView(scroll);
    }

    // --- the map ---

    private void showMap() {
        final JSONObject data;
        try {
            final BookChapters.Range here = AiJobs.knownChapter(target);
            final int reader = MapData.chapterAt(book, here == null ? null : here.key, target.page);
            data = MapData.join(target.title, book, reader, MapData.startWhole(book, studied));
        } catch (Exception e) {
            LOG.e(e);
            return;
        }
        if (web == null || content.getChildCount() == 0 || content.getChildAt(0) != web) {
            content.removeAllViews();
            web = createWebView();
            if (web == null) {
                content.addView(fallback(data));
                return;
            }
            content.addView(web);
        }
        // JSON is JavaScript, but for two line separators JavaScript strings once refused
        pendingData = data.toString().replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
        if (pageLoaded) {
            renderPage();
        }
    }

    private void renderPage() {
        if (web != null && pendingData != null) {
            web.evaluateJavascript("BookMap.render(" + pendingData + "," + dark + ")", null);
            pendingData = null;
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private WebView createWebView() {
        final WebView w;
        try {
            w = new WebView(a);
        } catch (Exception e) {
            // no WebView on this device (or it is being updated): the plain list
            LOG.e(e);
            return null;
        }
        final WebSettings ws = w.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setBlockNetworkLoads(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(false);
        w.setBackgroundColor(dark ? 0xff121212 : 0xfff1f3f6);
        w.addJavascriptInterface(new Bridge(), "BookMapBridge");
        pageLoaded = false;
        w.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageLoaded = true;
                renderPage();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // the map never leaves its page
                return true;
            }
        });
        w.loadUrl("file:///android_asset/bookmap/map.html");
        return w;
    }

    private static WebView destroy(WebView w) {
        if (w != null) {
            w.destroy();
        }
        return null;
    }

    /** Calls from the map's page (kept by name for R8: proguard-rules.pro). */
    public final class Bridge {
        @JavascriptInterface
        public void jump(String id) {
            a.runOnUiThread(() -> go(id));
        }

        @JavascriptInterface
        public void setWhole(boolean whole) {
            new Thread(() -> {
                try {
                    MapStore.get().update(target.name, b -> b.whole = whole);
                } catch (Exception e) {
                    LOG.e(e);
                }
            }, "@T Book map").start();
        }
    }

    /** To the page of a moment ("3.4": chapter 3, moment 4), its sentence looked for around the page it had. */
    private void go(String id) {
        final MapBook.Moment m = moment(id);
        if (m == null) {
            return;
        }
        dialog.dismiss();
        AiJump.toQuote(dc, m.quote, m.page, -1, -1);
    }

    private MapBook.Moment moment(String id) {
        final int dot = id == null ? -1 : id.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        try {
            final int n = Integer.parseInt(id.substring(0, dot));
            final int index = Integer.parseInt(id.substring(dot + 1)) - 1;
            for (MapBook.Chapter c : book.chapters.values()) {
                if (c.n == n && index >= 0 && index < c.moments.size()) {
                    return c.moments.get(index);
                }
            }
        } catch (NumberFormatException e) {
            // not an id of this map
        }
        return null;
    }

    /** Without a WebView: the chapters and their key moments, each opening its page. */
    private View fallback(JSONObject data) {
        final ScrollView scroll = new ScrollView(a);
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        final int pad = Dips.dpToPx(14);
        list.setPadding(pad, Dips.dpToPx(4), pad, pad);
        scroll.addView(list);
        list.addView(text(a.getString(R.string.map_no_webview), 14, false));
        final Set<String> key = new HashSet<>();
        final JSONArray keys = data.optJSONArray("key");
        for (int i = 0; keys != null && i < keys.length(); i++) {
            key.add(keys.optString(i));
        }
        final JSONArray chapters = data.optJSONArray("chapters");
        final JSONArray moments = data.optJSONArray("moments");
        for (int c = 0; chapters != null && c < chapters.length(); c++) {
            final JSONObject chapter = chapters.optJSONObject(c);
            final TextView title = text(chapter.optString("title"), 15, true);
            title.setPadding(0, Dips.dpToPx(12), 0, Dips.dpToPx(2));
            list.addView(title);
            for (int i = 0; moments != null && i < moments.length(); i++) {
                final JSONObject m = moments.optJSONObject(i);
                if (m.optInt("ch") != chapter.optInt("n") || !key.contains(m.optString("id"))) {
                    continue;
                }
                final TextView row = text("• " + m.optString("label"), 14, false);
                row.setPadding(0, Dips.dpToPx(4), 0, Dips.dpToPx(4));
                ripple(row);
                final String id = m.optString("id");
                row.setOnClickListener(v -> go(id));
                list.addView(row);
            }
        }
        return scroll;
    }

    private TextView button(String label) {
        final TextView b = text(label, 15, true);
        b.setTextColor(TintUtil.color);
        b.setPadding(Dips.dpToPx(10), Dips.dpToPx(8), Dips.dpToPx(10), Dips.dpToPx(8));
        ripple(b);
        return b;
    }

    private void ripple(View v) {
        final TypedValue ripple = new TypedValue();
        a.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        v.setBackgroundResource(ripple.resourceId);
    }

    private TextView text(String s, int sp, boolean bold) {
        final TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(dark ? 0xffeeeeee : 0xff222222);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }
}
