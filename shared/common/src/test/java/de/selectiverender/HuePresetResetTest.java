package de.selectiverender;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HuePresetResetTest {
    @Test void firstHueAfterPresetResetsOnce() {
        var reset = new HuePresetReset(0x336699, false);
        reset.preset();
        assertTrue(reset.hue());
        assertFalse(reset.hue());
    }

    @Test void manualSliderChangeDisarms() {
        var reset = new HuePresetReset(0xFFFFFF, false);
        reset.manual();
        assertFalse(reset.hue());
    }

    @Test void savedPresetColorsStartArmed() {
        assertTrue(new HuePresetReset(0xFFFFFF, false).hue());
        assertTrue(new HuePresetReset(0, false).hue());
        assertTrue(new HuePresetReset(0x123456, true).hue());
        assertFalse(new HuePresetReset(0x123456, false).hue());
    }
}
