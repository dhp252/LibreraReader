package com.foobnix.assist.teach;

import com.foobnix.ai.QuoteMatcher;
import com.foobnix.assist.recall.Recall;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Teach-back (canvas "Teach-back", Assist.RECALL), the pure part: a chat in which the reader
 * explains a chapter to a curious beginner. Each turn is one request holding the chapter's key
 * ideas with their passages, the conversation so far and the reader's last answer; the model
 * answers with one follow-up question, or flags a gap with a quote. A gap is shown only when its
 * quote is found in the ideas' passages (the book's own words). Kept in {@code AssistStore} under
 * {@code teach:<chapter key>} is the session's summary, not the chat. No Android here.
 */
public final class Teach {

    public static final String PREFIX = "teach:";
    /** Answers of the reader at most; the last one gets a closing instead of a question. */
    public static final int MAX_TURNS = 8;
    /** Passages of an idea sent with each turn, and their length at most. */
    static final int PASSAGES = 2;
    static final int PASSAGE_CHARS = 400;

    private Teach() {
    }

    public static String key(String chapterKey) {
        return PREFIX + chapterKey;
    }

    /** Something the reader got wrong or could not say, with the book's words that settle it. */
    public static final class Gap {
        /** The idea's place in the session's ideas (0-based), or -1. */
        public int idea = -1;
        /** What the book says, in plain words. */
        public String say = "";
        /** The book's own words. */
        public String quote = "";
        /** The 0-based page of the quote when it was stored (a hint), or -1. */
        public int page = -1;
    }

    /** One line of the chat. */
    public static final class Line {
        public static final int BEGINNER = 0;
        public static final int READER = 1;
        public static final int GAP = 2;

        public final int kind;
        public final String text;
        /** The reader talked instead of typing. */
        public final boolean spoken;
        public final Gap gap;

        Line(int kind, String text, boolean spoken, Gap gap) {
            this.kind = kind;
            this.text = text == null ? "" : text.trim();
            this.spoken = spoken;
            this.gap = gap;
        }
    }

    /** What a turn's request answered, as the model wrote it (the gap not yet checked against the book). */
    public static final class Reply {
        /** The numbers (from 1) of the ideas explained by now. */
        public final List<Integer> explained = new ArrayList<>();
        /** {@link Gap#idea} is the model's number (from 1) here. */
        public Gap gap;
        public String reply = "";
        public boolean done;
    }

    /** A teach-back in progress. */
    public static final class Session {
        public final List<Recall.Idea> ideas;
        public final List<Line> lines = new ArrayList<>();
        public final List<Gap> gaps = new ArrayList<>();
        private final boolean[] explained;
        /** The reader's answers so far. */
        public int answers;
        public boolean finished;

        /** @param opener the beginner's first question */
        public Session(List<Recall.Idea> ideas, String opener) {
            this.ideas = ideas == null ? new ArrayList<Recall.Idea>() : ideas;
            this.explained = new boolean[this.ideas.size()];
            lines.add(new Line(Line.BEGINNER, opener, false, null));
        }

        public void answer(String text, boolean spoken) {
            lines.add(new Line(Line.READER, text, spoken, null));
            answers++;
        }

        /** The answer just given is the last one: the model closes instead of asking on. */
        public boolean isLast() {
            return answers >= MAX_TURNS;
        }

        public boolean isExplained(int index) {
            return index >= 0 && index < explained.length && explained[index];
        }

        public int explainedCount() {
            int n = 0;
            for (boolean b : explained) {
                if (b) {
                    n++;
                }
            }
            return n;
        }

        /**
         * A turn's answer: ideas explained stay explained, a gap whose quote is in the book is
         * shown before the beginner's next line, and the session ends on "done" or the last turn.
         *
         * @return the gap shown, or null
         */
        public Gap apply(Reply r) {
            for (int n : r.explained) {
                if (n >= 1 && n <= explained.length) {
                    explained[n - 1] = true;
                }
            }
            final Gap gap = verify(r.gap, ideas);
            if (gap != null) {
                gaps.add(gap);
                lines.add(new Line(Line.GAP, gap.say, false, gap));
            }
            if (!r.reply.trim().isEmpty()) {
                lines.add(new Line(Line.BEGINNER, r.reply, false, null));
            }
            finished = r.done || isLast();
            return gap;
        }

