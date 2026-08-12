package de.selectiverender;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraversalSectionIndexTest {
    @Test
    void cachesAndDeduplicatesSectionKeys() {
        TraversalSectionIndex index = TraversalSectionIndex.of(List.of(
                new BlockRegion(0, 31, 0, 15, 0, 15),
                new BlockRegion(16, 47, 0, 15, 0, 15)));

        assertFalse(index.isEmpty());
        assertEquals(3, index.size());
    }

    @Test
    void avoidsHugeCaches() {
        TraversalSectionIndex index = TraversalSectionIndex.of(List.of(
                new BlockRegion(Integer.MIN_VALUE, Integer.MAX_VALUE,
                        Integer.MIN_VALUE, Integer.MAX_VALUE,
                        Integer.MIN_VALUE, Integer.MAX_VALUE)));
        assertTrue(index.isEmpty());
    }
}
