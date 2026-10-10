package com.foobnix.tts;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.model.BookCSS;

import org.ebookdroid.droids.mupdf.codec.TextWord;
import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What read-aloud is saying right now, for the reader views to mark on the page.
 * <p>
 * {@link TTSService} reports each utterance with its exact text, and every view asks for the
 * marked area of the page it draws. The text is located among the page words once per utterance
 * and page ({@link TtsTextLocator}); drawing a frame then only paints the cached line bands.
 */
public class TTSHighlight {

    public static final String DEFAULT_COLOR = "#FFC107";
    private static final int ALPHA = 90;
    /** Words after the tapped one that go into the anchor TTS starts reading from. */
    private static final int ANCHOR_WORDS = 6;

    private static final TTSHighlight instance = new TTSHighlight();

    private final Paint paint = new Paint();
    private String paintColor;

    private int page = -1;
    private String text;
    private long utterance;
    private int fromWord;
    private int lastEndPage = -1;
    private int lastEndWord;

    private long resolvedUtterance = -1;
    private int resolvedPage = -1;
    private TextWord[][] resolvedText;
    private List<RectF> resolvedBands = Collections.emptyList();

    public static TTSHighlight get() {
        return instance;
    }

    public static boolean isEnabled() {
        return AppState.get().isTTSHighlight;
    }

    /** An utterance started: {@code text} of document page {@code page} is being spoken. */
    public void onUtterance(int page, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        synchronized (this) {
            if (page == this.page && text.equals(this.text)) {
                return;
            }
            // a repeated phrase is looked up after the previous utterance of the same page
            fromWord = page == lastEndPage ? lastEndWord : 0;
            this.page = page;
            this.text = text;
            utterance++;
        }
        EventBus.getDefault().post(new MessageTtsHighlight());
    }

    /** Read-aloud is over: nothing stays marked. */
    public void clear() {
        synchronized (this) {
            if (text == null) {
                return;
            }
            text = null;
            page = -1;
            lastEndPage = -1;
            utterance++;
        }
        EventBus.getDefault().post(new MessageTtsHighlight());
    }

    public synchronized boolean isActive() {
        return text != null;
    }

    /**
     * The marked area of a page as bands in page-relative coordinates (0..1, like {@link TextWord}),
     * one band per line, or an empty list when the page holds no part of the current utterance.
     */
    public synchronized List<RectF> bandsFor(int docPage, TextWord[][] pageText) {
        if (!isEnabled() || text == null || docPage != page || pageText == null) {
            return Collections.emptyList();
        }
        if (resolvedUtterance == utterance && resolvedPage == docPage && resolvedText == pageText) {
            return resolvedBands;
        }
        List<RectF> bands = Collections.emptyList();
        try {
            final List<TextWord> words = words(pageText);
            final List<String> strings = new ArrayList<>(words.size());
            for (TextWord word : words) {
                strings.add(word.getWord());
            }
            final int[] range = TtsTextLocator.findWords(strings, text, fromWord);
            if (range != null) {
                bands = toBands(words.subList(range[0], range[1] + 1));
                lastEndPage = docPage;
                lastEndWord = range[1];
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        resolvedUtterance = utterance;
        resolvedPage = docPage;
        resolvedText = pageText;
        resolvedBands = bands;
        return bands;
    }

    public synchronized Paint paint() {
        final String color = AppState.get().ttsHighlightColor;
        if (paintColor == null || !paintColor.equals(color)) {
            int value;
            try {
                value = Color.parseColor(color);
            } catch (Exception e) {
                value = Color.parseColor(DEFAULT_COLOR);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(value);
            paint.setAlpha(ALPHA);
            paintColor = color;
        }
        return paint;
    }

    /** The words of a page in reading order, without the off-page ones the views skip too. */
    public static List<TextWord> words(TextWord[][] pageText) {
        final List<TextWord> words = new ArrayList<>();
        if (pageText == null) {
            return words;
        }
        final boolean textFormat = BookCSS.get().isTextFormat();
        for (TextWord[] line : pageText) {
            if (line == null) {
                continue;
            }
            for (TextWord word : line) {
                if (word == null || (!textFormat && (word.left < 0 || word.top < 0))) {
                    continue;
                }
                words.add(word);
            }
        }
        return words;
    }

    /** The tapped word and a few after it: what TTS looks for to start reading at its sentence. */
    public static String anchorFrom(List<TextWord> words, int index) {
        final StringBuilder anchor = new StringBuilder();
        for (int i = index; i < words.size() && i < index + ANCHOR_WORDS; i++) {
            final String w = words.get(i).getWord();
            if (w != null && !w.trim().isEmpty()) {
                anchor.append(w.trim()).append(' ');
            }
        }
        return anchor.toString().trim();
    }

    /** Joins the words of each line into one band, so the mark reads as a continuous stroke. */
    static List<RectF> toBands(List<TextWord> words) {
        final List<RectF> bands = new ArrayList<>();
        RectF band = null;
        for (TextWord word : words) {
            if (word.width() <= 0 || word.height() <= 0) {
                continue;
            }
            if (band != null && sameLine(band, word)) {
                band.union(word);
            } else {
                band = new RectF(word);
                bands.add(band);
            }
        }
        return bands;
    }

    private static boolean sameLine(RectF a, RectF b) {
        final float overlap = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
        return overlap > 0.5f * Math.min(a.height(), b.height());
    }
}
