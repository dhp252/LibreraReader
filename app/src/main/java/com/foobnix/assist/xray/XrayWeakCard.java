package com.foobnix.assist.xray;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;

import java.util.List;

/**
 * The weak spot card (canvas XrayWeak): the weakest supported idea of the chapter, two sentences on
 * why, and how well each cause beside it is supported (3 bars: strong, fair, weak). Argument map
 * goes back to the map; Go to it closes the X-ray at the idea's first mark.
 */
final class XrayWeakCard {

    /** Goes to an idea's mark (and closes the X-ray). */
    interface Jump {
        void to(XrayIdea idea);
    }

    private static final int MAX_CAUSES = 6;

    private XrayWeakCard() {
    }

    static void show(Activity a, XrayTree tree, XrayRatings ratings, Jump jump) {
        final XrayTree.Node weak = tree.node(ratings.weakestId());
        if (weak == null) {
            return;
        }
        final Dialog dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final LinearLayout sheet = new LinearLayout(a);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackground(CanvasParts.sheet());
        final int gutter = CanvasLook.dp(CanvasLook.GUTTER_DP);
        sheet.setPadding(gutter, CanvasLook.dp(8), gutter, CanvasLook.dp(20));

        final View handle = new View(a);
        handle.setBackground(CanvasParts.round(CanvasLook.lineStrong(), 0, CanvasLook.dp(2)));
        final LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(CanvasLook.dp(36), CanvasLook.dp(4));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.bottomMargin = CanvasLook.dp(12);
        sheet.addView(handle, hl);

        // head: label, title, close
        final LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        final LinearLayout titles = new LinearLayout(a);
        titles.setOrientation(LinearLayout.VERTICAL);
        String label = a.getString(R.string.xray_weak_label);
        if (weak.idea.firstPage >= 0) {
            label += " · " + a.getString(R.string.xray_page, weak.idea.firstPage + 1);
        }
        final TextView mono = text(a, label, 11, 500, XrayScreen.weakText());
        mono.setTypeface(CanvasLook.mono(a, 500));
        mono.setLetterSpacing(0.04f);
        mono.setAllCaps(true);
        titles.addView(mono);
        titles.addView(text(a, TextUtils.isEmpty(ratings.weakTitle) ? weak.idea.text : ratings.weakTitle, 18, 600,
                CanvasLook.ink()));
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView close = text(a, "✕", 16, 400, CanvasLook.ink2());
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(a.getString(R.string.close));
        close.setBackground(CanvasParts.ripple(CanvasParts.round(CanvasLook.surface2(), 0, CanvasLook.radius())));
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        sheet.addView(head);

        // why it is weak
        final XrayRatings.Entry entry = ratings.get(weak.idea.id);
        String why = ratings.weakWhy;
        if (TextUtils.isEmpty(why)) {
            why = weak.idea.text + (entry != null && !entry.why.isEmpty() ? " (" + entry.why + ")" : "");
        }
        final TextView explanation = text(a, why, 14, 400, CanvasLook.ink());
        explanation.setLineSpacing(0, 1.2f);
        sheet.addView(explanation, spaced(12));

        // how well each cause is supported
        final List<XrayTree.Node> causes = tree.causesAround(weak.idea.id, MAX_CAUSES);
        if (!causes.isEmpty()) {
            final XrayTree.Node holding = XrayTree.causeHolding(causes, weak);
            final LinearLayout box = new LinearLayout(a);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackground(CanvasParts.round(CanvasLook.surface(), 0, CanvasLook.dp(CanvasLook.RADIUS_LARGE_DP)));
            final int p = CanvasLook.dp(12);
            box.setPadding(p, p, p, p);
            final TextView title = text(a, a.getString(R.string.xray_causes_title), 11, 400, CanvasLook.muted());
            title.setTypeface(CanvasLook.mono(a, 400));
            title.setLetterSpacing(0.04f);
            title.setAllCaps(true);
            box.addView(title);
            for (XrayTree.Node cause : causes) {
                box.addView(causeRow(a, cause, XrayTree.strength(cause, ratings), ratings.get(cause.idea.id), cause == holding),
                        spaced(8));
            }
            sheet.addView(box, spaced(12));
        }

        // Argument map, Go to it
        final LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        final TextView map = button(a, R.string.xray_tab_map, false);
        map.setOnClickListener(v -> dialog.dismiss());
        final TextView go = button(a, R.string.xray_go_to_it, true);
        go.setOnClickListener(v -> {
            dialog.dismiss();
            jump.to(weak.idea);
        });
        go.setEnabled(weak.idea.first != null || weak.idea.firstPage >= 0);
        final LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, CanvasLook.dp(48), 1);
        final LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, CanvasLook.dp(48), 1);
        right.leftMargin = CanvasLook.dp(8);
        buttons.addView(map, left);
        buttons.addView(go, right);
        sheet.addView(buttons, spaced(12));

        dialog.setContentView(sheet);
        final Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.18f);
        }
        dialog.show();
    }

    /** "Practice  ▬▬▬  strong · numbers"; the weak spot's cause in orange and bold. */
    private static View causeRow(Activity a, XrayTree.Node cause, XrayRatings.Rating strength, XrayRatings.Entry entry,
                                 boolean weak) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        final String name = entry != null && !entry.label.isEmpty() ? entry.label : cause.idea.text;
        final TextView label = text(a, name, 13, weak ? 600 : 400, CanvasLook.ink());
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        final LinearLayout bars = new LinearLayout(a);
        bars.setOrientation(LinearLayout.HORIZONTAL);
        final int filled = strength == null ? 0 : strength.bars;
        final int on = weak ? XrayScreen.weakLine() : CanvasLook.accent();
        for (int i = 0; i < 3; i++) {
            final View bar = new View(a);
            bar.setBackground(CanvasParts.round(i < filled ? on : CanvasLook.lineStrong(), 0, CanvasLook.dp(3)));
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(CanvasLook.dp(18), CanvasLook.dp(6));
            lp.leftMargin = i == 0 ? CanvasLook.dp(10) : CanvasLook.dp(3);
            bars.addView(bar, lp);
        }
        row.addView(bars);

        String says = strength == null ? "–" : a.getString(XrayScreen.ratingName(strength)).toLowerCase(java.util.Locale.getDefault());
        if (entry != null && !entry.why.isEmpty()) {
            says += " · " + entry.why;
        }
        final TextView how = text(a, says, CanvasLook.TEXT_SMALL, weak ? 600 : 400, weak ? XrayScreen.weakText() : CanvasLook.ink2());
        how.setPadding(CanvasLook.dp(10), 0, 0, 0);
        how.setMaxLines(2);
        row.addView(how, new LinearLayout.LayoutParams(CanvasLook.dp(110), ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private static LinearLayout.LayoutParams spaced(int topDp) {
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = CanvasLook.dp(topDp);
        return lp;
    }

    private static TextView text(Activity a, String s, float sp, int weight, int color) {
        final TextView t = new TextView(a);
        t.setText(s);
        t.setTypeface(CanvasLook.ui(a, weight));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private static TextView button(Activity a, int s, boolean primary) {
        final TextView t = text(a, a.getString(s), CanvasLook.TEXT_TITLE, 600, primary ? CanvasLook.onAccent() : CanvasLook.ink());
        t.setGravity(Gravity.CENTER);
        t.setBackground(primary ? CanvasParts.primaryButton() : CanvasParts.secondaryButton());
        return t;
    }
}
