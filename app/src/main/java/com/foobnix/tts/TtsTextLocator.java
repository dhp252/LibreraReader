package com.foobnix.tts;

import com.foobnix.android.utils.TxtUtils;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;

/**
 * Finds read-aloud text on the page, and the place to start reading for a word on the page.
 * <p>
 * The text TTS speaks (page HTML through {@link TxtUtils#replaceHTMLforTTS}) and the words drawn
 * on the page come from different pipelines, so both sides are compared only by their letters and
 * digits in lower case, in any script. Spacing, punctuation, hyphenated line ends ("exam-" + "ple")
 * and the TTS markers then no longer matter.
 */
public final class TtsTextLocator {

    /** Shortest head or tail of the spoken text trusted as an anchor when it is not on the page verbatim. */
    static final int MIN_ANCHOR = 8;
    static final int MAX_ANCHOR = 40;

    private static final String[] MARKERS = {TxtUtils.TTS_PAUSE, TxtUtils.TTS_NEXT, TxtUtils.TTS_SKIP, TxtUtils.TTS_STOP};

    private TtsTextLocator() {
    }

    /**
     * Letters and digits of the text in lower case, without accents: "Đây, là!" -> "đayla".
     * Composed and decomposed accents compare equal, and ligatures ("ﬁ") become their letters.
     */
    public static String normalize(CharSequence text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int cp = Character.codePointAt(text, i);
            i += Character.charCount(cp);
            fold(cp, out);
        }
        return out.toString();
    }

    /**
     * Appends one code point as {@link #normalize} sees it. Works per code point (NFKD of a single
     * code point, then the combining marks dropped), so offsets can be mapped back to the source.
     */
    private static void fold(int cp, StringBuilder out) {
        if (cp < 0x80) {
            if (Character.isLetterOrDigit(cp)) {
                out.append((char) Character.toLowerCase(cp));
            }
            return;
        }
        final String decomposed = Normalizer.normalize(new String(Character.toChars(cp)), Normalizer.Form.NFKD);
        for (int k = 0; k < decomposed.length(); ) {
            final int c = decomposed.codePointAt(k);
            k += Character.charCount(c);
            if (Character.isLetterOrDigit(c)) {
                out.appendCodePoint(Character.toLowerCase(c));
            }
        }
    }

    /**
     * Word range of a spoken utterance among the words of a page.
     * <p>
     * The first utterance of a page can start with the end of the previous page (TTS carries an
     * unfinished sentence over); then only its tail is found and the range starts at the page top.
     *
     * @param pageWords the words of the page in reading order
     * @param spoken    the utterance as passed to the TTS engine
     * @param fromWord  where the previous utterance of this page ended: a match at or after it wins
     *                  over an earlier one, so repeated phrases are found in reading order
     * @return {first, last} inclusive word indexes, or null when the text is not on the page
     */
    public static int[] findWords(List<String> pageWords, String spoken, int fromWord) {
        final String target = normalize(spoken);
        if (target.isEmpty() || pageWords == null || pageWords.isEmpty()) {
            return null;
        }

        final int count = pageWords.size();
        final String[] words = new String[count];
        int total = 0;
        for (int i = 0; i < count; i++) {
            words[i] = normalize(pageWords.get(i));
            total += words[i].length();
        }
        if (total == 0) {
            return null;
        }

        final StringBuilder page = new StringBuilder(total);
        final int[] owner = new int[total];
        final int[] wordStart = new int[count];
        for (int i = 0; i < count; i++) {
            wordStart[i] = page.length();
            for (int k = 0; k < words[i].length(); k++) {
                owner[page.length() + k] = i;
            }
            page.append(words[i]);
        }
        final String text = page.toString();
        final int from = fromWord > 0 && fromWord < count ? wordStart[fromWord] : 0;

        final int exact = indexOf(text, target, from);
        if (exact >= 0) {
            return new int[]{owner[exact], owner[exact + target.length() - 1]};
        }
        if (target.length() <= MIN_ANCHOR) {
            return null;
        }

        int head = -1;
        for (int len = Math.min(MAX_ANCHOR, target.length() - 1); len >= MIN_ANCHOR && head < 0; len--) {
            head = indexOf(text, target.substring(0, len), from);
        }
        int tail = -1;
        for (int len = Math.min(MAX_ANCHOR, target.length() - 1); len >= MIN_ANCHOR && tail < 0; len--) {
            final int at = indexOf(text, target.substring(target.length() - len), head >= 0 ? head : from);
            if (at >= 0) {
                tail = at + len - 1;
            }
        }

        final int last = text.length() - 1;
        if (head >= 0 && tail > head && tail - head < 2 * target.length() + MIN_ANCHOR) {
            return new int[]{owner[head], owner[tail]};
        }
        if (head >= 0) {
            return new int[]{owner[head], owner[Math.min(last, head + target.length() - 1)]};
        }
        if (tail >= 0) {
            return new int[]{owner[Math.max(0, tail - target.length() + 1)], owner[tail]};
        }
        return null;
    }

    /**
     * Where reading aloud has to start so that it begins with the sentence holding the tapped word.
     *
     * @param ttsText  the page text as prepared for TTS, with its pause markers
     * @param words    the tapped word followed by a few of the next words on the page; fewer of
     *                 them are tried when TTS changed one (replacement rules, footnote marks)
     * @param position where the tapped word sits among the words of the page, 0..1; decides
     *                 between repeated phrases
     * @return offset into ttsText where the sentence starts, or -1 when the words are not found
     */
    public static int findReadingStart(String ttsText, List<String> words, float position) {
        if (isEmpty(ttsText) || words == null || words.isEmpty()) {
            return -1;
        }

        final StringBuilder norm = new StringBuilder(ttsText.length());
        int[] source = new int[ttsText.length() + 16];
        for (int i = 0; i < ttsText.length(); ) {
            final String marker = markerAt(ttsText, i);
            if (marker != null) {
                i += marker.length();
                continue;
            }
            final int cp = ttsText.codePointAt(i);
            final int before = norm.length();
            fold(cp, norm);
            if (norm.length() > source.length) {
                // a ligature or compatibility character folds into more than one letter
                source = Arrays.copyOf(source, Math.max(norm.length(), source.length * 2));
            }
            for (int k = before; k < norm.length(); k++) {
                source[k] = i;
            }
            i += Character.charCount(cp);
        }
        final String text = norm.toString();
        if (text.isEmpty()) {
            return -1;
        }

        for (int n = words.size(); n >= 1; n--) {
            final StringBuilder anchor = new StringBuilder();
            for (int i = 0; i < n; i++) {
                anchor.append(normalize(words.get(i)));
            }
            final String target = anchor.toString();
            if (target.isEmpty()) {
                continue;
            }
            final int at = closestOccurrence(text, target, position);
            if (at >= 0) {
                final int offset = source[at];
                final int pause = ttsText.lastIndexOf(TxtUtils.TTS_PAUSE, offset);
                return pause < 0 ? 0 : pause + TxtUtils.TTS_PAUSE.length();
            }
        }
        return -1;
    }

    /**
     * The sentence of {@code text} around {@code offset}, for engines that report the spoken
     * character range of one long utterance (pauses between sentences switched off).
     */
    public static String sentenceAt(String text, int offset) {
        if (isEmpty(text) || offset < 0 || offset >= text.length()) {
            return null;
        }
        int start = offset;
        while (start > 0 && !isSentenceEnd(text.charAt(start - 1))) {
            start--;
        }
        int end = offset;
        while (end < text.length() && !isSentenceEnd(text.charAt(end))) {
            end++;
        }
        if (end < text.length()) {
            end++;
        }
        final String sentence = text.substring(start, end).trim();
        return sentence.isEmpty() ? null : sentence;
    }

    private static boolean isSentenceEnd(char c) {
        return c == '.' || c == '!' || c == '?' || c == '…' || c == '\n' || c == '。';
    }

    private static int closestOccurrence(String text, String target, float position) {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int at = text.indexOf(target); at >= 0; at = text.indexOf(target, at + 1)) {
            if (position < 0) {
                return at;
            }
            final double distance = Math.abs((double) at / text.length() - position);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = at;
            }
        }
        return best;
    }

    private static String markerAt(String text, int i) {
        for (String marker : MARKERS) {
            if (text.startsWith(marker, i)) {
                return marker;
            }
        }
        return null;
    }

    private static boolean isEmpty(String text) {
        return text == null || text.isEmpty();
    }

    private static int indexOf(String text, String target, int from) {
        final int at = text.indexOf(target, from);
        return at >= 0 || from == 0 ? at : text.indexOf(target);
    }
}
