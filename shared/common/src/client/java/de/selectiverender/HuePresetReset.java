package de.selectiverender;

final class HuePresetReset {
    private boolean armed;

    HuePresetReset(int color, boolean rainbow) { armed = rainbow || isPreset(color); }

    static boolean isPreset(int color) {
        int rgb = color & 0xFFFFFF;
        return rgb == 0 || rgb == 0xFFFFFF;
    }

    void preset() { armed = true; }
    void manual() { armed = false; }

    boolean hue() {
        boolean reset = armed;
        armed = false;
        return reset;
    }
}
