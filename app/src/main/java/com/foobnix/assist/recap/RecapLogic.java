package com.foobnix.assist.recap;

import com.foobnix.ai.BookChapters;
import com.foobnix.ai.SideMatter;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The rules of the re-entry recap (canvas "Recap · back after 2 days / 3 weeks"), free of Android:
 * how long the reader was away and what size of recap that asks for, how much of the book is sent
 * and which pages (only pages before the reader's own, never past it), the highlight shown, and
 * when a recap made before can be shown again without asking the model.
 */
public final class RecapLogic {

    public static final long DAY_MS = 24L * 60 * 60 * 1000;
    /** Away at least this long: a short recap (20 seconds). */
    public static final int SHORT_DAYS = 2;
    /** Away at least this long: the longer recap with its 30 sec / 2 min / 5 min choice. */
    public static final int LONG_DAYS = 7;

    public static final String SHORT = "short";
    public static final String LONG = "long";
    /** AssistStore key prefix: {@code recap:short}, {@code recap:long}. */
    public static final String KEY_PREFIX = "recap:";

    /** Recap lengths of the longer recap; the short one shows its own three lines. */
    public static final int LEVEL_30S = 1;
    public static final int LEVEL_2M = 2;
    public static final int LEVEL_5M = 3;

    public static final int UNIT_DAYS = 0;
    public static final int UNIT_WEEKS = 1;
    public static final int UNIT_MONTHS = 2;

    /** Characters of book text at most per recap request (the provider's own limit can be lower). */
    static final int SHORT_BUDGET = 40_000;
    static final int LONG_BUDGET = 150_000;
    /** The least of a chapter's opening worth sending. */
    static final int MIN_OPENING = 600;
    /** A highlight counts as "near" where the reader stopped within this many pages before it. */
    static final int NEAR_PAGES = 30;

    private RecapLogic() {
    }

    // ------------------------------------------------------------------ time away

    /** Whole days between the last reading and now; 0 when unknown or in the future. */
    public static int daysAway(long lastReadMs, long nowMs) {
        if (lastReadMs <= 0 || nowMs <= lastReadMs) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, (nowMs - lastReadMs) / DAY_MS);
    }

    /** {@link #SHORT} for 2 to 6 days away, {@link #LONG} for 7 or more, else null (no recap). */
    public static String bucket(long lastReadMs, long nowMs) {
        final int days = daysAway(lastReadMs, nowMs);
        if (days >= LONG_DAYS) {
            return LONG;
        }
        return days >= SHORT_DAYS ? SHORT : null;
    }

    /** The length the longer recap opens on: 30 sec for a week or so, 2 min for weeks, 5 min after months. */
    public static int level(int days) {
        if (days < 14) {
            return LEVEL_30S;
        }
        return days < 45 ? LEVEL_2M : LEVEL_5M;
    }

    /** How to say the time away: {count, unit} with unit {@link #UNIT_DAYS}, {@link #UNIT_WEEKS} or {@link #UNIT_MONTHS}. */
    public static int[] away(int days) {
        if (days < 14) {
            return new int[]{Math.max(0, days), UNIT_DAYS};
        }
        if (days < 60) {
            return new int[]{days / 7, UNIT_WEEKS};
        }
        return new int[]{days / 30, UNIT_MONTHS};
    }

    /** The local calendar day of a time (days since 1970), for "made today". */
    public static long day(long ms, int zoneOffsetMs) {
        return Math.floorDiv(ms + zoneOffsetMs, DAY_MS);
    }

    public static String key(String bucket) {
        return KEY_PREFIX + bucket;
    }

    // ------------------------------------------------------------------ what is sent

    /** Characters of book text to send for a recap of this size, within the provider's limit. */
    public static int budget(String bucket, int maxChars) {
        final int own = LONG.equals(bucket) ? LONG_BUDGET : SHORT_BUDGET;
        return Math.max(0, Math.min(own, maxChars));
    }

    /**
     * The part of the budget for the pages right before the reader's (sent whole): most of it for
     * a short recap, about a third for the longer one, whose story so far needs every chapter.
     */
    public static int tailBudget(String bucket, int budget) {
        return LONG.equals(bucket) ? budget * 35 / 100 : budget * 60 / 100;
    }

    /** The opening of a chapter before the pages sent whole, with the characters it may take. */
    public static final class Slice {
        public final int from;
        /** Exclusive; never past the pages sent whole (nor the reader's page). */
        public final int to;
        public final String title;
        public final int maxChars;

        Slice(int from, int to, String title, int maxChars) {
            this.from = from;
            this.to = to;
            this.title = title;
            this.maxChars = maxChars;
        }

        @Override
        public String toString() {
            return from + "-" + to + ":" + maxChars;
        }
    }

    /**
     * The openings of the chapters that start before {@code tailStart} (the first page sent
     * whole), sharing {@code budget}; side matter and the front pages left out. When there are too
     * many chapters for {@link #MIN_OPENING} each, chapters are taken evenly, the latest kept.
     */
    public static List<Slice> openings(List<BookChapters.Range> chapters, int tailStart, int budget) {
        final List<BookChapters.Range> before = new ArrayList<>();
        if (chapters != null) {
            for (BookChapters.Range r : chapters) {
                if (r.from < tailStart && r.to > r.from && !BookChapters.FRONT.equals(r.key)
                        && !SideMatter.isSideTitle(r.title)) {
                    before.add(r);
                }
            }
        }
        final List<Slice> out = new ArrayList<>();
        if (before.isEmpty() || budget < MIN_OPENING) {
            return out;
        }
        final int fit = Math.max(1, budget / MIN_OPENING);
        final List<BookChapters.Range> picked = new ArrayList<>();
        if (before.size() <= fit) {
            picked.addAll(before);
        } else {
            // evenly spread, counted back from the latest so the chapter before the reader's is in
            final int n = before.size();
            int lastIndex = -1;
            for (int i = 0; i < fit; i++) {
                final int index = (int) Math.round((double) i * (n - 1) / Math.max(1, fit - 1));
                if (index != lastIndex) {
                    picked.add(before.get(index));
                    lastIndex = index;
                }
            }
        }
        final int share = budget / picked.size();
        for (BookChapters.Range r : picked) {
            out.add(new Slice(r.from, Math.min(r.to, tailStart), r.title, share));
        }
        return out;
    }

    /** At most {@code max} characters, cut after a sentence (or at least a word) and marked with "…". */
    public static String cut(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        if (max <= 1) {
            return "";
        }
        final String head = text.substring(0, max - 1);
        int end = -1;
        for (int i = head.length() - 1; i >= head.length() / 2; i--) {
            final char c = head.charAt(i);
            if ((c == '.' || c == '!' || c == '?' || c == '\n') && (i + 1 >= head.length() || Character.isWhitespace(head.charAt(i + 1)))) {
                end = i + 1;
                break;
            }
        }
        if (end < 0) {
            final int space = head.lastIndexOf(' ');
            end = space > head.length() / 2 ? space : head.length();
        }
        return head.substring(0, end).trim() + "…";
    }

    /** Text of the book for the prompt, from one chapter. */
    public static final class Excerpt {
        public final String chapter;
        /** 1-based pages, for the model's "Ch." references and the reader's sense of place. */
        public final int firstPage;
        public final int lastPage;
        public final String text;
        /** The pages are sent whole (the ones right before the reader's), not only an opening. */
        public final boolean whole;

        public Excerpt(String chapter, int firstPage, int lastPage, String text, boolean whole) {
            this.chapter = chapter == null ? "" : chapter;
            this.firstPage = firstPage;
            this.lastPage = lastPage;
            this.text = text;
            this.whole = whole;
        }
    }

    /**
     * The text read for a recap as excerpts in page order: per chapter its opening (pages before
     * {@code tailStart}) and its pages sent whole (from {@code tailStart} up to, not including,
     * {@code page}). Pages from {@code page} on are never used, whatever {@code plain} holds.
     *
     * @param plain text by page (0-based); null or empty for pages not read
     */
    public static List<Excerpt> excerpts(List<BookChapters.Range> chapters, String[] plain, int tailStart, int page) {
        final List<Excerpt> out = new ArrayList<>();
        if (chapters == null || plain == null) {
            return out;
        }
        final int end = Math.min(page, plain.length);
        for (BookChapters.Range r : chapters) {
            if (r.from >= end) {
                break;
            }
            add(out, r, plain, r.from, Math.min(Math.min(r.to, tailStart), end), false);
            add(out, r, plain, Math.max(r.from, tailStart), Math.min(r.to, end), true);
        }
        return out;
    }

    private static void add(List<Excerpt> out, BookChapters.Range r, String[] plain, int from, int to, boolean whole) {
        final StringBuilder text = new StringBuilder();
        int first = -1;
        int last = -1;
        for (int p = Math.max(0, from); p < to; p++) {
            final String t = plain[p];
            if (t == null || t.trim().isEmpty()) {
                continue;
            }
            if (text.length() > 0) {
                text.append("\n\n");
            }
            text.append(t.trim());
            if (first < 0) {
                first = p;
            }
            last = p;
        }
        if (first >= 0) {
            out.add(new Excerpt(r.title, first + 1, last + 1, text.toString(), whole));
        }
    }

    /** "Chapter XIV. In which…" → "Chapter XIV"; at most 18 characters. */
    public static String shortChapter(String title) {
        if (title == null) {
            return "";
        }
        String t = title.trim();
        for (String stop : new String[]{". ", ": ", " – ", " — ", " - "}) {
            int i = t.indexOf(stop);
            // "Ch. 14" keeps its dot: a cut needs a word and a number (or name) before it
            while (i > 0 && t.substring(0, i).indexOf(' ') < 0) {
                i = t.indexOf(stop, i + 1);
            }
            if (i > 0) {
                t = t.substring(0, i);
            }
        }
        return t.length() > 18 ? t.substring(0, 17).trim() + "…" : t;
    }

    // ------------------------------------------------------------------ the highlight

    /** A mark of the reader's, as far as the recap needs it. */
    public static final class MarkInfo {
        public final int page;
        public final long time;
        public final String quote;
        public final String note;

        public MarkInfo(int page, long time, String quote, String note) {
            this.page = page;
            this.time = time;
            this.quote = quote == null ? "" : quote.trim();
            this.note = note == null ? "" : note.trim();
        }

        public boolean hasNote() {
            return !note.isEmpty();
        }
    }

    /**
     * The highlight or note to show: the latest made within {@link #NEAR_PAGES} pages before (or
     * on) the reader's page, else the latest anywhere before it; none past the page (a spoiler).
     */
    public static MarkInfo pickMark(List<MarkInfo> marks, int page) {
        MarkInfo near = null;
        MarkInfo any = null;
        if (marks == null) {
            return null;
        }
        for (MarkInfo m : marks) {
            if (m == null || m.page > page || (m.quote.isEmpty() && m.note.isEmpty())) {
                continue;
            }
            if (any == null || m.time > any.time) {
                any = m;
            }
            if (page - m.page <= NEAR_PAGES && (near == null || m.time > near.time)) {
                near = m;
            }
        }
        return near != null ? near : any;
    }

    // ------------------------------------------------------------------ the saved recap

    /** Fields of a saved recap ({@code recap:<bucket>} in AssistStore). */
    public static final String PAGE = "page";
    public static final String PAGES = "pages";
    public static final String DAY = "day";
    public static final String RECAP = "recap";

    /** A recap saved for the same page of the same layout, made today: shown without asking again. */
    public static boolean reusable(JSONObject saved, int page, int pages, long today) {
        return saved != null && saved.optJSONObject(RECAP) != null && saved.optInt(PAGE, -1) == page
                && saved.optInt(PAGES, -1) == pages && saved.optLong(DAY, Long.MIN_VALUE) == today;
    }
}
