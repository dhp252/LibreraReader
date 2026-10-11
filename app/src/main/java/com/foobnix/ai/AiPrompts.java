package com.foobnix.ai;

import java.util.ArrayList;
import java.util.List;

/** What is asked of the model, and reading its answers back. */
public final class AiPrompts {

    private AiPrompts() {
    }

    /** @param answerLanguage the reader's choice; empty means the language of the book */
    public static String summarySystem(String answerLanguage) {
        return "You summarize parts of a book for someone who is reading it. "
                + "Write in " + language(answerLanguage) + ". "
                + "Stay faithful to the text you are given: no outside knowledge about the book, "
                + "and nothing from parts of it you were not given. "
                + "Plain prose in short paragraphs; no headings, tables or Markdown.";
    }

    /** What a chapter summary is, also when the summaries of its parts are joined. */
    public static final String CHAPTER_TASK = "Summarize this chapter in 6 to 10 sentences: what happens "
            + "or what is argued, the people or ideas that matter, and why it matters for the rest of "
            + "the book. Skip filler such as \"This chapter describes\".";
    /** What a book summary is, also when the summaries of its parts are joined. */
    public static final String BOOK_TASK = "Summarize the whole book: its central conflict or thesis, "
            + "the main characters or ideas and what drives them, how it develops and ends, and 3 to 5 themes.";

    /** Summaries of parts too many for one request: a few at a time into one, a step towards the whole. */
    public static final String PARTS_TASK = "Summarize them together as one part in 5 to 8 sentences, keeping "
            + "names, events and claims a later summary of the whole would need.";

    public static String chapterSummary(String bookTitle, String chapterTitle, String text) {
        return header(bookTitle, chapterTitle) + CHAPTER_TASK + "\n\n<chapter>\n" + text + "\n</chapter>";
    }

    public static String bookSummary(String bookTitle, String text) {
        return header(bookTitle, null) + BOOK_TASK + "\n\n<book>\n" + text + "\n</book>";
    }

    /** First step of a long text: one part, summarized so that the parts can be joined. */
    public static String partSummary(String bookTitle, String partTitle, String text) {
        return header(bookTitle, partTitle)
                + "This is one part of a longer text. Summarize it in 5 to 8 sentences, keeping names, "
                + "events and claims a later summary of the whole would need.\n\n"
                + "<part>\n" + text + "\n</part>";
    }

    /** Second step of a long text: the part summaries, in order, into one summary. */
    public static String joinSummaries(String bookTitle, String task, List<String> parts) {
        final StringBuilder out = new StringBuilder(header(bookTitle, null));
        out.append("These are summaries of consecutive parts of the text, in order. ").append(task).append("\n\n");
        for (int i = 0; i < parts.size(); i++) {
            out.append("<part n=\"").append(i + 1).append("\">\n").append(parts.get(i)).append("\n</part>\n");
        }
        return out.toString();
    }

    public static String keyPassagesSystem() {
        return "You pick the passages of a text that matter most for understanding it. "
                + "Answer with a JSON array of strings and nothing else. Each string is one sentence "
                + "copied exactly from the text, character for character: no changes, no ellipses, "
                + "no quotation marks added. Never pick headings, captions, page numbers or lines "
                + "of a table of contents.";
    }

    public static String keyPassages(String bookTitle, String chapterTitle, String text, int count) {
        return header(bookTitle, chapterTitle)
                + "Pick the " + count + " most important sentences of this text: key claims, "
                + "turning points, definitions, conclusions. Copy each one exactly.\n\n"
                + "<text>\n" + text + "\n</text>";
    }

    /** How many key passages to ask for: about one per 3000 characters, 3 to 12. */
    public static int passageCount(int textLength) {
        return Math.max(3, Math.min(12, textLength / 3000));
    }

    private static String header(String bookTitle, String partTitle) {
        final StringBuilder out = new StringBuilder();
        if (bookTitle != null && !bookTitle.trim().isEmpty()) {
            out.append("Book: ").append(bookTitle.trim()).append('\n');
        }
        if (partTitle != null && !partTitle.trim().isEmpty()) {
            out.append("Part: ").append(partTitle.trim()).append('\n');
        }
        return out.length() > 0 ? out.append('\n').toString() : "";
    }

    private static String language(String answerLanguage) {
        return answerLanguage == null || answerLanguage.trim().isEmpty()
                ? "the language the text is written in"
                : answerLanguage.trim();
    }

    /**
     * The sentences of a key-passages answer: a JSON array of strings, also when the model put it
     * in a code fence or wrote something around it. Empty when there is no such array.
     */
    public static List<String> parseQuotes(String answer) {
        final List<String> quotes = new ArrayList<>();
        if (answer == null) {
            return quotes;
        }
        final int open = answer.indexOf('[');
        if (open < 0) {
            return quotes;
        }
        int i = open + 1;
        final int n = answer.length();
        while (i < n) {
            final char c = answer.charAt(i);
            if (c == ']') {
                break;
            }
            if (c != '"') {
                i++;
                continue;
            }
            final StringBuilder s = new StringBuilder();
            i++;
            boolean closed = false;
            while (i < n) {
                final char d = answer.charAt(i++);
                if (d == '"') {
                    closed = true;
                    break;
                }
                if (d != '\\' || i >= n) {
                    s.append(d);
                    continue;
                }
                final char e = answer.charAt(i++);
                switch (e) {
                    case 'n':
                        s.append('\n');
                        break;
                    case 't':
                        s.append('\t');
                        break;
                    case 'r':
                        s.append('\r');
                        break;
                    case 'b':
                        s.append('\b');
                        break;
                    case 'f':
                        s.append('\f');
                        break;
                    case 'u':
                        if (i + 4 <= n) {
                            try {
                                s.append((char) Integer.parseInt(answer.substring(i, i + 4), 16));
                            } catch (NumberFormatException ignored) {
                                // a broken escape: keep the rest of the sentence
                            }
                            i += 4;
                        }
                        break;
                    default:
                        s.append(e);
                }
            }
            if (!closed) {
                break;
            }
            final String quote = s.toString().trim();
            if (!quote.isEmpty()) {
                quotes.add(quote);
            }
        }
        return quotes;
    }
}
