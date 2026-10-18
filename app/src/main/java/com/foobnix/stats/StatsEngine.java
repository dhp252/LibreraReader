package com.foobnix.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Turns each device's events, in the order the device wrote them, into {@link StatsData}
 * following {@link StatsRules}. Feeding is incremental: what a device appends later continues
 * where its previous events stopped (an open session, a book's last page).
 */
public class StatsEngine {

    private final Map<String, Device> devices = new HashMap<>();

    /** One device's summaries and what its next events continue. */
    private static class Device {
        final StatsData data = new StatsData();
        Session session;
        /** End of the last reading or listening, for the session gap. */
        long lastActivity;
        /** When the reader left the screen, 0 while it is on screen. */
        long pausedAt;
        String lastSessionBook;
        long listenStart;
        int listenTz;
        String listenBook;
    }

    private static class Session {
        final String book;
        final long start;
        final int tz;
        /** Started by opening the book (a bounce candidate), not by coming back to it. */
        final boolean opened;
        long end;
        boolean read;

        Session(String book, long start, int tz, boolean opened) {
            this.book = book;
            this.start = start;
            this.tz = tz;
            this.opened = opened;
            this.end = start;
        }
    }

    public void feed(String deviceId, StatsEvent e) {
        final Device d = device(deviceId);
        switch (e.type) {
            case StatsEvent.DEV:
                if (e.name != null) {
                    d.data.devices.put(deviceId, e.name);
                }
                return;
            case StatsEvent.OPEN:
                open(d, e);
                return;
            case StatsEvent.RESUME:
                resume(d, e);
                return;
            case StatsEvent.PAUSE:
                d.pausedAt = e.ts;
                return;
            case StatsEvent.CLOSE:
                close(d, e);
                return;
            case StatsEvent.PAGE:
                page(d, e);
                return;
            case StatsEvent.JUMP:
                jump(d, e);
                return;
            case StatsEvent.TTS:
                tts(d, e);
                return;
            default:
                count(d, e);
        }
    }

    /** Each device's summaries so far (the device's open session is not in them yet). */
    public Map<String, StatsData> perDevice() {
        final Map<String, StatsData> out = new HashMap<>();
        for (Map.Entry<String, Device> e : devices.entrySet()) {
            out.put(e.getKey(), e.getValue().data);
        }
        return out;
    }

    public StatsData merged(Set<String> deviceIds) {
        return StatsData.merge(perDevice(), deviceIds);
    }

    private Device device(String id) {
        Device d = devices.get(id);
        if (d == null) {
            d = new Device();
            d.data.devices.put(id, id);
            devices.put(id, d);
        }
        return d;
    }

    private void open(Device d, StatsEvent e) {
        endSession(d);
        d.pausedAt = 0;
        final StatsData.Book b = bookOf(d, e);
        b.opens++;
        if (b.firstOpen == 0) {
            b.firstOpen = e.ts;
        }
        b.lastOpen = Math.max(b.lastOpen, e.ts);
        b.prevPage = -1;
        progress(b, e.percent, e.ts);
        d.data.day(StatsData.dayOf(e.ts, e.tz)).opens++;
        startSession(d, e.book, e.ts, e.tz, true);
    }

    private void resume(Device d, StatsEvent e) {
        final boolean keep = d.session != null && d.session.book.equals(e.book)
                && d.pausedAt > 0 && e.ts - d.pausedAt <= StatsRules.RESUME_KEEP_MS;
        d.pausedAt = 0;
        if (!keep) {
            endSession(d);
            startSession(d, e.book, e.ts, e.tz, false);
        }
        if (e.book != null) {
            d.data.book(e.book).lastOpen = Math.max(d.data.book(e.book).lastOpen, e.ts);
        }
    }

    private void close(Device d, StatsEvent e) {
        if (e.book != null) {
            progress(d.data.book(e.book), e.percent, e.ts);
        }
        endSession(d);
        d.pausedAt = 0;
    }

    private void page(Device d, StatsEvent e) {
        final StatsData.Book b = d.data.book(e.book);
        if (e.pages > 0) {
            b.pages = e.pages;
        }
        final boolean tts = StatsEvent.MODE_TTS.equals(e.mode);
        final long raw = Math.max(0, e.raw);
        final long start = e.ts - raw;
        final long ms = Math.min(raw, StatsRules.PAGE_CAP_MS);
        final float progress = e.pages > 0 ? Math.min(1f, e.page / (float) e.pages) : -1;

        // the position moves with read aloud too; its time is the listening time
        if (!tts) {
            if (d.session == null || !d.session.book.equals(e.book) || start - d.lastActivity > StatsRules.SESSION_GAP_MS
                    || (d.pausedAt > 0 && start - d.pausedAt > StatsRules.RESUME_KEEP_MS)) {
                endSession(d);
                startSession(d, e.book, start, e.tz, false);
                d.pausedAt = 0;
            }
            final int dayKey = StatsData.dayOf(start, e.tz);
            final StatsData.Day day = d.data.day(dayKey);
            day.readMs += ms;
            day.hourMs[StatsData.hourOf(start, e.tz)] += ms;
            day.addBookMs(e.book, ms);
            b.readMs += ms;
            if (e.eye > 0) {
                final long eye = Math.min(e.eye, StatsRules.PAGE_CAP_MS);
                final long look = Math.min(Math.max(0, e.look), eye);
                day.eyeMs += eye;
                day.lookMs += look;
                b.eyeMs += eye;
                b.lookMs += look;
            }

            final boolean read = raw >= StatsRules.SKIM_MS;
            final StatsData.Chapter chapter = chapter(b, e);
            if (chapter != null) {
                chapter.ms += ms;
            }
            if (!read) {
                if (StatsEvent.MODE_READ.equals(e.mode)) {
                    day.skims++;
                    b.skims++;
                }
            } else {
                if (e.words >= 0) {
                    b.countedWords += e.words;
                    b.countedPages++;
                }
                final int words = e.words >= 0 ? e.words : b.averagePageWords();
                final float pages = b.isFixedLayout() ? 1f : words / (float) StatsRules.WORDS_PER_VIRTUAL_PAGE;
                day.words += words;
                day.pages += pages;
                b.words += words;
                b.pagesRead += pages;
                b.pageViews++;
                b.readDays.add(dayKey);
                b.wordsByDay.put(dayKey, b.wordsByDay.getOrDefault(dayKey, 0L) + words);
                if (chapter != null) {
                    chapter.words += words;
                }
                if (b.firstRead == 0) {
                    b.firstRead = start;
                    day.starts++;
                }
                if (progress >= 0) {
                    final int slot = Math.min(StatsRules.PROGRESS_SLOTS - 1, (int) (progress * StatsRules.PROGRESS_SLOTS));
                    if (b.seen.get(slot)) {
                        day.rereads++;
                        b.rereads++;
                    }
                    b.seen.set(slot);
                }
                d.session.read = true;

                final boolean timed = StatsEvent.MODE_READ.equals(e.mode) && raw <= StatsRules.PAGE_CAP_MS && e.words > 0;
                final double wpm = timed ? e.words * 60_000.0 / raw : 0;
                if (timed && wpm <= StatsRules.WPM_MAX * (b.isCjk() ? 2 : 1)) {
                    (b.isCjk() ? day.cpm : day.wpm).add(wpm);
                    b.wpm.add(wpm);
                    b.speed.add(raw, e.words);
                    if (chapter != null) {
                        chapter.speed.add(raw, e.words);
                    }
                    if (!b.isCjk()) {
                        day.hourSpeed[StatsData.hourOf(start, e.tz)].add(raw, e.words);
                        final int stretch = (int) (Math.max(0, start - d.session.start) / StatsRules.FATIGUE_BUCKET_MS);
                        StatsData.speed(day.fatigue, stretch).add(raw, e.words);
                    }
                    StatsData.speed(day.langSpeed, b.lang == null ? "" : b.lang).add(raw, e.words);
                }
                if (progress >= StatsRules.FINISHED && !b.finished) {
                    b.finished = true;
                    b.finishTimes.add(e.ts);
                    day.finishes++;
                } else if (progress >= 0 && progress < StatsRules.REREAD_FROM && b.finished) {
                    b.finished = false;
                    b.seen.clear();
                }
            }
            if (b.prevPage >= 0 && e.page < b.prevPage) {
                day.backtracks++;
                b.backtracks++;
            }
            d.lastActivity = Math.max(d.lastActivity, start + ms);
            d.session.end = Math.max(d.session.end, start + ms);
        }
        b.prevPage = e.page;
        progress(b, progress, e.ts);
    }

    private void jump(Device d, StatsEvent e) {
        final StatsData.Book b = d.data.book(e.book);
        if (e.to < e.from && !StatsEvent.VIA_SYNC.equals(e.via)) {
            d.data.day(StatsData.dayOf(e.ts, e.tz)).backtracks++;
            b.backtracks++;
        }
        b.prevPage = e.to;
    }

    private void tts(Device d, StatsEvent e) {
        endListening(d, e.ts);
        if (e.on) {
            d.listenStart = e.ts;
            d.listenTz = e.tz;
            d.listenBook = e.book;
        }
    }

    private void endListening(Device d, long at) {
        if (d.listenStart <= 0 || d.listenBook == null) {
            d.listenStart = 0;
            return;
        }
        final long ms = Math.max(0, Math.min(at - d.listenStart, StatsRules.LISTEN_CAP_MS));
        final StatsData.Day day = d.data.day(StatsData.dayOf(d.listenStart, d.listenTz));
        day.listenMs += ms;
        day.addBookMs(d.listenBook, ms);
        final StatsData.Book b = d.data.book(d.listenBook);
        b.listenMs += ms;
        if (ms > 0) {
            b.readDays.add(StatsData.dayOf(d.listenStart, d.listenTz));
        }
        // listening is reading time of the session it falls in
        if (d.session == null || !d.session.book.equals(d.listenBook) || d.listenStart - d.lastActivity > StatsRules.SESSION_GAP_MS) {
            endSession(d);
            startSession(d, d.listenBook, d.listenStart, d.listenTz, false);
        }
        d.session.read |= ms > 0;
        d.session.end = Math.max(d.session.end, d.listenStart + ms);
        d.lastActivity = Math.max(d.lastActivity, d.listenStart + ms);
        d.listenStart = 0;
        d.listenBook = null;
    }

    private void count(Device d, StatsEvent e) {
        final StatsData.Day day = d.data.day(StatsData.dayOf(e.ts, e.tz));
        final StatsData.Book b = e.book == null ? null : d.data.book(e.book);
        switch (e.type) {
            case StatsEvent.HIGHLIGHT:
                day.highlights++;
                if (b != null) {
                    b.highlights++;
                }
                break;
            case StatsEvent.NOTE:
                day.notes++;
                if (b != null) {
                    b.notes++;
                }
                break;
            case StatsEvent.BOOKMARK:
                day.bookmarks++;
                if (b != null) {
                    b.bookmarks++;
                }
                break;
            case StatsEvent.SEARCH:
                day.searches++;
                if (b != null) {
                    b.searches++;
                }
                break;
            case StatsEvent.LOOKUP:
                day.lookups++;
                if (b != null) {
                    b.lookups++;
                }
                break;
            default:
                // a type of a newer version: skipped
        }
    }

    private void startSession(Device d, String book, long start, int tz, boolean opened) {
        d.session = new Session(book, start, tz, opened);
        d.lastActivity = Math.max(d.lastActivity, start);
    }

    private void endSession(Device d) {
        final Session s = d.session;
        if (s == null) {
            return;
        }
        d.session = null;
        final long length = Math.max(0, s.end - s.start);
        final StatsData.Day day = d.data.day(StatsData.dayOf(s.start, s.tz));
        final StatsData.Book b = d.data.book(s.book);
        if (s.opened && !s.read && length < StatsRules.BOUNCE_MS) {
            day.bounces++;
            b.bounces++;
            return;
        }
        if (!s.read) {
            // came back and left without reading: not a session
            return;
        }
        day.sessions++;
        day.sessionMs += length;
        day.longestSessionMs = Math.max(day.longestSessionMs, length);
        b.sessions++;
        if (d.lastSessionBook != null && !d.lastSessionBook.equals(s.book)) {
            day.switches++;
        }
        d.lastSessionBook = s.book;
    }

    private StatsData.Book bookOf(Device d, StatsEvent e) {
        final StatsData.Book b = d.data.book(e.book);
        if (e.title != null) {
            b.title = e.title;
        }
        if (e.author != null) {
            b.author = e.author;
        }
        if (e.lang != null) {
            b.lang = e.lang;
        }
        if (e.ext != null) {
            b.ext = e.ext;
        }
        if (e.pages > 0) {
            b.pages = e.pages;
        }
        return b;
    }

    private static StatsData.Chapter chapter(StatsData.Book b, StatsEvent e) {
        if (e.chapter == null || e.chapter.isEmpty()) {
            return null;
        }
        StatsData.Chapter c = b.chapters.get(e.chapter);
        if (c == null) {
            c = new StatsData.Chapter(e.chapter, e.page);
            b.chapters.put(e.chapter, c);
        }
        c.firstPage = Math.min(c.firstPage, e.page);
        return c;
    }

    private static void progress(StatsData.Book b, float percent, long ts) {
        if (percent >= 0 && ts >= b.progressTs) {
            b.progress = percent;
            b.progressTs = ts;
        }
    }
}
