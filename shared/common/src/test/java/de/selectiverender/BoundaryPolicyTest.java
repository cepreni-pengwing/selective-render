package de.selectiverender;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoundaryPolicyTest {
    @Test void appliesConfiguredModeOnlyAcrossVisibleToFilteredCuts() {
        assertEquals(SelectiveRenderSettings.BoundaryMode.COLOR,
                BoundaryPolicy.mode(SelectiveRenderSettings.BoundaryMode.COLOR,
                        true, false, false));
        assertEquals(SelectiveRenderSettings.BoundaryMode.CULLED,
                BoundaryPolicy.mode(SelectiveRenderSettings.BoundaryMode.CULLED,
                        true, false, false));
    }

    @Test void hiddenAndNonBoundaryTransitionsRemainNormal() {
        assertEquals(SelectiveRenderSettings.BoundaryMode.NORMAL,
                BoundaryPolicy.mode(SelectiveRenderSettings.BoundaryMode.COLOR,
                        true, false, true));
        assertEquals(SelectiveRenderSettings.BoundaryMode.NORMAL,
                BoundaryPolicy.mode(SelectiveRenderSettings.BoundaryMode.COLOR,
                        true, true, false));
        assertEquals(SelectiveRenderSettings.BoundaryMode.NORMAL,
                BoundaryPolicy.mode(SelectiveRenderSettings.BoundaryMode.COLOR,
                        false, false, false));
        assertEquals(SelectiveRenderSettings.BoundaryMode.NORMAL,
                BoundaryPolicy.mode(SelectiveRenderSettings.BoundaryMode.NORMAL,
                        true, false, false));
    }
}
