package com.foobnix.assist.skeleton;

import com.foobnix.ai.BookChapters;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** What "Skeleton first" asks of the model about a book's skim, and reading its answer back. */
public final class SkeletonPrompts {

    private SkeletonPrompts() {
    }

    /**
     * @param answerLanguage the reader's choice; empty means the language of the book
     * @param grouped        the book has parts holding chapters (else a plain list of chapters)
     */
    public static String system(String answerLanguage, boolean grouped) {
        final StringBuilder out = new StringBuilder();
        out.append("Before a reader starts a book, you show them its whole argument, the way a skilled reader ")
                .append("would after skimming it: from the title, the parts, the length of each chapter and the ")
                .append("opening and ending of each chapter. Write in ")
                .append(answerLanguage == null || answerLanguage.trim().isEmpty()
                        ? "the language the book is written in" : answerLanguage.trim())
                .append(".\n")
                .append("- line: the book's thesis, its main claim or story, in one plain sentence of at most 20 words.\n")
                .append("- paragraph: the whole argument in one paragraph of 3 to 5 sentences: the claim, how the book ")
                .append("gets there, and what follows from it.\n");
        if (grouped) {
            out.append("- parts: for each part, line: what the part does in the argument, in at most 15 words; ")
                    .append("optional: true only when the part is background the argument does not need ")
                    .append("(history, cases, digressions).\n")
                    .append("- conclusion: the n of the part where the author draws the conclusion, or -1 when no part does.\n");
        } else {
            out.append("- parts: an empty list (the book has no parts).\n")
                    .append("- conclusion: the n of the chapter where the author draws the conclusion, or -1 when none does.\n");
        }
        out.append("- conclusion_note: one short sentence saying where the conclusion is, e.g. \"Smith saves his ")
                .append("conclusion for Book IV.\"; empty when conclusion is -1.\n")
                .append("- chapters: for each chapter, line: what it is about, in one sentence of at most 15 words");
        if (!grouped) {
            out.append("; optional: true only when it is background the argument does not need");
        }
        out.append(".\n")
                .append("Judge only from what you are given. Side matter (copyright, contents, index, bios, ")
                .append("acknowledgments, bibliography, ads, calls to join or review) is never the conclusion.\n")
                .append("Answer with one JSON object and nothing else: {\"line\": \"...\", \"paragraph\": \"...\", ")
                .append("\"parts\": [{\"n\": <the part's n>, \"line\": \"...\", \"optional\": false}], ")
                .append("\"conclusion\": <n>, \"conclusion_note\": \"...\", ")
                .append("\"chapters\": [{\"n\": <the chapter's n>, \"line\": \"...\"")
                .append(grouped ? "" : ", \"optional\": false")
                .append("}]}, one entry per chapter, in order.");
        return out.toString();
    }

    /**
     * @param parts    the book's parts ({@link SkeletonParts#group})
     * @param chapters every chapter in order, as numbered in {@code pack}
     * @param pack     the skim ({@code SkimPack.build} of {@code chapters})
     */
    public static String user(String bookTitle, List<SkeletonParts.Part> parts, List<BookChapters.Range> chapters,
                              String pack) {
        final StringBuilder out = new StringBuilder();
        if (bookTitle != null && !bookTitle.trim().isEmpty()) {
            out.append("Book: ").append(bookTitle.trim()).append("\n\n");
        }
        if (SkeletonParts.hasGroups(parts)) {
            out.append("The book's parts and the chapters each holds (chapter n as in the skim):\n<parts>\n");
            int n = 0;
            for (int i = 0; i < parts.size(); i++) {
                final SkeletonParts.Part p = parts.get(i);
                final int first = n;
                n += p.chapters.size();
                out.append("<part n=\"").append(i).append("\" title=\"").append(attr(p.range.title))
                        .append("\" chapters=\"").append(first).append(n - 1 > first ? "-" + (n - 1) : "")
                        .append("\"/>\n");
            }
            out.append("</parts>\n\n");
        }
        out.append("The skim of the book, chapter by chapter (words = the chapter's full length):\n\n")
                .append("<book>\n").append(pack).append("</book>");
        return out.toString();
    }

    /** An answer, by the numbers the prompt gave. */
    public static final class Answer {
        public String line = "";
        public String paragraph = "";
        /** A part's n, or a chapter's n for a book without parts; -1 = none. */
        public int conclusion = -1;
        public String conclusionNote = "";
        public final Map<Integer, String> partLines = new LinkedHashMap<>();
        public final Set<Integer> optionalParts = new LinkedHashSet<>();
        public final Map<Integer, String> chapterLines = new LinkedHashMap<>();
        public final Set<Integer> optionalChapters = new LinkedHashSet<>();

