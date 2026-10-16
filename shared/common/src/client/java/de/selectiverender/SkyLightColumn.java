package de.selectiverender;

public final class SkyLightColumn {
    private SkyLightColumn() { }

    public static int passDown(int light, int opacity) {
        if (light <= 0) return 0;
        int clampedOpacity = Math.min(15, Math.max(0, opacity));
        if (light == 15 && clampedOpacity == 0) return 15;
        return Math.max(0, light - Math.max(1, clampedOpacity));
    }
}
