package com.foobnix.assist.path;

import android.app.Activity;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.ai.AiHighlights;
import com.foobnix.android.utils.Apps;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.util.List;

/**
 * "Shortest path to one idea", the block of the skeleton screen (canvas artboard Skeleton): a
 * field for what the reader wants to understand and one button; with a path already stored, its
 * chapters in short and "5 of 32 chapters. Skips the 27 you don't need for this." The path itself
 * is made and shown by {@link PathScreen}. Nothing while {@link Assist#PATH} is off.
 */
public final class PathBlock {

    private PathBlock() {
    }

    /**
     * @param close closes the skeleton screen before the path screen opens
     * @return the block with its own margins, or null while the feature is off
     */
    public static View view(Activity a, DocumentController dc, Runnable close) {
        if (!Assist.PATH.isOn() || a == null || dc == null || dc.getCurrentBook() == null) {
            return null;
        }
        final String book = AiHighlights.bookName(dc.getCurrentBook().getPath());
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.line(), dp(16)));

        final LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(PathViews.icon(a, R.drawable.glyphicons_476_sort_attributes, CanvasLook.accent()),
                new LinearLayout.LayoutParams(dp(18), dp(18)));
        final TextView title = PathViews.text(a, a.getString(R.string.canvas_t20_block_title), CanvasLook.TEXT_BODY, 600,
                CanvasLook.ink());
        title.setPadding(dp(8), 0, 0, 0);
        head.addView(title);
        box.addView(head);

        final EditText field = PathViews.field(a, a.getString(R.string.canvas_t20_block_hint), true);
        field.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        field.setMinHeight(CanvasLook.target());
        box.addView(field, PathViews.wide(10));

        // the stored path in short, once read
        final LinearLayout stored = new LinearLayout(a);
        stored.setOrientation(LinearLayout.VERTICAL);
        box.addView(stored, PathViews.wide());

        final TextView button = PathViews.primary(a, a.getString(R.string.canvas_t20_find));
        final Runnable go = () -> {
            PathViews.hideKeyboard(field);
            final String goal = field.getText().toString().trim();
            if (close != null) {
                close.run();
            }
            PathScreen.show(a, dc, goal);
        };
        button.setOnClickListener(v -> go.run());
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE) {
                go.run();
                return true;
            }
            return false;
        });
        box.addView(button, PathViews.wide(48, 10));

        new Thread(() -> {
            final PathData data = PathStore.load(book);
            a.runOnUiThread(() -> {
                if (Apps.isDestroyedActivity(a) || data.isEmpty() || !Assist.PATH.isOn()) {
                    return;
                }
                if (TextUtils.isEmpty(field.getText())) {
                    field.setText(data.goal);
                }
                fill(a, stored, data);
                button.setText(R.string.canvas_t20_open_path);
            });
        }, "@T Path").start();

        final LinearLayout.LayoutParams params = PathViews.wide(12);
        params.leftMargin = dp(16);
        params.rightMargin = dp(16);
        box.setLayoutParams(params);
        return box;
    }

    /** The chapters on their line, 34 dp each, and the count. */
    private static void fill(Activity a, LinearLayout into, PathData data) {
        into.removeAllViews();
        final List<PathData.Chapter> on = data.active();
        for (int i = 0; i < on.size(); i++) {
            final PathData.Chapter c = on.get(i);
            final boolean goal = data.isGoal(c);
            final LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(34));
            row.addView(new PathViews.Dot(a, c.done ? PathViews.Dot.FILLED : goal ? PathViews.Dot.GOAL : PathViews.Dot.RING,
                            i == 0, i == on.size() - 1, CanvasLook.muted()),
                    new LinearLayout.LayoutParams(dp(20), ViewGroup.LayoutParams.MATCH_PARENT));
            final TextView code = PathViews.mono(a, c.code.isEmpty() ? String.valueOf(i + 1) : c.code, CanvasLook.muted());
            code.setTypeface(CanvasLook.mono(a, 400));
            code.setMinWidth(dp(34));
            code.setMaxWidth(dp(60));
            final LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            codeParams.leftMargin = dp(12);
            row.addView(code, codeParams);
            final String name = !c.title.isEmpty() ? c.title : !c.line.isEmpty() ? c.line
                    : a.getString(R.string.canvas_t20_chapter);
            final TextView title = PathViews.text(a, name, 14, goal ? 600 : 400, CanvasLook.ink());
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            final LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            titleParams.leftMargin = dp(12);
            row.addView(title, titleParams);
            final int tag = goal ? R.string.canvas_t20_goal : c.done ? R.string.canvas_t20_done : 0;
            if (tag != 0) {
                final TextView t = PathViews.text(a, a.getString(tag), 12, 600, goal ? CanvasLook.accent() : CanvasLook.muted());
                t.setPadding(dp(8), 0, 0, 0);
                row.addView(t);
            }
            into.addView(row, PathViews.wide(i == 0 ? 8 : 0));
        }
        final int skipped = data.skippedCount();
        final String count = a.getString(data.total == 1 ? R.string.canvas_t20_of_chapter : R.string.canvas_t20_of_chapters,
                on.size(), data.total) + ".";
        into.addView(PathViews.muted(a, skipped > 1 ? count + " " + a.getString(R.string.canvas_t20_block_skips, skipped)
                : count), PathViews.wide(8));
    }

    private static int dp(int dp) {
        return CanvasLook.dp(dp);
    }
}
