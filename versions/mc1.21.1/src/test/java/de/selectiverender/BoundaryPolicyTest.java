package de.selectiverender;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoundaryPolicyTest {
    @Test void appliesConfiguredModeOnlyAcrossVisibleToFilteredCuts() {
        assertEquals(SelectiveRenderSettings.BoundaryMode.BLACK,
                BoundaryPolicy.mode(SelectiveRenderSettings.BoundaryMode.BLACK,
                        true, false, false));
        assertEquals(SelectiveRenderSettings.BoundaryMode.CULLED,
                BoundaryPolicy.mode(SelectiveRenderSettings.BoundaryMode.CULLED,
                        true, false, false));
    }

    @Test void hiddenAndNonBoundaryTransitionsRemainNormal() {
        assertEquals(SelectiveRenderSettings.BoundaryMode.NORMAL,
                BoundaryPolicy.mode(SelectiveRenderSettings.BoundaryMode.BLACK,
                        true, false, true));
        assertEquals(SelectiveRenderSettings.BoundaryMode.NORMAL,
                BoundaryPolicy.mode(SelectiveRenderSettings.BoundaryMode.BLACK,
                        true, true, false));
        assertEquals(SelectiveRenderSettings.BoundaryMode.NORMAL,
                BoundaryPolicy.mode(SelectiveRenderSettings.BoundaryMode.BLACK,
                        false, false, false));
        assertEquals(SelectiveRenderSettings.BoundaryMode.NORMAL,
                BoundaryPolicy.mode(SelectiveRenderSettings.BoundaryMode.NORMAL,
                        true, false, false));
    }
}
