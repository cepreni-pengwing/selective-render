package de.selectiverender;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BoundaryLayersTest {
    @Test void grassOverlayCannotEscapeAnOpaqueColorOrReplacementTexture() {
        for(var mode:SelectiveRenderSettings.BoundaryMode.values()) for(boolean normal:new boolean[]{false,true})
            for(int opacity:new int[]{0,25,50,75,100}) {
                boolean hasNormal=java.util.Arrays.asList(BoundaryStyle.renderLayers(mode,normal,opacity)).contains(SelectiveRenderSettings.BoundaryMode.NORMAL);
                if(mode!=SelectiveRenderSettings.BoundaryMode.NORMAL)
                    assertEquals(hasNormal,BoundaryStyle.nativeOverlay(mode,normal,opacity),mode+" / "+opacity);
            }
    }

    @Test void opaqueColorDoesNotEmitAnInvisibleCompetingUnderlay() {
        var color=SelectiveRenderSettings.BoundaryMode.COLOR;
        var texture=SelectiveRenderSettings.BoundaryMode.TEXTURE;
        var normal=SelectiveRenderSettings.BoundaryMode.NORMAL;
        var both=SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE;
        assertArrayEquals(new SelectiveRenderSettings.BoundaryMode[]{color},BoundaryStyle.renderLayers(both,true,100));
        assertArrayEquals(new SelectiveRenderSettings.BoundaryMode[]{color},BoundaryStyle.renderLayers(color,true,100));
        assertArrayEquals(new SelectiveRenderSettings.BoundaryMode[]{texture},BoundaryStyle.renderLayers(both,true,0));
        assertArrayEquals(new SelectiveRenderSettings.BoundaryMode[]{normal},BoundaryStyle.renderLayers(color,true,0));
        assertEquals(0,BoundaryStyle.renderLayers(color,false,0).length);
        assertArrayEquals(new SelectiveRenderSettings.BoundaryMode[]{texture,color},BoundaryStyle.renderLayers(both,true,50));
    }
    @Test void normalUnderlayIsOnlyEmittedWhenColorHasNoReplacementTexture() {
        var normal=SelectiveRenderSettings.BoundaryMode.NORMAL;
        var color=SelectiveRenderSettings.BoundaryMode.COLOR;
        var texture=SelectiveRenderSettings.BoundaryMode.TEXTURE;
        assertArrayEquals(new SelectiveRenderSettings.BoundaryMode[]{normal,color},BoundaryStyle.renderLayers(color,true));
        assertArrayEquals(new SelectiveRenderSettings.BoundaryMode[]{color},BoundaryStyle.renderLayers(color,false));
        for(boolean underlay:new boolean[]{false,true}) {
            assertArrayEquals(new SelectiveRenderSettings.BoundaryMode[]{texture,color},BoundaryStyle.renderLayers(SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE,underlay));
            assertArrayEquals(new SelectiveRenderSettings.BoundaryMode[]{texture},BoundaryStyle.renderLayers(texture,underlay));
        }
    }
    @Test void independentLayersCoverEveryCombination() {
        for (boolean color : new boolean[]{false, true}) for (boolean texture : new boolean[]{false, true}) {
            var mode = BoundaryStyle.layers(color, texture);
            assertEquals(color, BoundaryStyle.color(mode)); assertEquals(texture, BoundaryStyle.texture(mode));
        }
        assertEquals(SelectiveRenderSettings.BoundaryMode.NORMAL, BoundaryStyle.layers(false, false));
    }
    @Test void colorOverTextureUsesStraightAlphaWithExactEndpoints() {
        var both = BoundaryStyle.layers(true, true);
        assertEquals(0xFF0000FF, BoundaryStyle.compose(0xFF0000FF, 0xFF0000, 0, both));
        assertEquals(0xFFFF0000, BoundaryStyle.compose(0xFF0000FF, 0xFF0000, 100, both));
        assertEquals(0xFF80007F, BoundaryStyle.compose(0xFF0000FF, 0xFF0000, 50, both));
        assertEquals(0x80FF0000, BoundaryStyle.compose(0x000000FF, 0xFF0000, 50, both));
        assertEquals(0xFF123456, BoundaryStyle.compose(0xFF123456, 0, 50, BoundaryStyle.layers(false, true)));
        assertEquals(0xFF123456, BoundaryStyle.compose(0xFF123456, 0, 0, BoundaryStyle.layers(false, false)));
    }
    @Test void layersKeepIdenticalPositionsAtEveryViewingDistance() {
        assertEquals(0,BoundaryStyle.overlayOffset(SelectiveRenderSettings.BoundaryMode.COLOR));

        assertEquals(0,BoundaryStyle.overlayOffset(SelectiveRenderSettings.BoundaryMode.NORMAL));
        assertEquals(0,BoundaryStyle.overlayOffset(SelectiveRenderSettings.BoundaryMode.TEXTURE));
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
    @Test void sliderTexturesRemainOpaqueAcrossTheBlueHueRange() {
        for (int hue = 230; hue <= 275; hue++) for (int kind = 0; kind < 4; kind++) for (int x = 0; x < 256; x++) {
            int pixel = BoundaryStyle.sliderPixel(kind, x, 7, hue / 360f, 1, 1);
            assertEquals(255, pixel >>> 24);
        }
        assertEquals(0xFF000000, BoundaryStyle.sliderPixel(2, 0, 0, 265 / 360f, 1, 1));
        assertEquals(0xFF000000 | BoundaryStyle.hsv(265 / 360f, 1, 1), BoundaryStyle.sliderPixel(2, 255, 0, 265 / 360f, 1, 1));
    }
    @Test void textureAndColorAreSeparateDrawLayersInStableOrder() {
        assertArrayEquals(new SelectiveRenderSettings.BoundaryMode[]{SelectiveRenderSettings.BoundaryMode.TEXTURE,
                SelectiveRenderSettings.BoundaryMode.COLOR}, BoundaryStyle.renderLayers(SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE,true,50));
        assertFalse(BoundaryStyle.fullbright(SelectiveRenderSettings.BoundaryMode.TEXTURE));
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
    @Test void distinctPlanesAreNeverMergedByAnArbitraryDistanceTolerance() {
        var tracker = new BoundaryQuadTracker();
        face(tracker, 1, false); assertTrue(tracker.accept(2));
        face(tracker, 1.001f, false); assertTrue(tracker.accept(2));
        face(tracker, 1.01f, false); assertTrue(tracker.accept(2));
        assertEquals(0,BoundaryStyle.overlayOffset(SelectiveRenderSettings.BoundaryMode.COLOR));
    }
    @Test void differentAndNonFlatGeometryIsPreserved() {
        var tracker = new BoundaryQuadTracker(); face(tracker, 0, false); assertTrue(tracker.accept(2));
        tracker.vertex(0, .5f, 0, 0); assertTrue(tracker.accept(2));
        tracker.vertex(0, 0, 0, .5f); assertTrue(tracker.accept(2)); assertTrue(tracker.accept(2));
    }
}
