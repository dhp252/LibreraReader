package com.foobnix.assist.edition;

import android.app.Activity;
import android.app.Dialog;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.SafeArea;
import com.foobnix.assist.Assist;
import com.foobnix.assist.edition.EditionModel.Block;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;

/**
 * One page of "Your edition", full screen (canvas "Edition page"): a sheet with the running head
 * (the book's title, "YOUR EDITION", the page number), the chapter's heading and its blocks (in
 * your words in the reading face, what you got wrong in the orange card, "Tried it" in the accent
 * card, connections as chips, what is still open in italics), and at the foot the previous and
 * next page with how long the whole edition takes. Reading only: nothing is sent or written.
 */
final class EditionPageScreen {

    private final Activity a;
    private final EditionData data;
    private final Runnable onExport;
    private int number;

    private Dialog dialog;
    private TextView subtitle;
    private ScrollView scroll;
    private LinearLayout sheet;
    private TextView previous;
    private TextView next;

    private EditionPageScreen(Activity a, EditionData data, int number, Runnable onExport) {
        this.a = a;
        this.data = data;
        this.number = number;
        this.onExport = onExport;
    }

    /** @param number the page to open, from 1 */
    static void show(Activity a, EditionData data, int number, Runnable onExport) {
        if (!Assist.EDITION.isOn() || data == null || data.pages.size() == 0 || Apps.isDestroyedActivity(a)) {
            return;
        }
        new EditionPageScreen(a, data, Math.max(1, Math.min(number, data.pages.size())), onExport).open();
    }

    private void open() {
        final boolean dark = CanvasLook.isNight();
        dialog = new Dialog(a, dark ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final LinearLayout page = EditionScreen.column(a);
        page.setBackgroundColor(CanvasLook.surface2());

        final TextView[] sub = new TextView[1];
        final View bar = EditionScreen.bar(a, a.getString(R.string.canvas_t26_title), sub, new View[1], dialog::dismiss,
                onExport);
        bar.setBackgroundColor(CanvasLook.bg());
        page.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                CanvasLook.dp(CanvasLook.BAR_DP)));
        subtitle = sub[0];

