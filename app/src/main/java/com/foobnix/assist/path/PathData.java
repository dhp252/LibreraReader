package com.foobnix.assist.path;

import com.foobnix.study.StudyBook;
import com.foobnix.study.StudyTime;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The reader's shortest path through a book to one idea: the goal as typed, the chapters to read
 * in order (by chapter key, the same on every device and in every layout) with a one-line why
 * each, which were read ({@code done}) and which the 2-minute check took off the path
 * ({@code known}), and what the rest of the book is about. Kept in {@code AssistStore} under
 * {@link #KEY}, one path per book: a new goal replaces it.
 */
public final class PathData {

    /** The AssistStore item of a book's path. */
    public static final String KEY = "path:current";

    /** {@link Chapter#answer}: the check has not asked this chapter yet. */
    public static final int NOT_ASKED = -1;
    /** {@link Chapter#answer}: "Not sure, keep this chapter on my path". */
    public static final int NOT_SURE = -2;

    public String goal = "";
    /** In reading order; the goal chapter is the last. */
    public final List<Chapter> chapters = new ArrayList<>();
    public String goalKey = "";
    /** "wages, rent, banking, Europe's history, taxes". */
    public String skipped = "";
    /** How many chapters the book's list had. */
    public int total;
    /** Characters of every chapter of the book's list, for the time the whole book takes. */
    public final List<Long> allChars = new ArrayList<>();
    public String model = "";
    public long made;

    /** One chapter of the path. */
    public static final class Chapter {
        public final String key;
        public String title = "";
        /** "I.7", or "". */
        public String code = "";
        public String why = "";
        /** What the chapter is about (the skeleton's line); the check's question is built from it. */
        public String line = "";
        public long chars;
        /** The reader finished it. */
        public boolean done;
        /** The 2-minute check showed the reader knows it: off the path until put back. */
        public boolean known;
        /** The check's question, or null before it was made. */
        public PathPrompts.Question question;
        /** The option picked (0 to 3), {@link #NOT_ASKED} or {@link #NOT_SURE}. */
        public int answer = NOT_ASKED;

        public Chapter(String key) {
            this.key = key;
        }

        JSONObject toJson() throws JSONException {
            final JSONObject o = new JSONObject().put("key", key).put("title", title).put("code", code)
                    .put("why", why).put("line", line).put("chars", chars).put("done", done).put("known", known)
                    .put("a", answer);
            if (question != null) {
                o.put("q", question.toJson());
            }
            return o;
        }

        static Chapter fromJson(JSONObject o) {
            final String key = o == null ? "" : o.optString("key", "");
            if (key.isEmpty()) {
                return null;
            }
            final Chapter c = new Chapter(key);
            c.title = o.optString("title", "");
            c.code = o.optString("code", "");
            c.why = o.optString("why", "");
            c.line = o.optString("line", "");
            c.chars = o.optLong("chars", 0);
            c.done = o.optBoolean("done", false);
            c.known = o.optBoolean("known", false);
            c.question = PathPrompts.Question.fromJson(o.optJSONObject("q"));
            c.answer = o.optInt("a", NOT_ASKED);
            return c;
        }
    }

    public boolean isEmpty() {
        return chapters.isEmpty();
    }

    // ------------------------------------------------------------------ from the model's answer

    /**
     * The model's path laid on the book's chapter list: a step naming no chapter of the list is
     * dropped, a chapter counts once, and the goal chapter (the one the model named when it is on
     * the path, else the last) goes last.
     */
    public static PathData fromAnswer(String goal, PathPrompts.Answer a, List<PathChapters.Entry> list, String model,
                                      long now) {
        final PathData d = new PathData();
        d.goal = goal == null ? "" : goal.trim().replaceAll("\\s+", " ");
        d.total = list.size();
        for (PathChapters.Entry e : list) {
            d.allChars.add(e.chars);
        }
        final Set<String> seen = new HashSet<>();
        for (PathPrompts.Step s : a.steps) {
            final PathChapters.Entry e = entry(list, s.n, s.key);
            if (e == null || !seen.add(e.key) || d.chapters.size() >= PathPrompts.MAX_STEPS) {
                continue;
            }
            final Chapter c = new Chapter(e.key);
            c.title = e.title;
            c.code = e.code;
            c.why = PathText.clip(s.why, 120);
            c.line = e.line;
            c.chars = e.chars;
            d.chapters.add(c);
        }
        if (!d.chapters.isEmpty()) {
            final PathChapters.Entry named = entry(list, a.goal, a.goalKey);
            final Chapter goalChapter = named == null ? null : d.chapter(named.key);
            if (goalChapter != null && d.chapters.indexOf(goalChapter) != d.chapters.size() - 1) {
                d.chapters.remove(goalChapter);
                d.chapters.add(goalChapter);
            }
            d.goalKey = d.chapters.get(d.chapters.size() - 1).key;
        }
        d.skipped = PathText.clip(a.skipped, 160);
        d.model = model == null ? "" : model;
        d.made = now;
        return d;
    }

    private static PathChapters.Entry entry(List<PathChapters.Entry> list, int n, String key) {
        if (key != null && !key.isEmpty()) {
            for (PathChapters.Entry e : list) {
                if (e.key.equals(key)) {
                    return e;
                }
            }
        }
        return n >= 0 && n < list.size() ? list.get(n) : null;
    }

    // ------------------------------------------------------------------ the path now

    public Chapter chapter(String key) {
        for (Chapter c : chapters) {
            if (c.key.equals(key)) {
                return c;
            }
        }
        return null;
    }

    public boolean isGoal(Chapter c) {
        return c != null && c.key.equals(goalKey);
    }

    /** The chapters still on the path (the check took the others off), in order. */
    public List<Chapter> active() {
        final List<Chapter> out = new ArrayList<>();
        for (Chapter c : chapters) {
            if (!c.known) {
                out.add(c);
            }
        }
        return out;
    }

    /** The chapters the check took off the path, in order. */
    public List<Chapter> known() {
        final List<Chapter> out = new ArrayList<>();
        for (Chapter c : chapters) {
            if (c.known) {
                out.add(c);
            }
        }
        return out;
    }

    /** The chapters of the book that are not on the path. */
    public int skippedCount() {
        return Math.max(0, total - active().size());
    }

    /** The first chapter of the path not read yet; the goal chapter when all are; null for an empty path. */
    public Chapter firstUnread() {
        final List<Chapter> on = active();
        for (Chapter c : on) {
            if (!c.done) {
                return c;
            }
        }
        return on.isEmpty() ? null : on.get(on.size() - 1);
    }

    /** The next chapter of the path after {@code key} that is not read yet, or null (the path is done). */
    public Chapter nextAfter(String key) {
        final List<Chapter> on = active();
        int at = -1;
        for (int i = 0; i < on.size(); i++) {
            if (on.get(i).key.equals(key)) {
                at = i;
            }
        }
        for (int i = at + 1; i < on.size(); i++) {
            if (!on.get(i).done) {
                return on.get(i);
            }
        }
        if (at < 0) {
            return null;
        }
        // going back: one left out earlier on the path
        for (int i = 0; i < at; i++) {
            if (!on.get(i).done) {
                return on.get(i);
            }
        }
        return null;
    }

    /** Whether every chapter still on the path was read. */
    public boolean allDone() {
        for (Chapter c : chapters) {
            if (!c.known && !c.done) {
                return false;
            }
        }
        return !chapters.isEmpty();
    }

    // ------------------------------------------------------------------ times

    /** Minutes to read a chapter at the reader's speed (0 = not known yet); 0 when its length is not known. */
    public static int minutes(long chars, int wpm) {
        return chars <= 0 ? 0 : StudyTime.minutes(chars, StudyBook.READ, wpm);
    }

    /** Minutes for the chapters still on the path. */
    public int minutes(int wpm) {
        int sum = 0;
        for (Chapter c : chapters) {
            if (!c.known) {
                sum += minutes(c.chars, wpm);
            }
        }
        return sum;
    }

    /** Minutes for the whole book, chapter by chapter as the skeleton counts them. */
    public int bookMinutes(int wpm) {
        long sum = 0;
        for (Long chars : allChars) {
            sum += minutes(chars == null ? 0 : chars, wpm);
        }
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    /** The share of the book's time the path takes, 0 to 1; 0 when the times are not known. */
    public float share(int wpm) {
        final int book = bookMinutes(wpm);
        return book <= 0 ? 0 : Math.max(0f, Math.min(1f, minutes(wpm) / (float) book));
    }

    // ------------------------------------------------------------------ the 2-minute check

    /** The chapters the check asks about: on the path, not read, not the goal, not answered yet. */
    public List<Chapter> toCheck() {
        final List<Chapter> out = new ArrayList<>();
        for (Chapter c : chapters) {
            if (!c.known && !c.done && !isGoal(c) && c.answer == NOT_ASKED) {
                out.add(c);
            }
        }
        return out;
    }

    /** Whether the check has anything to ask (else its button is not shown). */
    public boolean canCheck() {
        return !toCheck().isEmpty();
    }

    /**
     * The reader's answer to a chapter's question: the right one takes the chapter off the path.
     *
     * @param picked the option (0 to 3) or {@link #NOT_SURE}
     * @return whether the chapter was taken off
     */
    public boolean answer(String key, int picked) {
        final Chapter c = chapter(key);
        if (c == null || isGoal(c)) {
            return false;
        }
        c.answer = picked;
        c.known = c.question != null && picked >= 0 && picked == c.question.right;
        return c.known;
    }

    /** Undo, or "Put back": the chapter is on the path again (its answer stays, so it is not asked twice). */
    public boolean putBack(String key) {
        final Chapter c = chapter(key);
        if (c == null || !c.known) {
            return false;
        }
        c.known = false;
        return true;
    }

    /** Lets the check ask again every chapter still on the path (a new round of questions is made). */
    public void resetCheck() {
        for (Chapter c : chapters) {
            if (!c.known) {
                c.answer = NOT_ASKED;
                c.question = null;
            }
        }
    }

    // ------------------------------------------------------------------ reading

    /** The chapter on the path holding {@code page} (0-based) in a layout's {@code ranges}, or null. */
    public Chapter at(Map<String, int[]> ranges, int page) {
        if (ranges == null) {
            return null;
        }
        for (Chapter c : chapters) {
            final int[] r = ranges.get(c.key);
            if (!c.known && r != null && page >= r[0] && page < r[1]) {
                return c;
            }
        }
        return null;
    }

    /**
     * The path chapter a page turn just finished: its last page was on screen and the reader
     * turned on to the first page after it (for the book's last chapter: to the book's last page).
     * A jump from elsewhere finishes nothing.
     *
     * @param ranges    the chapters in the reader's layout, key = {first page, page after the last}
     * @param prevFrom  the first page shown before the turn, 0-based; -1 = none
     * @param prevLast  the last page shown before the turn
     * @param from      the first page shown now
     * @param to        the last page shown now
     * @param pageCount pages of the book in this layout
     * @return the chapter, or null
     */
    public Chapter finishedBy(Map<String, int[]> ranges, int prevFrom, int prevLast, int from, int to, int pageCount) {
        if (ranges == null || prevFrom < 0 || from <= prevFrom) {
            return null;
        }
        for (Chapter c : chapters) {
            final int[] r = ranges.get(c.key);
            if (c.known || c.done || r == null || r[1] <= r[0]) {
                continue;
            }
            final int last = r[1] - 1;
            if (r[1] >= pageCount) {
                // the book ends with it: there is no page after
                if (prevLast >= r[0] && prevLast < pageCount - 1 && to >= pageCount - 1 && from <= prevLast + 1) {
                    return c;
                }
            } else if (prevFrom <= last && last <= prevLast && from >= r[1] && from <= r[1] + 1) {
                return c;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ stored

    public JSONObject toJson() throws JSONException {
        final JSONArray list = new JSONArray();
        for (Chapter c : chapters) {
            list.put(c.toJson());
        }
        return new JSONObject().put("goal", goal).put("chapters", list).put("goalKey", goalKey)
                .put("skipped", skipped).put("total", total).put("all", new JSONArray(allChars))
                .put("model", model).put("made", made);
    }

    /** A stored path; empty when {@code o} is null. */
    public static PathData fromJson(JSONObject o) {
        final PathData d = new PathData();
        if (o == null) {
            return d;
        }
        d.goal = o.optString("goal", "");
        final JSONArray list = o.optJSONArray("chapters");
        for (int i = 0; list != null && i < list.length(); i++) {
            final Chapter c = Chapter.fromJson(list.optJSONObject(i));
            if (c != null && d.chapter(c.key) == null) {
                d.chapters.add(c);
            }
        }
        d.goalKey = o.optString("goalKey", "");
        if (d.chapter(d.goalKey) == null && !d.chapters.isEmpty()) {
            d.goalKey = d.chapters.get(d.chapters.size() - 1).key;
        }
        d.skipped = o.optString("skipped", "");
        d.total = Math.max(o.optInt("total", 0), d.chapters.size());
        final JSONArray all = o.optJSONArray("all");
        for (int i = 0; all != null && i < all.length(); i++) {
            d.allChars.add(all.optLong(i, 0));
        }
        d.model = o.optString("model", "");
        d.made = o.optLong("made", 0);
        return d;
    }
}
