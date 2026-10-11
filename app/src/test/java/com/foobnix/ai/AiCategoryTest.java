package com.foobnix.ai;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AiCategoryTest {

    @Test
    public void codesReadBackInAnyCase() {
        for (AiCategory category : AiCategory.values()) {
            assertEquals(category, AiCategory.of(category.code));
            assertEquals(category, AiCategory.of(" " + category.code.toUpperCase() + " "));
        }
        assertNull(AiCategory.of("yellow"));
        assertNull(AiCategory.of(""));
        assertNull(AiCategory.of(null));
    }

    @Test
    public void everyCategoryHasItsOwnColorAndNoneIsTheReadersYellow() {
        final Set<Integer> colors = new HashSet<>();
        for (AiCategory category : AiCategory.values()) {
            assertNotEquals(AiCategory.READER_YELLOW, category.color);
            assertTrue(colors.add(category.color));
        }
        assertEquals(0xffff6666, AiCategory.MAIN.color);
    }
}
