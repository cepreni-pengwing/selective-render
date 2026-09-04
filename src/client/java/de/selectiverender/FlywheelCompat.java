package de.selectiverender;

import de.selectiverender.mixin.flywheel.VisualizationManagerInvoker;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.world.ClientWorld;

final class FlywheelCompat {
    private FlywheelCompat() { }

    static void refresh(ClientWorld world) {
        if (world != null && FabricLoader.getInstance().isModLoaded("flywheel")) {
            VisualizationManagerInvoker.selectiverender$reset(world);
        }
    }
}
