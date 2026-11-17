package com.foobnix.assist.path;

import com.foobnix.ai.AiHighlights;
import com.foobnix.ai.AiStore;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.AssistStore;
import com.foobnix.assist.skeleton.SkeletonData;
import com.foobnix.dao2.FileMeta;
import com.foobnix.model.AppProfile;
import com.foobnix.study.StudyStore;
import com.foobnix.ui2.AppDB;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The books of the library a topic can be merged across: those on this device that already have
 * one line per chapter, from a skeleton or a study preview. Both stores are only read.
 */
final class TopicBooks {

    /** The picker shows this many at most, the latest worked on first. */
    static final int MAX = 20;

    private TopicBooks() {
    }

    /** A book of the library with chapter lines. */
    static final class Item {
        final String path;
        final String name;
        final String title;
        final String author;
        final int year;
        /** When its lines were written last. */
        final long time;

        Item(String path, String name, String title, String author, int year, long time) {
            this.path = path;
            this.name = name;
            this.title = title;
            this.author = author;
            this.year = year;
            this.time = time;
        }

        TopicData.Book book() {
            return new TopicData.Book(name, title, author, year);
        }
    }

    /** A book's one line per chapter: its skeleton, else its study preview; empty when it has neither. */
    static SkeletonData lines(String bookName) {
        final SkeletonData d = SkeletonData.fromJson(AssistStore.get().item(bookName, SkeletonData.KEY));
        return d.chapterLines.isEmpty() ? SkeletonData.fromStudy(StudyStore.get().load(bookName)) : d;
    }

    /** The books to pick from. Off the main thread. */
    static List<Item> list() {
        final List<Item> out = new ArrayList<>();
        try {
            // the data files there are, by file name, with when they changed last
            final Map<String, Long> files = new HashMap<>();
            for (String folder : new String[]{AssistStore.FOLDER, StudyStore.FOLDER}) {
                final File[] found = new File(AppProfile.SYNC_FOLDER_PROFILE, folder).listFiles();
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
            final List<Item> candidates = new ArrayList<>();
            final Set<String> seen = new HashSet<>();
            for (FileMeta meta : AppDB.get().getAll()) {
                final String path = meta == null ? null : meta.getPath();
                if (path == null) {
                    continue;
                }
                final String name = AiHighlights.bookName(path);
                final Long time = files.get(AiStore.fileName(name));
                if (time == null || !seen.add(name) || !new File(path).isFile()) {
                    continue;
                }
                final Integer year = meta.getYear();
                final String title = meta.getTitle() == null || meta.getTitle().trim().isEmpty() ? name : meta.getTitle().trim();
                candidates.add(new Item(path, name, title, meta.getAuthor() == null ? "" : meta.getAuthor().trim(),
                        year == null ? 0 : year, time));
            }
            Collections.sort(candidates, (x, y) -> Long.compare(y.time, x.time));
            for (Item item : candidates) {
                if (out.size() >= MAX) {
                    break;
                }
                if (!lines(item.name).chapterLines.isEmpty()) {
                    out.add(item);
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return out;
    }

    /** Where a book (by file name) is on this device, or null. Off the main thread. */
    static String pathOf(String bookName) {
        try {
            for (FileMeta meta : AppDB.get().getAll()) {
                final String path = meta == null ? null : meta.getPath();
                if (path != null && AiHighlights.bookName(path).equals(bookName) && new File(path).isFile()) {
                    return path;
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return null;
    }
}
