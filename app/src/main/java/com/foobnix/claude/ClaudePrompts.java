package com.foobnix.claude;

import com.foobnix.ai.AiPrompts;
import com.foobnix.ai.AiSettings;
import com.foobnix.ai.AiStore;
import com.foobnix.ai.BookChapters;
import com.foobnix.assist.Assist;
import com.foobnix.assist.AssistStore;
import com.foobnix.assist.lens.LensProfile;
import com.foobnix.assist.lens.LensPrompts;
import com.foobnix.assist.pictures.PicturePrompts;
import com.foobnix.assist.prequestions.PreQ;
import com.foobnix.assist.skeleton.SkeletonParts;
import com.foobnix.assist.skeleton.SkeletonPrompts;
import com.foobnix.assist.updated.UpdatedLens;
import com.foobnix.assist.updated.UpdatedPrompts;
import com.foobnix.assist.xray.XrayPrompts;
import com.foobnix.assist.zoom.ZoomGoal;
import com.foobnix.assist.zoom.ZoomPrompts;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The system prompts the API would get for a book, by feature: what goes in the desk's
 * {@code prompts/} folder. Built by the features' own prompt code with the reader's settings (answer
 * language, master prompt, goals, Lens), in the variant an answer of Claude's is read with: from
 * the chapter's text, never from stored ideas.
 */
final class ClaudePrompts {

    private ClaudePrompts() {
    }

    /** Feature id → system prompt, for the features that are on. */
    static Map<String, String> forBook(AiSettings s, String bookName, List<BookChapters.Entry> toc, int pages) {
        final Map<String, String> out = new LinkedHashMap<>();
        final String lang = s.answerLanguage;
        final String goal = AiStore.get().load(bookName).goal;
        final boolean hasGoal = goal != null && !goal.trim().isEmpty();
        out.put(ClaudeFeature.IDEAS.id, AiPrompts.ideasSystem(hasGoal, lang));
        out.put(ClaudeFeature.SUMMARY.id, AiPrompts.summarySystem(lang));
        if (Assist.SKELETON.isOn()) {
            out.put(ClaudeFeature.SKELETON.id, SkeletonPrompts.system(lang,
                    SkeletonParts.hasGroups(SkeletonParts.group(toc, pages))));
        }
        if (Assist.PRE_QUESTIONS.isOn()) {
            out.put(ClaudeFeature.PRE_QUESTIONS.id, PreQ.system(lang));
        }
        if (Assist.XRAY.isOn()) {
            out.put(ClaudeFeature.XRAY.id, XrayPrompts.system(lang));
        }
        if (Assist.LENS.isOn()) {
            final LensProfile profile = LensProfile.fromJson(AssistStore.get().item(AssistStore.LIBRARY, LensProfile.KEY));
            if (profile.isSet()) {
                final LensProfile.Level level = LensProfile.Level.fromJson(AssistStore.get().item(bookName, LensProfile.LEVEL_KEY));
                out.put(ClaudeFeature.LENS.id, LensPrompts.bridgeSystem(profile, level, lang, false));
            }
        }
        if (Assist.ZOOM.isOn()) {
            out.put(ClaudeFeature.ZOOM.id, ZoomPrompts.system(lang, zoomGoal(bookName) != null));
        }
        if (Assist.UPDATED.isOn()) {
            out.put(ClaudeFeature.UPDATED.id, UpdatedPrompts.system(lang, lensFields()));
        }
        if (Assist.PICTURES.isOn()) {
            out.put(ClaudeFeature.PICTURES.id, PicturePrompts.system(lang, PicturePrompts.Sent.TEXT));
        }
        for (Map.Entry<String, String> e : out.entrySet()) {
            e.setValue(AiPrompts.withMaster(s.masterPrompt, e.getValue()));
        }
        return out;
    }

    /** What the zoom goal adds to its request, or null when the book has none. */
    static String zoomGoal(String bookName) {
        try {
            final ZoomGoal goal = ZoomGoal.fromJson(AssistStore.get().item(bookName, ZoomGoal.KEY));
            if (goal == null) {
                return null;
            }
            final String bookGoal = AiStore.get().load(bookName).goal;
            return goal.prompt(bookGoal == null ? "" : bookGoal.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** The reader's Lens fields, sent with "The book, updated" while Lens is on. */
    static List<String> lensFields() {
        try {
            return Assist.LENS.isOn() ? UpdatedLens.fields(AssistStore.get().item(AssistStore.LIBRARY, UpdatedLens.KEY))
                    : Collections.<String>emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
