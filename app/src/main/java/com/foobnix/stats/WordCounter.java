package com.foobnix.stats;

import android.content.Context;
import android.content.SharedPreferences;

import com.foobnix.ai.BookText;
import com.foobnix.android.utils.LOG;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Words on each page of the open book, counted in the background from a copy of the document
 * laid out like the reader ({@link BookText}, as AI and TTS read it), and kept in the app's
 * cache per book and layout. The page events of the log carry these counts; time left adds them
 * up. Also records the Flesch reading ease of English books for the readability stat.
 */
public final class WordCounter {

    /** Waits this long after a book opens: the reader lays the book out first. */
    private static final long START_DELAY_MS = 5_000L;
    private static final String PREFS = "stats";

    private static volatile Counts current;
    private static Context app;

    private static class Counts {
        final String key;
        final String path;
        final int[] words;
        volatile boolean cancelled;

        Counts(String key, String path, int pages) {
            this.key = key;
            this.path = path;
            this.words = new int[pages];
            java.util.Arrays.fill(words, -1);
        }
    }

    private WordCounter() {
    }

    /**
     * Counts the pages of the book the reader shows, unless they are counted for this layout.
     *
     * @param pages the reader's page count: counts of another layout are not used
     */
    public static void start(Context c, final String path, final int width, final int height, final int fontSize,
                             final int pages, final String lang) {
        if (path == null || pages <= 0) {
            return;
        }
        app = c.getApplicationContext();
        final String key = path + "|" + width + "x" + height + "|" + fontSize + "|" + pages;
        final Counts was = current;
        if (was != null && was.key.equals(key)) {
            return;
        }
        if (was != null) {
            was.cancelled = true;
        }
        final Counts counts = new Counts(key, path, pages);
        current = counts;
        final Thread thread = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            if (load(counts)) {
                return;
            }
            try {
                Thread.sleep(START_DELAY_MS);
            } catch (InterruptedException e) {
                return;
            }
            count(counts, width, height, fontSize, lang);
        }, "@T stats words");
        thread.start();
    }

    /** Words on this 1-based page of the book shown, -1 when not counted yet. */
    public static int words(String path, int page1) {
        final Counts c = current;
        if (c == null || !c.path.equals(path) || page1 < 1 || page1 > c.words.length) {
            return -1;
        }
        return c.words[page1 - 1];
    }

    /**
     * Words on pages {@code from1..to1} (1-based, inclusive) of the book shown; a page not
     * counted yet counts as the average of those that are. -1 when no page is counted.
     */
    public static long wordsBetween(String path, int from1, int to1) {
        final Counts c = current;
        if (c == null || !c.path.equals(path)) {
            return -1;
        }
        long counted = 0;
        int countedPages = 0;
        for (int w : c.words) {
            if (w >= 0) {
                counted += w;
                countedPages++;
            }
        }
        if (countedPages == 0) {
            return -1;
        }
        final double average = counted / (double) countedPages;
        double total = 0;
        for (int p = Math.max(1, from1); p <= Math.min(to1, c.words.length); p++) {
            final int w = c.words[p - 1];
            total += w >= 0 ? w : average;
        }
        return Math.round(total);
    }

    /** Flesch reading ease of an English book (file name), NaN when not known. */
    public static double flesch(Context c, String bookKey) {
        final SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        final float f = sp.getFloat("flesch." + bookKey, Float.NaN);
        return Float.isNaN(f) ? Double.NaN : f;
    }

    private static void count(Counts counts, int width, int height, int fontSize, String lang) {
        final BookText text = BookText.open(counts.path, width, height, fontSize);
        if (text == null) {
            return;
        }
        final TextCounts total = new TextCounts();
        try {
            if (text.pageCount() != counts.words.length) {
                LOG.d("WordCounter", "other layout", text.pageCount(), counts.words.length);
                return;
            }
            for (int i = 0; i < counts.words.length; i++) {
                if (counts.cancelled) {
                    return;
                }
                final TextCounts page = new TextCounts().add(BookText.words(text.text(i)));
                counts.words[i] = (int) page.words;
                total.addAll(page);
            }
        } catch (Exception e) {
            LOG.e(e);
            return;
        } finally {
            text.close();
        }
        save(counts);
        final double flesch = total.flesch();
        if (lang != null && lang.toLowerCase().startsWith("en") && !Double.isNaN(flesch) && app != null) {
            final String book = new File(counts.path).getName();
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat("flesch." + book, (float) flesch).apply();
        }
    }

    private static File cacheFile(Counts counts) {
        if (app == null) {
            return null;
        }
        final File folder = new File(app.getCacheDir(), "stats-words");
        return new File(folder, Integer.toHexString(counts.key.hashCode()) + "-" + counts.words.length + ".txt");
    }

    private static boolean load(Counts counts) {
        final File file = cacheFile(counts);
        if (file == null || !file.isFile()) {
            return false;
        }
        try (InputStream in = new FileInputStream(file)) {
            final byte[] bytes = new byte[(int) file.length()];
            int read = 0;
            while (read < bytes.length) {
                final int n = in.read(bytes, read, bytes.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            final String[] lines = new String(bytes, 0, read, StandardCharsets.UTF_8).split("\n", 2);
            if (lines.length < 2 || !lines[0].equals(counts.key)) {
                return false;
            }
            final String[] values = lines[1].trim().split(",");
            if (values.length != counts.words.length) {
                return false;
            }
            for (int i = 0; i < values.length; i++) {
                counts.words[i] = Integer.parseInt(values[i]);
            }
            return true;
        } catch (IOException | NumberFormatException e) {
            LOG.d("WordCounter", e.getMessage());
            return false;
        }
    }

    private static void save(Counts counts) {
        final File file = cacheFile(counts);
        if (file == null) {
            return;
        }
        final File folder = file.getParentFile();
        if (folder != null && !folder.isDirectory() && !folder.mkdirs()) {
            return;
        }
        final StringBuilder out = new StringBuilder(counts.key).append('\n');
        for (int i = 0; i < counts.words.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(counts.words[i]);
        }
        try (OutputStream o = new FileOutputStream(file)) {
            o.write(out.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.d("WordCounter", e.getMessage());
        }
    }
}
