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
                var sprite = BoundaryTextureResolver.bound().sprite();
                var image = ((de.selectiverender.mixin.BoundarySpriteImagesAccess)(Object)sprite.getContents()).selectiverender$images()[0];
                int pixel = BoundaryStyle.swapRedBlue(image.getColor(0,0));
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
            client.setScreen(new BoundaryAppearanceScreen(null));
        } else if (ticks == 100) {
            verifyRegionalBoundaries(client);
            verifyInvalidAppearanceEscape(client);
            SelectiveRenderSettings.setBoundaryRainbow(false);
            SelectiveRenderSettings.setBoundaryAppearance(0, "minecraft:stone", 100);
            SelectiveRenderSettings.setBoundaryMode(SelectiveRenderSettings.BoundaryMode.NORMAL);
            SelectiveRenderConfig.deleteRegion(client, NAME);
            SelectiveRenderClient.LOGGER.info("[SR boundary smoke] PASS");
        }
    }

    private static void assertBoundaryAlpha(boolean textured, int expected) {
        assertBoundaryAlpha(textured ? SelectiveRenderSettings.BoundaryMode.TEXTURE : SelectiveRenderSettings.BoundaryMode.COLOR, expected);
    }
    private static void assertBoundaryAlpha(SelectiveRenderSettings.BoundaryMode mode, int expected) {
        var image = ((de.selectiverender.mixin.BoundarySpriteImagesAccess)(Object)BoundaryColorTexture.sprite(mode).getContents()).selectiverender$images()[0];
        if ((image.getColor(0, 0) >>> 24) != expected)
            throw new IllegalStateException("Boundary atlas alpha does not match requested opacity");
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
