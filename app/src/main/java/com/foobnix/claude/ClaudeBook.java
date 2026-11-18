package com.foobnix.claude;

import android.app.Activity;
import android.content.Context;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiProgress;
import com.foobnix.ai.AiSettings;
import com.foobnix.ai.AiStore;
import com.foobnix.ai.AiTask;
import com.foobnix.ai.AiWork;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.skeleton.SkeletonParts;
import com.foobnix.model.AppProfile;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The open book and an outside Claude: when a book opens (or a sync brings answers), its desk is
 * made or brought up to date ({@link ClaudeDesk}), and the answers that are drawn on pages without
 * a tap (key passages, the book's summary, the skeleton) are taken at once, as background tasks.
 * Nothing at all while AI settings > Work with Claude through WebDAV is off.
 */
public final class ClaudeBook {

    /** Books whose desk is being written now (one run per book at a time). */
    private static final Set<String> BUSY = new HashSet<>();

    private ClaudeBook() {
    }

    /** A reader opened a book. */
    public static void opened(Activity activity, DocumentController dc) {
        try {
            if (activity == null || dc == null || dc.getCurrentBook() == null || !Claude.isOn(activity)) {
                return;
            }
            final AiJobs.Target target = AiJobs.Target.of(dc);
            final Context app = activity.getApplicationContext();
            final Thread thread = new Thread(() -> {
                prepare(app, target, false);
                takeWaiting(app, target);
            }, "@T claude desk");
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** ✦ > Prepare for Claude: the desk now, its text made again. Calls back on a background thread. */
    public static void prepareNow(Activity activity, DocumentController dc, Runnable done) {
        final AiJobs.Target target = AiJobs.Target.of(dc);
        final Context app = activity.getApplicationContext();
        new Thread(() -> {
            prepare(app, target, true);
            if (done != null) {
                done.run();
            }
        }, "@T claude desk").start();
    }

    /** A sync brought files of the open book: answers among them are taken. */
    public static void synced(Context c, DocumentController dc) {
        try {
            if (c == null || dc == null || dc.getCurrentBook() == null || !Claude.isOn(c)) {
                return;
            }
            final AiJobs.Target target = AiJobs.Target.of(dc);
            new Thread(() -> takeWaiting(c.getApplicationContext(), target), "@T claude answers").start();
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** Key passages, the book's summary and the skeleton Claude wrote: taken as tasks, no API asked. */
    static void takeWaiting(Context app, AiJobs.Target target) {
        try {
            final File desk = Claude.desk(target.name);
            if (!ClaudeAnswers.chapters(desk, ClaudeFeature.IDEAS.id, ClaudeFeature.IDEAS.format).isEmpty()) {
                enqueue(app, AiTask.highlightsFromClaude(target));
            }
            if (ClaudeAnswers.has(desk, ClaudeFeature.SUMMARY.id, ClaudeAnswers.BOOK, ClaudeFeature.SUMMARY.format)) {
                enqueue(app, AiTask.bookSummary(target, false));
            }
            if (com.foobnix.assist.Assist.SKELETON.isOn()
                    && ClaudeAnswers.has(desk, ClaudeFeature.SKELETON.id, ClaudeAnswers.BOOK, ClaudeFeature.SKELETON.format)) {
                enqueue(app, AiTask.skeleton(target));
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    private static void enqueue(Context app, AiTask task) {
        final int state = AiProgress.state(task.key());
        if (state != AiProgress.RUNNING && state != AiProgress.WAITING) {
            AiWork.enqueue(app, task);
        }
    }

    /**
     * Writes the book's desk: book.json and prompts every time (only sent on when they changed),
     * text.txt when the book file or the layout changed, or when asked ({@code force}).
     */
    static void prepare(Context app, AiJobs.Target target, boolean force) {
        synchronized (BUSY) {
            if (!BUSY.add(target.name)) {
                return;
            }
        }
        try {
            if (AppProfile.SYNC_FOLDER_PROFILE == null) {
                return;
            }
            final File desk = Claude.desk(target.name);
            final File source = new File(target.path);
            final String layout = target.width + "x" + target.height + "@" + target.fontSize;
            final boolean makeText = force || !ClaudeDesk.textCurrent(desk, source.length(), source.lastModified(), layout);
            final AiSettings settings = AiSettings.load(app);
            try (BookText text = BookText.open(target.path, target.width, target.height, target.fontSize)) {
                if (text == null) {
                    return;
                }
                final int pages = text.pageCount();
                final List<BookChapters.Entry> toc = text.toc();
                final List<BookChapters.Range> chapters = BookChapters.chaptersOrBlocks(toc, pages, BookChapters.BLOCK_PAGES);
                final Map<String, Long> chars = new LinkedHashMap<>();
                boolean wrote = false;
                if (makeText) {
                    final String[] plain = new String[pages];
                    for (int p = 0; p < pages; p++) {
                        plain[p] = BookText.plain(text.body(p));
                    }
                    for (BookChapters.Range r : chapters) {
                        long n = 0;
                        for (int p = Math.max(0, r.from); p < r.to && p < pages; p++) {
                            n += plain[p] == null ? 0 : plain[p].length();
                        }
                        chars.put(r.key, n);
                    }
                    wrote |= ClaudeDesk.write(new File(desk, ClaudeDesk.TEXT), ClaudeDesk.text(chapters, plain));
                } else {
                    chars.putAll(storedChars(desk));
                }

                final ClaudeDesk.Book b = new ClaudeDesk.Book();
                b.book = target.name;
                b.title = target.title;
                b.devicePath = target.path;
                b.file = serverPath(source);
                b.size = source.length();
                b.modified = source.lastModified();
                b.pages = pages;
                b.readerPage = target.page;
                b.layout = layout;
                final String goal = AiStore.get().load(target.name).goal;
                b.goal = goal == null ? "" : goal.trim();
                b.zoomGoal = ClaudePrompts.zoomGoal(target.name);
                b.chapters = chapters;
                b.chars = chars;
                final List<SkeletonParts.Part> parts = SkeletonParts.group(toc, pages);
                b.skeletonParts = new ArrayList<>();
                int n = 0;
                for (SkeletonParts.Part p : parts) {
                    final int first = n;
                    n += p.chapters.size();
                    b.skeletonParts.add(new String[]{p.range.title == null ? "" : p.range.title,
                            first + (n - 1 > first ? "-" + (n - 1) : "")});
                }
                b.skeletonChapters = SkeletonParts.chapters(parts);
                b.prepared = System.currentTimeMillis();
                fillMeta(b, target.path);

                final Map<String, String> prompts = ClaudePrompts.forBook(settings, target.name, toc, pages);
                b.features = new ArrayList<>(prompts.keySet());
                for (Map.Entry<String, String> e : prompts.entrySet()) {
                    wrote |= ClaudeDesk.write(new File(new File(desk, ClaudeDesk.PROMPTS), e.getKey() + ".txt"), e.getValue());
                }
                // "prepared" changes every time: book.json is compared without it
                final File json = new File(desk, ClaudeDesk.BOOK_JSON);
                final String now = ClaudeDesk.bookJson(b).toString(2);
                if (!makeText && sameButPrepared(json, now)) {
                    // nothing new
                } else {
                    wrote |= ClaudeDesk.write(json, now);
                }
                if (wrote) {
                    com.foobnix.webdav.SyncEvents.readingChanged();
                }
            }
        } catch (Throwable e) {
            LOG.e(e);
        } finally {
            synchronized (BUSY) {
                BUSY.remove(target.name);
            }
        }
    }

    /** The chapters' lengths book.json had (text.txt not made again). */
    private static Map<String, Long> storedChars(File desk) {
        final Map<String, Long> out = new LinkedHashMap<>();
        try {
            final org.json.JSONArray a = new org.json.JSONObject(ClaudeAnswers.text(new File(desk, ClaudeDesk.BOOK_JSON)))
                    .optJSONArray("chapters");
            for (int i = 0; a != null && i < a.length(); i++) {
                final org.json.JSONObject o = a.optJSONObject(i);
                if (o != null) {
                    out.put(o.optString("key"), o.optLong("chars"));
                }
            }
        } catch (Exception e) {
            // counted again next time the text is made
        }
        return out;
    }

    static boolean sameButPrepared(File json, String now) {
        try {
            final org.json.JSONObject a = new org.json.JSONObject(ClaudeAnswers.text(json));
            final org.json.JSONObject b = new org.json.JSONObject(now);
            a.remove("prepared");
            b.remove("prepared");
            // both written by bookJson: the same keys in the same order
            return a.toString().equals(b.toString());
        } catch (Exception e) {
            return false;
        }
    }

    /** The book file's path on the server when it lies in the synced Books folder, else null. */
    static String serverPath(File source) {
        final File books = AppProfile.SYNC_FOLDER_BOOKS;
        if (books == null) {
            return null;
        }
        final String root = books.getAbsolutePath() + File.separator;
        final String path = source.getAbsolutePath();
        return path.startsWith(root) ? com.foobnix.webdav.WebDav.BOOKS + "/" + path.substring(root.length()).replace('\\', '/') : null;
    }

    private static void fillMeta(ClaudeDesk.Book b, String path) {
        try {
            final com.foobnix.dao2.FileMeta meta = com.foobnix.ui2.AppDB.get().load(path);
            if (meta != null) {
                b.author = meta.getAuthor() == null ? "" : meta.getAuthor().trim();
                final Integer y = meta.getYear();
                b.year = y == null ? 0 : y;
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }
}
