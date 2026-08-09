package de.selectiverender.mixin.sodium;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.LightType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.WorldSlice", remap = false)
abstract class WorldSliceMixin {
    @Shadow @Final private ClientWorld world;
    @Unique private final Map<Long, Integer> selectiverender$highestOccluders = new HashMap<>();

    @Inject(method = "copyData", at = @At("HEAD"))
    private void selectiverender$clearLightCache(CallbackInfo ci) {
        selectiverender$highestOccluders.clear();
    }

    @Inject(method = "getBlockState(III)Lnet/minecraft/block/BlockState;", at = @At("HEAD"), cancellable = true, remap = true)
    private void selectiverender$filterBlockState(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (!SelectiveRenderState.shouldRender(x, y, z)) {
            cir.setReturnValue(Blocks.AIR.getDefaultState());
        }
    }

    @Inject(method = "getLightLevel(Lnet/minecraft/world/LightType;Lnet/minecraft/util/math/BlockPos;)I", at = @At("HEAD"), cancellable = true, remap = true)
    private void selectiverender$filterLightLevel(LightType type, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!SelectiveRenderState.shouldRender(pos)) {
            cir.setReturnValue(type == LightType.SKY ? 15 : 0);
        } else if (type == LightType.SKY && selectiverender$hasOpenVirtualSky(pos)) {
            cir.setReturnValue(15);
        }
    }

    @Inject(method = "getBaseLightLevel(Lnet/minecraft/util/math/BlockPos;I)I", at = @At("HEAD"), cancellable = true, remap = true)
    private void selectiverender$filterBaseLightLevel(BlockPos pos, int ambientDarkness,
                                                       CallbackInfoReturnable<Integer> cir) {
        if (!SelectiveRenderState.shouldRender(pos) || selectiverender$hasOpenVirtualSky(pos)) {
            cir.setReturnValue(Math.max(0, 15 - ambientDarkness));
        }
    }

    @Unique
    private boolean selectiverender$hasOpenVirtualSky(BlockPos pos) {
        if ((!SelectiveRenderState.enabled() && !SelectiveRenderState.hideEnabled())
                || !SelectiveRenderState.shouldRender(pos)) {
            return false;
        }

        long column = ChunkPos.toLong(pos.getX(), pos.getZ());
        int highestOccluder = selectiverender$highestOccluders.computeIfAbsent(column, ignored -> {
            BlockPos.Mutable cursor = new BlockPos.Mutable(pos.getX(), 0, pos.getZ());
            int top = SelectiveRenderState.visibleColumnTop(
                    pos.getX(), pos.getZ(), world.getTopY() - 1);
            int bottom = SelectiveRenderState.visibleColumnBottom(
                    pos.getX(), pos.getZ(), world.getBottomY());
            if (top == Integer.MIN_VALUE || bottom == Integer.MAX_VALUE || bottom > top) {
                return Integer.MIN_VALUE;
            }
            for (int y = top; y >= bottom; y--) {
                cursor.setY(y);
                if (!SelectiveRenderState.shouldRender(cursor)) continue;
                BlockState state = world.getBlockState(cursor);
                if (state.getOpacity(world, cursor) > 0) return y;
            }
            return Integer.MIN_VALUE;
        });
        return highestOccluder <= pos.getY();
    }
}
