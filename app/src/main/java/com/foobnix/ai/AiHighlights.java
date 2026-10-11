package com.foobnix.ai;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.foobnix.android.utils.LOG;
import com.foobnix.tts.TTSHighlight;

import org.ebookdroid.droids.mupdf.codec.TextWord;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
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
    private List<String> keys = Collections.emptyList();
    private boolean visible = true;
    private int color = Color.parseColor(AiSettings.DEFAULT_HIGHLIGHT_COLOR);
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
            keys = Collections.emptyList();
            visible = settings.highlightsVisible;
            color = parseColor(settings.highlightColor);
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
            keys = Collections.emptyList();
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
        final LinkedHashSet<String> found = new LinkedHashSet<>();
        for (String text : data.quoteTexts()) {
            final String key = QuoteMatcher.key(text);
            if (key != null) {
                found.add(key);
            }
        }
        synchronized (this) {
            if (bookName == null || !bookName.equals(book)) {
                return;
            }
            this.data = data;
            keys = new ArrayList<>(found);
            version++;
            cache.clear();
        }
        changed();
    }

    public synchronized String getBook() {
        return book;
    }

    /** The stored AI data of the open book as last read or written (summaries too). */
    public synchronized AiStore.Book data() {
        return data;
    }

    public synchronized int count() {
        return keys.size();
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

    public void setColor(String hex) {
        synchronized (this) {
            color = parseColor(hex);
        }
        changed();
    }

    /**
     * The highlighted parts of a page as bands in page-relative coordinates (0..1, like
     * {@link TextWord}), one per line, or an empty list.
     */
    public synchronized List<RectF> bandsFor(int docPage, TextWord[][] pageText) {
        if (!visible || keys.isEmpty() || pageText == null) {
            return Collections.emptyList();
        }
        final Cached cached = cache.get(docPage);
        if (cached != null && cached.text == pageText && cached.version == version) {
            return cached.bands;
        }
        List<RectF> bands = Collections.emptyList();
        try {
            bands = bands(pageText, keys);
        } catch (Exception e) {
            LOG.e(e);
        }
        cache.put(docPage, new Cached(pageText, version, bands));
        return bands;
    }

    /** Words of a page covered by quotes, as one band per line of each covered run of words. */
    static List<RectF> bands(TextWord[][] pageText, List<String> keys) {
        final List<TextWord> words = TTSHighlight.words(pageText);
        final List<String> strings = new ArrayList<>(words.size());
        for (TextWord word : words) {
            strings.add(word.getWord());
        }
        final List<int[]> ranges = QuoteMatcher.onPage(new QuoteMatcher.Words(strings), keys);
        if (ranges.isEmpty()) {
            return Collections.emptyList();
        }
        final boolean[] marked = new boolean[words.size()];
        for (int[] range : ranges) {
            for (int i = Math.max(0, range[0]); i <= range[1] && i < marked.length; i++) {
                marked[i] = true;
            }
        }
        final List<RectF> bands = new ArrayList<>();
        int start = -1;
        for (int i = 0; i <= marked.length; i++) {
            final boolean on = i < marked.length && marked[i];
            if (on && start < 0) {
                start = i;
            } else if (!on && start >= 0) {
                bands.addAll(TTSHighlight.toBands(words.subList(start, i)));
                start = -1;
            }
        }
        return bands;
    }

    public synchronized Paint paint() {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setAlpha(ALPHA);
        return paint;
    }

    static int parseColor(String hex) {
        try {
            return Color.parseColor(hex);
        } catch (Exception e) {
            return Color.parseColor(AiSettings.DEFAULT_HIGHLIGHT_COLOR);
        }
    }

    private void changed() {
        EventBus.getDefault().post(new MessageAiHighlights());
    }
}
