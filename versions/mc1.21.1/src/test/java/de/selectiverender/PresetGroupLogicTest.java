package de.selectiverender;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresetGroupLogicTest {
    @Test
    void toggleAllSelectsAndClearsTheGroup() {
        LinkedHashSet<String> active = new LinkedHashSet<>();
        assertFalse(PresetGroupLogic.toggleAll(active, List.of("one", "two")));
        assertEquals(List.of("one", "two"), List.copyOf(active));
        assertTrue(PresetGroupLogic.toggleAll(active, List.of("one", "two")));
        assertTrue(active.isEmpty());
    }

    @Test
    void renamePreservesMembership() {
        LinkedHashSet<String> group = new LinkedHashSet<>(List.of("one", "two"));
        PresetGroupLogic.replaceMembership(group, "one", "renamed");
        assertEquals(List.of("two", "renamed"), List.copyOf(group));
    }
}
