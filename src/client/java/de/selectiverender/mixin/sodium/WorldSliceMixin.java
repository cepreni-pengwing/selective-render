package de.selectiverender.mixin.sodium;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.WorldSlice", remap = false)
abstract class WorldSliceMixin {
    @Inject(method = "getBlockState(III)Lnet/minecraft/block/BlockState;", at = @At("HEAD"), cancellable = true, remap = true)
    private void selectiverender$filterBlockState(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (!SelectiveRenderState.shouldRender(new BlockPos(x, y, z))) {
            cir.setReturnValue(Blocks.AIR.getDefaultState());
        }
    }

    @Inject(method = "getLightLevel(Lnet/minecraft/world/LightType;Lnet/minecraft/util/math/BlockPos;)I", at = @At("HEAD"), cancellable = true, remap = true)
    private void selectiverender$filterLightLevel(LightType type, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!SelectiveRenderState.shouldRender(pos)) cir.setReturnValue(type == LightType.SKY ? 15 : 0);
    }

    @Inject(method = "getBaseLightLevel(Lnet/minecraft/util/math/BlockPos;I)I", at = @At("HEAD"), cancellable = true, remap = true)
    private void selectiverender$filterBaseLightLevel(BlockPos pos, int ambientDarkness,
                                                       CallbackInfoReturnable<Integer> cir) {
        if (!SelectiveRenderState.shouldRender(pos)) cir.setReturnValue(Math.max(0, 15 - ambientDarkness));
    }
}
