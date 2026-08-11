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
    @Unique private final Map<Long, Integer> selectiverender$virtualSkyLight = new HashMap<>();

    @Inject(method = "copyData", at = @At("HEAD"))
    private void selectiverender$clearLightCache(CallbackInfo ci) {
        selectiverender$highestOccluders.clear();
        selectiverender$virtualSkyLight.clear();
    }

    @Inject(method = "getBlockState(III)Lnet/minecraft/block/BlockState;", at = @At("HEAD"), cancellable = true, remap = true)
    private void selectiverender$filterBlockState(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (!SelectiveRenderState.shouldRender(x, y, z)) {
            cir.setReturnValue(Blocks.AIR.getDefaultState());
        }
    }

    @Inject(method = "getLightLevel(Lnet/minecraft/world/LightType;Lnet/minecraft/util/math/BlockPos;)I", at = @At("RETURN"), cancellable = true, remap = true)
    private void selectiverender$filterLightLevel(LightType type, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!SelectiveRenderState.shouldRender(pos)) {
            cir.setReturnValue(type == LightType.SKY ? 15 : 0);
        } else if (type == LightType.SKY && cir.getReturnValueI() < 15) {
            int virtualLight = selectiverender$getVirtualSkyLight(pos);
            if (virtualLight >= 0) cir.setReturnValue(Math.max(cir.getReturnValueI(), virtualLight));
        }
    }

    @Inject(method = "getBaseLightLevel(Lnet/minecraft/util/math/BlockPos;I)I", at = @At("HEAD"), cancellable = true, remap = true)
    private void selectiverender$filterBaseLightLevel(BlockPos pos, int ambientDarkness,
                                                       CallbackInfoReturnable<Integer> cir) {
        if (!SelectiveRenderState.shouldRender(pos)) {
            cir.setReturnValue(Math.max(0, 15 - ambientDarkness));
        }
    }

    @Inject(method = "getBaseLightLevel(Lnet/minecraft/util/math/BlockPos;I)I", at = @At("RETURN"), cancellable = true, remap = true)
    private void selectiverender$applyVirtualBaseLight(BlockPos pos, int ambientDarkness,
                                                        CallbackInfoReturnable<Integer> cir) {
        int virtualLight = selectiverender$getVirtualSkyLight(pos);
        if (virtualLight >= 0) {
            cir.setReturnValue(Math.max(cir.getReturnValueI(), Math.max(0, virtualLight - ambientDarkness)));
        }
    }

    @Unique
    private int selectiverender$getVirtualSkyLight(BlockPos pos) {
        if ((!SelectiveRenderState.enabled() && !SelectiveRenderState.hideEnabled())
                || !SelectiveRenderState.shouldRender(pos)) {
            return -1;
        }

        return selectiverender$virtualSkyLight.computeIfAbsent(pos.asLong(), ignored ->
                selectiverender$calculateVirtualSkyLight(pos));
    }

    @Unique
    private int selectiverender$calculateVirtualSkyLight(BlockPos pos) {
        if (selectiverender$getHighestVisibleOccluder(pos.getX(), pos.getZ()) <= pos.getY()) return 15;

        boolean positiveX = true;
        boolean negativeX = true;
        boolean positiveZ = true;
        boolean negativeZ = true;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int distance = 1; distance < 15; distance++) {
            positiveX = positiveX && selectiverender$isOpenStep(cursor, pos.getX() + distance, pos.getY(), pos.getZ());
            negativeX = negativeX && selectiverender$isOpenStep(cursor, pos.getX() - distance, pos.getY(), pos.getZ());
            positiveZ = positiveZ && selectiverender$isOpenStep(cursor, pos.getX(), pos.getY(), pos.getZ() + distance);
            negativeZ = negativeZ && selectiverender$isOpenStep(cursor, pos.getX(), pos.getY(), pos.getZ() - distance);
            if ((positiveX && selectiverender$getHighestVisibleOccluder(pos.getX() + distance, pos.getZ()) <= pos.getY())
                    || (negativeX && selectiverender$getHighestVisibleOccluder(pos.getX() - distance, pos.getZ()) <= pos.getY())
                    || (positiveZ && selectiverender$getHighestVisibleOccluder(pos.getX(), pos.getZ() + distance) <= pos.getY())
                    || (negativeZ && selectiverender$getHighestVisibleOccluder(pos.getX(), pos.getZ() - distance) <= pos.getY())) {
                return 15 - distance;
            }
            if (!positiveX && !negativeX && !positiveZ && !negativeZ) break;
        }
        return 0;
    }

    @Unique
    private boolean selectiverender$isOpenStep(BlockPos.Mutable cursor, int x, int y, int z) {
        cursor.set(x, y, z);
        return !SelectiveRenderState.shouldRender(cursor)
                || world.getBlockState(cursor).getOpacity(world, cursor) == 0;
    }

    @Unique
    private int selectiverender$getHighestVisibleOccluder(int x, int z) {
        long column = ChunkPos.toLong(x, z);
        return selectiverender$highestOccluders.computeIfAbsent(column, ignored -> {
            BlockPos.Mutable cursor = new BlockPos.Mutable(x, 0, z);
            int top = SelectiveRenderState.visibleColumnTop(
                    x, z, world.getTopY() - 1);
            int bottom = SelectiveRenderState.visibleColumnBottom(
                    x, z, world.getBottomY());
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
    }
}
