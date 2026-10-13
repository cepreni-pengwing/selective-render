package de.selectiverender;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VirtualSkyActivationTest {
    @Test void missingSkylightSamplesCannotTurnIntoFullbright() {
        assertFalse(VirtualSkyActivation.canUseDirectSky(-1,true));
        assertFalse(VirtualSkyActivation.canUseDirectSky(15,false));
        assertTrue(VirtualSkyActivation.canUseDirectSky(0,true));
        assertTrue(VirtualSkyActivation.canUseDirectSky(15,true));
    }

    @Test
    void remainsNoOpOnFreshInstallWithNoActiveFilters() {
        assertFalse(VirtualSkyActivation.isActive(false, false, false, 0));
        assertTrue(VirtualSkyActivation.isActive(false, false, false, 3));
    }

    @Test
    void activeFiltersKeepVirtualLightingIndependentlyOfInteractionPolicy() {
        assertTrue(VirtualSkyActivation.isActive(false, false, true, 1));
        assertFalse(VirtualSkyActivation.isActive(false, false, true, 0));
    }

    @Test
    void activeRenderOrHiddenGroupsStillEnableVirtualLighting() {
        assertTrue(VirtualSkyActivation.isActive(true, false, false, 0));
        assertTrue(VirtualSkyActivation.isActive(false, true, false, 0));
    }
}
