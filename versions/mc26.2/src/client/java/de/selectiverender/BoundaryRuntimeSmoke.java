package de.selectiverender;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
/** Opt-in, disposable CI-world exercise. Disabled in all normal game sessions. */
final class BoundaryRuntimeSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("selectiverender.boundary.smoke");
    private static int ticks;
    private static final String NAME = "__sr_boundary_smoke__";
    private BoundaryRuntimeSmoke() { }
    static void tick(Minecraft client) {
        if (!ENABLED || client.level == null || client.player == null || ticks > 130) return;
        ticks++;
        if (ticks == 10) {
            BlockPos pos = client.player.blockPosition();
            SelectiveRenderSettings.setVirtualLightMode(SelectiveRenderSettings.VirtualLightMode.NONE);
            if (!SelectiveRenderConfig.saveRegion(client, NAME, new BlockRegion(pos.getX() - 4, pos.getX() + 4,
                    pos.getY() - 8, pos.getY() + 8, pos.getZ() - 4, pos.getZ() + 4), false))
                throw new IllegalStateException("Boundary smoke region could not be created");
            SelectiveRenderSettings.setBoundaryAppearance(0x20C0FF, "minecraft:stone", 50);
            SelectiveRenderSettings.setBoundaryMode(SelectiveRenderSettings.BoundaryMode.COLOR);
            assertBoundaryAlpha(false, 128);
            SelectiveRenderClient.LOGGER.info("[SR boundary smoke] COLOR 50%");
            BlockPos air = pos.above(2);
            if (client.level.getBlockState(air).isAir()) {
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
            var probe = client.player.blockPosition().below();
            var selected = BoundaryTextureResolver.selectedState();
            if (BoundaryTextureResolver.virtualWorld(probe, net.minecraft.core.Direction.WEST, selected).getBlockState(probe) != selected)
                throw new IllegalStateException("Boundary virtual block read did not survive production remapping");
            BoundaryTextureResolver.bind(probe, net.minecraft.core.Direction.WEST);
            if (BoundaryTextureResolver.bound() == null) throw new IllegalStateException("Boundary model did not resolve a loaded stone face");
            if (Boolean.getBoolean("selectiverender.ctm.smoke")) {
                var sprite = BoundaryTextureResolver.bound().sprite();
                var image = ((de.selectiverender.mixin.BoundarySpriteImagesAccess)(Object)sprite.contents()).selectiverender$images()[0];
                int pixel = image.getPixel(0,0);
                if ((pixel & 0xFFFFFF) != 0x20CC70) throw new IllegalStateException("Boundary model did not use the connected resourcepack tile: " + Integer.toHexString(pixel));
                SelectiveRenderClient.LOGGER.info("[SR CTM smoke] Loaded resourcepack connected tile resolved");
            }
            BoundaryTextureResolver.clear();

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
            verifyGlobalPalette(client);
            SelectiveRenderSettings.setBoundaryRainbow(false);
            SelectiveRenderSettings.setBoundaryAppearance(0xFF0000, "minecraft:stone", 50);
            client.gui.setScreen(new BoundaryAppearanceScreen(null));
        } else if (ticks == 85) {
            capturePalette(client, "sr-global-palette.png");
        } else if (ticks == 90) {
            var editor=new RegionEditorScreen(null);
            client.gui.setScreen(editor);
            try {
                var region=RegionEditorScreen.class.getDeclaredField("region"); region.setAccessible(true); region.set(editor,NAME);
                var open=RegionEditorScreen.class.getDeclaredField("boundariesOpen"); open.setAccessible(true); open.setBoolean(editor,true);
                editor.init();
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Palette screenshot setup failed",error); }
        } else if (ticks == 95) {
            capturePalette(client, "sr-regional-palette.png");
        } else if (ticks == 100) {
            verifyFilterOnlyLightingAndEntities(client);
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


    private static void capturePalette(Minecraft client, String name) {
        net.minecraft.client.Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1, message -> { });
    }
    /** Check the sprites used by terrain, not just the settings value or GUI preview. */
    private static void verifyGlobalPalette(Minecraft client) {
        var mode=SelectiveRenderSettings.BoundaryMode.COLOR;
        var pos=client.player.blockPosition();
        BoundaryRenderContext.clear();
        for (int percent : new int[]{0,1,25,50,75,99,100}) {
            SelectiveRenderSettings.setBoundaryRainbow(false);
            SelectiveRenderSettings.setBoundaryAppearance(0xFF0000,"minecraft:stone",percent);
            var sprite=BoundaryColorTexture.sprite(mode);
            assertBoundaryAlpha(mode,BoundaryStyle.alpha(percent));
            if (BoundaryColorTexture.argb(mode,pos,net.minecraft.core.Direction.WEST,0,0,0)!=0xFFFF0000)
                throw new IllegalStateException("Global RGB did not reach terrain vertices");
            float u=BoundaryColorTexture.u(mode,pos,net.minecraft.core.Direction.WEST,0,0,0);
            SelectiveRenderSettings.setBoundaryRainbow(true);
            if (BoundaryColorTexture.sprite(mode)!=sprite || BoundaryColorTexture.u(mode,pos,net.minecraft.core.Direction.WEST,1,0,1)!=u)
                throw new IllegalStateException("Rainbow changed color sprite or spanned the spectrum in UVs");
            assertBoundaryAlpha(mode,BoundaryStyle.alpha(percent));
        }
        SelectiveRenderClient.LOGGER.info("[SR palette smoke] Global alpha 0/1/25/50/75/99/100 and stable rainbow UVs verified");
    }

    private static void assertBoundaryAlpha(boolean textured, int expected) {
        assertBoundaryAlpha(textured ? SelectiveRenderSettings.BoundaryMode.TEXTURE : SelectiveRenderSettings.BoundaryMode.COLOR, expected);
    }
    private static void assertBoundaryAlpha(SelectiveRenderSettings.BoundaryMode mode, int expected) {
        var image = ((de.selectiverender.mixin.BoundarySpriteImagesAccess)(Object)BoundaryColorTexture.sprite(mode).contents()).selectiverender$images()[0];
        if ((image.getPixel(0, 0) >>> 24) != expected)
            throw new IllegalStateException("Boundary atlas alpha does not match requested opacity");
    }

    private static void verifyRegionToggleTransition(net.minecraft.client.Minecraft client) {
        String dimension=client.level.dimension().identifier().toString();
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

    private static void verifyRegionalBoundaries(Minecraft client) {
        String dimension=client.level.dimension().identifier().toString();
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
            BoundaryRenderContext.bind(client.player.blockPosition());
            if(BoundaryRenderContext.rgb()!=0x123456 || BoundaryRenderContext.opacity()!=35 || !BoundaryRenderContext.texture().equals("minecraft:dirt"))
                throw new IllegalStateException("Regional renderer did not resolve its independent style");
            var mode=SelectiveRenderSettings.BoundaryMode.COLOR;
            var image=((de.selectiverender.mixin.BoundarySpriteImagesAccess)(Object)BoundaryColorTexture.sprite(mode).contents()).selectiverender$images()[0];
            if((image.getPixel(0,0)>>>24)!=BoundaryStyle.alpha(35) || BoundaryColorTexture.argb(mode)!=0xFF123456)
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


    private static void verifyFilterOnlyLightingAndEntities(Minecraft client) {
        var previous=SelectiveRenderState.snapshot();
        var light=SelectiveRenderSettings.virtualLightMode();
        boolean hidden=SelectiveRenderSettings.interactWithHiddenRegions();
        var pos=client.player.blockPosition();
        var column=new BlockRegion(pos.getX()-4,pos.getX()+4,client.level.getMinY(),client.level.getMaxY(),pos.getZ()-4,pos.getZ()+4);
        var filtered=new FilteredRegion(column,java.util.Set.of(new BlockFilterRule(BlockFilterRule.Mode.ONLY,BlockFilterRule.Kind.ID,"minecraft:air")));
        try {
            SelectiveRenderSettings.setVirtualLightMode(SelectiveRenderSettings.VirtualLightMode.BOTH);
            SelectiveRenderSettings.setInteractWithHiddenRegions(false);
            SelectiveRenderState.setSavedState(java.util.List.of(),false,java.util.List.of(),false,java.util.List.of(),java.util.List.of(filtered));
            if(!SelectiveRenderState.virtualSkyLightActive() || SelectiveRenderState.highestVisibleOccluder(client.level,pos.getX(),pos.getZ())!=Integer.MIN_VALUE)
                throw new IllegalStateException("Only-filtered roof still blocked direct skylight");
            if(VirtualSkyLightSampler.sample(client.level,pos)!=15) throw new IllegalStateException("Filter-only sampler did not provide direct sky");
            var stand=new net.minecraft.world.entity.decoration.ArmorStand(client.level,pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            if(SelectiveRenderState.shouldRender(stand) || SelectiveRenderState.shouldInteract(stand))
                throw new IllegalStateException("Only-filtered entity remained visible or targetable");
            SelectiveRenderSettings.setInteractWithHiddenRegions(true);
            if(!SelectiveRenderState.shouldInteract(stand)) throw new IllegalStateException("Hidden interactions did not permit the filtered entity");
            SelectiveRenderClient.LOGGER.info("[SR filter smoke] Filter-only roofs admit skylight and entity targeting respects hidden interactions");
        } finally {
            SelectiveRenderState.setSavedState(previous.configuredRegions(),previous.enabled(),previous.hiddenRegions(),previous.hideEnabled(),previous.visibleOverrides(),previous.filteredRegions());
            SelectiveRenderSettings.setVirtualLightMode(light); SelectiveRenderSettings.setInteractWithHiddenRegions(hidden);
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

    private static void verifyRegionalControlVisibility(Minecraft client) {
        var originalMode=SelectiveRenderSettings.boundaryMode();
        var editor=new RegionEditorScreen(null);
        client.gui.setScreen(editor);
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
        finally { client.gui.setScreen(null); SelectiveRenderSettings.setBoundaryMode(originalMode); }
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

    private static void verifyInvalidAppearanceEscape(net.minecraft.client.Minecraft client) {
        int color = SelectiveRenderSettings.boundaryColor();
        int opacity = SelectiveRenderSettings.boundaryOpacity();
        String texture = SelectiveRenderSettings.boundaryTexture();
        var parent = new SelectiveRenderSettingsScreen(null, SelectiveRenderSettingsScreen.Tab.BOUNDARIES, null, 0);
        client.gui.setScreen(parent);
        var appearance = new BoundaryAppearanceScreen(parent);
        client.gui.setScreen(appearance);
        if (appearance.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.AbstractSliderButton).count() != 4)
            throw new IllegalStateException("Boundary page did not initialize all four sliders");
        var field = appearance.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.EditBox)
                .map(child -> (net.minecraft.client.gui.components.EditBox) child).findFirst().orElseThrow();
        appearance.setFocused(field); field.setFocused(true);
        field.setValue("minecraft:s");
        appearance.keyPressed(new net.minecraft.client.input.KeyEvent(264, 0, 0));
        if (!field.isFocused() || appearance.getFocused() != field)
            throw new IllegalStateException("Autocomplete arrow navigation escaped the input field");
        field.setValue("selectiverender:missing_smoke_block");
        appearance.onClose();
        if (client.gui.screen() != parent || color != SelectiveRenderSettings.boundaryColor()
                || opacity != SelectiveRenderSettings.boundaryOpacity()
                || !texture.equals(SelectiveRenderSettings.boundaryTexture()))
            throw new IllegalStateException("Invalid boundary input trapped the player or changed appearance");
        client.gui.setScreen(null);
        SelectiveRenderClient.LOGGER.info("[SR menu smoke] Invalid boundary ID safely discarded");
    }
}
