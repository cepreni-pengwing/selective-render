package de.selectiverender;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionVisibilityTest {
    private static final BlockRegion WHITELIST = new BlockRegion(0, 15, 0, 15, 0, 15);
    private static final BlockRegion OUTSIDE_HIDDEN = new BlockRegion(32, 47, 0, 15, 0, 15);

    @Test
    void visibleHiddenPresetOverridesWhitelist() {
        assertTrue(RegionVisibility.block(true, List.of(WHITELIST), List.of(),
                List.of(OUTSIDE_HIDDEN), 32, 0, 0));
        assertTrue(RegionVisibility.section(true, List.of(WHITELIST), List.of(),
                List.of(OUTSIDE_HIDDEN), 2, 0, 0));
    }

    @Test
    void activeHiddenPresetStillWinsInsideWhitelist() {
        assertFalse(RegionVisibility.block(true, List.of(WHITELIST), List.of(WHITELIST),
                List.of(), 0, 0, 0));
        assertFalse(RegionVisibility.section(true, List.of(WHITELIST), List.of(WHITELIST),
                List.of(), 0, 0, 0));
    }

    @Test
    void hiddenRegionWinsOverVisibleOverrideWhenBothAreActive() {
        assertFalse(RegionVisibility.block(true, List.of(WHITELIST), List.of(OUTSIDE_HIDDEN),
                List.of(OUTSIDE_HIDDEN), 32, 0, 0));
        assertFalse(RegionVisibility.section(true, List.of(WHITELIST), List.of(OUTSIDE_HIDDEN),
                List.of(OUTSIDE_HIDDEN), 2, 0, 0));
    }

    @Test
    void hiddenOnlyLeavesUnrelatedSectionsUnchanged() {
        assertEquals(SectionVisibility.UNCHANGED,
                RegionVisibility.classifySection(false, List.of(), List.of(OUTSIDE_HIDDEN), List.of(),
                        0, 0, 0));
        assertEquals(SectionVisibility.HIDDEN,
                RegionVisibility.classifySection(false, List.of(), List.of(OUTSIDE_HIDDEN), List.of(),
                        2, 0, 0));
    }

    @Test
    void blockAlignedWhitelistHasNoPerBlockBoundaryWork() {
        assertEquals(SectionVisibility.UNCHANGED,
                RegionVisibility.classifySection(true, List.of(WHITELIST), List.of(), List.of(),
                        0, 0, 0));
        assertEquals(SectionVisibility.HIDDEN,
                RegionVisibility.classifySection(true, List.of(WHITELIST), List.of(), List.of(),
                        1, 0, 0));
    }

    @Test
    void partialBoundariesRemainBlockFiltered() {
        BlockRegion partial = new BlockRegion(1, 14, 1, 14, 1, 14);
        assertEquals(SectionVisibility.PARTIAL,
                RegionVisibility.classifySection(true, List.of(partial), List.of(), List.of(),
                        0, 0, 0));
        assertEquals(SectionVisibility.PARTIAL,
                RegionVisibility.classifySection(false, List.of(), List.of(partial), List.of(),
                        0, 0, 0));
    }
}
