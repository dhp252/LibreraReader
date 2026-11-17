package com.foobnix.assist.edition;

import com.foobnix.assist.recall.Recall;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * "Tidy my words" (the edition's only AI request, sent on a tap): the reader's own recall of each
 * chapter and the chapter's idea lines go out, no book text; for each chapter a tidied passage
 * comes back that keeps the reader's wording and adds nothing. A passage that brings in words
 * the reader did not use is not kept ({@link #keepsWording}). Stored per chapter as
 * {@code edition:words:<chapter key>} with a mark of the words it was made from, so a new recall
 * of the chapter shows raw again until it is tidied.
 */
public final class EditionPrompts {

    public static final String PREFIX = "edition:words:";
    /** Chapters in one request at most. */
    public static final int MAX_CHAPTERS = 40;
    /** Characters of one chapter's recall sent at most. */
    public static final int MAX_WORDS = 4000;
    /** Share of a tidied passage's words (4 letters and more) that must be the reader's or the ideas'. */
    static final double OWN_SHARE = 0.75;

    private EditionPrompts() {
    }

    public static String key(String chapterKey) {
        return PREFIX + chapterKey;
    }

    /** A mark of the raw words a passage was tidied from. */
    public static String mark(String raw) {
        final String t = raw == null ? "" : raw.trim();
        return t.length() + ":" + Integer.toHexString(t.hashCode());
    }

    /** The item of a tidied passage. */
    public static JSONObject item(String tidied, String raw, String model, long now) throws JSONException {
        return new JSONObject().put("text", tidied).put("from", mark(raw)).put("model", model == null ? "" : model)
                .put("date", now);
    }

    /** The tidied passage of a stored item when it was made from these raw words; else null. */
    public static String tidied(JSONObject item, String raw) {
        if (item == null) {
            return null;
        }
        final String text = item.optString("text", "").trim();
        return text.isEmpty() || !mark(raw).equals(item.optString("from", "")) ? null : text;
    }

    /**
     * The chapters that go into one request: in order, {@link #MAX_CHAPTERS} at most, and only as
     * many as fit {@code maxChars} characters of recall and ideas (a chapter is never cut in half;
     * the first always goes).
     */
    public static List<EditionModel.Raw> fit(List<EditionModel.Raw> raws, int maxChars) {
        final List<EditionModel.Raw> out = new ArrayList<>();
        int used = 0;
        for (EditionModel.Raw r : raws) {
            int size = Math.min(MAX_WORDS, r.words.length()) + r.title.length() + 40;
            for (String idea : r.ideas) {
                size += idea.length() + 4;
            }
            if (out.size() >= MAX_CHAPTERS || (!out.isEmpty() && used + size > maxChars)) {
                break;
            }
            out.add(r);
            used += size;
        }
        return out;
    }

    public static String system(String answerLanguage) {
        final String lang = answerLanguage == null || answerLanguage.trim().isEmpty()
                ? "the language the reader wrote in" : answerLanguage.trim();
        return "A reader finished a book. After each chapter they said or wrote from memory what it said; these "
                + "notes go into a short personal edition of the book. You get, per chapter, the reader's own notes "
                + "(perhaps a rough voice transcript with recognition mistakes) and the chapter's key ideas, which "
                + "are only there so you can read through such mistakes. You do not get the book and must not use "
                + "what you know of it.\n"
                + "Tidy each chapter's notes into one clean passage: fix spelling, recognition mistakes, false "
                + "starts, repetitions and punctuation, and join fragments into sentences. Keep the reader's own "
                + "words, order, examples and numbers. Add nothing: no idea, fact, example or key idea the reader "
                + "did not say, no correction of what they got wrong, no conclusion, no praise. Leave out nothing "
                + "they said. The passage is about as long as the notes, never longer. Write it in " + lang + ", in "
                + "the reader's voice.\n"
                + "Answer with only JSON, no other text:\n"
                + "{\"chapters\": [{\"n\": 1, \"text\": \"...\"}]}\n"
                + "Every chapter given, each once, by its number.";
    }

    /** The user message: the chapters numbered from 1, each with its ideas and the reader's notes. */
    public static String user(String bookTitle, List<EditionModel.Raw> raws) {
        final StringBuilder out = new StringBuilder("Book: ").append(Recall.oneLine(bookTitle)).append('\n');
        for (int i = 0; i < raws.size(); i++) {
            final EditionModel.Raw r = raws.get(i);
            out.append("\nChapter ").append(i + 1).append(": ").append(Recall.oneLine(r.title)).append('\n');
            if (!r.ideas.isEmpty()) {
                out.append("Key ideas (not the reader's words):\n");
                for (String idea : r.ideas) {
                    out.append("- ").append(Recall.oneLine(idea)).append('\n');
                }
            }
            out.append("<notes>\n").append(cap(r.words)).append("\n</notes>\n");
        }
        return out.toString();
    }

    private static String cap(String words) {
        final String t = words == null ? "" : words.trim();
        if (t.length() <= MAX_WORDS) {
            return t;
        }
        final int space = t.lastIndexOf(' ', MAX_WORDS);
        return t.substring(0, space > MAX_WORDS / 2 ? space : MAX_WORDS);
    }

    /**
     * The answer: the passage of each chapter by its place in the request (0-based). Tolerant of
     * fences, text around the JSON, a bare array, and other names for the fields.
     */
    public static Map<Integer, String> parse(String answer, int count) {
        final Map<Integer, String> out = new LinkedHashMap<>();
        JSONArray list = null;
        final JSONObject root = Recall.object(answer);
        if (root != null) {
            for (String name : new String[]{"chapters", "passages", "items"}) {
                if (list == null) {
                    list = root.optJSONArray(name);
                }
            }
        } else if (answer != null) {
            final int open = answer.indexOf('[');
            final int close = answer.lastIndexOf(']');
            if (open >= 0 && close > open) {
                try {
                    list = new JSONArray(answer.substring(open, close + 1));
                } catch (JSONException e) {
                    list = null;
                }
            }
        }
        for (int i = 0; list != null && i < list.length(); i++) {
            final JSONObject o = list.optJSONObject(i);
            if (o == null) {
                continue;
            }
            int n = i + 1;
            for (String name : new String[]{"n", "chapter", "id"}) {
                final Object v = o.opt(name);
                if (v instanceof Number) {
                    n = ((Number) v).intValue();
                    break;
                }
                if (v instanceof String && ((String) v).trim().matches("\\d{1,3}")) {
                    n = Integer.parseInt(((String) v).trim());
                    break;
                }
            }
            final String text = o.optString("text", o.optString("passage", "")).trim();
            if (n >= 1 && n <= count && !text.isEmpty() && !out.containsKey(n - 1)) {
                out.put(n - 1, text);
            }
        }
        return out;
    }

    /**
     * Whether a tidied passage is still the reader's: not much longer than their notes, and its
     * words (of four letters and more) are mostly words of the notes or of the chapter's ideas
     * (which a fixed recognition mistake may come from). Words are compared by their first five
     * letters, so "switching" and "switched" count as one.
     */
    public static boolean keepsWording(String raw, List<String> ideas, String tidied) {
        final String t = tidied == null ? "" : tidied.trim();
        final String r = raw == null ? "" : raw.trim();
        if (t.isEmpty() || t.length() > r.length() * 1.3 + 60) {
            return false;
        }
        final Set<String> own = stems(r);
        if (ideas != null) {
            for (String idea : ideas) {
                own.addAll(stems(idea));
            }
        }
        int total = 0;
        int known = 0;
        for (String word : stemList(t)) {
            total++;
            if (own.contains(word)) {
                known++;
            }
        }
        return total == 0 || known >= OWN_SHARE * total;
    }

    private static Set<String> stems(String text) {
        return new HashSet<>(stemList(text));
    }

    private static List<String> stemList(String text) {
        final List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String w : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (w.length() >= 4) {
                out.add(w.length() > 5 ? w.substring(0, 5) : w);
            }
        }
        return out;
    }
}
