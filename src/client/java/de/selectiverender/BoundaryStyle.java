package de.selectiverender;

/** Renderer-independent color/opacity math. */
public final class BoundaryStyle {
    private BoundaryStyle() { }
    public static int opacity(int value) { return Math.max(0, Math.min(100, value)); }
    public static int alpha(int percent) { return Math.round(opacity(percent) * 2.55f); }
    public static boolean color(SelectiveRenderSettings.BoundaryMode mode) {
        return mode == SelectiveRenderSettings.BoundaryMode.COLOR || mode == SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE;
    }
    public static boolean texture(SelectiveRenderSettings.BoundaryMode mode) {
        return mode == SelectiveRenderSettings.BoundaryMode.TEXTURE || mode == SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE;
    }
    public static SelectiveRenderSettings.BoundaryMode layers(boolean color, boolean texture) {
        return color ? (texture ? SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE : SelectiveRenderSettings.BoundaryMode.COLOR)
                : (texture ? SelectiveRenderSettings.BoundaryMode.TEXTURE : SelectiveRenderSettings.BoundaryMode.NORMAL);
    }
    /** Straight-alpha color-over-texture composition; each enabled layer uses the shared opacity. */
    public static int compose(int source, int rgb, int percent, SelectiveRenderSettings.BoundaryMode mode) {
        if (mode == SelectiveRenderSettings.BoundaryMode.NORMAL) return source;
        if (mode == SelectiveRenderSettings.BoundaryMode.CULLED) return 0;
        int a = alpha(percent);
        int bottomAlpha = texture(mode) ? ((source >>> 24) * a + 127) / 255 : 0;
        if (!color(mode)) return source & 0xFFFFFF | bottomAlpha << 24;
        if (a == 0) return 0;
        double bottom = bottomAlpha / 255d * (1 - a / 255d), top = a / 255d, total = top + bottom;
        int result = (int)Math.round(total * 255) << 24;
        for (int shift = 0; shift <= 16; shift += 8) result |= (int)Math.round((((rgb >> shift) & 255) * top
                + ((source >> shift) & 255) * bottom) / total) << shift;
        return result;
    }
    /** ARGB <-> NativeImage's legacy ABGR representation (symmetric). */
    public static int swapRedBlue(int value) { return value & 0xFF00FF00 | (value >> 16 & 255) | (value & 255) << 16; }
    public static int hsv(float hue, float saturation, float value) {
        hue = hue - (float)Math.floor(hue);
        saturation = Math.max(0, Math.min(1, saturation)); value = Math.max(0, Math.min(1, value));
        float h = hue * 6, f = h - (float)Math.floor(h), p = value * (1 - saturation);
        float q = value * (1 - saturation * f), t = value * (1 - saturation * (1 - f));
        float r, g, b;
        switch ((int)h) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }
        return Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }
    public static float[] hsv(int rgb) {
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float hue = d == 0 ? 0 : max == r ? ((g - b) / d + 6) % 6 : max == g ? (b - r) / d + 2 : (r - g) / d + 4;
        return new float[]{hue / 6, max == 0 ? 0 : d / max, max};
    }
}
