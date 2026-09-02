package de.selectiverender;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderReloadPolicyTest {
    @Test void moderateUpdatesNoLongerReloadTheWholeRenderer() {
        assertFalse(RenderReloadPolicy.requiresFullReload(2000, 5000));
    }
    @Test void absoluteLimitIsStrictlyAbove4096() {
        assertFalse(RenderReloadPolicy.requiresFullReload(4096, 10000));
        assertTrue(RenderReloadPolicy.requiresFullReload(4097, 10000));
    }
    @Test void relativeLimitIsStrictlyAboveSixtyPercent() {
        assertFalse(RenderReloadPolicy.requiresFullReload(600, 1000));
        assertTrue(RenderReloadPolicy.requiresFullReload(601, 1000));
    }
}
