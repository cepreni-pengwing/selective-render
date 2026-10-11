package de.selectiverender;

import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegionOptionsTest {
    @Test void oldRegionalSpatialChoicesMigrateWithoutChangingGlobalModes() {
        assertEquals(SelectiveRenderSettings.InteractionMode.EVERYWHERE,
                RegionOptions.DEFAULT.with(3, SelectiveRenderSettings.InteractionMode.INSIDE).interactions());
        assertEquals(SelectiveRenderSettings.InteractionMode.NONE,
                RegionOptions.DEFAULT.with(3, SelectiveRenderSettings.InteractionMode.OUTSIDE).interactions());
        Gson gson = new Gson();
        assertEquals(SelectiveRenderSettings.InteractionMode.EVERYWHERE,
                gson.fromJson("{\"interactions\":\"INSIDE\"}", RegionOptions.class).interactions());
        assertEquals(4, SelectiveRenderSettings.InteractionMode.values().length);
    }
    @Test void defaultsInheritAndDoNotIntroducePolicies() {
        assertTrue(RegionOptions.DEFAULT.filtersActive());
        assertFalse(RegionOptions.DEFAULT.hasOverrides());
        assertFalse(RegionOptions.DEFAULT.with(0, false).filtersActive());
        assertFalse(RegionOptions.DEFAULT.with(0, false).hasOverrides());
    }
    @Test void independentValuesRoundTripIncludingExplicitFalse() {
        var options = RegionOptions.DEFAULT.with(0, false)
                .with(1, SelectiveRenderSettings.BoundaryMode.NORMAL).with(2, false)
                .with(3, SelectiveRenderSettings.InteractionMode.EVERYWHERE).with(4, false).with(5, true);
        Gson gson = new Gson();
        assertEquals(options, gson.fromJson(gson.toJson(options), RegionOptions.class));
        assertNull(options.with(1, null).boundary());
        assertEquals(options.interactions(), options.with(1, null).interactions());
        assertTrue(options.hasOverrides());
    }
    @Test void renameAndFilterEditsPreserveOverridesAndRules() {
        var box = new BlockRegion(0, 10, 0, 10, 0, 10);
        var rule = new BlockFilterRule(BlockFilterRule.Mode.HIDE, BlockFilterRule.Kind.ID, "minecraft:stone");
        var options = RegionOptions.DEFAULT.with(0, false).with(1, SelectiveRenderSettings.BoundaryMode.BLACK);
        var original = new RegionDocument("minecraft:overworld", Map.of("old", List.of(box)),
                Set.of(), Set.of(), Set.of(), Map.of("old", List.of(rule)), false, false, Map.of("old", options));
        var edited = original.edit("old", "new", List.of(box), true, false, List.of(rule));
        assertEquals(options, edited.options().get("new"));
        assertFalse(edited.options().containsKey("old"));
        assertEquals(List.of(rule), edited.filters().get("new"));
        assertEquals(options, original.options().get("old"));
        assertThrows(UnsupportedOperationException.class, () -> edited.options().clear());
    }
    @Test void storedRegionPoliciesPreserveExplicitValues() throws Exception {
        Class<?> type = Class.forName("de.selectiverender.SelectiveRenderConfig$StoredConfig");
        Gson gson = new Gson();
        String json = "{\"formatVersion\":8,\"regionOptions\":{\"test\":{\"filtersEnabled\":false,\"boundary\":\"NORMAL\",\"hiddenInteractions\":true}}}";
        var saved = com.google.gson.JsonParser.parseString(gson.toJson(gson.fromJson(json, type))).getAsJsonObject();
        var options = saved.getAsJsonObject("regionOptions").getAsJsonObject("test");
        assertFalse(options.get("filtersEnabled").getAsBoolean());
        assertEquals("NORMAL", options.get("boundary").getAsString());
        assertTrue(options.get("hiddenInteractions").getAsBoolean());
        assertFalse(options.has("interactions"));
    }
    @Test void plotMinimumAndMaximumPersistIndependently() throws Exception {
        Class<?> type = Class.forName("de.selectiverender.SelectiveRenderSettings$StoredSettings");
        Gson gson = new Gson();
        Object value = gson.fromJson("{\"defaultPlotMinY\":-32,\"defaultPlotMaxY\":256}", type);
        var saved = com.google.gson.JsonParser.parseString(gson.toJson(value)).getAsJsonObject();
        assertEquals(-32, saved.get("defaultPlotMinY").getAsInt());
        assertEquals(256, saved.get("defaultPlotMaxY").getAsInt());
    }
    @Test void emptyInputSuggestsBothIdsAndTags() {
        assertEquals(List.of("#minecraft:slabs", "minecraft:stone"),
                FilterCompletion.matches("", List.of("minecraft:stone", "#minecraft:slabs")));
    }
}
