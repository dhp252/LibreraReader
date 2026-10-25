package com.foobnix.bookmap;

import com.foobnix.ai.AiStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What the book map asks of the model (design-2026-10-25.md), and reading its answers back. The
 * same prompts for every book: no genre is assumed anywhere.
 */
public final class MapPrompts {

    /** Lanes of a map: more do not fit a phone. */
    public static final int LANES = 7;
    /** Threads one moment may name. */
    static final int MOMENT_THREADS = 4;

    private MapPrompts() {
    }

    private static final String KINDS = "Give each moment one kind:\n"
            + "- introduce: the thread first appears or is defined\n"
            + "- develop: it grows: a scene, evidence, an example, an elaboration\n"
            + "- turn: it changes direction: a twist, reversal, objection, caveat, failure\n"
            + "- resolve: it is settled: a payoff, an answer, a conclusion, an exit\n";

    private static String threadsRule() {
        return "1. Threads: list what this chapter is built from that recurs or will matter beyond it: people, "
                + "groups, places, objects, ideas, claims, problems, open questions. Use whatever this book is made "
                + "of; do not force a genre. Name each the way the book does, add the other names it goes by, and "
                + "say in at most 8 words what it is.\n";
    }

    /** How quotes of the book are copied: the app finds them in the book's words, so any change loses them. */
    private static final String QUOTE_RULES = "Every quote is copied exactly from the text, character for "
            + "character: no changes, no ellipses, no quotation marks added. Never quote headings, captions, "
            + "page numbers or lines of a table of contents.";

    /** Step 1: one chapter (or a part of a long one) into its threads and moments. */
    public static String chapterSystem(String answerLanguage) {
        return "You map how a book unfolds, so that a reader can see its shape at a glance. You get one chapter, "
                + "or one part of a long chapter.\n"
                + threadsRule()
                + "2. Moments: go through the text in order and list every moment that adds to or changes a thread. "
                + KINDS
                + "Each moment names 1 to 4 threads from your list (several means they meet in it), a label of at "
                + "most 8 words in your own words, and a quote: one whole sentence copied from the text. Leave out "
                + "moments that change nothing.\n"
                + "Keep names as the book has them; write the labels and what each thread is in "
                + language(answerLanguage) + ".\n"
                + "Answer with JSON only:\n"
                + "{\"threads\":[{\"name\":\"\",\"aliases\":[],\"what\":\"\"}],"
                + "\"moments\":[{\"threads\":[],\"kind\":\"\",\"label\":\"\",\"quote\":\"\"}]}\n"
                + QUOTE_RULES;
    }

    /**
     * @param part  1-based part of a long chapter
     * @param parts how many parts the chapter was cut into (1: the whole chapter)
     */
    public static String chapter(String bookTitle, String chapterTitle, int part, int parts, String text) {
        final String title = parts > 1 ? chapterTitle + " (part " + part + " of " + parts + ")" : chapterTitle;
        return header(bookTitle, title) + "Map this text.\n\n<text>\n" + text + "\n</text>";
    }

    /** Step 1 from the ideas key passages already found in a chapter: no text of the book is sent. */
    public static String ideasSystem(String answerLanguage) {
        return "You map how a book unfolds, so that a reader can see its shape at a glance. You get the ideas "
                + "found in one chapter, in reading order, each with its id and category; not the chapter's text.\n"
                + threadsRule()
                + "2. Moments: turn the ideas into moments. A moment is one idea (by its id) that adds to or changes "
                + "a thread. " + KINDS
                + "Each moment names 1 to 4 threads from your list (several means they meet in it) and a label of "
                + "at most 8 words in your own words. Leave out ideas that change nothing.\n"
                + "Keep names as the book has them; write the labels and what each thread is in "
                + language(answerLanguage) + ".\n"
                + "Answer with JSON only:\n"
                + "{\"threads\":[{\"name\":\"\",\"aliases\":[],\"what\":\"\"}],"
                + "\"moments\":[{\"idea\":0,\"threads\":[],\"kind\":\"\",\"label\":\"\"}]}";
    }

    public static String ideas(String bookTitle, String chapterTitle, List<AiStore.Idea> ideas) {
        final StringBuilder out = new StringBuilder(header(bookTitle, chapterTitle));
        out.append("The ideas of this chapter:\n");
        for (AiStore.Idea idea : ideas) {
            out.append(idea.id).append(". [").append(idea.category == null ? "idea" : idea.category.code).append("] ")
                    .append(idea.text.trim());
            if (idea.parent > 0) {
                out.append(" (supports ").append(idea.parent).append(')');
            }
            out.append('\n');
        }
        return out.toString();
    }