        /** What is kept of the session: the ideas explained and not, and the gaps; not the chat. */
        public JSONObject summary(String title, long date) throws JSONException {
            final JSONObject o = new JSONObject();
            o.put("title", title == null ? "" : title);
            o.put("date", date);
            o.put("finished", finished);
            o.put("answers", answers);
            o.put("total", ideas.size());
            final JSONArray yes = new JSONArray();
            final JSONArray no = new JSONArray();
            for (int i = 0; i < ideas.size(); i++) {
                (explained[i] ? yes : no).put(ideas.get(i).text);
            }
            o.put("explained", yes);
            o.put("missing", no);
            final JSONArray g = new JSONArray();
            for (Gap gap : gaps) {
                g.put(new JSONObject()
                        .put("idea", gap.idea >= 0 && gap.idea < ideas.size() ? ideas.get(gap.idea).text : "")
                        .put("say", gap.say).put("quote", gap.quote).put("page", gap.page));
            }
            o.put("gaps", g);
            return o;
        }
    }

    // ------------------------------------------------------------------ the request

    /** @param last the reader's answer is the last one: a closing instead of a question */
    public static String system(String answerLanguage, boolean last) {
        final String lang = answerLanguage == null || answerLanguage.trim().isEmpty()
                ? "the language the reader answers in" : answerLanguage.trim();
        return "You play a curious beginner who has not read the book. A reader is explaining a chapter to you "
                + "(teach-back): explaining it shows them what they really understood. You get the chapter's key "
                + "ideas, numbered, each with passages from the book; they are your private notes: the reader does "
                + "not see them, and you never recite them. You also get the conversation so far and the reader's "
                + "latest answer, which may be a rough voice transcript: read through its mistakes.\n"
                + "1. \"explained\": the numbers of the ideas the reader has by now explained correctly in their own "
                + "words, over the whole conversation. A name dropped or a vague gesture does not count.\n"
                + "2. \"gap\": when the latest answer says something the book contradicts, or the reader hedges or "
                + "says they don't know on a point a passage settles: the idea's number, \"quote\": the words of that "
                + "idea's passage that settle it, copied exactly from the passage, character for character (a "
                + "sentence or a clause, at least 6 words), and \"say\": one or two sentences on what the book says, "
                + "in plain words, in " + lang + ". Otherwise \"gap\" is null. Never make up a gap for an answer "
                + "that is right, even a short one, and never quote words that are not in a passage.\n"
                + (last
                ? "3. \"reply\": this was the reader's last answer. Close as the beginner in two or three short "
                + "sentences: thank them, say what you now understand best, and name one thing that sounds worth "
                + "rereading. No question. \"done\" is true.\n"
                : "3. \"reply\": your next line as the beginner: one or two short spoken sentences reacting to what "
                + "they said (after a gap, to what the book says), ending with exactly one follow-up question: "
                + "about an idea not explained yet, a \"why\", a \"but what if\", or an example from their own "
                + "week. No jargon, never answering your own question, never grading the reader. When every idea "
                + "is explained, close instead in two short sentences without a question and set \"done\" to "
                + "true; otherwise \"done\" is false.\n")
                + "Write the reply in " + lang + ". Answer with only JSON, no other text:\n"
                + "{\"explained\": [1, 3], \"gap\": {\"idea\": 4, \"quote\": \"...\", \"say\": \"...\"}, "
                + "\"reply\": \"...\", \"done\": false}";
    }

    /** The turn's user message; the session's last line is the reader's latest answer. */
    public static String user(String bookTitle, String chapterTitle, Session s) {
        final StringBuilder out = new StringBuilder();
        out.append("Book: ").append(Recall.oneLine(bookTitle)).append("\nChapter: ").append(Recall.oneLine(chapterTitle))
                .append("\n\nKey ideas (your private notes):\n");
        for (int i = 0; i < s.ideas.size(); i++) {
            final Recall.Idea idea = s.ideas.get(i);
            out.append(i + 1).append(". ").append(Recall.oneLine(idea.text)).append('\n');
            int sent = 0;
            for (String q : idea.quotes) {
                if (sent >= PASSAGES) {
                    break;
                }
                final String passage = cut(Recall.oneLine(q));
                if (!passage.isEmpty()) {
                    out.append("   passage: ").append(passage).append('\n');
                    sent++;
                }
            }
        }
        final int lastAnswer = lastReaderLine(s);
        out.append("\nConversation so far:\n");
        for (int i = 0; i < s.lines.size(); i++) {
            if (i == lastAnswer) {
                continue;
            }
            final Line l = s.lines.get(i);
            out.append(l.kind == Line.READER ? "Reader: " : l.kind == Line.GAP ? "(Gap shown to the reader: " : "Beginner: ")
                    .append(Recall.oneLine(l.text)).append(l.kind == Line.GAP ? ")" : "").append('\n');
        }
        out.append("\nThe reader's latest answer (answer ").append(s.answers).append(" of at most ").append(MAX_TURNS)
                .append("):\n<answer>\n").append(lastAnswer < 0 ? "" : s.lines.get(lastAnswer).text).append("\n</answer>");
        return out.toString();
    }

