package de.selectiverender;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualLightPropagationTest {
    @Test
    void skipsNeighborsThatCannotBecomeBrighter() {
        assertFalse(VirtualLightPropagation.canImprove(15, 15));
        assertFalse(VirtualLightPropagation.canImprove(15, 14));
        assertFalse(VirtualLightPropagation.canImprove(1, 0));
    }

    @Test
    void retainsNeighborsThatMayBecomeBrighter() {
        assertTrue(VirtualLightPropagation.canImprove(15, 13));
        assertTrue(VirtualLightPropagation.canImprove(2, 0));
    }
}
