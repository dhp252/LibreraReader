package com.foobnix.webdav;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** The skill on the server (F-016): when it is sent, and that the asset folder holds what SKILL.md names. */
public class AiSkillTest {

    @Test
    public void sentWhenMissingOrOlder() {
        assertTrue(AiSkill.needsUpload(null, 1));
        assertTrue(AiSkill.needsUpload("{\"version\": 1}", 2));
        assertTrue(AiSkill.needsUpload("not json", 1));
        assertFalse(AiSkill.needsUpload("{\"version\": 1}", 1));
        // a newer app put it there: left alone
        assertFalse(AiSkill.needsUpload("{\"version\": 3}", 2));
    }

    @Test
    public void versionOfSkillJson() {
        assertEquals(4, AiSkill.version("{\"version\": 4}"));
        assertEquals(0, AiSkill.version("{}"));
        assertEquals(0, AiSkill.version(""));
    }

    @Test
    public void assetsMatchSkill() throws Exception {
        final File dir = assets();
        assertTrue(AiSkill.version(read(new File(dir, AiSkill.VERSION_FILE))) >= 1);
        final String skill = read(new File(dir, "SKILL.md"));
        // the skill's name is its folder's name on the server
        final String folder = AiSkill.FOLDER.substring(AiSkill.FOLDER.lastIndexOf('/') + 1);
        assertTrue(skill.startsWith("---\nname: " + folder + "\n"));
        for (String ref : new String[]{"chapters.md", "ai-file.md", "assist-file.md"}) {
            assertTrue(ref, new File(dir, "reference/" + ref).isFile());
            assertTrue(ref, skill.contains("reference/" + ref));
        }
    }

    static File assets() {
        for (String p : new String[]{"src/main/assets/" + AiSkill.ASSETS, "app/src/main/assets/" + AiSkill.ASSETS}) {
            final File f = new File(p);
            if (f.isDirectory()) {
                return f;
            }
        }
        throw new AssertionError("assets/" + AiSkill.ASSETS + " not found from " + new File(".").getAbsolutePath());
    }

    static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }
}
