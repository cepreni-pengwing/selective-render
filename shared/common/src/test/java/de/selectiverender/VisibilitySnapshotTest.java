package de.selectiverender;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisibilitySnapshotTest {
    private static final BlockRegion NORMAL = new BlockRegion(0, 15, 0, 15, 0, 15);
    private static final BlockRegion OVERRIDE = new BlockRegion(32, 47, 0, 15, 0, 15);
    private static final BlockRegion PLOT = new BlockRegion(64, 79, 0, 15, 0, 15);

    @Test
    void publishesCompleteDerivedStateInOneImmutableObject() {
        VisibilitySnapshot snapshot = VisibilitySnapshot.create(
                List.of(NORMAL), true, List.of(), true, List.of(OVERRIDE),
                List.of(), false, false, 7);

        assertTrue(snapshot.enabled());
        assertEquals(List.of(NORMAL, OVERRIDE), snapshot.traversalRegions());
        assertTrue(snapshot.activeRegionIndex().contains(1, 1, 1));
        assertTrue(snapshot.overrideRegionIndex().contains(33, 1, 1));
        assertEquals(7, snapshot.generation());
    }

    @Test
    void plotStateReplacesTheActiveWhitelistWithoutMutatingTheOldSnapshot() {
        VisibilitySnapshot normal = VisibilitySnapshot.create(
                List.of(NORMAL), true, List.of(), false, List.of(),
                List.of(), false, false, 2);
        VisibilitySnapshot plot = normal.withPlotState(List.of(PLOT), true, true, 3);

        assertSame(NORMAL, normal.activeRegions().get(0));
        assertFalse(normal.plotModeActive());
        assertSame(PLOT, plot.activeRegions().get(0));
        assertTrue(plot.plotRenderingEnabled());
        assertEquals(3, plot.generation());
    }

    @Test
    void configuredToggleCreatesANewSnapshot() {
        VisibilitySnapshot enabled = VisibilitySnapshot.create(
                List.of(NORMAL), true, List.of(OVERRIDE), false, List.of(),
                List.of(), false, false, 9);
        VisibilitySnapshot disabled = enabled.toggleConfiguredState(10);

        assertTrue(enabled.enabled());
        assertFalse(disabled.enabled());
        assertFalse(enabled.hideEnabled());
        assertFalse(disabled.hideEnabled());
        assertEquals(10, disabled.generation());
    }

    @Test
    void savedBlockFiltersRemainInTheSnapshotWhenRenderRegionsAreOff() {
        FilteredRegion filter = new FilteredRegion(NORMAL, java.util.Set.of(
                new BlockFilterRule(BlockFilterRule.Mode.HIDE, BlockFilterRule.Kind.ID,
                        "minecraft:oak_planks")));
        VisibilitySnapshot snapshot = VisibilitySnapshot.create(
                List.of(NORMAL), false, List.of(), false, List.of(), List.of(),
                List.of(filter), false, false, 14);

        assertFalse(snapshot.enabled());
        assertEquals(List.of(filter), snapshot.filteredRegions());
    }

    @Test
    void configuredToggleNeverChangesTheHiddenGroupState() {
        VisibilitySnapshot snapshot = VisibilitySnapshot.create(
                List.of(NORMAL), true, List.of(OVERRIDE), true, List.of(),
                List.of(), false, false, 11);

        VisibilitySnapshot disabled = snapshot.toggleConfiguredState(12);
        VisibilitySnapshot enabledAgain = disabled.toggleConfiguredState(13);

        assertTrue(snapshot.hideEnabled());
        assertTrue(disabled.hideEnabled());
        assertTrue(enabledAgain.hideEnabled());
    }
}
