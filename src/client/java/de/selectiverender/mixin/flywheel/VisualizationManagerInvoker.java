package de.selectiverender.mixin.flywheel;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl", remap = false)
public interface VisualizationManagerInvoker {
    @Invoker("reset")
    static void selectiverender$reset(World world) {
        throw new AssertionError();
    }
}
