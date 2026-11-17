package com.foobnix.assist.fiction;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * What the who's who asks of the model, free of Android. The model gets the book's text only up
 * to the reader's page ({@link FictionBlocks}), chapter by chapter under numbered headers, and
 * answers with one JSON object ({@link FictionCast#parse}). A long text goes in parts: each later
 * request carries the who's who so far and the next part, and answers only what that part
 * changes ({@link FictionCast#merge}).
 */
public final class FictionPrompts {

    /** Characters a request needs around the book text: the instructions and the cast so far. */
    public static final int ROOM = 6000;
    /** Book text one request holds at most, whatever the AI settings allow. */
    public static final int MAX_BLOCK = 200_000;
    static final int MIN_BLOCK = 8000;

    private FictionPrompts() {
    }

    /** Characters of book text one request may hold, from the AI settings' limit. */
    public static int cap(int maxChars) {
        return Math.max(MIN_BLOCK, Math.min(MAX_BLOCK, maxChars - ROOM));
    }

    /** The rule every request of the feature starts with. */
    static String shield(String answerLanguage) {
        return "Write in " + language(answerLanguage) + ". "
                + "The text you get is the book only up to the page the reader is on; it stops there. Use only that "
                + "text. Never use what you know of this book from anywhere else, even if you know it well, and never "
                + "reveal, guess or hint at anything that comes later: not who someone turns out to be, not what "
                + "happens next, not how anything ends.\n"
                + "The text comes chapter by chapter, each under <chapter n=\"…\">, where n counts the chapters. "
                + "Where a chapter is asked for, give that number. Do not write chapter numbers inside your sentences. "
                + "Spell every name exactly as the book spells it.\n";
    }

    /**
     * @param answerLanguage the reader's choice (AI settings); empty: the language of the book
     * @param update         the request carries a who's who made from the earlier text
     */
    public static String castSystem(String answerLanguage, boolean update) {
        final StringBuilder s = new StringBuilder();
        s.append("You keep a spoiler-free who's who for someone who is in the middle of reading a story. ");
        s.append(shield(answerLanguage));
        if (update) {
            s.append("You also get the who's who so far, made from the earlier text. The text given now is the part "
                    + "of the book right after it. Answer only what this part changes: every new person with all "
                    + "fields; and for a person already listed whose situation changed, the same \"id\" with "
                    + "\"as_of\" and \"now\" written again whole (keeping what is still true), \"role\" or "
                    + "\"group\" if it changed, \"minor\": false if they became important, and only their new "
                    + "moments. Leave out the people this part does not touch. Give only new or changed ties.\n");
        }
        s.append("Answer with one JSON object only, no Markdown around it, with these fields:\n"
                + "- \"story\": true; or false when the text is not a story with people in it (a textbook, an "
                + "essay), and then nothing else.\n"
                + "- \"people\": everyone met in the text, the people the story follows first. People of small "
                + "parts count too, and so do animals and things with a name and a part in the story. Each:\n"
                + "  {\"id\": a short lowercase id without spaces, the same every time this person is meant,\n"
                + "   \"name\": the name the book mostly uses for them,\n"
                + "   \"aliases\": the other names, titles and nicknames the book uses for them (no pronouns),\n"
                + "   \"gender\": \"f\", \"m\", or \"\" when the text does not say,\n"
                + "   \"role\": who they are, in at most 8 words,\n"
                + "   \"first\": the number of the chapter they first appear in,\n"
                + "   \"as_of\": two or three sentences on who they are and what has happened to them, as of the "
                + "end of the text,\n"
                + "   \"now\": one sentence on where they are and what they are doing at the end of the text,\n"
                + "   \"group\": the family, household, crew or club they belong to, as the book names it, or \"\",\n"
                + "   \"minor\": true for someone met in passing, false for the people the story follows,\n"
                + "   \"moments\": their story so far in order, at most 8, each {\"ch\": chapter number, \"line\": "
                + "one short sentence}}\n"
                + "- \"relations\": how the people the story follows are tied, at most 12, each {\"a\": a person's "
                + "id, \"b\": another person's id or the name of a group, \"label\": a few words such as \"his "
                + "servant\" or \"the wager\", \"ch\": the number of the chapter the tie begins in}.\n"
                + "Plain text in every string.");
        return s.toString();
    }

    /**
     * @param soFar the stored cast the request builds on, or null for the first request
     * @param part  which part of the text this is, from 1
     * @param parts how many parts the text is sent in
     * @param text  the block ({@link FictionBlocks#text})
     */
    public static String castUser(String bookTitle, FictionCast soFar, int part, int parts, String text) {
        final StringBuilder out = new StringBuilder();
        head(out, bookTitle, part, parts);
        if (soFar != null && !soFar.isEmpty()) {
            out.append("The who's who so far (up to the end of chapter ").append(soFar.mark.ch).append("):\n")
                    .append(brief(soFar)).append("\n\n");
            out.append("The next part of the text:\n\n");
        } else {
            out.append("The text, from the start of the book:\n\n");
        }
        out.append(text);
        return out.toString();
    }

    static void head(StringBuilder out, String bookTitle, int part, int parts) {
        if (bookTitle != null && !bookTitle.trim().isEmpty()) {
            out.append("Book: ").append(bookTitle.trim()).append('\n');
        }
        if (parts > 1) {
            out.append("The text up to the reader's page is long and comes in ").append(parts)
                    .append(" parts; this is part ").append(part).append(". Describe everything as of the end of "
                            + "this part.\n");
        } else {
            out.append("The text below ends at the reader's page.\n");
        }
    }

    /** The cast so far as a request carries it: who is who and where they stand, without their story lines. */
    static String brief(FictionCast cast) {
        final JSONArray people = new JSONArray();
        try {
            for (FictionCast.Person p : cast.people) {
                final JSONObject j = new JSONObject();
                j.put("id", p.id).put("name", p.name);
                if (!p.aliases.isEmpty()) {
                    j.put("aliases", new JSONArray(p.aliases));
                }
                j.put("role", p.role).put("as_of", p.asOf).put("now", p.now);
                if (!p.group.isEmpty()) {
                    j.put("group", p.group);
                }
                j.put("minor", p.minor);
                people.put(j);
            }
            final JSONArray ties = new JSONArray();
            for (FictionCast.Relation r : cast.relations) {
                ties.put(new JSONObject().put("a", r.a).put("b", r.b).put("label", r.label));
            }
            return new JSONObject().put("people", people).put("relations", ties).toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    static String language(String answerLanguage) {
        return answerLanguage == null || answerLanguage.trim().isEmpty()
                ? "the language the text is written in"
                : answerLanguage.trim();
    }
}
