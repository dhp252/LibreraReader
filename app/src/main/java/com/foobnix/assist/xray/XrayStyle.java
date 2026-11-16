package com.foobnix.assist.xray;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.foobnix.ai.AiCategory;
import com.foobnix.ai.AiHighlights;
import com.foobnix.ai.MessageAiHighlights;
import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.assist.AssistStore;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.model.AppState;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The X-ray drawing of the key-passage marks (canvas "Argument X-ray"): the same marks, drawn by
 * the job they do. A claim (main idea) gets the claim tint; evidence a solid accent underline; an
 * example a dotted grey underline; a caveat, which a rendered page cannot set in italics, is muted:
 * a wash of the page colour over it (the text reads lighter) and a thin muted underline. Concepts
 * and "apply" keep a light fill of their own colour. Must-read paragraph bars take the kind's colour.
 * <p>
 * Only while {@link Assist#XRAY} is on and the reader has not turned the X-ray marks off (Argument
 * X-ray > Marks in text); otherwise the reader views draw the marks as before.
 */
public final class XrayStyle {

    /** The canvas's grey for an example's dotted underline (day). */
    public static final int EXAMPLE_GREY = 0xFF7C828E;
    /** Store key of the "X-ray marks on the page" choice, in the library file. */
    static final String KEY = "xray:style";

    private static final int CLAIM_ALPHA_DAY = 0x26;
    private static final int CLAIM_ALPHA_NIGHT = 0x40;
    private static final int OTHER_ALPHA = 0x33;
    private static final int CAVEAT_WASH_ALPHA = 0x66;
    private static final int BAR_ALPHA = 200;

    /** Null until read from the store; the marks are X-rayed meanwhile. */
    private static volatile Boolean marks;
    private static final AtomicBoolean loading = new AtomicBoolean();

    private static final Paint fill = new Paint();
    private static final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);

    private XrayStyle() {
    }

    /** Key passages are drawn the X-ray way now. */
    public static boolean isOn() {
        if (!Assist.XRAY.isOn()) {
            return false;
        }
        final Boolean on = marks;
        if (on == null) {
            load();
            return true;
        }
        return on;
    }

    /** The reader's choice, X-ray marks on the page (default on). */
    public static boolean marksShown() {
        final Boolean on = marks;
        return on == null || on;
    }

    /** Turns the X-ray marks on the page on or off, for every book; the pages draw again. */
    public static void setMarksShown(final boolean on) {
        marks = on;
        EventBus.getDefault().post(new MessageAiHighlights());
        new Thread(() -> {
            try {
                AssistStore.get().put(AssistStore.LIBRARY, KEY, new JSONObject().put("marks", on));
            } catch (Exception e) {
                LOG.e(e);
            }
        }, "@T X-ray style").start();
    }

    private static void load() {
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        new Thread(() -> {
            boolean on = true;
            try {
                final JSONObject o = AssistStore.get().item(AssistStore.LIBRARY, KEY);
                on = o == null || o.optBoolean("marks", true);
            } catch (Exception e) {
                LOG.e(e);
            }
            final boolean was = marksShown();
            if (marks == null) {
                marks = on;
            }
            if (was != on) {
                EventBus.getDefault().post(new MessageAiHighlights());
            }
        }, "@T X-ray style").start();
    }

    /**
     * Draws one band of {@link AiHighlights#bandsFor} at {@code rect} (screen coordinates) the
     * X-ray way, when the X-ray is on; false (nothing drawn) otherwise, so the caller draws it as
     * before. Called from the reader views' draw.
     */
    public static boolean draw(Canvas canvas, RectF rect, RectF band) {
        if (!(band instanceof AiHighlights.Band) || !isOn()) {
            return false;
        }
        final AiHighlights.Band b = (AiHighlights.Band) band;
        final AppState s = AppState.get();
        final boolean dark = !s.isDayNotInvert;
        if (!b.bar) {
            drawMark(canvas, rect, XrayKind.of(b.category), b.category, dark, dark ? s.colorNigthBg : s.colorDayBg);
            return true;
        }
        synchronized (fill) {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(inkOf(XrayKind.of(b.category), b.category, dark));
            fill.setAlpha(BAR_ALPHA);
            canvas.drawRect(rect, fill);
        }
        return true;
    }

    /** The colour of a kind's underline or bar. */
    static int inkOf(XrayKind kind, AiCategory category, boolean dark) {
        switch (kind) {
            case CLAIM:
            case EVIDENCE:
                return dark ? CanvasLook.ACCENT_N : CanvasLook.ACCENT;
            case EXAMPLE:
                return dark ? CanvasLook.MUTED_N : EXAMPLE_GREY;
            case CAVEAT:
                return dark ? CanvasLook.MUTED_N : CanvasLook.MUTED;
            default:
                return category == null ? CanvasLook.ACCENT : category.color;
        }
    }

    /** The underline's thickness for a line of text this tall: about the canvas's 2 px at 17 px type. */
    static float stroke(float lineHeight) {
        final float dp = Dips.dpToPx(1);
        return Math.max(1.5f * dp, Math.min(3f * dp, lineHeight * 0.08f));
    }

    /**
     * One line of a marked passage, the X-ray way (also the legend's samples).
     *
     * @param pageBg the colour under the text, for a caveat's wash
     */
    static void drawMark(Canvas canvas, RectF rect, XrayKind kind, AiCategory category, boolean dark, int pageBg) {
        synchronized (fill) {
            mark(canvas, rect, kind, category, dark, pageBg);
        }
    }

    private static void mark(Canvas canvas, RectF rect, XrayKind kind, AiCategory category, boolean dark, int pageBg) {
        final float w = stroke(rect.height());
        fill.setStyle(Paint.Style.FILL);
        switch (kind) {
            case CLAIM:
                fill.setColor(dark ? CanvasLook.ACCENT_N : CanvasLook.ACCENT);
                fill.setAlpha(dark ? CLAIM_ALPHA_NIGHT : CLAIM_ALPHA_DAY);
                canvas.drawRect(rect, fill);
                break;
            case EVIDENCE:
                fill.setColor(inkOf(kind, category, dark));
                canvas.drawRect(rect.left, rect.bottom, rect.right, rect.bottom + w, fill);
                break;
            case EXAMPLE: {
                dot.setStyle(Paint.Style.FILL);
                dot.setColor(inkOf(kind, category, dark));
                final float r = w / 2f;
                final float y = rect.bottom + r;
                for (float x = rect.left + r; x <= rect.right - r + 0.5f; x += w * 2f) {
                    canvas.drawCircle(x, y, r, dot);
                }
                break;
            }
            case CAVEAT:
                fill.setColor(pageBg);
                fill.setAlpha(CAVEAT_WASH_ALPHA);
                canvas.drawRect(rect, fill);
                fill.setColor(inkOf(kind, category, dark));
                fill.setAlpha(0xFF);
                canvas.drawRect(rect.left, rect.bottom, rect.right, rect.bottom + Math.max(1f, w / 2f), fill);
                break;
            default:
                fill.setColor(inkOf(kind, category, dark));
                fill.setAlpha(OTHER_ALPHA);
                canvas.drawRect(rect, fill);
                break;
        }
    }
}
