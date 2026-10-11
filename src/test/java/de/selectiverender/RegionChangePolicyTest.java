package de.selectiverender;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionChangePolicyTest {
    @Test
    void visibleHiddenOverrideCountsAsAVisibleChange() {
        assertTrue(RegionChangePolicy.affectsRendering(
                false, true, false, true, true, false, true));
    }

    @Test
    void inactiveRegionDoesNotCountWithoutAnEnabledGroup() {
        assertFalse(RegionChangePolicy.affectsRendering(
                false, false, false, false, true, false, true));
    }

    @Test
    void overrideDeletionCountsInPlotMode() {
        assertTrue(RegionChangePolicy.affectsRendering(
                false, false, true, true, true, false, true));
    }

    @Test
    void visibleHiddenRegionCountsWhenTheHideGroupIsDisabled() {
        assertTrue(RegionChangePolicy.affectsRendering(
                false, true, false, true, true, true, false));
    }
}
