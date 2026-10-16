package de.selectiverender;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegionPolicyTest {
    private static final BlockRegion BOX = new BlockRegion(0, 10, 0, 10, 0, 10);
    @Test void laterLegacyBoundaryFillsMissingLayersWithoutReplacingEarlierExplicitColor() {
        var regions=new LinkedHashMap<String,List<BlockRegion>>();
        regions.put("first",List.of(BOX)); regions.put("second",List.of(BOX));
        var interaction=RegionOptions.DEFAULT.with(3,SelectiveRenderSettings.InteractionMode.NONE);
        var legacy=RegionOptions.DEFAULT.with(1,SelectiveRenderSettings.BoundaryMode.TEXTURE);
        var firstIndex=RegionPolicyIndex.of(regions,Map.of("first",interaction,"second",legacy));
        assertTrue(firstIndex.optionsAt(5,5,5).inheritedTexture().enabled());
        assertFalse(firstIndex.optionsAt(5,5,5).inheritedColor().enabled());
        assertEquals(SelectiveRenderSettings.InteractionMode.NONE,firstIndex.optionsAt(5,5,5).interactions());
        var color=new RegionOptions.ColorLayer(true,0x123456,50,null,false);
        var secondIndex=RegionPolicyIndex.of(regions,Map.of("first",interaction.withColor(color),"second",legacy));
        assertEquals(color,secondIndex.optionsAt(5,5,5).inheritedColor());
        assertTrue(secondIndex.optionsAt(5,5,5).inheritedTexture().enabled());
    }
    @Test void cachedQueriesStayIsolatedAcrossCoordinatesSnapshotsAndThreads() throws Exception {
        var regions=new LinkedHashMap<String,List<BlockRegion>>();
        regions.put("first",List.of(BOX)); regions.put("second",List.of(BOX));
        var first=RegionOptions.DEFAULT.with(5,true);
        var second=RegionOptions.DEFAULT.with(2,false);
        var index=RegionPolicyIndex.of(regions,Map.of("first",first,"second",second));
        var merged=index.optionsAt(5,5,5);
        assertTrue(merged.hiddenInteractions()); assertFalse(merged.cullFluids());
        assertSame(merged,index.optionsAt(5,5,5));
        assertSame(RegionOptions.DEFAULT,index.optionsAt(11,5,5));
        var changed=RegionPolicyIndex.of(regions,Map.of("first",first.with(5,false),"second",second));
        assertFalse(changed.optionsAt(5,5,5).hiddenInteractions());
        assertTrue(index.optionsAt(5,5,5).hiddenInteractions());
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try { assertTrue(executor.submit(()->index.optionsAt(5,5,5).hiddenInteractions()).get()); }
        finally { executor.shutdownNow(); }
    }
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
