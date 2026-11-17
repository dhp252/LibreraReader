package com.foobnix.assist.path;

import com.foobnix.assist.skeleton.SkeletonData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What "One topic, several books" asks of the model, and reading its answer back. Only each
 * book's chapter list (titles and one line each) is sent, never a book's text.
 */
public final class TopicPrompts {

    private TopicPrompts() {
    }

    /** The room the prompt and the answer need around the chapter lists. */
    private static final int ROOM = 4000;
    private static final int MIN_LINE = 30;
    /** A book's list is cut after this many chapters. */
    public static final int MAX_CHAPTERS = 200;

    public static String system(String answerLanguage) {
        return "A reader wants to study one topic across several books. You get the topic and, for each book, its "
                + "chapters with one line each. Make one merged reading path: only the chapters that are about the "
                + "topic, at most " + TopicData.MAX_STEPS + " in all, in the order to read them across the books "
                + "(what the others build on first). What the books repeat is said once. Write in "
                + PathPrompts.language(answerLanguage) + ".\n"
                + "- path: for each chapter, book: the book's n, chapter: the chapter's n in that book, why: at most "
                + "10 words on what it adds to the topic.\n"
                + "- agree: up to 3 points the books agree on: point: one short sentence; books: the n of every "
                + "book that makes it (two or more); note: at most 15 words, such as each book's own example, or "
                + "empty.\n"
                + "- disagree: up to 3 questions the books answer differently: question: a short question; "
                + "positions: for each book that answers it, book: its n, and says: that author's position in at "
                + "most 25 words.\n"
                + "- only: up to 4 points made mostly in one book: book: its n, point: at most 5 words.\n"
                + "Judge only from the lists; use only the n they give; a book that says nothing on the topic gets "
                + "no chapter. When no chapter is about the topic, answer with an empty path.\n"
                + "Answer with one JSON object and nothing else: {\"path\": [{\"book\": <n>, \"chapter\": <n>, "
                + "\"why\": \"...\"}], \"agree\": [{\"point\": \"...\", \"books\": [<n>, <n>], \"note\": \"...\"}], "
                + "\"disagree\": [{\"question\": \"...\", \"positions\": [{\"book\": <n>, \"says\": \"...\"}]}], "
                + "\"only\": [{\"book\": <n>, \"point\": \"...\"}]}";
    }

    /**
     * @param lists    the chapter list of each book, in the order of {@code books}
     * @param maxChars the most characters a request may hold; lines are shortened to fit
     */
    public static String user(String topic, List<TopicData.Book> books, List<List<PathChapters.Entry>> lists,
                              int maxChars) {
        final String head = "Topic: " + PathText.clip(topic, 300) + "\n\n";
        String body = books(books, lists, Integer.MAX_VALUE);
        final int room = Math.max(ROOM, maxChars - ROOM) - head.length();
        if (body.length() > room) {
            int chapters = 0;
            for (List<PathChapters.Entry> list : lists) {
                chapters += list.size();
            }
            body = books(books, lists, Math.max(MIN_LINE, (room - books(books, lists, 0).length()) / Math.max(1, chapters)));
        }
        return head + body;
    }

    private static String books(List<TopicData.Book> books, List<List<PathChapters.Entry>> lists, int lineLimit) {
        final StringBuilder out = new StringBuilder();
        for (int b = 0; b < books.size() && b < lists.size(); b++) {
            final TopicData.Book book = books.get(b);
            out.append("<book n=\"").append(b).append("\" title=\"").append(PathPrompts.attr(book.title)).append('"');
            if (!book.author.isEmpty()) {
                out.append(" author=\"").append(PathPrompts.attr(book.author)).append('"');
            }
            if (book.year > 0) {
                out.append(" year=\"").append(book.year).append('"');
            }
            out.append(">\n");
            final List<PathChapters.Entry> list = lists.get(b);
            for (int i = 0; i < list.size(); i++) {
                final PathChapters.Entry e = list.get(i);
                out.append("<chapter n=\"").append(i).append('"');
                if (!e.partTitle.isEmpty()) {
                    out.append(" part=\"").append(PathPrompts.attr(e.partTitle)).append('"');
                }
                if (!e.title.isEmpty()) {
                    out.append(" title=\"").append(PathPrompts.attr(e.title)).append('"');
                }
                out.append('>').append(lineLimit <= 0 ? "" : PathText.clip(e.line, lineLimit)).append("</chapter>\n");
            }
            out.append("</book>\n");
        }
        return out.toString();
    }

