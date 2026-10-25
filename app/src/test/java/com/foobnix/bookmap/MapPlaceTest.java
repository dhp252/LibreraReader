package com.foobnix.bookmap;

import com.foobnix.ai.AiCategory;
import com.foobnix.ai.AiStore;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MapPlaceTest {

    private final List<String> words = new ArrayList<>();
    private final List<Integer> pages = new ArrayList<>();

    private void page(int page, String text) {
        for (String w : text.split(" ")) {
            words.add(w);
            pages.add(page);
        }
    }

    private static MapPrompts.Draft draft(String label, String quote) {
        return new MapPrompts.Draft(Collections.singletonList("Gatsby"), MapBook.DEVELOP, label, quote, 0);
    }

    @Test
    public void anchorsQuotesToTheBooksWordsInReadingOrder() {
        page(4, "In my younger and more vulnerable years my father gave me some advice that I have been");
        page(5, "turning over in my mind ever since. Gatsby turned out all right at the end.");
        final MapPlace.Placed placed = MapPlace.place(words, pages, Arrays.asList(
                draft("End", "Gatsby turned out all right at the end."),
                // the model's quote marks and dashes differ: still found
                draft("Advice", "“my father gave me some advice that I have been turning over”"),
                draft("Invented", "He never said anything like this at all."),
                draft("Too short", "Gatsby")));
        assertEquals(2, placed.dropped);
        assertEquals(2, placed.moments.size());
        final MapBook.Moment advice = placed.moments.get(0);
        assertEquals("Advice", advice.label);
        assertEquals("over a page break the quote starts on its first page", 4, advice.page);
        assertEquals("my father gave me some advice that I have been turning over", advice.quote);
        assertTrue(advice.at > 0 && advice.at < placed.moments.get(1).at);
        assertEquals(5, placed.moments.get(1).page);
    }

    @Test
    public void ideasBorrowTheirStoredSentence() {
        page(0, "Fortune is the arbiter of one half of our actions but she still leaves us the other half.");
        final AiStore.Chapter stored = new AiStore.Chapter("xxv#0", "XXV", "m", 1, Arrays.asList(
                new AiStore.Quote("Fortune is the arbiter of one half of our actions", 0, AiCategory.MAIN, 1),
                new AiStore.Quote("another sentence of idea one", 0, AiCategory.MAIN, 1)));
        final List<MapPrompts.Draft> drafts = MapPlace.fromIdeas(stored, Arrays.asList(
                new MapPrompts.Draft(Collections.singletonList("Fortune"), MapBook.DEVELOP, "Half", "", 1),
                new MapPrompts.Draft(Collections.singletonList("Fortune"), MapBook.TURN, "Unmarked", "", 2)));
        assertEquals("Fortune is the arbiter of one half of our actions", drafts.get(0).quote);
        assertEquals("", drafts.get(1).quote);
        final MapPlace.Placed placed = MapPlace.place(words, pages, drafts);
        assertEquals(1, placed.moments.size());
        assertEquals(1, placed.dropped);
    }
}
