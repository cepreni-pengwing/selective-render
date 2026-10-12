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
            SelectiveRenderClient.LOGGER.info("[SR boundary smoke] COLOR 50%");
        } else if (ticks == 40) {
            SelectiveRenderSettings.setBoundaryMode(SelectiveRenderSettings.BoundaryMode.TEXTURE);
            SelectiveRenderClient.LOGGER.info("[SR boundary smoke] TEXTURE 50%");
        } else if (ticks == 70) {
            SelectiveRenderSettings.setBoundaryAppearance(0x20C0FF, "minecraft:stone", 0);
            SelectiveRenderClient.LOGGER.info("[SR boundary smoke] TEXTURE 0%");
        } else if (ticks == 100) {
            verifyInvalidAppearanceEscape(client);
            SelectiveRenderSettings.setBoundaryAppearance(0, "minecraft:stone", 100);
            SelectiveRenderSettings.setBoundaryMode(SelectiveRenderSettings.BoundaryMode.NORMAL);
            SelectiveRenderConfig.deleteRegion(client, NAME);
            SelectiveRenderClient.LOGGER.info("[SR boundary smoke] PASS");
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
        var field = appearance.children().stream().filter(child -> child instanceof net.minecraft.client.gui.widget.TextFieldWidget)
                .map(child -> (net.minecraft.client.gui.widget.TextFieldWidget) child).findFirst().orElseThrow();
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
