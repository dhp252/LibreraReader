package com.foobnix.canvas.prefs;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;

import com.foobnix.android.utils.LOG;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.TintUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * The library's Preferences tab (fragment_preferences.xml, PrefFragment2) in the look of the
 * "Book That Reads You Back" canvas, done in code over the inflated panel so the layout file
 * stays as it is: the page colour behind everything, each section head set as a small JetBrains
 * Mono label, the settings under it gathered into a rounded card, rows at least 48 dp tall, row
 * titles 15 sp in ink, secondary lines 12 sp muted, boxes and links in the accent, dividers as
 * hairlines.
 * <p>
 * Every entry point returns at once while {@link CanvasLook#isOn()} is false, so the panel is
 * left exactly as the old code drew it. The walk only reparents views (findViewById still finds
 * them) and only the first time; later calls repaint, so the panel's own colour passes
 * (onTintChanged, a link turned into a button) can run before it in any order.
 */
public final class PrefsLook {

    private static final String GROUPED = "canvas_t07_grouped";

    // roles kept under R.id.canvas_t07_role
    private static final String HEADER = "header";
    private static final String SECTION = "section";
    private static final String CARD = "card";
    private static final String BAR = "bar";
    private static final String TITLE = "title";
    private static final String BODY = "body";
    private static final String SMALL = "small";
    private static final String BOX = "box";

    /** The section heads of fragment_preferences.xml (a title, or a row holding one). */
    private static final int[] HEADS = {R.id.section1, R.id.section2, R.id.section3, R.id.section4,
            R.id.section5, R.id.section6, R.id.section7, R.id.section8, R.id.section9, R.id.webdavHeader,
            R.id.aiHeader};

    /** Status lines under a head: secondary text. */
    private static final int[] SECONDARY = {R.id.syncInfo, R.id.syncInfo2, R.id.webdavStatus,
            R.id.aiPrefStatus, R.id.pVersion};

    /** Text drawn in colours the code picks on purpose (the day and night samples). */
    private static final int[] KEEP = {R.id.onDayColor, R.id.onNigthColor, R.id.profileLetter};

    private PrefsLook() {
    }

    /**
     * Puts the panel into the canvas look, or brings it back to it after one of the panel's
     * own colour passes. Safe to call any number of times.
     */
    public static void apply(View root) {
        if (root == null || !CanvasLook.isOn()) {
            return;
        }
        try {
            root.setBackgroundColor(CanvasLook.bg());
            bar(root.findViewById(R.id.panelRecent));
            View scroll = root.findViewById(R.id.scroll);
            if (scroll instanceof ViewGroup && ((ViewGroup) scroll).getChildCount() > 0
                    && ((ViewGroup) scroll).getChildAt(0) instanceof LinearLayout) {
                scroll.setBackgroundColor(CanvasLook.bg());
                LinearLayout list = (LinearLayout) ((ViewGroup) scroll).getChildAt(0);
                if (!GROUPED.equals(list.getTag())) {
                    list.setTag(GROUPED);
                    list.setPadding(0, CanvasLook.dp(4), 0, CanvasLook.dp(24));
                    List<LinearLayout> cards = new ArrayList<>();
                    group(list, cards);
                    keepCardsInStep(root, cards);
                }
                paint(list);
            }
            profile(root.findViewById(R.id.profileLetter));
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /**
     * A link of the panel (the textLink style) after it was drawn as a button: the word in the
     * accent, the ring a hairline, a mark inside it in the accent too.
     */
    public static void link(View view) {
        if (!(view instanceof TextView) || !CanvasLook.isOn()) {
            return;
        }
        TextView t = (TextView) view;
        int accent = CanvasLook.accent();
        t.setTextColor(accent);
        t.setTypeface(CanvasLook.ui(t.getContext(), 500));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_TITLE);
        t.setMinHeight(CanvasLook.dp(40));
        t.setGravity((t.getGravity() & ~Gravity.VERTICAL_GRAVITY_MASK) | Gravity.CENTER_VERTICAL);
        TintUtil.setRingColor(t, CanvasLook.lineStrong());
        if (!keepsOwnColours(t)) {
            for (Drawable icon : t.getCompoundDrawables()) {
                if (icon != null) {
                    icon.setColorFilter(accent, PorterDuff.Mode.SRC_IN);
                }
            }
        }
    }

    /**
     * A plain AlertDialog the panel opened, after show(): the page-coloured sheet with rounded
     * corners, the title and message in the interface font, the buttons in the accent and in
     * sentence case, boxes in the accent.
     */
    public static AlertDialog dialog(AlertDialog d) {
        if (d == null || !CanvasLook.isOn()) {
            return d;
        }
        try {
            Window w = d.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new InsetDrawable(
                        CanvasParts.round(CanvasLook.bg(), 0, CanvasLook.dp(CanvasLook.RADIUS_LARGE_DP)),
                        CanvasLook.dp(16)));
            }
            View message = d.findViewById(android.R.id.message);
            if (message instanceof TextView) {
                CanvasParts.text((TextView) message, CanvasLook.TEXT_BODY, 400);
                ((TextView) message).setTextColor(CanvasLook.ink2());
            }
            dialogButton(d.getButton(AlertDialog.BUTTON_POSITIVE), CanvasLook.accent());
            dialogButton(d.getButton(AlertDialog.BUTTON_NEGATIVE), CanvasLook.ink2());
            dialogButton(d.getButton(AlertDialog.BUTTON_NEUTRAL), CanvasLook.ink2());
            if (w != null) {
                titles(w.getDecorView());
                boxes(w.getDecorView());
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return d;
    }

    // ---- the panel

    /** The head bar: the page colour, the title as a heading in sentence case, an ink close mark. */
    private static void bar(View bar) {
        if (bar == null) {
            return;
        }
        // Made once and then only recoloured: the tab sets an alpha on this face after it is
        // inflated, and a new drawable would lose it.
        if (!BAR.equals(bar.getTag(R.id.canvas_t07_role)) || !(bar.getBackground() instanceof GradientDrawable)) {
            bar.setBackground(new GradientDrawable());
            bar.setTag(R.id.canvas_t07_role, BAR);
        }
        ((GradientDrawable) bar.getBackground()).setColor(CanvasLook.bg());

        View name = bar.findViewById(R.id.recentName);
        if (name instanceof TextView) {
            CanvasParts.text((TextView) name, CanvasLook.TEXT_HEADING, 600);
            ((TextView) name).setAllCaps(false);
        }
        View close = bar.findViewById(R.id.onCloseApp);
        if (close instanceof ImageView) {
            ((ImageView) close).setImageTintList(ColorStateList.valueOf(CanvasLook.ink()));
            close.setBackground(CanvasParts.ripple(null));
        }
    }

    /** The profile letter: a round in the accent tint. */
    private static void profile(View v) {
        if (!(v instanceof TextView)) {
            return;
        }
        TextView t = (TextView) v;
        GradientDrawable round = new GradientDrawable();
        round.setShape(GradientDrawable.OVAL);
        round.setSize(CanvasLook.dp(40), CanvasLook.dp(40));
        round.setColor(CanvasLook.accentTint());
        round.setStroke(Math.max(1, CanvasLook.dp(1)), CanvasLook.accentLine());
        t.setBackground(round);
        t.setTextColor(CanvasLook.accent());
        t.setTypeface(CanvasLook.ui(t.getContext(), 600));
    }

    /**
     * Splits a column at its section heads and gathers what stands between two heads into a
     * card put where the first of them stood. A column that starts with a head of its own
     * (the sync, WebDAV and AI sections) is split the same way inside itself, so hiding the
     * column still hides its head and its card together.
     */
    private static void group(LinearLayout parent, List<LinearLayout> cards) {
        List<View> kids = new ArrayList<>();
        for (int i = 0; i < parent.getChildCount(); i++) {
            kids.add(parent.getChildAt(i));
        }
        List<View> run = new ArrayList<>();
        for (View v : kids) {
            if (isHeader(v)) {
                close(parent, run, cards);
                v.setTag(R.id.canvas_t07_role, HEADER);
            } else if (isSection(v)) {
                close(parent, run, cards);
                v.setTag(R.id.canvas_t07_role, SECTION);
                group((LinearLayout) v, cards);
            } else {
                run.add(v);
            }
        }
        close(parent, run, cards);
    }

    private static void close(LinearLayout parent, List<View> run, List<LinearLayout> cards) {
        if (run.isEmpty()) {
            return;
        }
        LinearLayout card = new LinearLayout(parent.getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setTag(R.id.canvas_t07_role, CARD);
        int at = parent.indexOfChild(run.get(0));
        for (View v : run) {
            parent.removeView(v);
            card.addView(v);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(CanvasLook.dp(CanvasLook.GUTTER_DP), 0, CanvasLook.dp(CanvasLook.GUTTER_DP), CanvasLook.dp(10));
        parent.addView(card, at, lp);
        cards.add(card);
        run.clear();
    }

    /** A card whose rows are all hidden is hidden too, so no empty box is left standing. */
    private static void keepCardsInStep(View root, final List<LinearLayout> cards) {
        final Runnable step = () -> {
            for (LinearLayout card : cards) {
                boolean shown = false;
                for (int i = 0; i < card.getChildCount() && !shown; i++) {
                    shown = card.getChildAt(i).getVisibility() != View.GONE;
                }
                int want = shown ? View.VISIBLE : View.GONE;
                if (card.getVisibility() != want) {
                    card.setVisibility(want);
                }
            }
        };
        step.run();
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                step.run();
            }
        });
    }

    private static boolean isHeader(View v) {
        if (v == null) {
            return false;
        }
        if (HEADER.equals(v.getTag(R.id.canvas_t07_role))) {
            return true;
        }
        int id = v.getId();
        if (id != View.NO_ID) {
            for (int h : HEADS) {
                if (id == h) {
                    return true;
                }
            }
        }
        if (v instanceof TextView) {
            return isTitleText((TextView) v);
        }
        if (v instanceof LinearLayout && ((LinearLayout) v).getOrientation() == LinearLayout.HORIZONTAL) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c instanceof TextView && isTitleText((TextView) c)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A column of settings that starts with its own head. */
    private static boolean isSection(View v) {
        if (!(v instanceof LinearLayout)) {
            return false;
        }
        LinearLayout l = (LinearLayout) v;
        return l.getOrientation() == LinearLayout.VERTICAL && l.getChildCount() > 1 && isHeader(l.getChildAt(0));
    }

    /** The textSectionTitle style: the textLinkWhite tag, set in capitals. */
    private static boolean isTitleText(TextView t) {
        return !(t instanceof CompoundButton) && "textLinkWhite".equals(t.getTag()) && allCaps(t);
    }

    private static boolean allCaps(TextView t) {
        if (Build.VERSION.SDK_INT >= 28) {
            return t.isAllCaps();
        }
        return t.getTransformationMethod() != null && t.getTransformationMethod()
                                                       .getClass()
                                                       .getName()
                                                       .contains("AllCaps");
    }

    private static void paint(View v) {
        Object role = v.getTag(R.id.canvas_t07_role);
        Object tag = v.getTag();
        if (HEADER.equals(role)) {
            header(v);
            return;
        }
        // as TxtUtils.updateAllLinks: a view that keeps its own colours is passed over whole
        if ("no_tint".equals(tag)) {
            return;
        }
        if (tag != null && tag.toString().startsWith("with_tint")) {
            v.setBackgroundColor(CanvasLook.line());
            return;
        }
        if (has(KEEP, v.getId())) {
            return;
        }
        if (CARD.equals(role)) {
            v.setBackground(CanvasParts.card());
            v.setPadding(CanvasLook.dp(8), CanvasLook.dp(6), CanvasLook.dp(8), CanvasLook.dp(6));
        }
        if (v instanceof CardView) {
            // a tab row in the tab-order list: a plain white row on the card, no shadow
            CardView c = (CardView) v;
            c.setCardBackgroundColor(CanvasLook.bg());
            c.setCardElevation(0);
            c.setElevation(0);
            c.setRadius(CanvasLook.dp(CanvasLook.RADIUS_SMALL_DP));
        }
        if (v instanceof ViewGroup) {
            if (v instanceof LinearLayout && v.getParent() instanceof View
                    && CARD.equals(((View) v.getParent()).getTag(R.id.canvas_t07_role))
                    && ((LinearLayout) v).getOrientation() == LinearLayout.HORIZONTAL) {
                row((LinearLayout) v);
            }
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                paint(g.getChildAt(i));
            }
            return;
        }
        if (v instanceof EditText) {
            return;
        }
        if (v instanceof CompoundButton) {
            box((CompoundButton) v);
        } else if (v instanceof Button) {
            button((Button) v);
        } else if (v instanceof TextView) {
            if (isLink(v)) {
                link(v);
            } else {
                text((TextView) v);
            }
        } else if (v instanceof SeekBar) {
            ColorStateList accent = ColorStateList.valueOf(CanvasLook.accent());
            SeekBar s = (SeekBar) v;
            s.setProgressTintList(accent);
            s.setThumbTintList(accent);
            s.setProgressBackgroundTintList(ColorStateList.valueOf(CanvasLook.lineStrong()));
        } else if (v instanceof ProgressBar) {
            ((ProgressBar) v).setIndeterminateTintList(ColorStateList.valueOf(CanvasLook.accent()));
        } else if (v instanceof ImageView) {
            mark((ImageView) v);
        }
    }

    private static boolean isLink(View v) {
        Object tag = v.getTag();
        return "textLink".equals(tag) || "textLinkWhite".equals(tag);
    }

    /** A head: no fill, its title as a muted uppercase label set in over the card's edge. */
    private static void header(View v) {
        v.setBackground(null);
        if (v instanceof TextView) {
            label((TextView) v);
            v.setPadding(CanvasLook.dp(20), CanvasLook.dp(18), CanvasLook.dp(20), CanvasLook.dp(6));
            return;
        }
        if (!(v instanceof ViewGroup)) {
            return;
        }
        v.setPadding(CanvasLook.dp(20), CanvasLook.dp(10), CanvasLook.dp(16), 0);
        ViewGroup g = (ViewGroup) v;
        View title = titleOf(g);
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c == title) {
                c.setTag(R.id.canvas_t07_role, HEADER);
                label((TextView) c);
                c.setPadding(0, CanvasLook.dp(8), 0, CanvasLook.dp(6));
            } else if (c instanceof TextView && isLink(c)) {
                // an action standing in the head (Google Drive's sign in)
                link(c);
            }
        }
    }

    /** The title of a head row: its textSectionTitle, else its first line of text. */
    private static View titleOf(ViewGroup g) {
        View first = null;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (!(c instanceof TextView)) {
                continue;
            }
            if (isTitleText((TextView) c) || HEADER.equals(c.getTag(R.id.canvas_t07_role))) {
                return c;
            }
            if (first == null) {
                first = c;
            }
        }
        return first;
    }

    private static void label(TextView t) {
        t.setBackground(null);
        CanvasParts.label(t, false);
    }

    /** A row of a card: at least 48 dp tall, its content centred on the height. */
    private static void row(LinearLayout r) {
        r.setMinimumHeight(CanvasLook.dp(48));
        int g = r.getGravity();
        if ((g & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.TOP) {
            r.setGravity((g & ~Gravity.VERTICAL_GRAVITY_MASK) | Gravity.CENTER_VERTICAL);
        }
    }

    /**
     * Plain text: a row's title (bold in the layout, or standing in a row) 15 sp ink; a status
     * line or small print 12 sp muted; a paragraph 13 sp in the second ink. Sorted once, as
     * the sizes and faces it is sorted by are the ones this sets.
     */
    private static void text(TextView t) {
        Object role = t.getTag(R.id.canvas_t07_role);
        if (role == null) {
            role = kindOf(t);
            t.setTag(R.id.canvas_t07_role, role);
            if (allCaps(t)) {
                t.setAllCaps(false);
            }
        }
        if (SMALL.equals(role)) {
            CanvasParts.secondary(t);
        } else if (TITLE.equals(role)) {
            CanvasParts.text(t, CanvasLook.TEXT_BODY, 500);
        } else {
            CanvasParts.text(t, 13f, 400);
            t.setTextColor(CanvasLook.ink2());
        }
    }

    private static String kindOf(TextView t) {
        if (has(SECONDARY, t.getId()) || sp(t) < 12.5f) {
            return SMALL;
        }
        Typeface face = t.getTypeface();
        if (face != null && face.isBold()) {
            return TITLE;
        }
        if (t.getParent() instanceof LinearLayout
                && ((LinearLayout) t.getParent()).getOrientation() == LinearLayout.HORIZONTAL) {
            return TITLE;
        }
        return BODY;
    }

    /** A check box or switch: 15 sp ink (13 sp where the layout made it small), accent when on. */
    private static void box(CompoundButton b) {
        Object role = b.getTag(R.id.canvas_t07_role);
        if (role == null) {
            role = sp(b) < 12.5f ? SMALL : BOX;
            b.setTag(R.id.canvas_t07_role, role);
        }
        b.setTypeface(CanvasLook.ui(b.getContext(), 400));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, SMALL.equals(role) ? 13f : CanvasLook.TEXT_BODY);
        b.setTextColor(CanvasLook.ink());
        b.setMinHeight(CanvasLook.dp(48));
        int[][] states = {{android.R.attr.state_checked}, {}};
        ColorStateList track = new ColorStateList(states, new int[]{CanvasLook.accent(), CanvasLook.muted()});
        if (b instanceof Switch) {
            ((Switch) b).setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
            ((Switch) b).setTrackTintList(track);
        } else if (b instanceof SwitchCompat) {
            ((SwitchCompat) b).setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
            ((SwitchCompat) b).setTrackTintList(track);
        } else {
            b.setButtonTintList(track);
        }
    }

    private static void button(Button b) {
        b.setBackground(CanvasParts.secondaryButton());
        b.setTextColor(CanvasLook.accent());
        b.setTypeface(CanvasLook.ui(b.getContext(), 600));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_TITLE);
        b.setAllCaps(false);
        b.setMinHeight(CanvasLook.dp(40));
        b.setPadding(CanvasLook.dp(14), 0, CanvasLook.dp(14), 0);
    }

    /** A mark the colour pass tinted: the second ink, its ring a hairline. Colour chips are left. */
    private static void mark(ImageView img) {
        if (img.getId() == R.id.itColor || img.getImageTintList() == null) {
            return;
        }
        img.setImageTintList(ColorStateList.valueOf(CanvasLook.ink2()));
        TintUtil.setRingColor(img, CanvasLook.lineStrong());
    }

    // ---- dialogs

    private static void dialogButton(Button b, int color) {
        if (b == null) {
            return;
        }
        b.setTextColor(color);
        b.setTypeface(CanvasLook.ui(b.getContext(), 600));
        b.setAllCaps(false);
    }

    /** The dialog's own title: a line of text outside the content the panel put in it. */
    private static void titles(View v) {
        if (v.getId() == android.R.id.custom || v.getId() == android.R.id.message || v instanceof Button
                || v instanceof EditText) {
            return;
        }
        if (v instanceof TextView) {
            CanvasParts.text((TextView) v, 17f, 600);
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                titles(g.getChildAt(i));
            }
        }
    }

    private static void boxes(View v) {
        if (v instanceof CompoundButton) {
            box((CompoundButton) v);
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                boxes(g.getChildAt(i));
            }
        }
    }

    // ---- helpers

    /** A link with a picture standing in front of it keeps the picture's own colours. */
    private static boolean keepsOwnColours(TextView button) {
        if (!(button.getParent() instanceof ViewGroup)) {
            return false;
        }
        ViewGroup row = (ViewGroup) button.getParent();
        int at = row.indexOfChild(button);
        if (at <= 0 || !(row.getChildAt(at - 1) instanceof ImageView)) {
            return false;
        }
        Object tag = row.getChildAt(at - 1).getTag();
        return tag != null && "no_tint".equals(tag.toString());
    }

    private static float sp(TextView t) {
        float scaled = t.getResources().getDisplayMetrics().scaledDensity;
        return scaled <= 0 ? 14f : t.getTextSize() / scaled;
    }

    private static boolean has(int[] ids, int id) {
        if (id == View.NO_ID) {
            return false;
        }
        for (int i : ids) {
            if (i == id) {
                return true;
            }
        }
        return false;
    }
}