    /** Step 2: the whole book from step 1's output only: no text of the book. */
    public static String bookSystem(String answerLanguage) {
        return "You get the threads and moments of every chapter of a book, in order; a moment id like 3.4 is "
                + "chapter 3, moment 4. No text of the book. Build the book's map:\n"
                + "- premise: the book in one sentence.\n"
                + "- threads: merge the same thing under different names, and parts of one line of development (a "
                + "mystery and its clues, a theme and its symbol); keep the " + LANES + " threads that carry the book "
                + "(most moments, most meetings, longest span). Give each an id (a short lowercase word), a short "
                + "name (12 characters at most), what it is, and every chapter thread name it absorbs (\"merged\"). "
                + "Every other thread folds into Other.\n"
                + "- arcs: links between moments of different chapters that a reader needs to connect: pays_off (a "
                + "setup or question and its payoff), leads_to (cause and effect), supports, contradicts, needs (a "
                + "later moment builds on an earlier one). Only the ones that matter, at most 1 per 3 moments, each "
                + "with a why of at most 8 words.\n"
                + "- key: the moments to show zoomed out, about 2 per chapter, turns and meetings first.\n"
                + "Write the premise, names, what and why in " + language(answerLanguage) + ".\n"
                + "Answer with JSON only:\n"
                + "{\"premise\":\"\",\"threads\":[{\"id\":\"\",\"name\":\"\",\"what\":\"\",\"merged\":[]}],"
                + "\"arcs\":[{\"from\":\"\",\"to\":\"\",\"type\":\"\",\"why\":\"\"}],\"key\":[]}";
    }

    /**
     * The chapters' threads and moments for step 2, in reading order, within {@code budget}
     * characters: "develop" moments of one thread go first, then all "develop" moments, then the
     * threads' descriptions; the text is cut only when even that does not fit.
     */
    public static String book(String bookTitle, List<MapBook.Chapter> chapters, int budget) {
        for (int level = 0; level <= 3; level++) {
            final String text = bookText(bookTitle, chapters, level);
            if (text.length() <= budget || level == 3) {
                return text.length() <= budget ? text : text.substring(0, Math.max(0, budget));
            }
        }
        return "";
    }

    private static String bookText(String bookTitle, List<MapBook.Chapter> chapters, int level) {
        final StringBuilder out = new StringBuilder(header(bookTitle, null));
        for (MapBook.Chapter c : chapters) {
            if (c.moments.isEmpty()) {
                continue;
            }
            out.append("<chapter n=\"").append(c.n).append("\" title=\"").append(c.title.replace('"', '\'')).append("\">\n");
            out.append("Threads: ");
            for (int i = 0; i < c.threads.size(); i++) {
                final MapBook.Thread t = c.threads.get(i);
                if (i > 0) {
                    out.append("; ");
                }
                out.append(t.name);
                if (!t.aliases.isEmpty()) {
                    out.append(" (").append(join(t.aliases)).append(')');
                }
                if (level < 3 && !t.what.isEmpty()) {
                    out.append(": ").append(t.what);
                }
            }
            out.append('\n');
            for (int i = 0; i < c.moments.size(); i++) {
                final MapBook.Moment m = c.moments.get(i);
                final boolean develop = MapBook.DEVELOP.equals(m.kind);
                if ((level >= 1 && develop && m.threads.size() < 2) || (level >= 2 && develop)) {
                    continue;
                }
                out.append(c.momentId(i)).append(' ').append(m.kind).append(" [").append(join(m.threads)).append("] ")
                        .append(m.label).append('\n');
            }
            out.append("</chapter>\n");
        }
        return out.toString();
    }

