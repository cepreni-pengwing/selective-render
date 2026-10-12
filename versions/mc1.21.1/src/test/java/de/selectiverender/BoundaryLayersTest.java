package de.selectiverender;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BoundaryLayersTest {
    @Test void independentLayersCoverEveryCombination() {
        for (boolean color : new boolean[]{false, true}) for (boolean texture : new boolean[]{false, true}) {
            var mode = BoundaryStyle.layers(color, texture);
            assertEquals(color, BoundaryStyle.color(mode)); assertEquals(texture, BoundaryStyle.texture(mode));
        }
        assertEquals(SelectiveRenderSettings.BoundaryMode.NORMAL, BoundaryStyle.layers(false, false));
    }
    @Test void colorOverTextureUsesStraightAlphaWithExactEndpoints() {
        var both = BoundaryStyle.layers(true, true);
        assertEquals(0, BoundaryStyle.compose(0xFF0000FF, 0xFF0000, 0, both));
        assertEquals(0xFFFF0000, BoundaryStyle.compose(0xFF0000FF, 0xFF0000, 100, both));
        assertEquals(0xC0AA0055, BoundaryStyle.compose(0xFF0000FF, 0xFF0000, 50, both));
        assertEquals(0x80FF0000, BoundaryStyle.compose(0x000000FF, 0xFF0000, 50, both));
        assertEquals(0x80123456, BoundaryStyle.compose(0xFF123456, 0, 50, BoundaryStyle.layers(false, true)));
        assertEquals(0xFF123456, BoundaryStyle.compose(0xFF123456, 0, 0, BoundaryStyle.layers(false, false)));
    }
    @Test void legacyNativeImageColorOrderIsReversible() {
        for (int color : new int[]{0, -1, 0x800000FF, 0xFF123456})
            assertEquals(color, BoundaryStyle.swapRedBlue(BoundaryStyle.swapRedBlue(color)));
        assertEquals(0x80FF0000, BoundaryStyle.swapRedBlue(0x800000FF));
    }
    @Test void blueHueRangeDoesNotChangeBrightnessOrSkipColors() {
        int previous = BoundaryStyle.hsv(239 / 360f, 1, 1);
        for (int degrees = 240; degrees <= 261; degrees++) {
            int color = BoundaryStyle.hsv(degrees / 360f, 1, 1);
            assertEquals(255, Math.max(color >> 16 & 255, Math.max(color >> 8 & 255, color & 255)));
            for (int shift = 0; shift <= 16; shift += 8)
                assertTrue(Math.abs((color >> shift & 255) - (previous >> shift & 255)) <= 5);
            previous = color;
        }
    }
    private static void face(BoundaryQuadTracker tracker, float z, boolean reverse) {
        float[][] vertices = {{0,0,z}, {1,0,z}, {1,1,z}, {0,1,z}};
        for (int i = 0; i < 4; i++) { var v = vertices[reverse ? 3-i : i]; tracker.vertex(i,v[0],v[1],v[2]); }
    }
    @Test void tintOverlayCopiesAreEmittedOnceRegardlessOfVertexOrder() {
        var tracker = new BoundaryQuadTracker(); face(tracker, 0, false); assertTrue(tracker.accept(2));
        face(tracker, 0, true); assertFalse(tracker.accept(2));
        assertTrue(tracker.accept(3)); face(tracker, 1, false); assertTrue(tracker.accept(2));
        tracker.reset(); face(tracker, 0, false); assertTrue(tracker.accept(2));
    }
    @Test void differentAndNonFlatGeometryIsPreserved() {
        var tracker = new BoundaryQuadTracker(); face(tracker, 0, false); assertTrue(tracker.accept(2));
        tracker.vertex(0, .5f, 0, 0); assertTrue(tracker.accept(2));
        tracker.vertex(0, 0, 0, .5f); assertTrue(tracker.accept(2)); assertTrue(tracker.accept(2));
    }
}