    private static int lastReaderLine(Session s) {
        for (int i = s.lines.size() - 1; i >= 0; i--) {
            if (s.lines.get(i).kind == Line.READER) {
                return i;
            }
        }
        return -1;
    }

    /** A passage cut to {@link #PASSAGE_CHARS} at a word's end. */
    static String cut(String passage) {
        if (passage.length() <= PASSAGE_CHARS) {
            return passage;
        }
        final int space = passage.lastIndexOf(' ', PASSAGE_CHARS);
        return passage.substring(0, space > PASSAGE_CHARS / 2 ? space : PASSAGE_CHARS);
    }

    // ------------------------------------------------------------------ reading the answer

    /**
     * A turn's answer; tolerant of fences and text around the JSON, of a missing field, and of a
     * plain sentence instead of JSON (then it is the beginner's line). Null when nothing can be shown.
     */
    public static Reply parse(String answer) {
        final Reply r = new Reply();
        final JSONObject o = Recall.object(answer);
        if (o == null) {
            final String plain = answer == null ? "" : answer.trim();
            if (plain.isEmpty() || plain.length() > 600 || plain.indexOf('{') >= 0 || plain.indexOf('[') >= 0) {
                return null;
            }
            r.reply = plain;
            return r;
        }
        final JSONArray ex = o.optJSONArray("explained");
        if (ex != null) {
            for (int i = 0; i < ex.length(); i++) {
                final Object v = ex.opt(i);
                if (v instanceof Number) {
                    r.explained.add(((Number) v).intValue());
                } else if (v instanceof String && ((String) v).trim().matches("\\d{1,3}")) {
                    r.explained.add(Integer.parseInt(((String) v).trim()));
                }
            }
        }
        final JSONObject g = o.optJSONObject("gap");
        if (g != null) {
            final Gap gap = new Gap();
            gap.idea = g.optInt("idea", g.optInt("id", -1));
            gap.quote = g.optString("quote", "").trim();
            gap.say = g.optString("say", g.optString("text", "")).trim();
            if (!gap.quote.isEmpty() && !gap.say.isEmpty()) {
                r.gap = gap;
            }
        }
        r.reply = o.optString("reply", o.optString("question", "")).trim();
        r.done = o.optBoolean("done");
        return r.reply.isEmpty() && r.gap == null ? null : r;
    }

    /**
     * A gap as it may be shown: its quote found in a passage of the ideas (the named idea first),
     * then in the book's own words with the passage's page. Null when the quote is not in any
     * passage, or too short to be told from chance.
     *
     * @param gap the model's gap, {@link Gap#idea} its number from 1; may be null
     */
    public static Gap verify(Gap gap, List<Recall.Idea> ideas) {
        if (gap == null || ideas == null || QuoteMatcher.key(gap.quote) == null) {
            return null;
        }
        final List<Integer> order = new ArrayList<>();
        if (gap.idea >= 1 && gap.idea <= ideas.size()) {
            order.add(gap.idea - 1);
        }
        for (int i = 0; i < ideas.size(); i++) {
            if (!order.contains(i)) {
                order.add(i);
            }
        }
        for (int i : order) {
            final Recall.Idea idea = ideas.get(i);
            for (int q = 0; q < idea.quotes.size(); q++) {
                final List<String> words = Arrays.asList(idea.quotes.get(q).trim().split("\\s+"));
                final int[] range = QuoteMatcher.locate(new QuoteMatcher.Words(words), gap.quote);
                if (range != null) {
                    final Gap found = new Gap();
                    found.idea = i;
                    found.say = gap.say;
                    found.quote = QuoteMatcher.join(words.subList(range[0], range[1] + 1));
                    found.page = idea.pages.get(q);
                    return found;
                }
            }
        }
        return null;
    }
}