    /**
     * A book's chapters from its stored lines alone, when its file cannot be opened for its table
     * of contents: no titles (the screen shows the lines), in the order the lines are stored.
     */
    public static List<PathChapters.Entry> fromLines(SkeletonData data) {
        final List<PathChapters.Entry> out = new ArrayList<>();
        for (Map.Entry<String, String> e : data.chapterLines.entrySet()) {
            final String line = e.getValue() == null ? "" : e.getValue().trim();
            if (!line.isEmpty() && out.size() < MAX_CHAPTERS) {
                final Long chars = data.chars.get(e.getKey());
                out.add(new PathChapters.Entry(e.getKey(), "", "", line, chars == null ? 0 : chars, 0, 0));
            }
        }
        return out;
    }

    /** One chapter of the merged path as the model named it. */
    public static final class Step {
        public final int book;
        public final int chapter;
        public final String why;

        public Step(int book, int chapter, String why) {
            this.book = book;
            this.chapter = chapter;
            this.why = why;
        }
    }

    /** The model's answer, by the numbers the prompt gave. */
    public static final class Answer {
        public final List<Step> steps = new ArrayList<>();
        public final List<TopicData.Agree> agree = new ArrayList<>();
        public final List<TopicData.Disagree> disagree = new ArrayList<>();
        public final List<TopicData.Only> only = new ArrayList<>();
        /** The answer was read as the JSON asked for (an empty path then means: no chapter is about the topic). */
        public boolean read;
    }

    /** The whole JSON object; of an answer cut off, the chapters of the path that are whole. */
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
                final JSONArray path = root.optJSONArray("path");
                for (int i = 0; path != null && i < path.length(); i++) {
                    step(path.optJSONObject(i), out.steps);
                }
                final JSONArray agree = root.optJSONArray("agree");
                for (int i = 0; agree != null && i < agree.length(); i++) {
                    final JSONObject g = agree.optJSONObject(i);
                    if (g == null) {
                        continue;
                    }
                    final TopicData.Agree a = new TopicData.Agree();
                    a.point = g.optString("point", "").trim();
                    a.note = g.optString("note", "").trim();
                    final JSONArray in = g.optJSONArray("books");
                    for (int j = 0; in != null && j < in.length(); j++) {
                        a.books.add(in.optInt(j, -1));
                    }
                    out.agree.add(a);
                }
                final JSONArray disagree = root.optJSONArray("disagree");
                for (int i = 0; disagree != null && i < disagree.length(); i++) {
                    final JSONObject g = disagree.optJSONObject(i);
                    if (g == null) {
                        continue;
                    }
                    final TopicData.Disagree d = new TopicData.Disagree();
                    d.question = g.optString("question", "").trim();
                    final JSONArray positions = g.optJSONArray("positions");
                    for (int j = 0; positions != null && j < positions.length(); j++) {
                        final JSONObject p = positions.optJSONObject(j);
                        if (p != null) {
                            d.positions.add(new TopicData.Position(p.optInt("book", -1), p.optString("says", "").trim()));
                        }
                    }
                    out.disagree.add(d);
                }
                final JSONArray only = root.optJSONArray("only");
                for (int i = 0; only != null && i < only.length(); i++) {
                    final JSONObject o = only.optJSONObject(i);
                    if (o != null) {
                        out.only.add(new TopicData.Only(o.optInt("book", -1), o.optString("point", "").trim()));
                    }
                }
                out.read = path != null;
                return out;
            } catch (JSONException e) {
                // cut off or broken: the whole chapters of the path below
            }
        }
        int i = answer.indexOf("\"path\"");
        if (i < 0) {
            return out;
        }
        final int end = answer.indexOf("\"agree\"", i);
        while ((i = answer.indexOf('{', i)) >= 0 && (end < 0 || i < end)) {
            final int objectEnd = PathPrompts.objectEnd(answer, i);
            if (objectEnd < 0) {
                break;
            }
            try {
                step(new JSONObject(answer.substring(i, objectEnd + 1)), out.steps);
                i = objectEnd + 1;
            } catch (JSONException e) {
                i++;
            }
        }
        return out;
    }

    private static void step(JSONObject o, List<Step> into) {
        if (o == null || !(o.opt("book") instanceof Number) || !(o.opt("chapter") instanceof Number)) {
            return;
        }
        into.add(new Step(o.optInt("book", -1), o.optInt("chapter", -1), o.optString("why", "").trim()));
    }
}
