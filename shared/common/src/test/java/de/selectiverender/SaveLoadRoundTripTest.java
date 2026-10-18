package de.selectiverender;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SaveLoadRoundTripTest {
    private static final String SETTINGS = """
            {"formatVersion":5,"playerGroup":"SELF","playerScope":"INSIDE","interactionMode":"EVERYWHERE",
             "boundaryMode":"TEXTURE","boundaryColor":1193046,"boundaryOpacity":40,"boundaryColorLighting":true,
             "boundaryNormal":false,"boundaryRainbow":true,"boundaryTexture":"minecraft:dirt",
             "cullFluidBoundaryFaces":true,"worldEditSelection":false,"debugBoxes":true,
             "filterInteractionsWhenInactive":true,"blockFiltersEnabled":false,"interactWithHiddenRegions":true,
             "virtualLightMode":"TOP","fullReloadThreshold":1024,"defaultPlotMinY":-10,"defaultPlotMaxY":200,
             "plotGrids":{"server:test":{"x1":1,"z1":2,"x2":40,"z2":41,"roadWidth":7,"enabled":true}},
             "globalFilters":[{"mode":"HIDE","kind":"TAG","value":"minecraft:slabs"},{"mode":"ONLY","kind":"ID","value":"minecraft:stone"}],
             "globalFiltersActive":false,
             "menuNavigation":{"tab":"FILTERS","scrolls":{"FILTERS":3}}}
            """;

    @AfterEach void reset() { SelectiveRenderSettings.fromJson("{}"); }

    @Test void settingsSurviveSaveAndLoad() {
        SelectiveRenderSettings.fromJson(SETTINGS);
        String saved = SelectiveRenderSettings.toJson();
        SelectiveRenderSettings.fromJson("{}");
        SelectiveRenderSettings.fromJson(saved);
        assertEquals(JsonParser.parseString(saved), JsonParser.parseString(SelectiveRenderSettings.toJson()));
        assertEquals(PlayerRenderPolicy.Group.SELF, SelectiveRenderSettings.playerGroup());
        assertEquals(SelectiveRenderSettings.InteractionMode.EVERYWHERE, SelectiveRenderSettings.interactionMode());
        assertEquals("minecraft:dirt", SelectiveRenderSettings.boundaryTexture());
        assertEquals(40, SelectiveRenderSettings.boundaryOpacity());
        assertEquals(0x123456, SelectiveRenderSettings.boundaryColor());
        assertFalse(SelectiveRenderSettings.blockFiltersEnabled());
        assertEquals(1024, SelectiveRenderSettings.fullReloadThreshold());
        assertEquals(-10, SelectiveRenderSettings.defaultPlotMinY());
        assertEquals(200, SelectiveRenderSettings.defaultPlotMaxY());
        assertEquals(new PlotGrid(1, 2, 40, 41, 7, true), SelectiveRenderSettings.plotGrid("server:test"));
        assertEquals(List.of(new BlockFilterRule(BlockFilterRule.Mode.HIDE, BlockFilterRule.Kind.TAG, "minecraft:slabs"),
                new BlockFilterRule(BlockFilterRule.Mode.ONLY, BlockFilterRule.Kind.ID, "minecraft:stone")),
                SelectiveRenderSettings.globalFilters());
        assertFalse(SelectiveRenderSettings.globalFiltersActive());
        assertTrue(SelectiveRenderSettings.activeGlobalFilters().isEmpty());
        assertEquals("FILTERS", SelectiveRenderSettings.menuNavigation().tab);
        assertEquals(3, SelectiveRenderSettings.menuNavigation().scroll("FILTERS"));
    }

    @Test void everyStoredSettingIsWritten() throws ClassNotFoundException {
        SelectiveRenderSettings.fromJson(SETTINGS);
        var saved = JsonParser.parseString(SelectiveRenderSettings.toJson()).getAsJsonObject();
        for (var field : Class.forName("de.selectiverender.SelectiveRenderSettings$StoredSettings").getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
            assertTrue(saved.has(field.getName()), field.getName());
        }
    }

    @Test void invalidGlobalFiltersAreSkipped() {
        SelectiveRenderSettings.fromJson("{\"globalFilters\":[{\"mode\":\"HIDE\",\"kind\":\"ID\",\"value\":\"Not Valid\"},{\"mode\":\"HIDE\",\"kind\":\"ID\",\"value\":\"minecraft:dirt\"}]}");
        assertEquals(List.of(new BlockFilterRule(BlockFilterRule.Mode.HIDE, BlockFilterRule.Kind.ID, "minecraft:dirt")),
                SelectiveRenderSettings.globalFilters());
    }

    @Test void regionsFiltersAndOptionsSurviveSaveAndLoad() {
        var a1 = new BlockRegion(0, 10, -64, 100, 0, 10);
        var a2 = new BlockRegion(20, 30, 0, 50, 20, 30);
        var b = new BlockRegion(-5, 5, 0, 5, -5, 5);
        var document = new RegionDocument("minecraft:overworld",
                Map.of("a", List.of(a1, a2), "b", List.of(b)), Set.of("a"), Set.of("b"), Set.of("b"),
                Map.of("a", List.of(new BlockFilterRule(BlockFilterRule.Mode.HIDE, BlockFilterRule.Kind.ID, "minecraft:stone"),
                        new BlockFilterRule(BlockFilterRule.Mode.ONLY, BlockFilterRule.Kind.TAG, "minecraft:logs"))),
                true, false,
                Map.of("a", RegionOptions.DEFAULT.with(0, false),
                        "b", RegionOptions.DEFAULT.with(3, SelectiveRenderSettings.InteractionMode.NONE)));
        RegionDocument loaded = SelectiveRenderConfig.readDocument(SelectiveRenderConfig.writeDocument(document));
        assertEquals(document, loaded);
        assertEquals(document, SelectiveRenderConfig.readDocument(SelectiveRenderConfig.writeDocument(loaded)));
    }
}
