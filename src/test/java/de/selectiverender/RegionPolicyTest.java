package de.selectiverender;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegionPolicyTest {
    private static final BlockRegion BOX = new BlockRegion(0, 10, 0, 10, 0, 10);
    @Test void overridesAreSpatialAndExplicitFalseWins() {
        var index = RegionPolicyIndex.of(Map.of("test", List.of(BOX)), Map.of("test",
                RegionOptions.DEFAULT.with(1, SelectiveRenderSettings.BoundaryMode.CULLED).with(2, false)));
        assertEquals(SelectiveRenderSettings.BoundaryMode.CULLED, index.boundaryOverrideAt(5, 5, 5));
        assertEquals(false, index.fluidOverrideAt(5, 5, 5));
        assertSame(RegionOptions.DEFAULT, index.optionsAt(11, 5, 5));
        assertNull(index.boundaryOverrideAt(11, 5, 5));
        assertTrue(index.culledOverride);
    }
    @Test void overlapUsesFirstExplicitValueForEachField() {
        Map<String, List<BlockRegion>> regions = new LinkedHashMap<>();
        regions.put("first", List.of(BOX)); regions.put("second", List.of(BOX));
        var index = RegionPolicyIndex.of(regions, Map.of(
                "first", RegionOptions.DEFAULT.with(1, SelectiveRenderSettings.BoundaryMode.NORMAL),
                "second", RegionOptions.DEFAULT.with(1, SelectiveRenderSettings.BoundaryMode.COLOR).with(5, true)));
        var resolved = index.optionsAt(5, 5, 5);
        assertEquals(SelectiveRenderSettings.BoundaryMode.NORMAL, resolved.boundary());
        assertEquals(resolved.boundary(), index.boundaryOverrideAt(5, 5, 5));
        assertTrue(resolved.hiddenInteractions());
    }
    @Test void filterOnlyOptionsDoNotAddSpatialPolicies() {
        var index = RegionPolicyIndex.of(Map.of("test", List.of(BOX)), Map.of("test", RegionOptions.DEFAULT.with(0, false)));
        assertSame(RegionOptions.DEFAULT, index.optionsAt(5, 5, 5));
        assertFalse(index.blackOverride); assertFalse(index.culledOverride);
    }
}
