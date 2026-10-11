package de.selectiverender;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FluidSettingMigrationTest {
    @Test void acceptsOldWaterToggleAndWritesTheGeneralFluidToggle() throws ReflectiveOperationException {
        Class<?> type = Class.forName("de.selectiverender.SelectiveRenderSettings$StoredSettings");
        var field = type.getDeclaredField("cullFluidBoundaryFaces");
        field.setAccessible(true);
        Gson gson = new Gson();
        Object legacy = gson.fromJson("{\"cullWaterBoundaryFaces\":true}", type);
        assertTrue(field.getBoolean(legacy));
        String saved = gson.toJson(legacy);
        assertTrue(saved.contains("\"cullFluidBoundaryFaces\":true"));
        assertFalse(saved.contains("cullWaterBoundaryFaces"));
        Object current = gson.fromJson("{\"cullFluidBoundaryFaces\":false}", type);
        assertFalse(field.getBoolean(current));
    }
}
