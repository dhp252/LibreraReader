package com.foobnix.assist.review;

import com.foobnix.ai.AiHighlights;
import com.foobnix.ai.AiStore;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.AssistStore;
import com.foobnix.assist.recall.Recall;
import com.foobnix.assist.skeleton.SkeletonData;
import com.foobnix.dao2.FileMeta;
import com.foobnix.model.AppProfile;
import com.foobnix.study.StudyStore;
import com.foobnix.ui2.AppDB;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The idea lists "Find connections" sends: a book's stored key-passage ideas (F-004), else the
 * ideas its blank-page recalls kept, else its one line per chapter (a skeleton or a study
 * preview); and the other books of the library on this device that have any of these, the latest
 * worked on first (the way One topic lists books). Every store is only read; no book is opened.
 */
final class ReviewLinkBooks {

    private ReviewLinkBooks() {
    }

    /** A book's stored ideas, in reading order as far as the stores keep one. Off the main thread. */
    static List<ReviewLinkPrompts.Idea> ideas(String bookName) {
        final List<ReviewLinkPrompts.Idea> out = new ArrayList<>();
        try {
            for (AiStore.Chapter chapter : AiStore.get().load(bookName).highlights) {
                for (AiStore.Idea idea : chapter.ideas) {
                    if (idea.text != null && idea.parent < 0) {
                        out.add(new ReviewLinkPrompts.Idea(chapter.key, chapter.title, String.valueOf(idea.id), idea.text));
                    }
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
            for (Map.Entry<String, JSONObject> e : AssistStore.get().items(bookName, Recall.PREFIX).entrySet()) {
                final Recall.Attempt attempt = Recall.Attempt.fromJson(e.getValue());
                if (attempt != null) {
                    for (Recall.Idea idea : attempt.ideas) {
                        out.add(new ReviewLinkPrompts.Idea(e.getKey().substring(Recall.PREFIX.length()), attempt.title,
                                ReviewSources.recallId(attempt, idea), idea.text));
                    }
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
            SkeletonData lines = SkeletonData.fromJson(AssistStore.get().item(bookName, SkeletonData.KEY));
            if (lines.chapterLines.isEmpty()) {
                lines = SkeletonData.fromStudy(StudyStore.get().load(bookName));
            }
            for (Map.Entry<String, String> e : lines.chapterLines.entrySet()) {
                // a chapter's line stands for the chapter: it has no idea id of its own
                out.add(new ReviewLinkPrompts.Idea(e.getKey(), "", "line", e.getValue()));
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return out;
    }

    /**
     * The other books to look for connections in: those of the library on this device with
     * stored ideas, the latest worked on first, {@link ReviewLinkPrompts#MAX_BOOKS} at most.
     * Off the main thread.
     *
     * @param openBook the open book's file name: left out
     */
    static List<ReviewLinkPrompts.Book> others(String openBook) {
        final List<ReviewLinkPrompts.Book> out = new ArrayList<>();
        try {
            // the data files there are, by file name, with when they changed last
            final Map<String, Long> files = new HashMap<>();
            final List<File> folders = new ArrayList<>();
            final File profile = AppProfile.SYNC_FOLDER_PROFILE;
            folders.add(new File(profile, AssistStore.FOLDER));
            folders.add(new File(profile, StudyStore.FOLDER));
            folders.add(new File(profile, AiStore.FOLDER));
            final File[] devices = profile == null ? null : profile.listFiles();
            if (devices != null) {
                for (File folder : devices) {
                    if (folder.isDirectory() && folder.getName().startsWith(AppProfile.DEVICE_PREFIX)) {
                        folders.add(new File(folder, AiStore.FOLDER));
                    }
                }
            }
            for (File folder : folders) {
                final File[] found = folder.listFiles();
                if (found == null) {
                    continue;
                }
                for (File f : found) {
                    if (f.isFile() && f.getName().endsWith(".json")) {
                        final Long before = files.get(f.getName());
                        files.put(f.getName(), Math.max(before == null ? 0 : before, f.lastModified()));
                    }
                }
            }
            if (files.isEmpty()) {
                return out;
            }
            final List<Object[]> candidates = new ArrayList<>();
            final Set<String> seen = new HashSet<>();
            for (FileMeta meta : AppDB.get().getAll()) {
                final String path = meta == null ? null : meta.getPath();
                if (path == null) {
                    continue;
                }
                final String name = AiHighlights.bookName(path);
                final Long time = files.get(AiStore.fileName(name));
                if (time == null || name.equals(openBook) || !seen.add(name) || !new File(path).isFile()) {
                    continue;
                }
                candidates.add(new Object[]{meta, name, time});
            }
            Collections.sort(candidates, (x, y) -> Long.compare((Long) y[2], (Long) x[2]));
            for (Object[] c : candidates) {
                if (out.size() >= ReviewLinkPrompts.MAX_BOOKS) {
                    break;
                }
                final String name = (String) c[1];
                final List<ReviewLinkPrompts.Idea> ideas = ReviewLinkPrompts.spread(ideas(name), ReviewLinkPrompts.MAX_OTHER);
                if (!ideas.isEmpty()) {
                    out.add(book((FileMeta) c[0], name, ideas));
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return out;
    }

    /** A book of the library with the ideas of it that are sent. */
    static ReviewLinkPrompts.Book book(FileMeta meta, String name, List<ReviewLinkPrompts.Idea> ideas) {
        final Integer year = meta == null ? null : meta.getYear();
        final String title = meta == null || meta.getTitle() == null || meta.getTitle().trim().isEmpty() ? name
                : meta.getTitle().trim();
        return new ReviewLinkPrompts.Book(name, title, meta == null || meta.getAuthor() == null ? "" : meta.getAuthor().trim(),
                year == null ? 0 : year, ideas);
    }
}
