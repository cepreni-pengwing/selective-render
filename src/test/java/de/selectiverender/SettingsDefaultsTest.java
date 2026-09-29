package de.selectiverender;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsDefaultsTest {
    @Test void defaultsAreUnrestrictedWithNormalBoundariesAndNoDebugBoxes() {
        assertEquals(SelectiveRenderSettings.PlayerVisibility.EVERYWHERE,
                SelectiveRenderSettings.playerVisibility());
        assertEquals(SelectiveRenderSettings.InteractionMode.EVERYWHERE,
                SelectiveRenderSettings.interactionMode());
        assertEquals(SelectiveRenderSettings.BoundaryMode.NORMAL,
                SelectiveRenderSettings.boundaryMode());
        assertFalse(SelectiveRenderSettings.debugBoxes());
        assertFalse(SelectiveRenderSettings.filterInteractionsWhenInactive());
        assertFalse(SelectiveRenderSettings.interactWithHiddenRegions());
        assertEquals(SelectiveRenderSettings.VirtualLightMode.BOTH,
                SelectiveRenderSettings.virtualLightMode());
        assertEquals(8192, SelectiveRenderSettings.fullReloadThreshold());
        assertEquals(-64, SelectiveRenderSettings.defaultPlotMinY());
    }

    @Test void modesCycleThroughEveryValueAndWrap() {
        for (var value : SelectiveRenderSettings.PlayerVisibility.values()) {
            var cycled = value;
            for (int i = 0; i < SelectiveRenderSettings.PlayerVisibility.values().length; i++) {
                cycled = cycled.next();
            }
            assertEquals(value, cycled);
        }
        for (var value : SelectiveRenderSettings.VirtualLightMode.values()) {
            assertEquals(value, value.next().next().next().next());
        }
        assertTrue(SelectiveRenderSettings.VirtualLightMode.TOP.seedsColumn(true));
        assertFalse(SelectiveRenderSettings.VirtualLightMode.TOP.seedsColumn(false));
        assertFalse(SelectiveRenderSettings.VirtualLightMode.TOP.allowsPropagation(true, false));
        assertTrue(SelectiveRenderSettings.VirtualLightMode.SIDES.seedsColumn(false));
        assertTrue(SelectiveRenderSettings.VirtualLightMode.SIDES.allowsPropagation(false, true));
        assertFalse(SelectiveRenderSettings.VirtualLightMode.SIDES.allowsPropagation(true, false));
        for (var value : SelectiveRenderSettings.InteractionMode.values()) {
            assertEquals(value, value.next().next().next().next());
            assertNotEquals(value, value.next().next());
        }
        assertEquals(SelectiveRenderSettings.BoundaryMode.BLACK,
                SelectiveRenderSettings.BoundaryMode.NORMAL.next());
        assertEquals(SelectiveRenderSettings.BoundaryMode.CULLED,
                SelectiveRenderSettings.BoundaryMode.NORMAL.next().next());
        assertEquals(SelectiveRenderSettings.BoundaryMode.NORMAL,
                SelectiveRenderSettings.BoundaryMode.NORMAL.next().next().next());
    }
}
