package de.selectiverender;
import com.google.gson.Gson;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RegionalBoundaryTest {
    @Test void disabledLayersStayDisabledAcrossAppearanceAndInheritanceChanges() throws Exception {
        var field=SelectiveRenderSettings.class.getDeclaredField("boundaryMode"); field.setAccessible(true);
        var previous=field.get(null);
        var value=new AtomicReference<>(RegionOptions.DEFAULT);
        var target=new BoundaryEditorTarget(value::get,value::set);
        try {
            field.set(null,SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE);
            assertTrue(target.colorEnabled()); assertTrue(target.textureEnabled());
            target.setColorEnabled(false);
            assertFalse(target.colorEnabled()); assertTrue(target.textureEnabled());
            target.setNormal(false); target.setLighting(true);
            assertFalse(target.colorEnabled());
            target.setTextureEnabled(false); assertFalse(target.textureEnabled());
            target.inheritColor(); assertTrue(target.colorEnabled()); assertFalse(target.textureEnabled());
            field.set(null,SelectiveRenderSettings.BoundaryMode.NORMAL);
            assertFalse(target.colorEnabled());
            target.setColorEnabled(true); assertTrue(target.colorEnabled());
            target.inheritTexture(); assertFalse(target.textureEnabled());
            target.inheritColor(); assertFalse(target.colorEnabled());
        } finally { field.set(null,previous); }
    }
    @Test void globalLightingReachesEveryInheritedRegionButKeepsExplicitOverrides() throws Exception {
        var field=SelectiveRenderSettings.class.getDeclaredField("boundaryColorLighting"); field.setAccessible(true);
        boolean previous=field.getBoolean(null);
        var first=new AtomicReference<>(RegionOptions.DEFAULT);
        var second=new AtomicReference<>(RegionOptions.DEFAULT);
        var a=new BoundaryEditorTarget(first::get,first::set);
        var b=new BoundaryEditorTarget(second::get,second::set);
        try {
            field.setBoolean(null,false);
            a.setColorEnabled(true); b.setColorEnabled(true);
            a.commit(0x123456,"minecraft:stone",60,false,true,false);
            field.setBoolean(null,true);
            assertTrue(a.lighting()); assertTrue(b.lighting());
            a.setLighting(false); assertFalse(a.lighting()); assertTrue(b.lighting());
            field.setBoolean(null,false); assertFalse(a.lighting()); assertFalse(b.lighting());
            a.inheritLighting(); field.setBoolean(null,true);
            assertTrue(a.lighting()); assertTrue(b.lighting());
        } finally { field.setBoolean(null,previous); }
    }
    @Test void editingColorDoesNotSilentlyPinGlobalLighting() {
        var value=new AtomicReference<>(RegionOptions.DEFAULT);
        var target=new BoundaryEditorTarget(value::get,value::set);
        target.setColorEnabled(true);
        assertNull(value.get().colorLayer().lighting());
        target.commit(0x112233,"minecraft:stone",50,false,true,false);
        assertNull(value.get().colorLayer().lighting());
        target.setLighting(true);
        target.setColorEnabled(false); target.setColorEnabled(true);
        assertEquals(Boolean.TRUE,value.get().colorLayer().lighting());
        target.inheritLighting();
        assertNull(value.get().colorLayer().lighting());
        assertEquals(0x112233,value.get().colorLayer().rgb());
    }
    @Test void legacyNormalAndCulledChoicesCanChangeAndResetTheirUnderlay() {
        var legacyNormal=RegionOptions.DEFAULT.with(1,SelectiveRenderSettings.BoundaryMode.NORMAL);
        assertEquals(Boolean.TRUE,legacyNormal.inheritedNormal());
        var off=legacyNormal.withNormal(false);
        var box=new BlockRegion(0,9,0,9,0,9);
        assertEquals(SelectiveRenderSettings.BoundaryMode.CULLED,
                RegionPolicyIndex.of(Map.of("test",List.of(box)),Map.of("test",off)).boundaryOverrideAt(5,5,5));
        var culled=RegionOptions.DEFAULT.with(1,SelectiveRenderSettings.BoundaryMode.CULLED);
        assertEquals(Boolean.FALSE,culled.withTexture(new RegionOptions.TextureLayer(true,"minecraft:stone")).inheritedNormal());
        assertNull(culled.withNormal(null).inheritedNormal());
        assertEquals(0,culled.withNormal(null).inheritedColor().opacity());
    }
    @Test void normalInheritanceSurvivesLayerEditsAndSerialization() {
        var local=RegionOptions.DEFAULT.withNormal(false).withColor(new RegionOptions.ColorLayer(true,0xFFFFFF,25,null,false));
        var copy=new Gson().fromJson(new Gson().toJson(local),RegionOptions.class);
        assertEquals(local,copy);
        assertEquals(Boolean.FALSE,copy.withColor(null).normalFaces());
        assertEquals(SelectiveRenderSettings.BoundaryMode.CULLED,copy.withColor(null).withTexture(new RegionOptions.TextureLayer(false,null)).effectiveBoundary(SelectiveRenderSettings.BoundaryMode.NORMAL));
        var box=new BlockRegion(0,9,0,9,0,9);
        var index=RegionPolicyIndex.of(Map.of("test",List.of(box)),Map.of("test",local));
        assertEquals(Boolean.FALSE,index.optionsAt(5,5,5).normalFaces());
        assertNull(index.optionsAt(20,5,5).normalFaces());
    }
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
    @Test void overlappingPoliciesMergeNormalWithoutOverwritingFirstColorChoice() {
        var box=new BlockRegion(0,9,0,9,0,9);
        var map=new java.util.LinkedHashMap<String,List<BlockRegion>>();
        map.put("color",List.of(box)); map.put("normal",List.of(box));
        var color=RegionOptions.DEFAULT.withColor(new RegionOptions.ColorLayer(true,0x123456,50,null,false));
        var normal=RegionOptions.DEFAULT.with(1,SelectiveRenderSettings.BoundaryMode.NORMAL);
        var index=RegionPolicyIndex.of(map,Map.of("color",color,"normal",normal));
        assertEquals(Boolean.TRUE,index.optionsAt(5,5,5).inheritedNormal());
        assertEquals(color.colorLayer(),index.optionsAt(5,5,5).colorLayer());
    }
    @Test void regionalAppearancesAreSpatialAndCannotBleedIntoNeighbors() {
        var box=new BlockRegion(0,9,0,9,0,9);
        var options=RegionOptions.DEFAULT.withColor(new RegionOptions.ColorLayer(true,0x123456,50,true,false));
        var index=RegionPolicyIndex.of(Map.of("test",List.of(box)),Map.of("test",options));
        assertEquals(options,index.optionsAt(5,5,5)); assertSame(RegionOptions.DEFAULT,index.optionsAt(10,5,5));
        assertTrue(index.blackOverride); assertEquals(box,index.rainbowBoxAt(5,5,5));
    }
}
