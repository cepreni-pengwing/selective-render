package de.selectiverender;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionGroupLogicTest {
    @Test
    void toggleAllSelectsAndClearsTheGroup() {
        LinkedHashSet<String> active = new LinkedHashSet<>();
        assertFalse(RegionGroupLogic.toggleAll(active, List.of("one", "two")));
        assertEquals(List.of("one", "two"), List.copyOf(active));
        assertTrue(RegionGroupLogic.toggleAll(active, List.of("one", "two")));
        assertTrue(active.isEmpty());
    }

    @Test
    void renamePreservesMembership() {
        LinkedHashSet<String> group = new LinkedHashSet<>(List.of("one", "two"));
        RegionGroupLogic.replaceMembership(group, "one", "renamed");
        assertEquals(List.of("two", "renamed"), List.copyOf(group));
    }
}
