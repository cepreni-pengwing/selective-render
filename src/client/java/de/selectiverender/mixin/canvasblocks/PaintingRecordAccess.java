package de.selectiverender.mixin.canvasblocks;

import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "com.canvasblocks.painting.PaintingRecord", remap = false)
public interface PaintingRecordAccess {
    @Invoker("origin")
    BlockPos selectiverender$origin();

    @Invoker("usesEntity")
    boolean selectiverender$usesEntity();
}
