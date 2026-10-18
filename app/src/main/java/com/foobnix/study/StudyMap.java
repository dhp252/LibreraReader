package com.foobnix.study;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiSettingsDialog;
import com.foobnix.ai.BookChapters;
import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.TintUtil;

import org.json.JSONObject;

/**
 * The map of a studied book, full screen: the outline drawn as a mind map (markmap in a WebView,
 * bundled, no network) or as an indented list. A tap on an idea goes to its page. "What do I
 * still recall?" sends the outline and the reader's words (not the book) and names what was left out.
 */
public final class StudyMap {

    /** Above this many ideas the map opens with the chapters folded. */
    private static final int FOLD_ABOVE = 40;

    private final Activity a;
    private final StudySession s;
    private final Outline.Item root;
    private final boolean dark;
    private Dialog dialog;
    private FrameLayout content;
    private WebView web;
    private boolean mindMap;
    private TextView toggle;
    private StudyJobs.RecallJob recallJob;

    private StudyMap(Activity a, StudySession s, Outline.Item root) {
        this.a = a;
        this.s = s;
        this.root = root;
        this.dark = !AppState.get().isDayNotInvert;
    }

    /** @param recall open with the "What do I still recall?" box ready (coming back to the book) */
    public static void show(Activity a, StudySession s, boolean recall) {
        if (s == null) {
            return;
        }
        new Thread(() -> {
            final Outline.Item root = Outline.build(s.controller().getTitle(), s.book(), s.notes());
            a.runOnUiThread(() -> new StudyMap(a, s, root).open(recall));
        }, "@T Study map").start();
    }

