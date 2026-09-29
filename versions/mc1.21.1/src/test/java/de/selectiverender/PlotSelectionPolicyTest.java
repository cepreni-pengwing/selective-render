package de.selectiverender;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PlotSelectionPolicyTest {
    @Test
    void firstPlotEnablesIsolationEvenAfterClearingADisabledSelection() {
        assertTrue(PlotSelectionPolicy.renderingAfterAdd(true, false));
        assertTrue(PlotSelectionPolicy.renderingAfterAdd(true, true));
    }

    @Test
    void addingMorePlotsPreservesTheUsersToggle() {
        assertFalse(PlotSelectionPolicy.renderingAfterAdd(false, false));
        assertTrue(PlotSelectionPolicy.renderingAfterAdd(false, true));
    }

    @Test
    void collectWhileDisabledThenEnableTheEntireSelection() {
        BlockRegion first = new BlockRegion(0, 15, -100, 400, 0, 15);
        BlockRegion second = new BlockRegion(32, 47, -100, 400, 0, 15);
        VisibilitySnapshot snapshot = VisibilitySnapshot.EMPTY.withPlotState(List.of(first), true,
                PlotSelectionPolicy.renderingAfterAdd(true, false), 2);
        assertTrue(snapshot.enabled());
        snapshot = snapshot.withPlotState(snapshot.plotRegions(), true, false, 3);
        boolean rendering = PlotSelectionPolicy.renderingAfterAdd(false, snapshot.plotRenderingEnabled());
        snapshot = snapshot.withPlotState(List.of(first, second), true, rendering, 4);
        assertFalse(snapshot.enabled());
        assertEquals(List.of(first, second), snapshot.plotRegions());

        VisibilitySnapshot restored = VisibilitySnapshot.EMPTY.withPlotState(snapshot.plotRegions(),
                true, snapshot.plotRenderingEnabled(), 5);
        assertFalse(restored.enabled());
        restored = restored.withPlotState(restored.plotRegions(), true, true, 6);
        assertTrue(restored.enabled());
        assertTrue(restored.activeRegionIndex().contains(0, 0, 0));
        assertTrue(restored.activeRegionIndex().contains(32, 0, 0));
    }

    @Test
    void editingADisabledPlotSelectionDoesNotRebuildMeshes() {
        assertFalse(PlotSelectionPolicy.needsMeshUpdate(false, false));
        assertTrue(PlotSelectionPolicy.needsMeshUpdate(true, false));
        assertTrue(PlotSelectionPolicy.needsMeshUpdate(false, true));
        assertTrue(PlotSelectionPolicy.needsMeshUpdate(true, true));
    }
}
