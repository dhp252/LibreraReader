package com.foobnix.assist.prequestions;

import android.graphics.Paint;
import android.graphics.RectF;

import com.foobnix.ai.QuoteMatcher;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.tts.TTSHighlight;

import org.ebookdroid.droids.mupdf.codec.TextWord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The answer sentences of the open book's locked-in pre-questions, underlined on the page. Both
 * reader views ask for a page's underlines while drawing (like the AI marks); found by the page's
 * words, so they survive a new layout. Nothing when Assist.PRE_QUESTIONS is off.
 */
public final class PreQMarks {

    /** Line thickness as a part of the line's height. */
    private static final float THICKNESS = 0.09f;

    private static volatile List<String> keys = Collections.emptyList();
    private static volatile int version;
    private static final Map<Integer, Cached> cache = new HashMap<>();
    private static Paint paint;

    private static final class Cached {
        final TextWord[][] text;
        final int version;
        final List<RectF> lines;

        Cached(TextWord[][] text, int version, List<RectF> lines) {
            this.text = text;
            this.version = version;
            this.lines = lines;
        }
    }

    private PreQMarks() {
    }

    /** The answer sentences to underline ({@link QuoteMatcher#key}s), for the open book. */
    static void set(List<String> quoteKeys) {
        keys = quoteKeys == null ? Collections.<String>emptyList() : new ArrayList<>(quoteKeys);
        synchronized (cache) {
            version++;
            cache.clear();
        }
    }

    /** Underlines of a page in page-relative units (0..1, like {@link TextWord}); usually none. */
    public static List<RectF> underlines(int docPage, TextWord[][] pageText) {
        final List<String> k = keys;
        if (k.isEmpty() || pageText == null || !Assist.PRE_QUESTIONS.isOn()) {
            return Collections.emptyList();
        }
        synchronized (cache) {
            final Cached c = cache.get(docPage);
            if (c != null && c.text == pageText && c.version == version) {
                return c.lines;
            }
        }
        List<RectF> lines = Collections.emptyList();
        try {
            lines = lines(pageText, k);
        } catch (Exception e) {
            LOG.e(e);
        }
        synchronized (cache) {
            if (cache.size() > 12) {
                cache.clear();
            }
            cache.put(docPage, new Cached(pageText, version, lines));
        }
        return lines;
    }

    private static List<RectF> lines(TextWord[][] pageText, List<String> k) {
        final List<TextWord> words = TTSHighlight.words(pageText);
        final List<String> strings = new ArrayList<>(words.size());
        for (TextWord w : words) {
            strings.add(w.getWord());
        }
        final QuoteMatcher.Words page = new QuoteMatcher.Words(strings);
        final List<RectF> out = new ArrayList<>();
        for (int[] range : QuoteMatcher.onPage(page, k)) {
            final int from = Math.max(0, range[0]);
            final int to = Math.min(words.size(), range[1] + 1);
            if (from >= to) {
                continue;
            }
            for (RectF band : TTSHighlight.toBands(words.subList(from, to))) {
                final float h = Math.max(band.height() * THICKNESS, 0.0015f);
                out.add(new RectF(band.left, band.bottom - h / 2, band.right, band.bottom + h / 2));
            }
        }
        return out;
    }

    /** The underline's paint: the canvas accent, lighter on a night page. */
    public static synchronized Paint paint() {
        if (paint == null) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.FILL);
        }
        // the page's own day or night, not the interface's
        paint.setColor(com.foobnix.model.AppState.get().isDayNotInvert ? CanvasLook.ACCENT : CanvasLook.ACCENT_N);
        return paint;
    }
}