        scroll = new ScrollView(a);
        sheet = EditionScreen.column(a);
        sheet.setBackground(CanvasParts.round(CanvasLook.bg(), 0, CanvasLook.dp(6)));
        sheet.setElevation(CanvasLook.dp(3));
        sheet.setPadding(CanvasLook.dp(18), CanvasLook.dp(18), CanvasLook.dp(18), CanvasLook.dp(20));
        final LinearLayout holder = EditionScreen.column(a);
        holder.setClipToPadding(false);
        holder.setPadding(CanvasLook.dp(14), CanvasLook.dp(12), CanvasLook.dp(14), CanvasLook.dp(12));
        holder.addView(sheet);
        scroll.addView(holder);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        final LinearLayout foot = new LinearLayout(a);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(CanvasLook.dp(16), CanvasLook.dp(12), CanvasLook.dp(16), CanvasLook.dp(16));
        previous = turn(false);
        previous.setOnClickListener(v -> go(number - 1));
        foot.addView(previous, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.dp(48)));
        final TextView middle = EditionLook.text(a, a.getString(R.string.canvas_t26_minute_refresher,
                data.model.minutes()), 12, 400, CanvasLook.muted());
        middle.setGravity(Gravity.CENTER);
        foot.addView(middle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        next = turn(true);
        next.setOnClickListener(v -> go(number + 1));
        foot.addView(next, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.dp(48)));
        page.addView(foot);

        dialog.setContentView(page);
        final Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        SafeArea.fit(dialog, page, dark);
        render();
        dialog.show();
    }

    private TextView turn(boolean forward) {
        final TextView t = EditionLook.text(a, "", 14, 600, forward ? CanvasLook.onAccent() : CanvasLook.ink());
        t.setGravity(Gravity.CENTER);
        t.setPadding(CanvasLook.dp(14), 0, CanvasLook.dp(14), 0);
        t.setMinWidth(CanvasLook.dp(76));
        t.setBackground(forward ? CanvasParts.primaryButton() : CanvasParts.secondaryButton());
        return t;
    }

    private void go(int to) {
        if (to < 1 || to > data.pages.size()) {
            return;
        }
        number = to;
        render();
        scroll.scrollTo(0, 0);
    }

    // ------------------------------------------------------------------ the page

    private void render() {
        final int count = data.pages.size();
        final EditionPages.Page page = data.pages.pages.get(number - 1);
        subtitle.setText(a.getString(R.string.canvas_t26_page_of, number, count));
        previous.setText(a.getString(R.string.canvas_t26_previous_page, number - 1));
        previous.setVisibility(number > 1 ? View.VISIBLE : View.INVISIBLE);
        next.setText(a.getString(R.string.canvas_t26_next_page, number + 1));
        next.setVisibility(number < count ? View.VISIBLE : View.INVISIBLE);

        sheet.removeAllViews();
        final LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        final TextView running = EditionLook.mono(a, EditionPrint.head(data.model, data.words), 10, CanvasLook.muted());
        running.setSingleLine(true);
        running.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(running, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView n = EditionLook.mono(a, String.valueOf(number), 10, CanvasLook.muted());
        n.setPadding(CanvasLook.dp(8), 0, 0, 0);
        head.addView(n);
        sheet.addView(head);

        // a section's first page carries its name, as the contents list it
        if (number == data.pages.firstPage(page.section)) {
            final TextView section = EditionLook.text(a, data.model.sections.get(page.section).title, 13, 600,
                    CanvasLook.accent());
            add(section, 12);
        }
        for (EditionPages.Chunk chunk : page.chunks) {
            if (!chunk.heading.isEmpty()) {
                final TextView heading = EditionLook.book(a, chunk.continued
                        ? EditionWords.f(data.words.continued, chunk.heading) : chunk.heading, 22, 2, CanvasLook.ink());
                add(heading, 12);
            }
            String label = "";
            for (Block b : chunk.blocks) {
                add(block(b, !b.label.isEmpty() && !b.label.equals(label)), 12);
                label = b.label;
            }
        }
    }

    private void add(View view, int topDp) {
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = CanvasLook.dp(topDp);
        sheet.addView(view, lp);
    }

    /** One block as the canvas sets its kind; the label only where it changes. */
    private View block(Block b, boolean withLabel) {
        final LinearLayout box = EditionScreen.column(a);
        int labelColor = CanvasLook.muted();
        switch (b.kind) {
            case WRONG:
                box.setBackground(CanvasParts.round(EditionLook.warnCard(), EditionLook.warnLine(), CanvasLook.dp(10)));
                box.setPadding(CanvasLook.dp(12), CanvasLook.dp(10), CanvasLook.dp(12), CanvasLook.dp(10));
                labelColor = CanvasLook.sensor();
                break;
            case TRIED:
                box.setBackground(CanvasParts.round(CanvasLook.accentTint(), CanvasLook.accentLine(), CanvasLook.dp(10)));
                box.setPadding(CanvasLook.dp(12), CanvasLook.dp(10), CanvasLook.dp(12), CanvasLook.dp(10));
                labelColor = CanvasLook.accent();
                break;
            default:
                break;
        }
        if (withLabel) {
            box.addView(EditionLook.mono(a, b.label, 10, labelColor));
        }
        final int gap = withLabel ? CanvasLook.dp(4) : 0;
        switch (b.kind) {
            case LINE:
                box.addView(pad(EditionLook.book(a, b.text, 18, 0, CanvasLook.ink()), gap));
                break;
            case PARAGRAPH:
            case WORDS:
                box.addView(pad(selectable(EditionLook.book(a, b.text, 15, 0, CanvasLook.ink())), gap));
                break;
            case PART:
            case CHAPTER_LINE:
                if (b.kind == EditionModel.Kind.CHAPTER_LINE) {
                    box.setPadding(CanvasLook.dp(12), 0, 0, 0);
                }
                if (!b.title.isEmpty()) {
                    box.addView(EditionLook.text(a, b.title, b.kind == EditionModel.Kind.PART ? 14 : 13, 600, CanvasLook.ink()));
                }
                box.addView(pad(EditionLook.book(a, b.text, 14, 0, CanvasLook.ink2()), CanvasLook.dp(2)));
                break;
            case LINK:
                final LinearLayout chip = EditionScreen.column(a);
                chip.setBackground(CanvasParts.round(CanvasLook.surface2(), 0, CanvasLook.dp(7)));
                chip.setPadding(CanvasLook.dp(10), CanvasLook.dp(7), CanvasLook.dp(10), CanvasLook.dp(7));
                chip.addView(EditionLook.text(a, b.title, 12, 600, CanvasLook.ink()));
                if (!b.text.isEmpty()) {
                    chip.addView(pad(plain(b.text, 13, CanvasLook.ink2()), CanvasLook.dp(2)));
                }
                final LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                chipLp.topMargin = withLabel ? CanvasLook.dp(6) : 0;
                box.addView(chip, chipLp);
                break;
            case OPEN:
            case GUESS:
                box.addView(pad(EditionLook.book(a, b.text, 14, 1, CanvasLook.ink()), gap));
                break;
            default:
                // EXPLAINED, WRONG, TRIED, NOTE: interface text
                if (!b.title.isEmpty()) {
                    box.addView(pad(EditionLook.text(a, b.title, 14, 600, CanvasLook.ink()), gap));
                }
                if (!b.text.isEmpty()) {
                    box.addView(pad(selectable(plain(b.text, 13, CanvasLook.ink())), b.title.isEmpty() ? gap : CanvasLook.dp(2)));
                }
                break;
        }
        if (!b.caption.isEmpty()) {
            box.addView(pad(plain(b.caption, 12, CanvasLook.muted()), CanvasLook.dp(4)));
        }
        return box;
    }

    private TextView plain(CharSequence text, float sp, int color) {
        final TextView t = EditionLook.text(a, text, sp, 400, color);
        t.setLineSpacing(CanvasLook.dp(3), 1f);
        return t;
    }

    private static TextView selectable(TextView t) {
        t.setTextIsSelectable(true);
        return t;
    }

    private static TextView pad(TextView t, int topPx) {
        t.setPadding(0, topPx, 0, 0);
        return t;
    }
}