    private void open(boolean recall) {
        dialog = new Dialog(a, dark ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(dark ? 0xff1e1e1e : Color.WHITE);

        // top bar: title, Mind map / Outline, close
        final LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        final int pad = Dips.dpToPx(12);
        bar.setPadding(pad, Dips.dpToPx(6), Dips.dpToPx(4), Dips.dpToPx(6));
        final TextView title = text(a.getString(R.string.study_map), 17, true);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        toggle = action("");
        toggle.setOnClickListener(v -> show(!mindMap));
        bar.addView(toggle);
        final TextView close = text("✕", 20, false);
        close.setPadding(pad, Dips.dpToPx(6), pad, Dips.dpToPx(6));
        close.setContentDescription(a.getString(R.string.close));
        close.setOnClickListener(v -> dialog.dismiss());
        bar.addView(close);
        page.addView(bar);

        content = new FrameLayout(a);
        page.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        page.addView(recallBox(recall));

        dialog.setContentView(page);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        dialog.setOnDismissListener(d -> {
            if (recallJob != null) {
                recallJob.cancel();
            }
            if (web != null) {
                web.destroy();
                web = null;
            }
        });
        web = createWebView();
        show(web != null);
        dialog.show();
    }

    /** Shows the mind map (when the WebView works) or the outline. */
    private void show(boolean map) {
        mindMap = map && web != null;
        content.removeAllViews();
        if (mindMap) {
            content.addView(web);
        } else {
            content.addView(outlineView());
        }
        toggle.setText(mindMap ? R.string.study_outline : R.string.study_mind_map);
        toggle.setVisibility(web == null ? View.GONE : View.VISIBLE);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private WebView createWebView() {
        final WebView w;
        try {
            w = new WebView(a);
        } catch (Exception e) {
            // no WebView on this device (or it is being updated): the outline only
            LOG.e(e);
            return null;
        }
        final WebSettings ws = w.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setBlockNetworkLoads(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(false);
        w.setBackgroundColor(dark ? 0xff1e1e1e : Color.WHITE);
        w.addJavascriptInterface(new Bridge(), "Study");
        final String data;
        try {
            final JSONObject tree = Outline.toMarkmap(root);
            data = tree.toString();
        } catch (Exception e) {
            LOG.e(e);
            return null;
        }
        final int level = root.count() > FOLD_ABOVE ? 2 : -1;
        w.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript("render(" + data + "," + dark + "," + level + ")", null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // the map never leaves its page
                return true;
            }
        });
        w.loadUrl("file:///android_asset/study/map.html");
        return w;
    }

    /** Taps from the map's page (kept by name for R8: proguard-rules.pro). */
    public final class Bridge {
        @JavascriptInterface
        public void jump(String id) {
            a.runOnUiThread(() -> go(Outline.find(root, id)));
        }
    }

    /** To the page of an idea (its quote), or to the start of a chapter. */
    private void go(Outline.Item item) {
        if (item == null || Outline.BOOK.equals(item.category)) {
            return;
        }
        final BookChapters.Range range = s.rangeOf(item.chapter);
        if (range == null) {
            return;
        }
        dialog.dismiss();
        if (Outline.CHAPTER.equals(item.category) || Outline.LINE.equals(item.category)) {
            s.goTo(range);
        } else {
            s.jumpTo(item.quote, item.page, range);
        }
    }

    private View outlineView() {
        final ScrollView scroll = new ScrollView(a);
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        final int pad = Dips.dpToPx(14);
        list.setPadding(pad, Dips.dpToPx(4), pad, pad);
        scroll.addView(list);
        final TextView book = text(root.text, 17, true);
        list.addView(book);
        if (!root.detail.isEmpty()) {
            final TextView thesis = text(root.detail, 15, false);
            thesis.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC));
            thesis.setPadding(0, Dips.dpToPx(4), 0, Dips.dpToPx(8));
            list.addView(thesis);
        }
        for (Outline.Item chapter : root.kids) {
            addRow(list, chapter, 0);
        }
        return scroll;
    }

    private void addRow(LinearLayout list, Outline.Item item, int depth) {
        final boolean chapter = Outline.CHAPTER.equals(item.category);
        final StringBuilder line = new StringBuilder();
        final String label = Outline.label(item);
        if (!label.isEmpty()) {
            line.append(label).append(": ");
        }
        line.append(item.text);
        if (!chapter && item.page >= 0) {
            line.append(" · p. ").append(item.page + 1);
        }
        final TextView row = text(chapter ? StudyBook.labelMark(item.detail) + "  " + line : line.toString(), chapter ? 15 : 14, chapter);
        if (chapter) {
            final android.text.SpannableString colored = new android.text.SpannableString(row.getText());
            colored.setSpan(new android.text.style.ForegroundColorSpan(Outline.color(item)), 0, 1, 0);
            row.setText(colored);
        }
        if (Outline.LINE.equals(item.category)) {
            row.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC));
        }
        row.setPadding(Dips.dpToPx(16 * depth), Dips.dpToPx(chapter ? 10 : 4), 0, Dips.dpToPx(4));
        if (item.dim) {
            row.setAlpha(0.5f);
        }
        ripple(row);
        row.setOnClickListener(v -> go(item));
        list.addView(row);
        for (Outline.Item k : item.kids) {
            addRow(list, k, depth + 1);
        }
    }

    /** "What do I still recall?": a box, Check, and the answer under it. */
    private View recallBox(boolean focus) {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        final int pad = Dips.dpToPx(12);
        box.setPadding(pad, Dips.dpToPx(6), pad, pad);
        box.setBackgroundColor(dark ? 0xff2a2a2a : 0xfff2f2f2);

        // the answer: up to a dozen lines, scrolled inside
        final TextView answer = text("", 14, false);
        answer.setMaxLines(12);
        answer.setVerticalScrollBarEnabled(true);
        answer.setMovementMethod(new android.text.method.ScrollingMovementMethod());
        answer.setPadding(0, 0, 0, Dips.dpToPx(6));
        answer.setVisibility(View.GONE);
        box.addView(answer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final EditText input = new EditText(a);
        input.setHint(R.string.study_recall_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMaxLines(4);
        input.setTextSize(14);
        row.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final ProgressBar progress = new ProgressBar(a);
        progress.setVisibility(View.GONE);
        row.addView(progress, new LinearLayout.LayoutParams(Dips.dpToPx(24), Dips.dpToPx(24)));
        final TextView check = action(a.getString(R.string.study_check));
        row.addView(check);
        box.addView(row);

        check.setOnClickListener(v -> {
            final String said = input.getText().toString().trim();
            if (said.isEmpty()) {
                input.requestFocus();
                return;
            }
            if (recallJob != null) {
                recallJob.cancel();
            }
            progress.setVisibility(View.VISIBLE);
            answer.setVisibility(View.VISIBLE);
            answer.setText("");
            recallJob = new StudyJobs.RecallJob(a, AiJobs.Target.of(s.controller()), Outline.toMarkdown(root), said,
                    new StudyJobs.TextScreen() {
                        @Override
                        public void onText(String all) {
                            answer.setText(all);
                        }

                        @Override
                        public void onDone(String all) {
                            progress.setVisibility(View.GONE);
                            answer.setText(all);
                        }

                        @Override
                        public void onError(String message) {
                            progress.setVisibility(View.GONE);
                            answer.setText(message);
                        }

                        @Override
                        public void onNeedSettings() {
                            progress.setVisibility(View.GONE);
                            AiSettingsDialog.show(a, null);
                        }
                    });
            recallJob.start();
        });
        if (focus) {
            input.requestFocus();
        }
        return box;
    }

    private TextView action(String label) {
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
