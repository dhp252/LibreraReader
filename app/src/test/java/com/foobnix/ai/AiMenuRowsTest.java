package com.foobnix.ai;

import org.junit.Test;

import java.util.Arrays;

import static com.foobnix.ai.AiMenuRows.Row.GOAL;
import static com.foobnix.ai.AiMenuRows.Row.HIGHLIGHT_BOOK;
import static com.foobnix.ai.AiMenuRows.Row.HIGHLIGHT_CHAPTER;
import static com.foobnix.ai.AiMenuRows.Row.IDEAS;
import static com.foobnix.ai.AiMenuRows.Row.PROGRESS;
import static com.foobnix.ai.AiMenuRows.Row.REMOVE_BOOK;
import static com.foobnix.ai.AiMenuRows.Row.REMOVE_CHAPTER;
import static com.foobnix.ai.AiMenuRows.Row.SAVE_INTO_PDF;
import static com.foobnix.ai.AiMenuRows.Row.SHOW;
import static org.junit.Assert.assertEquals;

public class AiMenuRowsTest {

    @Test
    public void aBookWithoutMarksOffersNothingToRemove() {
        assertEquals(Arrays.asList(IDEAS, HIGHLIGHT_CHAPTER, HIGHLIGHT_BOOK, SHOW, GOAL),
                AiMenuRows.keyPassages(false, false, true));
    }

    @Test
    public void marksCanBeRemovedAndSavedIntoAPdf() {
        assertEquals(Arrays.asList(IDEAS, HIGHLIGHT_CHAPTER, HIGHLIGHT_BOOK, SHOW, GOAL, REMOVE_CHAPTER, REMOVE_BOOK, SAVE_INTO_PDF),
                AiMenuRows.keyPassages(false, true, true));
        assertEquals("not a PDF the app may draw into",
                Arrays.asList(IDEAS, HIGHLIGHT_CHAPTER, HIGHLIGHT_BOOK, SHOW, GOAL, REMOVE_CHAPTER, REMOVE_BOOK),
                AiMenuRows.keyPassages(false, true, false));
    }

    @Test
    public void whileRunningOnlyTheProgressAndWhatDoesNotWriteTheFile() {
        assertEquals(Arrays.asList(IDEAS, PROGRESS, SHOW, GOAL), AiMenuRows.keyPassages(true, true, true));
        assertEquals(Arrays.asList(IDEAS, PROGRESS, SHOW, GOAL), AiMenuRows.keyPassages(true, false, false));
    }
}
