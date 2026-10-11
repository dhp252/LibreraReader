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

    /** A line of a marked passage, in page-relative coordinates, with why it was marked. */
    public static final class Band extends RectF {
        public final AiCategory category;

        Band(RectF rect, AiCategory category) {
            super(rect);
            this.category = category;
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
    /** The quotes' match keys by category. */
    private Map<AiCategory, List<String>> keys = Collections.emptyMap();
    private int keyCount;
    private boolean visible = true;
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
            keyCount = 0;
            visible = settings.highlightsVisible;
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
        final Map<AiCategory, List<String>> found = new EnumMap<>(AiCategory.class);
        final LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (AiStore.Chapter chapter : data.highlights) {
            for (AiStore.Quote quote : chapter.quotes) {
                final String key = QuoteMatcher.key(quote.text);
                if (key == null || !seen.add(key)) {
                    continue;
                }
                List<String> list = found.get(quote.category);
                if (list == null) {
                    list = new ArrayList<>();
                    found.put(quote.category, list);
                }
                list.add(key);
            }
        }
        synchronized (this) {
            if (bookName == null || !bookName.equals(book)) {
                return;
            }
            this.data = data;
            keys = found;
            keyCount = seen.size();
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

    /**
     * The highlighted parts of a page as bands in page-relative coordinates (0..1, like
     * {@link TextWord}), one per line, or an empty list. Each is a {@link Band}: draw it with
     * {@link #paint(RectF)}.
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

    /** Words of a page covered by quotes, as one band per line of each covered run of words of one category. */
    static List<RectF> bands(TextWord[][] pageText, Map<AiCategory, List<String>> keys) {
        final List<TextWord> words = TTSHighlight.words(pageText);
        final List<String> strings = new ArrayList<>(words.size());
        for (TextWord word : words) {
            strings.add(word.getWord());
        }
        final QuoteMatcher.Words pageWords = new QuoteMatcher.Words(strings);
        final AiCategory[] marked = new AiCategory[words.size()];
        boolean any = false;
        for (Map.Entry<AiCategory, List<String>> entry : keys.entrySet()) {
            for (int[] range : QuoteMatcher.onPage(pageWords, entry.getValue())) {
                for (int i = Math.max(0, range[0]); i <= range[1] && i < marked.length; i++) {
                    marked[i] = entry.getKey();
                    any = true;
                }
            }
        }
        if (!any) {
            return Collections.emptyList();
        }
        final List<RectF> bands = new ArrayList<>();
        int start = -1;
        for (int i = 0; i <= marked.length; i++) {
            final AiCategory on = i < marked.length ? marked[i] : null;
            if (start >= 0 && on != marked[start]) {
                for (RectF line : TTSHighlight.toBands(words.subList(start, i))) {
                    bands.add(new Band(line, marked[start]));
                }
                start = -1;
            }
            if (on != null && start < 0) {
                start = i;
            }
        }
        return bands;
    }

    /** The paint of a band from {@link #bandsFor}: its category's color, see-through. */
    public synchronized Paint paint(RectF band) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(band instanceof Band ? ((Band) band).category.color : AiCategory.MAIN.color);
        paint.setAlpha(ALPHA);
        return paint;
    }

    /**
     * The category of the mark at a point of a page drawn lately (page-relative coordinates,
     * 0..1), or null.
     */
    public synchronized AiCategory categoryAt(int docPage, float x, float y) {
        if (!visible) {
            return null;
        }
        final Cached cached = cache.get(docPage);
        if (cached == null || cached.version != version) {
            return null;
        }
        for (RectF band : cached.bands) {
            if (band instanceof Band && band.contains(x, y)) {
                return ((Band) band).category;
            }
        }
        return null;
    }

    private void changed() {
        EventBus.getDefault().post(new MessageAiHighlights());
    }
}
