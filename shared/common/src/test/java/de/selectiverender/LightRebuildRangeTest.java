package de.selectiverender;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LightRebuildRangeTest {
    @Test
    void expandsByTheVirtualLightRadiusWithoutOverflow() {
        assertEquals(86, LightRebuildRange.expandMin(100, 14));
        assertEquals(114, LightRebuildRange.expandMax(100, 14));
        assertEquals(Integer.MIN_VALUE,
                LightRebuildRange.expandMin(Integer.MIN_VALUE, 14));
        assertEquals(Integer.MAX_VALUE,
                LightRebuildRange.expandMax(Integer.MAX_VALUE, 14));
    }
}