        public boolean isEmpty() {
            return line.isEmpty() && paragraph.isEmpty() && chapterLines.isEmpty();
        }
    }

    private static final Pattern LINE = string("line");
    private static final Pattern PARAGRAPH = string("paragraph");
    private static final Pattern NOTE = string("conclusion_note");
    private static final Pattern CONCLUSION = Pattern.compile("\"conclusion\"\\s*:\\s*(-?\\d+)");

    private static Pattern string(String name) {
        return Pattern.compile("\"" + name + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    }

    /**
     * The answer: the whole JSON object, else whatever can be read (a code fence, prose around, an
     * answer cut off at the end).
     */
    public static Answer parse(String answer) {
        final Answer out = new Answer();
        if (answer == null) {
            return out;
        }
        final int open = answer.indexOf('{');
        final int close = answer.lastIndexOf('}');
        if (open >= 0 && close > open) {
            try {
                final JSONObject root = new JSONObject(answer.substring(open, close + 1));
                out.line = root.optString("line", "").trim();
                out.paragraph = root.optString("paragraph", "").trim();
                out.conclusion = root.optInt("conclusion", -1);
                out.conclusionNote = root.optString("conclusion_note", "").trim();
                final JSONArray parts = root.optJSONArray("parts");
                for (int i = 0; parts != null && i < parts.length(); i++) {
                    add(parts.optJSONObject(i), out.partLines, out.optionalParts);
                }
                final JSONArray chapters = root.optJSONArray("chapters");
                for (int i = 0; chapters != null && i < chapters.length(); i++) {
                    add(chapters.optJSONObject(i), out.chapterLines, out.optionalChapters);
                }
                return out;
            } catch (JSONException e) {
                // cut off or broken: read what can be read below
            }
        }
        final int partsAt = answer.indexOf("\"parts\"");
        final int chaptersAt = answer.indexOf("\"chapters\"");
        // the book-level strings come before the lists (or anywhere when there are none)
        final int listsAt = Math.min(partsAt < 0 ? answer.length() : partsAt, chaptersAt < 0 ? answer.length() : chaptersAt);
        final String head = answer.substring(0, listsAt);
        out.line = find(LINE, head);
        out.paragraph = find(PARAGRAPH, head);
        out.conclusionNote = find(NOTE, answer);
        final Matcher c = CONCLUSION.matcher(answer);
        if (c.find()) {
            out.conclusion = Integer.parseInt(c.group(1));
        }
        if (partsAt >= 0) {
            final int end = chaptersAt > partsAt ? chaptersAt : answer.length();
            objects(answer, partsAt, end, out.partLines, out.optionalParts);
        }
        if (chaptersAt >= 0) {
            final int end = partsAt > chaptersAt ? partsAt : answer.length();
            objects(answer, chaptersAt, end, out.chapterLines, out.optionalChapters);
        }
        return out;
    }

    private static void objects(String s, int from, int to, Map<Integer, String> lines, Set<Integer> optional) {
        int i = from;
        while ((i = s.indexOf('{', i)) >= 0 && i < to) {
            final int end = objectEnd(s, i);
            if (end < 0) {
                break;
            }
            try {
                add(new JSONObject(s.substring(i, end + 1)), lines, optional);
                i = end + 1;
            } catch (JSONException e) {
                i++;
            }
        }
    }

    private static void add(JSONObject o, Map<Integer, String> lines, Set<Integer> optional) {
        if (o == null || !o.has("n")) {
            return;
        }
        final int n = o.optInt("n", -1);
        if (n < 0) {
            return;
        }
        final String line = o.optString("line", "").trim();
        if (!line.isEmpty()) {
            lines.put(n, line);
        }
        if (o.optBoolean("optional", false)) {
            optional.add(n);
        }
    }

    private static String find(Pattern p, String s) {
        final Matcher m = p.matcher(s);
        if (!m.find()) {
            return "";
        }
        try {
            return new JSONObject("{\"s\":\"" + m.group(1) + "\"}").getString("s").trim();
        } catch (JSONException e) {
            return m.group(1).trim();
        }
    }

    static int objectEnd(String s, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                if (--depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String attr(String s) {
        return s == null ? "" : s.replace("\"", "'").replaceAll("\\s+", " ").trim();
    }
}
