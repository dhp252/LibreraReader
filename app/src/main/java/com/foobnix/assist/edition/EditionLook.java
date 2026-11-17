package com.foobnix.assist.edition;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import com.foobnix.android.utils.LOG;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;

/**
 * What the edition's screens are built from: the canvas tokens for the interface, Literata (the
 * app's own reading face, the system serif when it cannot be read) for the reading text, the
 * colours of the cover and of the "what you got wrong" card by day and night, and the edition's
 * words from the string resources.
 */
final class EditionLook {

    /** The app's reading fonts in the assets (see FontExtractor). */
    private static final String FONTS = "fonts-fork/";

    // the "what you got wrong" card (canvas EditionPage)
    private static final int WARN_CARD = 0xFFFFF8F3;
    private static final int WARN_LINE = 0xFFF3C6A5;
    private static final int WARN_CARD_N = 0xFF2B2018;
    private static final int WARN_LINE_N = 0xFF7A4A24;
    /** The orange dot of the cover's sample. */
    static final int WARN_DOT = 0xFFF27420;

    private static Typeface serif;
    private static Typeface serifItalic;
    private static Typeface serifBold;

    private EditionLook() {
    }

    /** The reading face: 0 regular, 1 italic, 2 bold. */
    static synchronized Typeface serif(Context c, int style) {
        if (serif == null) {
            serif = font(c, "Literata-Regular.ttf", Typeface.SERIF);
            serifItalic = font(c, "Literata-Italic.ttf", Typeface.create(Typeface.SERIF, Typeface.ITALIC));
            serifBold = font(c, "Literata-Bold.ttf", Typeface.create(Typeface.SERIF, Typeface.BOLD));
        }
        return style == 1 ? serifItalic : style == 2 ? serifBold : serif;
    }

    private static Typeface font(Context c, String name, Typeface fallback) {
        try {
            final Typeface t = Typeface.createFromAsset(c.getApplicationContext().getAssets(), FONTS + name);
            return t == null ? fallback : t;
        } catch (Exception e) {
            LOG.e(e);
            return fallback;
        }
    }

    static int warnCard() {
        return CanvasLook.isNight() ? WARN_CARD_N : WARN_CARD;
    }

    static int warnLine() {
        return CanvasLook.isNight() ? WARN_LINE_N : WARN_LINE;
    }

    /** The cover: the canvas's ink card by day, a raised surface at night. */
    static int coverFill() {
        return CanvasLook.isNight() ? CanvasLook.SURFACE_2_N : CanvasLook.INK;
    }

    static int coverText() {
        return CanvasLook.isNight() ? CanvasLook.INK_N : 0xFFFFFFFF;
    }

    static int coverSoft() {
        return CanvasLook.isNight() ? CanvasLook.INK_2_N : 0xFFD5D8DE;
    }

    static int coverMuted() {
        return CanvasLook.isNight() ? CanvasLook.MUTED_N : 0xFFA5ABB6;
    }

