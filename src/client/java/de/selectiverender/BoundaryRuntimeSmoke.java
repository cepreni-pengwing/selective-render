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
            SelectiveRenderSettings.setBoundaryAppearance(0, "minecraft:stone", 100);
            SelectiveRenderSettings.setBoundaryMode(SelectiveRenderSettings.BoundaryMode.NORMAL);
            SelectiveRenderConfig.deleteRegion(client, NAME);
            SelectiveRenderClient.LOGGER.info("[SR boundary smoke] PASS");
        }
    }
}
