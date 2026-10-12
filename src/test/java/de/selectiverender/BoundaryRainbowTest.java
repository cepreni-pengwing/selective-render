package de.selectiverender;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BoundaryRainbowTest {
    @Test void hueCoversTheWholePerimeterAndIsContinuousAtCorners() {
        var box = new BlockRegion(0,9,0,9,0,9);
        assertEquals(0,BoundaryRainbow.hue(box,0,0),.00001);
        assertEquals(.25,BoundaryRainbow.hue(box,10,0),.00001);
        assertEquals(.5,BoundaryRainbow.hue(box,10,10),.00001);
        assertEquals(.75,BoundaryRainbow.hue(box,0,10),.00001);
        assertEquals(.125,BoundaryRainbow.hue(box,5,0),.00001);
        assertEquals(.875,BoundaryRainbow.hue(box,0,5),.00001);
    }
    @Test void rectangularPlotsUsePhysicalPerimeterNotEqualSideFractions() {
        var box = new BlockRegion(-20,19,-64,319,-5,4);
        assertEquals(.4,BoundaryRainbow.hue(box,20,-5),.00001);
        assertEquals(.5,BoundaryRainbow.hue(box,20,5),.00001);
        assertEquals(.9,BoundaryRainbow.hue(box,-20,5),.00001);
    }
    @Test void previewIsOpaqueInThePreviouslyBrokenBlueRange() {
        for(int hue=240;hue<=270;hue++) for(int opacity=0;opacity<=100;opacity+=5)
            assertEquals(255,BoundaryStyle.previewPixel(100,7,hue/360f,1,1,opacity,false)>>>24);
        assertEquals(0xFFFF0000,BoundaryStyle.previewPixel(0,0,0,1,1,100,true));
        assertEquals(0xFFFF0000,BoundaryStyle.previewPixel(255,0,0,1,1,100,true));
    }
    @Test void neutralShaderMetadataIsScopedToColorLayer() {
        BoundaryShaderContext.begin(SelectiveRenderSettings.BoundaryMode.COLOR);
        try { assertTrue(BoundaryShaderContext.active()); assertEquals(-1,BoundaryShaderContext.id()); assertEquals(15,BoundaryShaderContext.emission()); }
        finally { BoundaryShaderContext.end(); }
        assertFalse(BoundaryShaderContext.active());
    }
    public static class Holder { public short blockId=55; public byte lightValue=3; public boolean ignoreMidBlock=false; }
    public static class Encoder { private final Holder contextHolder=new Holder(); }
    @Test void legacyIrisMetadataIsRestoredAfterEachBoundaryQuad() {
        var encoder = new Encoder(); BoundaryShaderContext.begin(SelectiveRenderSettings.BoundaryMode.COLOR);
        try { var saved=BoundaryShaderContext.legacyBegin(encoder); assertNotNull(saved);
            assertEquals(-1,encoder.contextHolder.blockId); assertEquals(15,encoder.contextHolder.lightValue); assertTrue(encoder.contextHolder.ignoreMidBlock);
            BoundaryShaderContext.legacyEnd(saved); assertEquals(55,encoder.contextHolder.blockId); assertEquals(3,encoder.contextHolder.lightValue); assertFalse(encoder.contextHolder.ignoreMidBlock);
        } finally { BoundaryShaderContext.end(); }
    }
}
