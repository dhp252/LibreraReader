package com.foobnix.assist.fiction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whether words selected in the book name someone of the stored who's who: by whole words, any
 * case, with or without a possessive ("Aouda's"). The selection may be the name or an alias
 * ("Mr. Fogg"), a part of a name made of whole words ("Fogg" of "Phileas Fogg"), or a few words
 * holding the whole name ("said Passepartout"). When it could be two people, it is nobody: no
 * card is better than the wrong one.
 */
public final class FictionNames {

    /** A longer selection is a passage, not a name. */
    static final int MAX_WORDS = 6;

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:['’][\\p{L}]+)*");

    /** Words that are no name by themselves. */
    private static final Set<String> TITLES = new HashSet<>(Arrays.asList(
            "mr", "mrs", "ms", "miss", "mx", "dr", "sir", "lady", "lord", "madame", "madam", "mme", "monsieur", "mlle",
            "mademoiselle", "herr", "frau", "don", "dona", "señor", "señora", "st", "saint", "captain", "colonel",
            "major", "general", "professor", "father", "mother", "brother", "sister", "uncle", "aunt", "king",
            "queen", "prince", "princess", "count", "countess", "baron", "duke", "duchess",
            "the", "a", "an", "of", "de", "la", "le", "du", "von", "van", "der", "and", "old", "young"));

    private FictionNames() {
    }

    /** A title or article ("Mr.", "Sir", "the"): no part of a person's initials, no name alone. */
    static boolean isTitle(String word) {
        final List<String> w = words(word);
        return w.size() == 1 && TITLES.contains(w.get(0));
    }

    /** The whole words of a text, lower case, a possessive ending taken off. */
    static List<String> words(String text) {
        final List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        final Matcher m = WORD.matcher(text);
        while (m.find()) {
            String w = m.group().replace('’', '\'').toLowerCase(Locale.ROOT);
            if (w.endsWith("'s")) {
                w = w.substring(0, w.length() - 2);
            } else if (w.endsWith("'")) {
                w = w.substring(0, w.length() - 1);
            }
            if (!w.isEmpty()) {
                out.add(w);
            }
        }
        return out;
    }

    /**
     * The person the selected words name, or null.
     *
     * @param people the people the reader may see now (hidden ones left out by the caller)
     */
    public static FictionCast.Person match(List<FictionCast.Person> people, String selection) {
        final List<String> selected = dropArticle(words(selection));
        if (selected.isEmpty() || selected.size() > MAX_WORDS || people == null) {
            return null;
        }
        FictionCast.Person best = null;
        int bestScore = 0;
        boolean tie = false;
        for (FictionCast.Person p : people) {
            int score = score(selected, p.name);
            for (String alias : p.aliases) {
                score = Math.max(score, score(selected, alias));
            }
            if (score == 0) {
                continue;
            }
            if (score > bestScore) {
                best = p;
                bestScore = score;
                tie = false;
            } else if (score == bestScore && best != p) {
                if (best.minor && !p.minor) {
                    // of two people of the same name, the one the story follows
                    best = p;
                    tie = false;
                } else if (best.minor == p.minor) {
                    tie = true;
                }
            }
        }
        return tie ? null : best;
    }

    /**
     * 3: the selection is the name; 2: whole words of the name, each a proper word of it ("Fogg",
     * not the "gentleman" of "the gentleman"); 1: the name, or proper words of it, inside a few
     * more words; 0: no.
     */
    private static int score(List<String> selected, String name) {
        final List<String> n = dropArticle(words(name));
        if (n.isEmpty()) {
            return 0;
        }
        if (selected.equals(n)) {
            return 3;
        }
        final Set<String> proper = proper(name);
        if (selected.size() < n.size() && indexOf(n, selected) >= 0 && proper.containsAll(selected)) {
            return 2;
        }
        if (n.size() < selected.size() && indexOf(selected, n) >= 0 && !onlyTitles(n)) {
            return 1;
        }
        // part of the name inside the selection: "Fogg and Aouda" names Fogg too
        for (int from = 0; from < n.size(); from++) {
            for (int to = n.size(); to > from; to--) {
                final List<String> run = n.subList(from, to);
                if (run.size() < selected.size() && proper.containsAll(run) && indexOf(selected, run) >= 0) {
                    return 1;
                }
            }
        }
        return 0;
    }

    /**
     * The words of a name that are names themselves: written with a capital (or in a script
     * without capitals) and no title.
     */
    private static Set<String> proper(String name) {
        final Set<String> out = new HashSet<>();
        final Matcher m = WORD.matcher(name);
        while (m.find()) {
            final int c = m.group().codePointAt(0);
            if (Character.isLowerCase(c)) {
                continue;
            }
            final List<String> w = words(m.group());
            if (!w.isEmpty() && !TITLES.contains(w.get(0))) {
                out.add(w.get(0));
            }
        }
        return out;
    }

    private static boolean onlyTitles(List<String> words) {
        for (String w : words) {
            if (!TITLES.contains(w)) {
                return false;
            }
        }
        return true;
    }

    private static List<String> dropArticle(List<String> words) {
        if (words.size() > 1 && (words.get(0).equals("the") || words.get(0).equals("a") || words.get(0).equals("an"))) {
            return words.subList(1, words.size());
        }
        return words;
    }

    /** Where {@code part} starts in {@code all} as a run of whole words, or -1. */
    private static int indexOf(List<String> all, List<String> part) {
        for (int i = 0; i + part.size() <= all.size(); i++) {
            if (all.subList(i, i + part.size()).equals(part)) {
                return i;
            }
        }
        return -1;
    }
}
