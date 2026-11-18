package com.foobnix.claude;

import com.foobnix.ai.AiPrompts;
import com.foobnix.assist.lens.LensProfile;
import com.foobnix.assist.lens.LensPrompts;
import com.foobnix.assist.pictures.PicturePrompts;
import com.foobnix.assist.prequestions.PreQ;
import com.foobnix.assist.skeleton.SkeletonPrompts;
import com.foobnix.assist.updated.UpdatedPrompts;
import com.foobnix.assist.xray.XrayPrompts;
import com.foobnix.assist.zoom.ZoomPrompts;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Guards the answer formats of the features an outside Claude answers ahead: an answer written
 * for one wording of a prompt is read by the app that has it. When a test below fails, a prompt
 * changed: if its reply format changed, raise that feature's {@link ClaudeFeature#format}; then
 * put the new hash here.
 */
public class ClaudeFeatureTest {

    /** The feature's system prompts in every variant the app sends for Claude's answers. */
    static String prompts(ClaudeFeature f) {
        switch (f) {
            case IDEAS:
                return AiPrompts.ideasSystem(false, "") + AiPrompts.ideasSystem(true, "");
            case SUMMARY:
                return AiPrompts.summarySystem("") + AiPrompts.CHAPTER_TASK + AiPrompts.BOOK_TASK;
            case SKELETON:
                return SkeletonPrompts.system("", true) + SkeletonPrompts.system("", false);
            case PRE_QUESTIONS:
                return PreQ.system("");
            case XRAY:
                return XrayPrompts.system("");
            case LENS:
                return LensPrompts.bridgeSystem(LensProfile.fromJson(null), LensProfile.Level.NEW, "", false);
            case ZOOM:
                return ZoomPrompts.system("", false) + ZoomPrompts.system("", true);
            case UPDATED:
                return UpdatedPrompts.system("", Collections.<String>emptyList());
            case PICTURES:
                return PicturePrompts.system("", PicturePrompts.Sent.TEXT);
            default:
                return null;
        }
    }

    @Test
    public void idsAreUniqueAndFound() {
        final Set<String> ids = new HashSet<>();
        for (ClaudeFeature f : ClaudeFeature.values()) {
            assertTrue(ids.add(f.id));
            assertEquals(f, ClaudeFeature.of(f.id));
            assertTrue(f.format >= 1);
            assertNotNull(f.id, prompts(f));
        }
    }

    @Test
    public void promptsAreTheOnesTheFormatsWereSetFor() {
        check(ClaudeFeature.IDEAS, H_IDEAS);
        check(ClaudeFeature.SUMMARY, H_SUMMARY);
        check(ClaudeFeature.SKELETON, H_SKELETON);
        check(ClaudeFeature.PRE_QUESTIONS, H_PRE_QUESTIONS);
        check(ClaudeFeature.XRAY, H_XRAY);
        check(ClaudeFeature.LENS, H_LENS);
        check(ClaudeFeature.ZOOM, H_ZOOM);
        check(ClaudeFeature.UPDATED, H_UPDATED);
        check(ClaudeFeature.PICTURES, H_PICTURES);
    }

    private static void check(ClaudeFeature f, int expected) {
        assertEquals("The prompt of \"" + f.id + "\" changed: raise ClaudeFeature." + f.name()
                + ".format if its reply format changed, then record the new hash", expected, prompts(f).hashCode());
    }

    // format 1 of each (2026-11-18)
    private static final int H_IDEAS = -723410083;
    private static final int H_SUMMARY = 1751646481;
    private static final int H_SKELETON = -1590051609;
    private static final int H_PRE_QUESTIONS = -1826620856;
    private static final int H_XRAY = -859789068;
    private static final int H_LENS = 2086835027;
    private static final int H_ZOOM = 1849803535;
    private static final int H_UPDATED = -1431108810;
    private static final int H_PICTURES = 1781392237;
}
