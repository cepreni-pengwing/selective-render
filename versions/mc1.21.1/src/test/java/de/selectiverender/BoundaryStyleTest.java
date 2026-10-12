package de.selectiverender;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BoundaryStyleTest {
    @Test void opacitySupportsEveryPercentWithExactEndpoints() {
        assertEquals(0, BoundaryStyle.opacity(-100)); assertEquals(100, BoundaryStyle.opacity(900));
        assertEquals(26, BoundaryStyle.opacity(26)); assertEquals(24, BoundaryStyle.opacity(24));
        assertEquals(0, BoundaryStyle.alpha(0)); assertEquals(255, BoundaryStyle.alpha(100));
        assertEquals(128, BoundaryStyle.alpha(50));
        for (int value = 0; value <= 100; value++) assertEquals(value, BoundaryStyle.opacity(value));
    }
    @Test void wheelIncludesBlackWhiteAndPrimaryColors() {
        assertEquals(0, BoundaryStyle.hsv(0, 1, 0)); assertEquals(0xFFFFFF, BoundaryStyle.hsv(0, 0, 1));
        assertEquals(0xFF0000, BoundaryStyle.hsv(0, 1, 1));
        assertEquals(0x00FF00, BoundaryStyle.hsv(1f / 3, 1, 1));
        assertEquals(0x0000FF, BoundaryStyle.hsv(2f / 3, 1, 1));
        for (int rgb : new int[]{0, 0xFFFFFF, 0x123456, 0xFF0044, 0x00FF00}) {
            var hsv = BoundaryStyle.hsv(rgb); assertEquals(rgb, BoundaryStyle.hsv(hsv[0], hsv[1], hsv[2]));
        }
    }
    @Test void legacyBlackAndColoredSettingsMigrateWithoutLosingRegions() {
        var gson = new Gson();
        for (String value : new String[]{"BLACK", "COLORED", "COLOR"}) assertEquals(SelectiveRenderSettings.BoundaryMode.COLOR,
                gson.fromJson("\"" + value + "\"", SelectiveRenderSettings.BoundaryMode.class));
        assertEquals(SelectiveRenderSettings.BoundaryMode.TEXTURE,
                gson.fromJson("\"TEXTURE\"", SelectiveRenderSettings.BoundaryMode.class));
    }
}
