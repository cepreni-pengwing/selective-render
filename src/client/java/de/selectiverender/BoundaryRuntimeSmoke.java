package de.selectiverender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
/** Opt-in, disposable CI-world exercise. Disabled in all normal game sessions. */
final class BoundaryRuntimeSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("selectiverender.boundary.smoke");
    private static int ticks;
    private static final String NAME = "__sr_boundary_smoke__";
    private BoundaryRuntimeSmoke() { }
    static void tick(MinecraftClient client) {
        if (!ENABLED || client.world == null || client.player == null || ticks > 130) return;
        ticks++;
        if (ticks == 10) {
            BlockPos pos = client.player.getBlockPos();
            SelectiveRenderSettings.setVirtualLightMode(SelectiveRenderSettings.VirtualLightMode.NONE);
            if (!SelectiveRenderConfig.saveRegion(client, NAME, new BlockRegion(pos.getX() - 4, pos.getX() + 4,
                    pos.getY() - 8, pos.getY() + 8, pos.getZ() - 4, pos.getZ() + 4), false))
                throw new IllegalStateException("Boundary smoke region could not be created");
            SelectiveRenderSettings.setBoundaryAppearance(0x20C0FF, "minecraft:stone", 50);
            SelectiveRenderSettings.setBoundaryMode(SelectiveRenderSettings.BoundaryMode.COLOR);
            assertBoundaryAlpha(false, 128);
            SelectiveRenderClient.LOGGER.info("[SR boundary smoke] COLOR 50%");
            BlockPos air = pos.up(2);
            if (client.world.getBlockState(air).isAir()) {
                SelectiveRenderConfig.setBlockFilter(client, NAME, new BlockFilterRule(BlockFilterRule.Mode.ONLY, BlockFilterRule.Kind.ID, "minecraft:stone"));
                SelectiveRenderSettings.setInteractWithHiddenRegions(false);
                if (SelectiveRenderState.shouldInteract(air) || !SelectiveRenderState.shouldPlaceAt(air))
                    throw new IllegalStateException("Only-filtered air must permit placement without permitting hidden-block interaction");
                SelectiveRenderConfig.clearBlockFilters(client, NAME);
                SelectiveRenderClient.LOGGER.info("[SR placement smoke] Only-filtered air permits placement");
            } else throw new IllegalStateException("Placement smoke expected an air cell above the spawn");

        } else if (ticks == 40) {
            SelectiveRenderSettings.setBoundaryMode(SelectiveRenderSettings.BoundaryMode.TEXTURE);
            assertBoundaryAlpha(true, 255);
            SelectiveRenderClient.LOGGER.info("[SR boundary smoke] TEXTURE 50%");
            var probe = client.player.getBlockPos().down();
            var selected = BoundaryTextureResolver.selectedState();
            if (BoundaryTextureResolver.virtualWorld(probe, net.minecraft.util.math.Direction.WEST, selected).getBlockState(probe) != selected)
                throw new IllegalStateException("Boundary virtual block read did not survive production remapping");
            BoundaryTextureResolver.bind(probe, net.minecraft.util.math.Direction.WEST);
            if (BoundaryTextureResolver.bound() == null) throw new IllegalStateException("Boundary model did not resolve a loaded stone face");
            if (Boolean.getBoolean("selectiverender.ctm.smoke")) {
                try { Class.forName("net.irisshaders.iris.compat.sodium.impl.vertex_format.terrain_xhfp.XHFPTerrainVertex"); }
                catch (ClassNotFoundException error) { throw new IllegalStateException("Iris encoder compatibility hook did not load", error); }
                var sprite = BoundaryTextureResolver.bound().sprite();
                var image = ((de.selectiverender.mixin.BoundarySpriteImagesAccess)(Object)sprite.getContents()).selectiverender$images()[0];
                int pixel = BoundaryStyle.swapRedBlue(image.getColor(0,0));
                if ((pixel & 0xFFFFFF) != 0x20CC70) throw new IllegalStateException("Boundary model did not use the connected resourcepack tile: " + Integer.toHexString(pixel));
                SelectiveRenderClient.LOGGER.info("[SR CTM smoke] Loaded resourcepack connected tile resolved");
            }
            BoundaryTextureResolver.clear();
            if (Boolean.getBoolean("selectiverender.ctm.smoke")) {
                verifyNestedCtm(probe); verifyRepeatCtm(probe); BoundaryTextureResolver.resetNestedSmokeFaces();
            }

        } else if (ticks == 55) {
            SelectiveRenderSettings.setBoundaryMode(SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE);
            assertBoundaryAlpha(false, 128);
            assertBoundaryAlpha(true, 255);
            SelectiveRenderClient.LOGGER.info("[SR boundary smoke] COLOR + TEXTURE 50%");
        } else if (ticks == 62) {
            SelectiveRenderSettings.setBoundaryAppearance(0xFF0000, "minecraft:stone", 50);
            SelectiveRenderSettings.setBoundaryRainbow(true);
            SelectiveRenderClient.LOGGER.info("[SR boundary smoke] Rainbow preset enabled");
        } else if (ticks == 70) {
            SelectiveRenderSettings.setBoundaryAppearance(0x20C0FF, "minecraft:stone", 0);
            assertBoundaryAlpha(false, 0);
            assertBoundaryAlpha(true, 255);
            SelectiveRenderClient.LOGGER.info("[SR boundary smoke] COLOR 0% / TEXTURE unchanged");
        } else if (ticks == 80) {
            client.setScreen(new BoundaryAppearanceScreen(null));
        } else if (ticks == 100) {
            if (Boolean.getBoolean("selectiverender.ctm.smoke")) {
                if (BoundaryTextureResolver.nestedSmokeFaces() == 0) throw new IllegalStateException("CTM tile was not resolved from an active terrain render context");
                SelectiveRenderClient.LOGGER.info("[SR CTM smoke] Nested terrain faces resolved: {}", BoundaryTextureResolver.nestedSmokeFaces());
            }
            verifyConfigValidationIsReadOnly();
            verifyRegionToggleTransition(client);
            verifyRegionalBoundaries(client);
            verifyRegionalControlVisibility(client);
            verifyInvalidAppearanceEscape(client);
            SelectiveRenderSettings.setBoundaryRainbow(false);
            SelectiveRenderSettings.setBoundaryAppearance(0, "minecraft:stone", 100);
            SelectiveRenderSettings.setBoundaryMode(SelectiveRenderSettings.BoundaryMode.NORMAL);
            SelectiveRenderConfig.deleteRegion(client, NAME);
            SelectiveRenderClient.LOGGER.info("[SR boundary smoke] PASS");
        }
    }

    private static void verifyNestedCtm(BlockPos probe) {
        try {
            var type=Class.forName("me.pepperbell.continuity.client.model.ModelObjectsContainer");
            var local=(ThreadLocal<?>)type.getField("THREAD_LOCAL").get(null);
            Object original=local.get(), transform=type.getField("ctmQuadTransform").get(original);
            var active=transform.getClass().getDeclaredField("active"); active.setAccessible(true);
            boolean wasActive=active.getBoolean(transform);
            try {
                active.setBoolean(transform,true); BoundaryTextureResolver.invalidate();
                BoundaryTextureResolver.bind(probe, net.minecraft.util.math.Direction.WEST);
                if (BoundaryTextureResolver.bound()==null || BoundaryTextureResolver.smokePixel(BoundaryTextureResolver.bound()) != 0x20CC70)
                    throw new IllegalStateException("Nested CTM read skipped resourcepack processing: " + (BoundaryTextureResolver.bound()==null ? "unresolved" : BoundaryTextureResolver.bound().sprite().getContents().getId() + " pixel=" + Integer.toHexString(BoundaryTextureResolver.smokePixel(BoundaryTextureResolver.bound()))));
                if (local.get()!=original || !active.getBoolean(transform)) throw new IllegalStateException("Boundary CTM read changed the outer terrain context");
            } finally { active.setBoolean(transform,wasActive); BoundaryTextureResolver.clear(); }
            SelectiveRenderClient.LOGGER.info("[SR CTM smoke] Active outer context preserved");
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Nested CTM smoke could not inspect its fixture", error); }
    }

    private static void verifyRepeatCtm(BlockPos probe) {
        int color=SelectiveRenderSettings.boundaryColor(), opacity=SelectiveRenderSettings.boundaryOpacity();
        String texture=SelectiveRenderSettings.boundaryTexture();
        try {
            SelectiveRenderSettings.setBoundaryAppearance(color,"minecraft:dirt",opacity);
            String base=repeatTile(probe), vertical=repeatTile(probe.up()), horizontal=repeatTile(probe.south());
            if (base.equals(vertical) || base.equals(horizontal) || !base.equals(repeatTile(probe.add(0,8,8))))
                throw new IllegalStateException("Boundary 8x8 repeat mapping collapsed to a single tile or lost its period");
            SelectiveRenderClient.LOGGER.info("[SR CTM smoke] Distinct 8x8 repeat tiles and period verified");
        } finally { SelectiveRenderSettings.setBoundaryAppearance(color,texture,opacity); BoundaryTextureResolver.clear(); }
    }

    private static String repeatTile(BlockPos pos) {
        BoundaryTextureResolver.bind(pos,net.minecraft.util.math.Direction.WEST);
        if (BoundaryTextureResolver.bound()==null) throw new IllegalStateException("Repeat model did not resolve");
        String id=BoundaryTextureResolver.bound().sprite().getContents().getId().toString();
        int pixel=BoundaryTextureResolver.smokePixel(BoundaryTextureResolver.bound());
        int r=pixel>>16 & 255, g=pixel>>8 & 255;
        if ((pixel & 255)!=200 || r<16 || r>184 || g<16 || g>184 || (r-16)%24!=0 || (g-16)%24!=0)
            throw new IllegalStateException("Repeat model did not use its resourcepack: "+id+" pixel="+Integer.toHexString(pixel));
        return id;
    }

    private static void assertBoundaryAlpha(boolean textured, int expected) {
        assertBoundaryAlpha(textured ? SelectiveRenderSettings.BoundaryMode.TEXTURE : SelectiveRenderSettings.BoundaryMode.COLOR, expected);
    }
    private static void assertBoundaryAlpha(SelectiveRenderSettings.BoundaryMode mode, int expected) {
        var image = ((de.selectiverender.mixin.BoundarySpriteImagesAccess)(Object)BoundaryColorTexture.sprite(mode).getContents()).selectiverender$images()[0];
        if ((image.getColor(0, 0) >>> 24) != expected)
            throw new IllegalStateException("Boundary atlas alpha does not match requested opacity");
    }

    private static void verifyRegionToggleTransition(MinecraftClient client) {
        String dimension=client.world.getRegistryKey().getValue().toString();
        var saved=SelectiveRenderConfig.editorDocument(client,dimension);
        var boxes=saved.regions().get(NAME);
        SelectiveRenderConfig.editRegion(client,dimension,NAME,NAME,boxes,true,false,java.util.List.of(),RegionOptions.DEFAULT);
        if(!SelectiveRenderConfig.toggleRegion(client,NAME)) throw new IllegalStateException("Named render toggle failed");
        var changed=SelectiveRenderConfig.editorDocument(client,dimension);
        if(changed.hidden().contains(NAME) || changed.selectedHidden().contains(NAME) || !changed.selected().contains(NAME))
            throw new IllegalStateException("Hidden-to-render conversion left stale membership");
        boolean enabled=SelectiveRenderConfig.groupEnabled();
        if(!SelectiveRenderConfig.toggleCurrent(client) || SelectiveRenderConfig.groupEnabled()==enabled)
            throw new IllegalStateException("Global render toggle did not affect converted region");
        if(!SelectiveRenderConfig.toggleCurrent(client) || SelectiveRenderConfig.groupEnabled()!=enabled)
            throw new IllegalStateException("Global render toggle did not restore converted region");
        // The editor uses the same selected conversion, without a named toggle to repair it.
        SelectiveRenderConfig.editRegion(client,dimension,NAME,NAME,boxes,true,false,java.util.List.of(),RegionOptions.DEFAULT);
        SelectiveRenderConfig.editRegion(client,dimension,NAME,NAME,boxes,false,true,java.util.List.of(),RegionOptions.DEFAULT);
        if(!SelectiveRenderConfig.toggleCurrent(client) || !SelectiveRenderConfig.toggleCurrent(client))
            throw new IllegalStateException("Editor conversion lost the render selection");
        SelectiveRenderConfig.editRegion(client,dimension,NAME,NAME,boxes,false,true,
                saved.filters().getOrDefault(NAME,java.util.List.of()),saved.options().getOrDefault(NAME,RegionOptions.DEFAULT));
        SelectiveRenderClient.LOGGER.info("[SR region smoke] Hidden-to-render selection survives global toggles");
    }

    private static void verifyRegionalBoundaries(MinecraftClient client) {
        String dimension=client.world.getRegistryKey().getValue().toString();
        var document=SelectiveRenderConfig.editorDocument(client,dimension);
        var original=document.options().getOrDefault(NAME,RegionOptions.DEFAULT);
        int color=SelectiveRenderSettings.boundaryColor(),opacity=SelectiveRenderSettings.boundaryOpacity();
        String texture=SelectiveRenderSettings.boundaryTexture();
        var regional=original.withTexture(new RegionOptions.TextureLayer(true,"minecraft:dirt"))
                .withColor(new RegionOptions.ColorLayer(true,0x123456,35,false,false));
        try {
            SelectiveRenderConfig.editRegion(client,dimension,NAME,NAME,document.regions().get(NAME),false,true,
                    document.filters().getOrDefault(NAME,java.util.List.of()),regional);
            var saved=SelectiveRenderConfig.editorDocument(client,dimension);
            if(!regional.equals(saved.options().get(NAME))) throw new IllegalStateException("Regional boundary appearance did not persist");
            BoundaryRenderContext.bind(client.player.getBlockPos());
            if(BoundaryRenderContext.rgb()!=0x123456 || BoundaryRenderContext.opacity()!=35 || !BoundaryRenderContext.texture().equals("minecraft:dirt"))
                throw new IllegalStateException("Regional renderer did not resolve its independent style");
            var mode=SelectiveRenderSettings.BoundaryMode.COLOR;
            var image=((de.selectiverender.mixin.BoundarySpriteImagesAccess)(Object)BoundaryColorTexture.sprite(mode).getContents()).selectiverender$images()[0];
            if((image.getColor(0,0)>>>24)!=BoundaryStyle.alpha(35) || BoundaryColorTexture.argb(mode)!=0xFF123456)
                throw new IllegalStateException("Regional alpha/color did not reach the terrain material");
            if(!BoundaryStyle.fullbright(mode)) throw new IllegalStateException("Regional color lighting did not override global defaults");
            if(color!=SelectiveRenderSettings.boundaryColor() || opacity!=SelectiveRenderSettings.boundaryOpacity() || !texture.equals(SelectiveRenderSettings.boundaryTexture()))
                throw new IllegalStateException("Regional editor changed global boundary defaults");
            SelectiveRenderClient.LOGGER.info("[SR region smoke] Independent texture/color persisted without changing global defaults");
        } finally {
            BoundaryRenderContext.clear(); BoundaryTextureResolver.clear();
            SelectiveRenderConfig.editRegion(client,dimension,NAME,NAME,document.regions().get(NAME),false,true,
                    document.filters().getOrDefault(NAME,java.util.List.of()),original);
        }
    }


    private static void verifyConfigValidationIsReadOnly() {
        try {
            var optionsField=SelectiveRenderConfig.class.getDeclaredField("OPTIONS"); optionsField.setAccessible(true);
            var options=(java.util.Map<String,RegionOptions>)optionsField.get(null);
            boolean present=options.containsKey(NAME); var previous=options.get(NAME);
            var original=RegionOptions.DEFAULT.withNormal(false);
            try {
                options.put(NAME,original);
                var type=Class.forName("de.selectiverender.SelectiveRenderConfig$StoredConfig");
                var other=new com.google.gson.Gson().fromJson("{\"formatVersion\":8,\"regionOptions\":{\""+NAME+"\":{\"normalFaces\":true}}}",type);
                var validate=SelectiveRenderConfig.class.getDeclaredMethod("validateStoredConfig",type); validate.setAccessible(true);
                validate.invoke(null,other);
                if(!original.equals(options.get(NAME))) throw new IllegalStateException("Reading another region file modified live options");
            } finally { if(present) options.put(NAME,previous); else options.remove(NAME); }
            SelectiveRenderClient.LOGGER.info("[SR region smoke] Config validation never changes live region options");
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Config isolation smoke failed",error); }
    }

    private static void verifyRegionalControlVisibility(MinecraftClient client) {
        var originalMode=SelectiveRenderSettings.boundaryMode();
        var editor=new RegionEditorScreen(null);
        client.setScreen(editor);
        try {
            var regionField=RegionEditorScreen.class.getDeclaredField("region"); regionField.setAccessible(true); regionField.set(editor,NAME);
            var open=RegionEditorScreen.class.getDeclaredField("boundariesOpen"); open.setAccessible(true); open.setBoolean(editor,true);
            var targetMethod=RegionEditorScreen.class.getDeclaredMethod("boundaryTarget"); targetMethod.setAccessible(true);
            var target=(BoundaryEditorTarget)targetMethod.invoke(editor);
            SelectiveRenderSettings.setBoundaryMode(SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE);
            target.setColorEnabled(false); editor.init(); assertRegionalPalette(editor,false);
            target.inheritColor(); editor.init(); assertRegionalPalette(editor,true);
            target.setTextureEnabled(false); editor.init(); assertRegionalPalette(editor,true);
            target.setColorEnabled(false); editor.init(); assertRegionalPalette(editor,false);
            SelectiveRenderSettings.setBoundaryMode(SelectiveRenderSettings.BoundaryMode.TEXTURE);
            target.inheritColor(); target.inheritTexture(); editor.init(); assertRegionalPalette(editor,false);
            SelectiveRenderSettings.setBoundaryMode(SelectiveRenderSettings.BoundaryMode.COLOR);
            editor.init(); assertRegionalPalette(editor,true);
            SelectiveRenderClient.LOGGER.info("[SR menu smoke] Regional Color Off removes all palette rows and widgets; global inheritance follows layer toggles");
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Regional visibility smoke failed",error); }
        finally { client.setScreen(null); SelectiveRenderSettings.setBoundaryMode(originalMode); }
    }
    private static void assertRegionalPalette(RegionEditorScreen editor,boolean enabled) throws ReflectiveOperationException {
        var palette=RegionEditorScreen.class.getDeclaredField("colorControls"); palette.setAccessible(true);
        var rowsField=RegionEditorScreen.class.getDeclaredField("rows"); rowsField.setAccessible(true);
        int count=0;
        for(Object row:(java.util.List<?>)rowsField.get(editor)) {
            var label=row.getClass().getDeclaredMethod("label"); label.setAccessible(true);
            if(((String)label.invoke(row)).startsWith("@color-")) count++;
        }
        if((palette.get(editor)!=null)!=enabled || count!=(enabled?5:0))
            throw new IllegalStateException("Regional Color visibility retained stale palette controls");
    }

    private static void verifyInvalidAppearanceEscape(MinecraftClient client) {
        int color = SelectiveRenderSettings.boundaryColor();
        int opacity = SelectiveRenderSettings.boundaryOpacity();
        String texture = SelectiveRenderSettings.boundaryTexture();
        var parent = new SelectiveRenderSettingsScreen(null, SelectiveRenderSettingsScreen.Tab.BOUNDARIES, null, 0);
        client.setScreen(parent);
        var appearance = new BoundaryAppearanceScreen(parent);
        client.setScreen(appearance);
        if (appearance.children().stream().filter(child -> child instanceof net.minecraft.client.gui.widget.SliderWidget).count() != 4)
            throw new IllegalStateException("Boundary page did not initialize all four sliders");
        var field = appearance.children().stream().filter(child -> child instanceof net.minecraft.client.gui.widget.TextFieldWidget)
                .map(child -> (net.minecraft.client.gui.widget.TextFieldWidget) child).findFirst().orElseThrow();
        appearance.setFocused(field); field.setFocused(true);
        field.setText("minecraft:s");
        appearance.keyPressed(264, 0, 0);
        if (!field.isFocused() || appearance.getFocused() != field)
            throw new IllegalStateException("Autocomplete arrow navigation escaped the input field");
        field.setText("selectiverender:missing_smoke_block");
        appearance.close();
        if (client.currentScreen != parent || color != SelectiveRenderSettings.boundaryColor()
                || opacity != SelectiveRenderSettings.boundaryOpacity()
                || !texture.equals(SelectiveRenderSettings.boundaryTexture()))
            throw new IllegalStateException("Invalid boundary input trapped the player or changed appearance");
        client.setScreen(null);
        SelectiveRenderClient.LOGGER.info("[SR menu smoke] Invalid boundary ID safely discarded");
    }
}
