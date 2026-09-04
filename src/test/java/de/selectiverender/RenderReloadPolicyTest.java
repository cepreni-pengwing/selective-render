package de.selectiverender;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderReloadPolicyTest {
    @Test void moderateUpdatesNoLongerReloadTheWholeRenderer() {
        assertFalse(RenderReloadPolicy.requiresFullReload(2000, 5000, 8192));
    }
    @Test void configuredAbsoluteLimitIsStrictlyApplied() {
        assertFalse(RenderReloadPolicy.requiresFullReload(8192, 10000, 8192));
        assertTrue(RenderReloadPolicy.requiresFullReload(8193, 10000, 8192));
    }
    @Test void relativeLimitIsStrictlyAboveEightyFivePercent() {
        assertFalse(RenderReloadPolicy.requiresFullReload(850, 1000, 8192));
        assertTrue(RenderReloadPolicy.requiresFullReload(851, 1000, 8192));
    }
}
