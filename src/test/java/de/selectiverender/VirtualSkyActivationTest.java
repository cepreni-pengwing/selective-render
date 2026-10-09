package de.selectiverender;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VirtualSkyActivationTest {
    @Test
    void remainsNoOpOnFreshInstallAndInVanillaInactiveMode() {
        assertFalse(VirtualSkyActivation.isActive(false, false, false, 0));
        assertFalse(VirtualSkyActivation.isActive(false, false, false, 3));
    }

    @Test
    void filtersKeepVirtualLightingOnlyInFilteredInactiveMode() {
        assertTrue(VirtualSkyActivation.isActive(false, false, true, 1));
        assertFalse(VirtualSkyActivation.isActive(false, false, true, 0));
    }

    @Test
    void activeRenderOrHiddenGroupsStillEnableVirtualLighting() {
        assertTrue(VirtualSkyActivation.isActive(true, false, false, 0));
        assertTrue(VirtualSkyActivation.isActive(false, true, false, 0));
    }
}
