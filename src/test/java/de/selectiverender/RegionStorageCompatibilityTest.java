package de.selectiverender;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegionStorageCompatibilityTest {
    @Test void renamedFieldsRetainExistingFileKeysAndSelections() throws ReflectiveOperationException {
        Class<?> type = Class.forName("de.selectiverender.SelectiveRenderConfig$StoredConfig");
        Gson gson = new Gson();
        String json = """
                {"formatVersion":8,"presets":{"old":{"minX":1,"maxX":2}},
                 "activePreset":"old","activePresets":["old"],
                 "hiddenPresets":["hidden"],"activeHiddenPresets":["hidden"],
                 "regionGroups":{},"blockFilters":{},"enabled":true,"hideEnabled":false}
                """;
        Object loaded = gson.fromJson(json, type);
        String[][] names = {{"regions", "presets"}, {"activeRegion", "activePreset"},
                {"activeRegions", "activePresets"}, {"hiddenRegions", "hiddenPresets"},
                {"activeHiddenRegions", "activeHiddenPresets"}};
        var saved = JsonParser.parseString(gson.toJson(loaded)).getAsJsonObject();
        var original = JsonParser.parseString(json).getAsJsonObject();
        for (String[] pair : names) {
            var field = type.getDeclaredField(pair[0]);
            field.setAccessible(true);
            assertNotNull(field.get(loaded));
            assertTrue(saved.has(pair[1]));
            assertFalse(saved.has(pair[0]));
            if (!pair[0].equals("regions")) assertEquals(original.get(pair[1]), saved.get(pair[1]));
        }
        assertEquals(8, saved.get("formatVersion").getAsInt());
        assertTrue(saved.get("enabled").getAsBoolean());
        assertFalse(saved.get("hideEnabled").getAsBoolean());
    }
}
