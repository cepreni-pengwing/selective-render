package de.selectiverender;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuNavigationStateTest {
    @Test void navigationSurvivesSerializationAndStaysSeparatePerWorld() {
        var state = new MenuNavigationState();
        state.tab = "REGIONS";
        state.scrolls.put("GENERAL", 3);
        var world = state.region("world-a");
        world.dimension = "minecraft:the_nether";
        world.selected = "build";
        world.collapsed.add("minecraft:overworld");
        world.scroll = 7; world.navigationScroll = 2;
        world.filtersOpen = true; world.boxesOpen = true;
        var gson = new Gson();
        var restored = gson.fromJson(gson.toJson(state), MenuNavigationState.class);
        restored.normalize();
        assertEquals("REGIONS", restored.tab);
        assertEquals(3, restored.scroll("GENERAL"));
        var view = restored.region("world-a");
        assertEquals("minecraft:the_nether", view.dimension);
        assertEquals("build", view.selected);
        assertTrue(view.collapsed.contains("minecraft:overworld"));
        assertEquals(7, view.scroll); assertEquals(2, view.navigationScroll);
        assertTrue(view.filtersOpen); assertTrue(view.boxesOpen);
        assertTrue(restored.region("world-b").collapsed.isEmpty());
        assertNull(restored.region("world-b").selected);
    }

    @Test void missingOrMalformedOptionalNavigationDoesNotBreakOldSettings() {
        var gson = new Gson();
        var state = gson.fromJson("{\"tab\":null,\"scrolls\":null,\"regions\":null}", MenuNavigationState.class);
        state.normalize();
        assertEquals("GENERAL", state.tab);
        assertEquals(0, state.scroll("GENERAL"));
        state.scrolls.put("GENERAL", -3);
        assertEquals(0, state.scroll("GENERAL"));
        var legacy = gson.fromJson("{\"regions\":{\"world\":{\"collapsed\":null},\"missing\":null}}", MenuNavigationState.class);
        legacy.normalize();
        assertTrue(legacy.region("world").collapsed.isEmpty());
        assertFalse(legacy.regions.containsKey("missing"));
    }
}
