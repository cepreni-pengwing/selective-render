package de.selectiverender;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.WorldAccess;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class FlywheelCompat {
    private static final String MANAGER_CLASS =
            "dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl";
    private static volatile boolean resolved;
    private static Method resetMethod;

    private FlywheelCompat() { }

    static boolean refresh(ClientWorld world) {
        if (world == null || !FabricLoader.getInstance().isModLoaded("flywheel")) return true;
        Method reset = resetMethod();
        if (reset == null) return false;
        try {
            reset.invoke(null, world);
            return true;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            SelectiveRenderClient.LOGGER.warn(
                    "Could not reset Flywheel visualizations; using a full renderer reload", exception);
            return false;
        }
    }

    private static Method resetMethod() {
        if (resolved) return resetMethod;
        synchronized (FlywheelCompat.class) {
            if (resolved) return resetMethod;
            try {
                resetMethod = Class.forName(MANAGER_CLASS).getMethod("reset", WorldAccess.class);
            } catch (ClassNotFoundException | NoSuchMethodException exception) {
                SelectiveRenderClient.LOGGER.warn(
                        "Flywheel visualization reset is unavailable; using full renderer reloads");
            }
            resolved = true;
            return resetMethod;
        }
    }
}
