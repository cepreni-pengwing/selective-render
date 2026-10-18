package de.selectiverender;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlotSelectionTest {
    private static final BlockRegion RAW = new BlockRegion(0, 31, -64, 319, 0, 31);

    private static List<BlockRegion> at(int minY, int maxY) { return PlotRegionTransform.apply(List.of(RAW), minY, maxY, 0); }

    @Test void sameHeightTogglesOff() {
        var selection = new PlotSelection();
        assertEquals(PlotSelection.Change.ADDED, selection.toggle(List.of(RAW), "a", List.of(RAW), -64, 319, 0, true, at(-64, 319)));
        assertEquals(PlotSelection.Change.REMOVED, selection.toggle(List.of(RAW), "a", List.of(RAW), -64, 319, 0, true, at(-64, 319)));
        assertTrue(selection.isEmpty());
    }

    @Test void changedHeightUpdatesInsteadOfRemoving() {
        var selection = new PlotSelection();
        selection.toggle(List.of(RAW), "a", List.of(RAW), -64, 319, 0, true, at(-64, 319));
        assertEquals(PlotSelection.Change.UPDATED, selection.toggle(List.of(RAW), "a", List.of(RAW), 0, 100, 0, false, at(0, 100)));
        assertEquals(1, selection.size());
        assertEquals(at(0, 100), selection.regions());
        assertEquals(PlotSelection.Change.REMOVED, selection.toggle(List.of(RAW), "a", List.of(RAW), 0, 100, 0, false, at(0, 100)));
    }

    @Test void identityIgnoresHeightOfGridPlots() {
        var selection = new PlotSelection();
        var flat = new BlockRegion(0, 31, 0, 0, 0, 31);
        selection.toggle(List.of(flat), "grid", List.of(RAW), -64, 319, 0, true, at(-64, 319));
        assertEquals(PlotSelection.Change.UPDATED, selection.toggle(List.of(RAW), "grid", List.of(RAW), 10, 20, 0, false, at(10, 20)));
        assertEquals(1, selection.size());
    }

    @Test void defaultHeightChangeKeepsPlotsActive() {
        var selection = new PlotSelection();
        var other = new BlockRegion(40, 71, -64, 319, 0, 31);
        selection.toggle(List.of(RAW), "a", List.of(RAW), -64, 319, 0, true, at(-64, 319));
        selection.toggle(List.of(other), "b", List.of(other), 5, 6, 0, false, PlotRegionTransform.apply(List.of(other), 5, 6, 0));
        assertTrue(selection.applyDefaultHeights(0, 64));
        assertEquals(2, selection.size());
        assertTrue(selection.regions().contains(new BlockRegion(0, 31, 0, 64, 0, 31)));
        assertTrue(selection.regions().contains(new BlockRegion(40, 71, 5, 6, 0, 31)));
        assertFalse(selection.applyDefaultHeights(0, 64));
        assertFalse(selection.applyDefaultHeights(10, 5));
        assertEquals(PlotSelection.Change.REMOVED, selection.toggle(List.of(RAW), "a", List.of(RAW), 0, 64, 0, true, at(0, 64)));
    }
}
