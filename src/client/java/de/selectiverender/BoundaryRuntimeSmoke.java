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
        } else if (ticks == 40) {
            SelectiveRenderSettings.setBoundaryMode(SelectiveRenderSettings.BoundaryMode.TEXTURE);
            assertBoundaryAlpha(true, 128);
            SelectiveRenderClient.LOGGER.info("[SR boundary smoke] TEXTURE 50%");
        } else if (ticks == 55) {
            SelectiveRenderSettings.setBoundaryMode(SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE);
            assertBoundaryAlpha(SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE, 192);
            SelectiveRenderClient.LOGGER.info("[SR boundary smoke] COLOR + TEXTURE 50%");
        } else if (ticks == 70) {
            SelectiveRenderSettings.setBoundaryAppearance(0x20C0FF, "minecraft:stone", 0);
            assertBoundaryAlpha(SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE, 0);
            SelectiveRenderClient.LOGGER.info("[SR boundary smoke] TEXTURE 0%");
        } else if (ticks == 80) {
            client.setScreen(new BoundaryAppearanceScreen(null));
        } else if (ticks == 100) {
            verifyInvalidAppearanceEscape(client);
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
