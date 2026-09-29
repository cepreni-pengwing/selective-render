package de.selectiverender;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InactiveStateTest {
    @Test
    void inactiveFiltersRenderEverythingDespiteStoredOverrides() {
        BlockRegion override = new BlockRegion(32, 47, 0, 15, 0, 15);
        assertTrue(RegionVisibility.block(false, List.of(), List.of(), List.of(override), 0, 0, 0));
        assertTrue(RegionVisibility.section(false, List.of(), List.of(), List.of(override), 0, 0, 0));
    }
}
