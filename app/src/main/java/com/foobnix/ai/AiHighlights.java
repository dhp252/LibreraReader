package com.foobnix.ai;

import android.app.Activity;
import android.graphics.Paint;
import android.graphics.RectF;

import com.foobnix.android.utils.LOG;
import com.foobnix.tts.TTSHighlight;

import org.ebookdroid.droids.mupdf.codec.TextWord;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The AI highlights of the open book, for the reader views to draw. Pages find the stored quotes
 * in their own words when they are drawn ({@link QuoteMatcher#onPage}), once per page and change:
 * a frame then only paints the cached line bands, as with the read-aloud mark.
 */
public class AiHighlights {

    private static final int ALPHA = 80;
    private static final int CACHED_PAGES = 12;

    private static final AiHighlights instance = new AiHighlights();

    /** Bars beside must-read paragraphs: nearly solid. */
    private static final int BAR_ALPHA = 200;
    /** A bar's width and its gap to the text, in page widths. */
    static final float BAR_WIDTH = 0.006f;
    static final float BAR_GAP = 0.008f;

    /**
     * A line of a key sentence, or the bar beside a must-read paragraph, in page-relative
     * coordinates, with why it was marked.
     */
    public static final class Band extends RectF {
        public final AiCategory category;
        /** The match key of the sentence or paragraph it draws ({@link QuoteMatcher#key}). */
        public final String key;
        public final boolean bar;

        Band(RectF rect, AiCategory category, String key, boolean bar) {
            super(rect);
            this.category = category;
            this.key = key;
            this.bar = bar;
        }
    }

    /** What a tapped mark stands for: its chapter, its words, and the idea it carries (null for key passages from before ideas). */
    public static final class Mark {
        public final AiStore.Chapter chapter;
        public final AiStore.Quote quote;
        public final AiStore.Idea idea;
        public final boolean paragraph;

        Mark(AiStore.Chapter chapter, AiStore.Quote quote, boolean paragraph) {
            this.chapter = chapter;
            this.quote = quote;
            this.idea = quote.idea >= 0 ? chapter.idea(quote.idea) : null;
            this.paragraph = paragraph;
        }
    }

    private static class Cached {
        final TextWord[][] text;
        final int version;
        final List<RectF> bands;

        Cached(TextWord[][] text, int version, List<RectF> bands) {
            this.text = text;
            this.version = version;
            this.bands = bands;
        }
    }

    private final Paint paint = new Paint();
    private final Map<Integer, Cached> cache = new LinkedHashMap<Integer, Cached>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Cached> eldest) {
            return size() > CACHED_PAGES;
        }
    };

    private Activity owner;
    private String book;
    private AiStore.Book data = new AiStore.Book();
    /** The key sentences' match keys by category. */
    private Map<AiCategory, List<String>> keys = Collections.emptyMap();
    /** The must-read paragraphs' match keys by category. */
    private Map<AiCategory, List<String>> paragraphKeys = Collections.emptyMap();
    private int keyCount;
    private boolean visible = true;
    private boolean barsVisible = true;
    private boolean sentencesVisible = true;
    private int version;

    public static AiHighlights get() {
        return instance;
    }

    /** File name of a book: what its AI data is stored under. */
    public static String bookName(String path) {
        return new File(path).getName();
    }

    /**
     * A book was opened: its highlights are read in the background and drawn when ready.
     *
     * @param owner the reader screen; only it closes the book again (a new screen may open
     *              before the one it replaces is destroyed)
     */
    public void open(Activity owner, String bookPath) {
        final AiSettings settings = AiSettings.load(owner);
        final String name = bookName(bookPath);
        synchronized (this) {
            this.owner = owner;
            book = name;
            data = new AiStore.Book();
            keys = Collections.emptyMap();
            paragraphKeys = Collections.emptyMap();
            keyCount = 0;
            visible = settings.highlightsVisible;
            barsVisible = settings.barsVisible;
            sentencesVisible = settings.sentencesVisible;
            version++;
            cache.clear();
        }
        reload(name);
    }

    /** The reader screen is gone: its AI work stops, and its book is no longer drawn. */
    public void close(Activity owner) {
        AiJobs.cancelFor(owner);
        synchronized (this) {
            if (this.owner != owner) {
                return;
            }
            this.owner = null;
            book = null;
            data = new AiStore.Book();
            keys = Collections.emptyMap();
            paragraphKeys = Collections.emptyMap();
            keyCount = 0;
            version++;
            cache.clear();
        }
    }

    /** Reads the stored highlights of the book again, on a thread of its own. */
    public void reload(final String bookName) {
        new Thread(() -> {
            try {
                set(bookName, AiStore.get().load(bookName));
            } catch (Exception e) {
                LOG.e(e);
            }
        }, "@T AI highlights").start();
    }

    /** The stored data of a book, as just read or written. */
    public void set(String bookName, AiStore.Book data) {
        final Map<AiCategory, List<String>> sentences = new EnumMap<>(AiCategory.class);
        final Map<AiCategory, List<String>> paragraphs = new EnumMap<>(AiCategory.class);
        final LinkedHashSet<String> seen = new LinkedHashSet<>();
        final LinkedHashSet<String> seenParagraphs = new LinkedHashSet<>();
        for (AiStore.Chapter chapter : data.highlights) {
            addKeys(chapter.quotes, sentences, seen);
            addKeys(chapter.paragraphs, paragraphs, seenParagraphs);
        }
        synchronized (this) {
            if (bookName == null || !bookName.equals(book)) {
                return;
            }
            this.data = data;
            keys = sentences;
            paragraphKeys = paragraphs;
            keyCount = seen.size() + seenParagraphs.size();
            version++;
            cache.clear();
        }
        changed();
    }

    private static void addKeys(List<AiStore.Quote> quotes, Map<AiCategory, List<String>> to, LinkedHashSet<String> seen) {
        for (AiStore.Quote quote : quotes) {
            final String key = QuoteMatcher.key(quote.text);
            if (key == null || !seen.add(key)) {
                continue;
            }
            List<String> list = to.get(quote.category);
            if (list == null) {
                list = new ArrayList<>();
                to.put(quote.category, list);
            }
            list.add(key);
        }
    }

    public synchronized String getBook() {
        return book;
    }

    /** The stored AI data of the open book as last read or written (summaries too). */
    public synchronized AiStore.Book data() {
        return data;
    }

    public synchronized int count() {
        return keyCount;
    }

    public synchronized boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean show) {
        synchronized (this) {
            visible = show;
        }
        changed();
    }

    public synchronized boolean isBarsVisible() {
        return barsVisible;
    }

    public synchronized boolean isSentencesVisible() {
        return sentencesVisible;
    }

    /** Which layers of the shown highlights are drawn: paragraph bars, key sentences. */
    public void setLayers(boolean bars, boolean sentences) {
        synchronized (this) {
            barsVisible = bars;
            sentencesVisible = sentences;
            version++;
            cache.clear();
        }
        changed();
    }

    /**
     * The highlighted parts of a page as bands in page-relative coordinates (0..1, like
     * {@link TextWord}): one per line of a key sentence, one bar per must-read paragraph and
     * column; or an empty list. Each is a {@link Band}: draw it with {@link #paint(RectF)}.
     */
    public synchronized List<RectF> bandsFor(int docPage, TextWord[][] pageText) {
        final Map<AiCategory, List<String>> sentences = sentencesVisible ? keys : Collections.<AiCategory, List<String>>emptyMap();
        final Map<AiCategory, List<String>> paragraphs = barsVisible ? paragraphKeys : Collections.<AiCategory, List<String>>emptyMap();
        if (!visible || (sentences.isEmpty() && paragraphs.isEmpty()) || pageText == null) {
            return Collections.emptyList();
        }
        final Cached cached = cache.get(docPage);
        if (cached != null && cached.text == pageText && cached.version == version) {
            return cached.bands;
        }
        List<RectF> bands = Collections.emptyList();
        try {
            bands = bands(pageText, sentences, paragraphs);
        } catch (Exception e) {
            LOG.e(e);
        }
        cache.put(docPage, new Cached(pageText, version, bands));
        return bands;
    }

    /**
     * Words of a page covered by key sentences, as one band per line of each covered run of words
     * of one sentence; then a bar beside each must-read paragraph on the page.
     */
    static List<RectF> bands(TextWord[][] pageText, Map<AiCategory, List<String>> keys,
                             Map<AiCategory, List<String>> paragraphKeys) {
        final List<TextWord> words = TTSHighlight.words(pageText);
        final List<String> strings = new ArrayList<>(words.size());
        for (TextWord word : words) {
            strings.add(word.getWord());
        }
        final QuoteMatcher.Words pageWords = new QuoteMatcher.Words(strings);
        final List<RectF> bands = new ArrayList<>();

        final AiCategory[] marked = new AiCategory[words.size()];
        final String[] markedKey = new String[words.size()];
        boolean any = false;
        for (Map.Entry<AiCategory, List<String>> entry : keys.entrySet()) {
            for (String key : entry.getValue()) {
                for (int[] range : QuoteMatcher.onPage(pageWords, Collections.singletonList(key))) {
                    for (int i = Math.max(0, range[0]); i <= range[1] && i < marked.length; i++) {
                        marked[i] = entry.getKey();
                        markedKey[i] = key;
                        any = true;
                    }
                }
            }
        }
        if (any) {
            int start = -1;
            for (int i = 0; i <= marked.length; i++) {
                final String on = i < marked.length ? markedKey[i] : null;
                if (start >= 0 && !markedKey[start].equals(on)) {
                    for (RectF line : TTSHighlight.toBands(words.subList(start, i))) {
                        bands.add(new Band(line, marked[start], markedKey[start], false));
                    }
                    start = -1;
                }
                if (on != null && start < 0) {
                    start = i;
                }
            }
        }

        for (Map.Entry<AiCategory, List<String>> entry : paragraphKeys.entrySet()) {
            for (String key : entry.getValue()) {
                for (int[] range : QuoteMatcher.onPage(pageWords, Collections.singletonList(key))) {
                    final List<RectF> lines = TTSHighlight.toBands(words.subList(Math.max(0, range[0]),
                            Math.min(words.size(), range[1] + 1)));
                    final float[][] edges = new float[lines.size()][];
                    for (int i = 0; i < edges.length; i++) {
                        final RectF line = lines.get(i);
                        edges[i] = new float[]{line.left, line.top, line.right, line.bottom};
                    }
                    for (float[] bar : bars(edges)) {
                        bands.add(new Band(new RectF(bar[0], bar[1], bar[2], bar[3]), entry.getKey(), key, true));
                    }
                }
            }
        }
        return bands;
    }

    /**
     * The bars beside a paragraph's lines ({left, top, right, bottom} each, in reading order): one
     * per column, left of the column's leftmost line; a new column starts where a line goes up.
     */
    static List<float[]> bars(float[][] lines) {
        final List<float[]> bars = new ArrayList<>();
        float[] bar = null;
        float lastTop = 0;
        for (float[] line : lines) {
            if (bar == null || line[1] < lastTop) {
                bar = new float[]{line[0], line[1], line[0], line[3]};
                bars.add(bar);
            }
            bar[0] = Math.min(bar[0], line[0]);
            bar[3] = Math.max(bar[3], line[3]);
            lastTop = line[1];
        }
        for (float[] b : bars) {
            final float right = Math.max(BAR_WIDTH, b[0] - BAR_GAP);
            b[0] = right - BAR_WIDTH;
            b[2] = right;
        }
        return bars;
    }

    /** The paint of a band from {@link #bandsFor}: its category's color, see-through for a sentence. */
    public synchronized Paint paint(RectF band) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(band instanceof Band ? ((Band) band).category.color : AiCategory.MAIN.color);
        paint.setAlpha(band instanceof Band && ((Band) band).bar ? BAR_ALPHA : ALPHA);
        return paint;
    }

    /**
     * The mark at a point of a page drawn lately (page-relative coordinates, 0..1), or null. A
     * bar is hard to hit with a finger: it counts a little wider, and a sentence wins over it.
     */
    public synchronized Band bandAt(int docPage, float x, float y) {
        if (!visible) {
            return null;
        }
        final Cached cached = cache.get(docPage);
        if (cached == null || cached.version != version) {
            return null;
        }
        Band bar = null;
        for (RectF r : cached.bands) {
            if (!(r instanceof Band)) {
                continue;
            }
            final Band band = (Band) r;
            if (!band.bar && band.contains(x, y)) {
                return band;
            }
            if (band.bar && bar == null && x >= band.left - BAR_HIT && x <= band.right + BAR_HIT
                    && y >= band.top && y <= band.bottom) {
                bar = band;
            }
        }
        return bar;
    }

    /** How much wider than drawn a bar counts for a tap, either side, in page widths. */
    static final float BAR_HIT = 0.02f;

    /** What a band stands for in the stored data, or null when it is gone. */
    public synchronized Mark mark(Band band) {
        for (AiStore.Chapter chapter : data.highlights) {
            for (AiStore.Quote quote : band.bar ? chapter.paragraphs : chapter.quotes) {
                if (band.key.equals(QuoteMatcher.key(quote.text))) {
                    return new Mark(chapter, quote, band.bar);
                }
            }
        }
        return null;
    }

    private void changed() {
        EventBus.getDefault().post(new MessageAiHighlights());
    }
}
