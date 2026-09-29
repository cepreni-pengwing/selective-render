package de.selectiverender;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresetVisibilityTest {
    @Test
    void visibleHiddenOverrideCountsAsAVisibleChange() {
        assertTrue(PresetVisibility.affectsRendering(
                false, true, false, true, true, false, true));
    }

    @Test
    void inactivePresetDoesNotCountWithoutAnEnabledGroup() {
        assertFalse(PresetVisibility.affectsRendering(
                false, false, false, false, true, false, true));
    }

    @Test
    void overrideDeletionCountsInPlotMode() {
        assertTrue(PresetVisibility.affectsRendering(
                false, false, true, true, true, false, true));
    }

    @Test
    void visibleHiddenPresetCountsWhenTheHideGroupIsDisabled() {
        assertTrue(PresetVisibility.affectsRendering(
                false, true, false, true, true, true, false));
    }
}
