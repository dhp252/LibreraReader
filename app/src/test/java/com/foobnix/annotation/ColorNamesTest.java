package com.foobnix.annotation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ColorNamesTest {

    @Test
    public void namesThePaletteColors() {
        String[] expected = {"yellow", "red", "green", "blue", "purple", "magenta", "orange", "gray", "black"};
        for (int i = 0; i < ColorNames.PALETTE.length; i++) {
            assertEquals(expected[i], ColorNames.name(ColorNames.PALETTE[i]));
        }
        assertEquals(ColorNames.EMOJI[0] + " yellow", ColorNames.label("#FFD400"));
    }

    @Test
    public void otherAppsColorsTakeTheNearestName() {
        assertEquals("yellow", ColorNames.name("#ffff00"));
        assertEquals("blue", ColorNames.name("#0000ff"));
        assertEquals("green", ColorNames.name("#00ff00"));
        assertEquals("red", ColorNames.name("#ff0000"));
        assertEquals("black", ColorNames.name("#202020"));
        assertEquals("yellow", ColorNames.name("not a color"));
        assertEquals("#2ea8e5", ColorNames.hex(0xff2ea8e5));
    }
}
