package de.selectiverender;

/** Renderer-independent color/opacity math. */
public final class BoundaryStyle {
    private BoundaryStyle() { }
    public static int opacity(int value) { return Math.max(0, Math.min(100, Math.round(value / 10f) * 10)); }
    public static int alpha(int percent) { return Math.round(opacity(percent) * 2.55f); }
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
