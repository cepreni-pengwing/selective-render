package de.selectiverender;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderReloadPolicyTest {
    @Test void moderateUpdatesNoLongerReloadTheWholeRenderer() {
        assertFalse(RenderReloadPolicy.requiresFullReload(2000, 8192));
    }
    @Test void configuredAbsoluteLimitIsStrictlyApplied() {
        assertFalse(RenderReloadPolicy.requiresFullReload(8192, 8192));
        assertTrue(RenderReloadPolicy.requiresFullReload(8193, 8192));
    }
    @Test void loadedPercentageDoesNotOverrideTheConfiguredLimit() {
        assertFalse(RenderReloadPolicy.requiresFullReload(850, 8192));
        assertFalse(RenderReloadPolicy.requiresFullReload(851, 8192));
        assertFalse(RenderReloadPolicy.requiresFullReload(1000, 8192));
    }
}
