package com.foobnix.annotation;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MarksStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void keepsTheMarksOfABookInItsOwnFile() throws Exception {
        MarksStore store = new MarksStore(tmp.getRoot());
        final Mark[] made = new Mark[2];
        store.update("Book.epub", marks -> {
            Mark text = Mark.create(Mark.HIGHLIGHT, "#ffd400");
            text.quote = "a bright cold day";
            text.note = "the opening";
            text.anchor = new Mark.Anchor("itwas", "inapril", 0, 120);
            marks.put(text.id, text);
            made[0] = text;
        });
        store.update("Book.epub", marks -> {
            Mark line = Mark.create(Mark.UNDERLINE, "#2ea8e5");
            line.created = made[0].created + 1;
            line.quote = "the clocks";
            line.anchor = new Mark.Anchor("", "", 1, 120);
            marks.put(line.id, line);
            made[1] = line;
        });

        Map<String, Mark> loaded = store.load("Book.epub");
        assertEquals(Arrays.asList(made[0].id, made[1].id), Arrays.asList(loaded.keySet().toArray()));
        Mark text = loaded.get(made[0].id);
        assertEquals("the opening", text.note);
        assertEquals("inapril", text.anchor.suffix);
        assertTrue(new File(tmp.getRoot(), "marks/Book.epub.json").isFile());
        assertEquals("marks/Book.epub.json", MarksStore.profilePath("Book.epub"));
        assertTrue(MarksStore.isMarksFile("marks/Book.epub.json"));
    }

    @Test
    public void aFileThatCannotBeReadIsNeverWrittenOver() throws Exception {
        File file = new File(tmp.getRoot(), "marks/Book.pdf.json");
        assertTrue(file.getParentFile().mkdirs());
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write("{\"m1\": {\"t\": 5, cut off".getBytes(StandardCharsets.UTF_8));
        }
        MarksStore store = new MarksStore(tmp.getRoot());
        assertTrue(store.load("Book.pdf").isEmpty());
        try {
            store.update("Book.pdf", marks -> marks.clear());
            fail("wrote over an unreadable file");
        } catch (IOException expected) {
            // the next sync brings a good copy
        }
        assertEquals("{\"m1\": {\"t\": 5, cut off", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void marksSurviveTheRoundTrip() throws Exception {
        Mark pdf = Mark.create(Mark.STRIKE, "#ff6666");
        pdf.quote = "two lines";
        pdf.parts.add(new Mark.Part(4, new float[]{0.1f, 0.2f, 0.9f, 0.23f, 0.1f, 0.23f, 0.5f, 0.26f}));
        Mark ink = Mark.create(Mark.INK, "#000000");
        ink.ink.add(new Mark.Ink(7, 0.0031f, new float[]{0.2f, 0.3f, 0.25f, 0.35f}));

        Mark pdfBack = Mark.fromJson(pdf.id, pdf.toJson());
        assertNotNull(pdfBack);
        assertEquals(Mark.STRIKE, pdfBack.kind);
        assertEquals(4, pdfBack.firstPage());
        assertArrayEquals(pdf.parts.get(0).rects, pdfBack.parts.get(0).rects, 0.00001f);
        assertEquals(0.2f, pdfBack.firstTop(), 0.00001f);
        assertTrue(pdfBack.isOnPositions());

        Mark inkBack = Mark.fromJson(ink.id, ink.toJson());
        assertTrue(inkBack.isInk());
        assertEquals(0.0031f, inkBack.ink.get(0).width, 0.00001f);
        assertEquals("", inkBack.note);

        // nothing says where it is: not a mark
        assertNull(Mark.fromJson("m0", new org.json.JSONObject("{\"t\":1,\"k\":\"highlight\"}")));
        assertTrue(Mark.newId().matches("m[0-9a-f]{16}"));
    }
}
