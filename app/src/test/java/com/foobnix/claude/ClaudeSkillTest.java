package com.foobnix.claude;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClaudeSkillTest {

    @Test
    public void filesHoldTheSkillFormatsAndEveryFeature() throws Exception {
        final Map<String, String> files = ClaudeSkill.files("---\nname: x\n---\nwritten by {{APP}}", "9.9");
        assertEquals("---\nname: x\n---\nwritten by app version 9.9", files.get("SKILL.md"));
        final JSONObject formats = new JSONObject(files.get(ClaudeSkill.FORMATS));
        assertEquals(ClaudeSkill.VERSION, formats.getInt("skill"));
        for (ClaudeFeature f : ClaudeFeature.values()) {
            assertEquals(f.format, formats.getJSONObject("features").getInt(f.id));
            final String md = files.get("features/" + f.id + ".md");
            assertTrue(f.id, md.startsWith("# "));
            assertTrue(f.id, md.contains("answers/" + f.id + "~<n>.json"));
            assertTrue(f.id, md.contains("prompts/" + f.id + ".txt"));
        }
    }

    @Test
    public void userTemplatesComeFromTheAppsOwnPrompts() {
        assertTrue(ClaudeSkill.feature(ClaudeFeature.PRE_QUESTIONS).contains(ClaudeSkill.TEXT));
        assertTrue(ClaudeSkill.feature(ClaudeFeature.IDEAS).contains("Map every idea of this text."));
        assertTrue(ClaudeSkill.feature(ClaudeFeature.UPDATED).contains("{{book.year}}"));
    }

    @Test
    public void aNewerAppsSkillIsLeftAlone() throws Exception {
        assertFalse(ClaudeSkill.newerThanMine(null));
        assertFalse(ClaudeSkill.newerThanMine("not json"));
        assertFalse(ClaudeSkill.newerThanMine(ClaudeSkill.formats("1").toString()));
        assertTrue(ClaudeSkill.newerThanMine(new JSONObject().put("skill", ClaudeSkill.VERSION + 1).toString()));
        final JSONObject newer = ClaudeSkill.formats("2");
        newer.getJSONObject("features").put(ClaudeFeature.ZOOM.id, ClaudeFeature.ZOOM.format + 1);
        assertTrue(ClaudeSkill.newerThanMine(newer.toString()));
        final JSONObject unknown = ClaudeSkill.formats("2");
        unknown.getJSONObject("features").put("fiction", 1);
        assertTrue(ClaudeSkill.newerThanMine(unknown.toString()));
    }
}
