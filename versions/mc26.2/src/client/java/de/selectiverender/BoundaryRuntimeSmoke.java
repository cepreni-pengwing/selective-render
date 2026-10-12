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
            client.gui.setScreen(new BoundaryAppearanceScreen(null));
        } else if (ticks == 100) {
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
        var image = ((de.selectiverender.mixin.BoundarySpriteImagesAccess)(Object)BoundaryColorTexture.sprite(mode).contents()).selectiverender$images()[0];
        if ((image.getPixel(0, 0) >>> 24) != expected)
            throw new IllegalStateException("Boundary atlas alpha does not match requested opacity");
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
