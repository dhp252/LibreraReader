package com.foobnix.tts;

import android.graphics.RectF;

import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.TxtUtils;
import org.ebookdroid.droids.mupdf.codec.TextWord;

import java.util.ArrayList;
import java.util.List;

public class TTSHighlightHelper {

    private static final String TAG = "TTSHighlightHelper";

    /**
     * Given raw page HTML, current TTS paragraph index, and page TextWords,
     * returns the list of TextWords that belong to the active TTS paragraph.
     */
    public static List<TextWord> getHighlightWordsForParagraph(String pageHTML, int paragraphIndex, TextWord[][] pageTexts) {
        List<TextWord> result = new ArrayList<>();
        if (TxtUtils.isEmpty(pageHTML) || pageTexts == null || pageTexts.length == 0 || paragraphIndex < 0) {
            return result;
        }

        try {
            String processedHTML = TxtUtils.replaceHTMLforTTS(pageHTML);
            String[] parts = processedHTML.split(TxtUtils.TTS_PAUSE);
            if (paragraphIndex >= parts.length) {
                return result;
            }

            String targetPart = parts[paragraphIndex].trim();
            if (TxtUtils.isEmpty(targetPart)) {
                return result;
            }

            List<TextWord> allWords = flattenWords(pageTexts);
            if (allWords.isEmpty()) {
                return result;
            }

            String normalizedTarget = normalizeText(targetPart);
            if (normalizedTarget.isEmpty()) {
                return result;
            }

            String[] targetWords = normalizedTarget.split("\\s+");
            if (targetWords.length == 0) {
                return result;
            }

            // Find starting position of targetWords inside allWords
            int bestMatchStart = -1;
            int maxMatchCount = 0;
            int firstWordLen = Math.min(3, targetWords.length);

            for (int i = 0; i < allWords.size(); i++) {
                int matchCount = 0;
                for (int j = 0; j < targetWords.length && (i + j) < allWords.size(); j++) {
                    String wordOnPage = normalizeText(allWords.get(i + j).getWord());
                    if (!wordOnPage.isEmpty() && (wordOnPage.contains(targetWords[j]) || targetWords[j].contains(wordOnPage))) {
                        matchCount++;
                    } else {
                        break;
                    }
                }
                if (matchCount > maxMatchCount && matchCount >= firstWordLen) {
                    maxMatchCount = matchCount;
                    bestMatchStart = i;
                }
            }

            if (bestMatchStart != -1) {
                int end = Math.min(allWords.size(), bestMatchStart + targetWords.length);
                for (int i = bestMatchStart; i < end; i++) {
                    result.add(allWords.get(i));
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }

        return result;
    }

    /**
     * Given a tapped word and page HTML, returns the TTS paragraph index for that word.
     */
    public static int getParagraphIndexForWord(String pageHTML, TextWord tappedWord) {
        if (TxtUtils.isEmpty(pageHTML) || tappedWord == null) {
            return 0;
        }

        try {
            String processedHTML = TxtUtils.replaceHTMLforTTS(pageHTML);
            String[] parts = processedHTML.split(TxtUtils.TTS_PAUSE);
            String normTapped = normalizeText(tappedWord.getWord());

            if (normTapped.isEmpty()) {
                return 0;
            }

            for (int i = 0; i < parts.length; i++) {
                String normPart = normalizeText(parts[i]);
                if (normPart.contains(normTapped)) {
                    return i;
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }

        return 0;
    }

    private static List<TextWord> flattenWords(TextWord[][] pageTexts) {
        List<TextWord> list = new ArrayList<>();
        if (pageTexts == null) return list;
        for (TextWord[] line : pageTexts) {
            if (line == null) continue;
            for (TextWord word : line) {
                if (word != null && TxtUtils.isNotEmpty(word.getWord())) {
                    list.add(word);
                }
            }
        }
        return list;
    }

    private static String normalizeText(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zA-Z0-9\\s]", "").toLowerCase().trim();
    }
}
