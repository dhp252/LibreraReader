package com.foobnix.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A chapter's ideas turned into marks (design-2026-10-21-layered-marks.md): the ideas of its
 * parts joined, their sentences and paragraphs found in the book's words, the ideas left without
 * a mark picked for a second try, and the chapter as stored.
 */
public final class IdeaMarks {

    private IdeaMarks() {
    }

    /** The ideas of a chapter's parts in reading order, renumbered so that ids stay unique. */
    public static List<AiPrompts.Idea> join(List<List<AiPrompts.Idea>> parts) {
        final List<AiPrompts.Idea> out = new ArrayList<>();
        int offset = 0;
        for (List<AiPrompts.Idea> part : parts) {
            final Set<Integer> ids = new HashSet<>();
            int max = 0;
            for (AiPrompts.Idea idea : part) {
                ids.add(idea.id);
                max = Math.max(max, idea.id);
            }
            for (AiPrompts.Idea idea : part) {
                // a parent only within its own part: the model never saw the other parts
                final int parent = ids.contains(idea.parent) ? idea.parent + offset : -1;
                out.add(new AiPrompts.Idea(idea.id + offset, idea.category, idea.text, parent, idea.sentences, idea.paragraphs));
            }
            offset += max;
        }
        return out;
    }

    /** The marks found for a chapter's ideas, each once. */
    public static final class Placed {
        public final List<AiStore.Quote> sentences = new ArrayList<>();
        public final List<AiStore.Quote> paragraphs = new ArrayList<>();
        /** The first word of each idea's earliest mark, by idea id. */
        final Map<Integer, Integer> firstWord = new HashMap<>();
        private final Set<String> keys = new HashSet<>();

        public boolean isMarked(int idea) {
            return firstWord.containsKey(idea);
        }
    }

    /**
     * Finds the ideas' sentences and paragraphs in the chapter's words.
     *
     * @param wordPage the page of each word
     */
    public static Placed place(List<String> words, List<Integer> wordPage, List<AiPrompts.Idea> ideas) {
        final Placed placed = new Placed();
        final QuoteMatcher.Words all = new QuoteMatcher.Words(words);
        for (AiPrompts.Idea idea : ideas) {
            addSentences(placed, all, words, wordPage, idea.id, idea.category, idea.sentences);
            for (String[] paragraph : idea.paragraphs) {
                add(placed, placed.paragraphs, words, wordPage, idea.id, idea.category,
                        QuoteMatcher.locateSpan(all, paragraph[0], paragraph[1]));
            }
        }
        return placed;
    }

    /** The ideas none of whose marks were found: what a repair request asks for again. */
    public static List<AiPrompts.Idea> unmarked(List<AiPrompts.Idea> ideas, Placed placed) {
        final List<AiPrompts.Idea> out = new ArrayList<>();
        for (AiPrompts.Idea idea : ideas) {
            if (!placed.isMarked(idea.id)) {
                out.add(idea);
            }
        }
        return out;
    }

    /** Adds the sentences a repair answer found, for the ideas it was asked about. */
    public static void repair(Placed placed, List<String> words, List<Integer> wordPage,
                              List<AiPrompts.Idea> asked, Map<Integer, List<String>> found) {
        final QuoteMatcher.Words all = new QuoteMatcher.Words(words);
        for (AiPrompts.Idea idea : asked) {
            final List<String> sentences = found.get(idea.id);
            if (sentences != null) {
                addSentences(placed, all, words, wordPage, idea.id, idea.category, sentences);
            }
        }
    }

    /** The chapter as stored: its ideas with whether each was marked and where, and the marks. */
    public static AiStore.Chapter chapter(String key, String title, String model, long time,
                                          List<AiPrompts.Idea> ideas, Placed placed, List<Integer> wordPage,
                                          boolean incomplete) {
        final List<AiStore.Idea> stored = new ArrayList<>();
        for (AiPrompts.Idea idea : ideas) {
            final Integer first = placed.firstWord.get(idea.id);
            stored.add(new AiStore.Idea(idea.id, idea.text, idea.category, idea.parent,
                    first != null, first != null ? wordPage.get(first) : -1));
        }
        return new AiStore.Chapter(key, title, model, time, placed.sentences, stored, placed.paragraphs, incomplete);
    }

    private static void addSentences(Placed placed, QuoteMatcher.Words all, List<String> words, List<Integer> wordPage,
                                     int idea, AiCategory category, List<String> sentences) {
        for (String sentence : sentences) {
            add(placed, placed.sentences, words, wordPage, idea, category, QuoteMatcher.locate(all, sentence));
        }
    }

    private static void add(Placed placed, List<AiStore.Quote> to, List<String> words, List<Integer> wordPage,
                            int idea, AiCategory category, int[] range) {
        if (range == null) {
            return;
        }
        final String text = QuoteMatcher.join(words.subList(range[0], range[1] + 1));
        final String key = QuoteMatcher.key(text);
        if (key == null) {
            return;
        }
        // the same words for two ideas: drawn once, for the first, but both are marked
        if (placed.keys.add((to == placed.paragraphs ? "p" : "s") + key)) {
            to.add(new AiStore.Quote(text, wordPage.get(range[0]), category, idea));
        }
        final Integer first = placed.firstWord.get(idea);
        if (first == null || range[0] < first) {
            placed.firstWord.put(idea, range[0]);
        }
    }
}
