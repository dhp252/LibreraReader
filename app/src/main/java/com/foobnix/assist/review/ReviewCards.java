package com.foobnix.assist.review;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The cards of a review session (canvas "Spaced review"): never flashcards. Each is a scenario
 * question with up to four options, each option an idea of the same book with its chapter: the
 * idea under review and up to three others taken from what is stored, shuffled on the device.
 * The scenario and the two answer texts come from the session's one request
 * ({@link ReviewPrompts}); a card without them asks "Which idea is this?" with the idea's first
 * words hidden. No Android here.
 */
public final class ReviewCards {

    /** Wrong options beside the right one. */
    public static final int OTHERS = 3;

    private ReviewCards() {
    }

    /** An idea that can stand as an option: its text and the chapter it is from. */
    public static final class Candidate {
        public final String text;
        public final String chapterTitle;

        public Candidate(String text, String chapterTitle) {
            this.text = text == null ? "" : text.trim();
            this.chapterTitle = chapterTitle == null ? "" : chapterTitle.trim();
        }
    }

    public static final class Option {
        public final String text;
        /** The chapter it is from (or, for an idea borrowed from another book, that book's title). */
        public final String tag;
        public final boolean correct;

        Option(String text, String tag, boolean correct) {
            this.text = text;
            this.tag = tag;
            this.correct = correct;
        }
    }

    public static final class Card {
        public final ReviewQueue.Entry entry;
        /** In the order shown. */
        public final List<Option> options = new ArrayList<>();
        public String scenario = "";
        public String right = "";
        public String notQuite = "";

        Card(ReviewQueue.Entry entry) {
            this.entry = entry;
        }

        public boolean hasScenario() {
            return !scenario.isEmpty();
        }

        public int correct() {
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).correct) {
                    return i;
                }
            }
            return -1;
        }

        /** The wrong options, in the order shown: what the request is told. */
        public List<String> others() {
            final List<String> out = new ArrayList<>();
            for (Option o : options) {
                if (!o.correct) {
                    out.add(o.text);
                }
            }
            return out;
        }
    }

    /**
     * One card per due idea, in the queue's order.
     *
     * @param pools  each book's stored ideas by book name (the same book's ideas are the wrong
     *               options; when it has too few, ideas of the other books fill in, tagged with
     *               their book)
     * @param titles each book's title by book name, for those borrowed options
     */
    public static List<Card> build(List<ReviewQueue.Entry> due, Map<String, List<Candidate>> pools,
                                   Map<String, String> titles, Random random) {
        final List<Card> cards = new ArrayList<>();
        for (ReviewQueue.Entry e : due) {
            final Card card = new Card(e);
            final Set<String> used = new HashSet<>();
            used.add(ReviewSources.norm(e.item.idea));
            final List<Option> options = new ArrayList<>();
            options.add(new Option(e.item.idea, e.item.chapterTitle, true));
            pick(options, used, pools == null ? null : pools.get(e.bookName), null, random);
            if (options.size() <= OTHERS && pools != null) {
                // a book with too few ideas of its own: the others lend theirs
                final List<String> names = new ArrayList<>(pools.keySet());
                Collections.sort(names);
                for (String name : names) {
                    if (!name.equals(e.bookName) && options.size() <= OTHERS) {
                        final String title = titles == null ? null : titles.get(name);
                        pick(options, used, pools.get(name), title == null ? "" : title, random);
                    }
                }
            }
            Collections.shuffle(options, random);
            card.options.addAll(options);
            cards.add(card);
        }
        return cards;
    }

    /** Adds ideas of {@code pool} not used yet, in a random order, until there are {@link #OTHERS} wrong ones. */
    private static void pick(List<Option> options, Set<String> used, List<Candidate> pool, String tag, Random random) {
        if (pool == null) {
            return;
        }
        final List<Candidate> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, random);
        for (Candidate c : shuffled) {
            if (options.size() > OTHERS) {
                return;
            }
            final String norm = ReviewSources.norm(c.text);
            if (!norm.isEmpty() && used.add(norm)) {
                options.add(new Option(c.text, tag == null ? c.chapterTitle : tag, false));
            }
        }
    }

    /** Puts the request's texts on the cards, by card number (1-based); a card without a scenario keeps none. */
    public static void apply(List<Card> cards, Map<Integer, ReviewPrompts.Texts> texts) {
        if (texts == null) {
            return;
        }
        for (int i = 0; i < cards.size(); i++) {
            final ReviewPrompts.Texts t = texts.get(i + 1);
            if (t != null && !t.scenario.isEmpty()) {
                final Card c = cards.get(i);
                c.scenario = t.scenario;
                c.right = t.right;
                c.notQuite = t.notQuite;
            }
        }
    }

    /**
     * The idea with its first words hidden, for the card without a scenario ("Which idea is
     * this?"): the first half of its words, at least one and at most four, become blanks.
     */
    public static String hidden(String idea) {
        final String[] words = idea == null ? new String[0] : idea.trim().split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) {
            return "";
        }
        final int hide = Math.max(1, Math.min(4, words.length / 2));
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(i < hide ? "___" : words[i]);
        }
        return out.toString();
    }
}