    private static String join(List<String> parts) {
        final StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(p);
        }
        return out.toString();
    }

    private static String header(String bookTitle, String partTitle) {
        final StringBuilder out = new StringBuilder();
        if (bookTitle != null && !bookTitle.trim().isEmpty()) {
            out.append("Book: ").append(bookTitle.trim()).append('\n');
        }
        if (partTitle != null && !partTitle.trim().isEmpty()) {
            out.append("Chapter: ").append(partTitle.trim()).append('\n');
        }
        return out.length() > 0 ? out.append('\n').toString() : "";
    }

    private static String language(String answerLanguage) {
        return answerLanguage == null || answerLanguage.trim().isEmpty()
                ? "the language the text is written in"
                : answerLanguage.trim();
    }

    // --- answers ---

    /** A moment as the model wrote it: its quote is not checked yet. */
    public static final class Draft {
        public final List<String> threads;
        public final String kind;
        public final String label;
        /** The quote for a chapter answer, or "" for an ideas answer. */
        public final String quote;
        /** The idea id for an ideas answer, or 0. */
        public final int idea;

        public Draft(List<String> threads, String kind, String label, String quote, int idea) {
            this.threads = threads;
            this.kind = kind;
            this.label = label;
            this.quote = quote;
            this.idea = idea;
        }
    }

    /** Step 1's answer: its threads and whole moments, and whether it was cut off. */
    public static final class Extract {
        public final List<MapBook.Thread> threads = new ArrayList<>();
        public final List<Draft> moments = new ArrayList<>();
        public boolean cutOff;

        /** The threads of another part, added after these; a name already here is not added again. */
        public void add(Extract other) {
            final Set<String> names = new HashSet<>();
            for (MapBook.Thread t : threads) {
                names.add(t.name.toLowerCase(Locale.ROOT));
            }
            for (MapBook.Thread t : other.threads) {
                if (names.add(t.name.toLowerCase(Locale.ROOT))) {
                    threads.add(t);
                }
            }
            moments.addAll(other.moments);
            cutOff |= other.cutOff;
        }
    }

    /** Step 2's answer. */
    public static final class Plan {
        public String premise = "";
        public final List<MapBook.Lane> lanes = new ArrayList<>();
        public final List<MapBook.Arc> arcs = new ArrayList<>();
        public final List<String> key = new ArrayList<>();
    }

    /** A chapter answer: moments without a quote are left out. */
    public static Extract parseChapter(String answer) {
        return parseExtract(answer, false);
    }

    /** An ideas answer: moments without an idea id are left out. */
    public static Extract parseIdeas(String answer) {
        return parseExtract(answer, true);
    }

    private static Extract parseExtract(String answer, boolean ideas) {
        final Extract out = new Extract();
        final JSONObject whole = wholeObject(answer);
        final List<JSONObject> threads = new ArrayList<>();
        final List<JSONObject> moments = new ArrayList<>();
        if (whole != null) {
            objects(whole.optJSONArray("threads"), threads);
            objects(whole.optJSONArray("moments"), moments);
        } else {
            // cut off before the end: the whole objects that came
            Scan.objects(answer, Scan.topLevel(answer, "threads"), threads);
            out.cutOff = !Scan.objects(answer, Scan.topLevel(answer, "moments"), moments);
        }
        for (JSONObject t : threads) {
            final String name = t.optString("name", "").trim();
            if (!name.isEmpty()) {
                out.threads.add(new MapBook.Thread(name, MapBook.strings(t.optJSONArray("aliases")), t.optString("what", "").trim()));
            }
        }
        for (JSONObject m : moments) {
            final String kind = m.optString("kind", "").trim().toLowerCase(Locale.ROOT);
            final String label = m.optString("label", "").trim();
            final List<String> names = MapBook.strings(m.optJSONArray("threads"));
            final String quote = ideas ? "" : m.optString("quote", "").trim();
            final int idea = ideas ? m.optInt("idea", 0) : 0;
            if (!MapBook.isKind(kind) || label.isEmpty() || names.isEmpty() || (ideas ? idea <= 0 : quote.isEmpty())) {
                continue;
            }
            out.moments.add(new Draft(names.size() > MOMENT_THREADS ? new ArrayList<>(names.subList(0, MOMENT_THREADS)) : names,
                    kind, label, quote, idea));
        }
        return out;
    }

    /** The whole-book answer, or null when it holds no lane. */
    public static Plan parseBook(String answer) {
        final JSONObject whole = wholeObject(answer);
        final List<JSONObject> lanes = new ArrayList<>();
        final List<JSONObject> arcs = new ArrayList<>();
        final Plan out = new Plan();
        if (whole != null) {
            out.premise = whole.optString("premise", "").trim();
            objects(whole.optJSONArray("threads"), lanes);
            objects(whole.optJSONArray("arcs"), arcs);
            out.key.addAll(MapBook.strings(whole.optJSONArray("key")));
        } else {
            Scan.objects(answer, Scan.topLevel(answer, "threads"), lanes);
            Scan.objects(answer, Scan.topLevel(answer, "arcs"), arcs);
        }
        final Set<String> ids = new HashSet<>();
        for (JSONObject l : lanes) {
            if (out.lanes.size() == LANES) {
                break;
            }
            final String name = l.optString("name", "").trim();
            final List<String> merged = MapBook.strings(l.optJSONArray("merged"));
            if (name.isEmpty()) {
                continue;
            }
            if (merged.isEmpty()) {
                merged.add(name);
            }
            String id = l.optString("id", "").trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
            if (id.isEmpty() || ids.contains(id) || MapBook.BOOK.equals(id)) {
                id = "t" + (out.lanes.size() + 1);
            }
            ids.add(id);
            out.lanes.add(new MapBook.Lane(id, name, l.optString("what", "").trim(), merged));
        }
        for (JSONObject a : arcs) {
            final String from = a.optString("from", "").trim();
            final String to = a.optString("to", "").trim();
            final String type = a.optString("type", "").trim().toLowerCase(Locale.ROOT);
            if (!from.isEmpty() && !to.isEmpty() && !from.equals(to) && MapBook.isArcType(type)) {
                out.arcs.add(new MapBook.Arc(from, to, type, a.optString("why", "").trim()));
            }
        }
        return out.lanes.isEmpty() ? null : out;
    }

    private static void objects(JSONArray a, List<JSONObject> into) {
        for (int i = 0; a != null && i < a.length(); i++) {
            final JSONObject o = a.optJSONObject(i);
            if (o != null) {
                into.add(o);
            }
        }
    }

    /** The answer's JSON object when it is whole (also in a code fence or with prose around), else null. */
    private static JSONObject wholeObject(String answer) {
        if (answer == null) {
            return null;
        }
        final int open = answer.indexOf('{');
        if (open < 0) {
            return null;
        }
        final int end = Scan.objectEnd(answer, open);
        if (end < 0) {
            return null;
        }
        try {
            return new JSONObject(answer.substring(open, end + 1));
        } catch (JSONException e) {
            return null;
        }
    }

    /** Reading the parts of a JSON answer that was cut off. */
    static final class Scan {
        private Scan() {
        }

        /** Where the value of {@code key} starts in the answer's outermost object, or -1. */
        static int topLevel(String s, String key) {
            if (s == null) {
                return -1;
            }
            final int open = s.indexOf('{');
            if (open < 0) {
                return -1;
            }
            int depth = 0;
            for (int i = open; i < s.length(); i++) {
                final char c = s.charAt(i);
                if (c == '"') {
                    final int close = stringEnd(s, i);
                    if (close < 0) {
                        return -1;
                    }
                    if (depth == 1 && s.substring(i + 1, close).equals(key)) {
                        int k = close + 1;
                        while (k < s.length() && Character.isWhitespace(s.charAt(k))) {
                            k++;
                        }
                        if (k < s.length() && s.charAt(k) == ':') {
                            return k + 1;
                        }
                    }
                    i = close;
                } else if (c == '{' || c == '[') {
                    depth++;
                } else if (c == '}' || c == ']') {
                    depth--;
                }
            }
            return -1;
        }

        /**
         * The whole objects of the array starting at or after {@code from}.
         *
         * @return whether the array closed
         */
        static boolean objects(String s, int from, List<JSONObject> into) {
            if (from < 0) {
                return false;
            }
            final int open = s.indexOf('[', from);
            if (open < 0) {
                return false;
            }
            int i = open + 1;
            while (i < s.length()) {
                final char c = s.charAt(i);
                if (c == ']') {
                    return true;
                }
                if (c != '{') {
                    i++;
                    continue;
                }
                final int end = objectEnd(s, i);
                if (end < 0) {
                    return false;
                }
                try {
                    into.add(new JSONObject(s.substring(i, end + 1)));
                } catch (JSONException e) {
                    // a broken object: the others still count
                }
                i = end + 1;
            }
            return false;
        }

        /** Where the object starting at {@code start} closes, strings skipped; -1 when it does not. */
        static int objectEnd(String s, int start) {
            int depth = 0;
            for (int i = start; i < s.length(); i++) {
                final char c = s.charAt(i);
                if (c == '"') {
                    final int close = stringEnd(s, i);
                    if (close < 0) {
                        return -1;
                    }
                    i = close;
                } else if (c == '{' || c == '[') {
                    depth++;
                } else if (c == '}' || c == ']') {
                    if (--depth == 0) {
                        return c == '}' ? i : -1;
                    }
                }
            }
            return -1;
        }

        private static int stringEnd(String s, int open) {
            for (int i = open + 1; i < s.length(); i++) {
                final char c = s.charAt(i);
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    return i;
                }
            }
            return -1;
        }
    }
}
