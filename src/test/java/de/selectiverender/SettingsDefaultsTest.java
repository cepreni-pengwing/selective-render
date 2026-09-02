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
    }

    @Test void modesCycleThroughEveryValueAndWrap() {
        for (var value : SelectiveRenderSettings.PlayerVisibility.values()) {
            assertEquals(value, value.next().next().next().next());
            assertNotEquals(value, value.next().next());
        }
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
