package com.foobnix.assist.debate;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DebateAuthorTest {

    private static void check(String meta, String name, String shortName, String initials) {
        final DebateAuthor a = DebateAuthor.of(meta);
        assertEquals(meta, name, a.name);
        assertEquals(meta, shortName, a.shortName);
        assertEquals(meta, initials, a.initials);
        assertEquals(meta, !name.isEmpty(), a.known);
    }

    @Test
    public void theNameAsTheLibraryHasIt() {
        check("Adam Smith", "Adam Smith", "Smith", "AS");
        check("  Adam \n Smith ", "Adam Smith", "Smith", "AS");
        check("Smith, Adam", "Adam Smith", "Smith", "AS");
        check("Plato", "Plato", "Plato", "P");
        check("Daniel Kahneman", "Daniel Kahneman", "Kahneman", "DK");
        check("Nguyễn Nhật Ánh", "Nguyễn Nhật Ánh", "Ánh", "NÁ");
        check("john maynard keynes", "john maynard keynes", "keynes", "JK");
    }

    @Test
    public void theFirstOfSeveralAuthors() {
        check("Adam Smith; David Hume", "Adam Smith", "Smith", "AS");
        check("Adam Smith, David Hume", "Adam Smith", "Smith", "AS");
        check("Adam Smith & David Hume", "Adam Smith", "Smith", "AS");
        check("Adam Smith and David Hume", "Adam Smith", "Smith", "AS");
        check("Smith, Adam; Hume, David", "Adam Smith", "Smith", "AS");
        check("Smith, Hume, Locke", "Smith", "Smith", "S");
    }

    @Test
    public void noNameIsTheAuthor() {
        check(null, "", "", "");
        check("", "", "", "");
        check("   ", "", "", "");
        check("---", "", "", "");
        assertFalse(DebateAuthor.of(null).known);
        assertTrue(DebateAuthor.of("X").known);
    }
}