    /** Interface text in Instrument Sans. */
    static TextView text(Context c, CharSequence s, float sp, int weight, int color) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(CanvasLook.ui(c, weight));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    /** Reading text in the reading face ({@code style}: 0 regular, 1 italic, 2 bold), its lines set a little apart. */
    static TextView book(Context c, CharSequence s, float sp, int style, int color) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(serif(c, style));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setLineSpacing(0, 1.15f);
        t.setTextColor(color);
        return t;
    }

    /** A small label in JetBrains Mono capitals. */
    static TextView mono(Context c, CharSequence s, float sp, int color) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(CanvasLook.mono(c, 500));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setLetterSpacing(0.05f);
        t.setAllCaps(true);
        t.setTextColor(color);
        return t;
    }

    /** A filled accent button, or an outlined one, with the canvas's 14 dp corners. */
    static TextView button(Context c, CharSequence s, boolean primary) {
        final TextView t = text(c, s, 15, 600, primary ? CanvasLook.onAccent() : CanvasLook.ink());
        t.setGravity(Gravity.CENTER);
        t.setPadding(CanvasLook.dp(16), 0, CanvasLook.dp(16), 0);
        t.setSingleLine(true);
        t.setBackground(primary ? CanvasParts.primaryButton() : CanvasParts.secondaryButton());
        return t;
    }

    /** The edition's words from the string resources. */
    static EditionWords words(Context c) {
        final EditionWords w = new EditionWords();
        w.yourEdition = c.getString(R.string.canvas_t26_title);
        w.sectionSkeleton = c.getString(R.string.canvas_t26_section_skeleton);
        w.sectionWords = c.getString(R.string.canvas_t26_section_words);
        w.sectionWrong = c.getString(R.string.canvas_t26_section_wrong);
        w.sectionConnections = c.getString(R.string.canvas_t26_section_connections);
        w.sectionOpen = c.getString(R.string.canvas_t26_section_open);
        w.labelLine = c.getString(R.string.canvas_t26_label_line);
        w.labelWords = c.getString(R.string.canvas_t26_section_words);
        w.labelTidied = c.getString(R.string.canvas_t26_label_tidied);
        w.labelExplained = c.getString(R.string.canvas_t26_label_explained);
        w.labelWrong = c.getString(R.string.canvas_t26_section_wrong);
        w.labelTried = c.getString(R.string.canvas_t26_label_tried);
        w.labelTriedDates = c.getString(R.string.canvas_t26_label_tried_dates);
        w.labelConnections = c.getString(R.string.canvas_t26_section_connections);
        w.labelNote = c.getString(R.string.canvas_t26_label_note);
        w.labelOpen = c.getString(R.string.canvas_t26_label_open);
        w.labelGuess = c.getString(R.string.canvas_t26_label_guess);
        w.missed = c.getString(R.string.canvas_t26_missed);
        w.unsure = c.getString(R.string.canvas_t26_unsure);
        w.leftOut = c.getString(R.string.canvas_t26_left_out);
        w.bookSays = c.getString(R.string.canvas_t26_book_says);
        w.rightSince = c.getString(R.string.canvas_t26_right_since);
        w.notYet = c.getString(R.string.canvas_t26_not_yet);
        w.missedShort = c.getString(R.string.canvas_t26_missed_short);
        w.unsureShort = c.getString(R.string.canvas_t26_unsure_short);
        w.rightSinceShort = c.getString(R.string.canvas_t26_right_since_short);
        w.measure = c.getString(R.string.canvas_t26_measure);
        w.verdictHeld = c.getString(R.string.canvas_t26_verdict_held);
        w.verdictMostly = c.getString(R.string.canvas_t26_verdict_mostly);
        w.verdictDidnt = c.getString(R.string.canvas_t26_verdict_didnt);
        w.same = c.getString(R.string.canvas_t26_same);
        w.disagrees = c.getString(R.string.canvas_t26_disagrees);
        w.extendsIdea = c.getString(R.string.canvas_t26_extends);
        w.debateOn = c.getString(R.string.canvas_t26_debate_on);
        w.guess = c.getString(R.string.canvas_t26_guess);
        w.howSettled = c.getString(R.string.canvas_t26_how_settled);
        w.settledEstablished = c.getString(R.string.canvas_t26_settled_established);
        w.settledDebated = c.getString(R.string.canvas_t26_settled_debated);
        w.settledSpeculative = c.getString(R.string.canvas_t26_settled_speculative);
        w.front = c.getString(R.string.canvas_t26_front);
        w.pagesFrom = c.getString(R.string.canvas_t26_pages_from);
        w.continued = c.getString(R.string.canvas_t26_continued);
        w.kindRecalls = c.getString(R.string.canvas_t26_kind_recalls);
        w.kindMisses = c.getString(R.string.canvas_t26_kind_misses);
        w.kindExperiments = c.getString(R.string.canvas_t26_kind_experiments);
        w.kindLinks = c.getString(R.string.canvas_t26_kind_links);
        w.kindQuestions = c.getString(R.string.canvas_t26_kind_questions);
        final String[] months = c.getResources().getStringArray(R.array.canvas_t26_months);
        if (months.length >= 12) {
            w.months = months;
        }
        return w;
    }
}
