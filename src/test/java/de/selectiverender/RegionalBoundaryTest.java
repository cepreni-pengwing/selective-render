package de.selectiverender;
import com.google.gson.Gson;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RegionalBoundaryTest {
    @Test void textureAndColorInheritanceAreIndependentAndSerializable() {
        var original=RegionOptions.DEFAULT.withTexture(new RegionOptions.TextureLayer(true,"minecraft:dirt"))
                .withColor(new RegionOptions.ColorLayer(true,0xFF0000,35,false,true));
        var copy=new Gson().fromJson(new Gson().toJson(original),RegionOptions.class);
        assertEquals(original,copy);
        assertEquals(SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE,copy.effectiveBoundary(SelectiveRenderSettings.BoundaryMode.NORMAL));
        assertNull(copy.withTexture(null).inheritedTexture()); assertEquals(copy.colorLayer(),copy.withTexture(null).colorLayer());
        assertNull(copy.withColor(null).inheritedColor()); assertEquals(copy.textureLayer(),copy.withColor(null).textureLayer());
    }
    @Test void legacyLayerModeCanResetOneLayerWithoutChangingTheOther() {
        var legacy=RegionOptions.DEFAULT.with(1,SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE);
        var reset=legacy.withTexture(null);
        assertNull(reset.inheritedTexture()); assertTrue(reset.inheritedColor().enabled());
        assertEquals(SelectiveRenderSettings.BoundaryMode.COLOR,reset.effectiveBoundary(SelectiveRenderSettings.BoundaryMode.NORMAL));
    }
    @Test void editorOnlyWritesTheRequestedLayerAndNeverGlobalDefaults() {
        int globalColor=SelectiveRenderSettings.boundaryColor(); String globalTexture=SelectiveRenderSettings.boundaryTexture();
        var value=new AtomicReference<>(RegionOptions.DEFAULT);
        var target=new BoundaryEditorTarget(value::get,value::set);
        target.commit(0x112233,"minecraft:dirt",35,true,true,false);
        assertEquals(0x112233,value.get().colorLayer().rgb()); assertNull(value.get().textureLayer());
        target.commit(0x112233,"minecraft:dirt",35,true,false,true);
        assertEquals("minecraft:dirt",value.get().textureLayer().id());
        target.inheritColor(); assertNull(value.get().colorLayer()); assertNotNull(value.get().textureLayer());
        assertEquals(globalColor,SelectiveRenderSettings.boundaryColor()); assertEquals(globalTexture,SelectiveRenderSettings.boundaryTexture());
    }
    @Test void regionalAppearancesAreSpatialAndCannotBleedIntoNeighbors() {
        var box=new BlockRegion(0,9,0,9,0,9);
        var options=RegionOptions.DEFAULT.withColor(new RegionOptions.ColorLayer(true,0x123456,50,true,false));
        var index=RegionPolicyIndex.of(Map.of("test",List.of(box)),Map.of("test",options));
        assertEquals(options,index.optionsAt(5,5,5)); assertSame(RegionOptions.DEFAULT,index.optionsAt(10,5,5));
        assertTrue(index.blackOverride); assertEquals(box,index.rainbowBoxAt(5,5,5));
    }
}
