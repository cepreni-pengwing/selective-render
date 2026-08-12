package de.selectiverender.mixin.sodium;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockBox;
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

import java.util.Arrays;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.WorldSlice", remap = false)
abstract class WorldSliceMixin {
    @Unique private static final int selectiverender$lightRadius = 14;
    @Shadow @Final private ClientWorld world;
    @Shadow private BlockBox volume;
    @Unique private byte[] selectiverender$virtualSkyLight;
    @Unique private int[] selectiverender$lightQueue;
    @Unique private int selectiverender$lightMinX;
    @Unique private int selectiverender$lightMinY;
    @Unique private int selectiverender$lightMinZ;
    @Unique private int selectiverender$lightSizeX;
    @Unique private int selectiverender$lightSizeY;
    @Unique private int selectiverender$lightSizeZ;
    @Unique private boolean selectiverender$virtualSkyPrepared;
    @Unique private static final int[][] selectiverender$directions = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0},
            {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    @Inject(method = "copyData", at = @At("HEAD"))
    private void selectiverender$clearLightCache(CallbackInfo ci) {
        selectiverender$virtualSkyPrepared = false;
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
        int maximumSkyLight = Math.max(0, 15 - ambientDarkness);
        if (cir.getReturnValueI() >= maximumSkyLight) return;
        int virtualLight = selectiverender$getVirtualSkyLight(pos);
        if (virtualLight >= 0) {
            cir.setReturnValue(Math.max(cir.getReturnValueI(), Math.max(0, virtualLight - ambientDarkness)));
        }
    }

    @Unique
    private int selectiverender$getVirtualSkyLight(BlockPos pos) {
        if (!SelectiveRenderState.enabled() && !SelectiveRenderState.hideEnabled()) {
            return -1;
        }
        if (!SelectiveRenderState.mayNeedVirtualSkyLight(
                pos.getX(), pos.getZ(), selectiverender$lightRadius)) return -1;
        if (!SelectiveRenderState.shouldRender(pos)) return -1;

        int highest = SelectiveRenderState.highestVisibleOccluder(world, pos.getX(), pos.getZ());
        if (pos.getY() > highest) return 15;
        if (pos.getY() == highest
                && !world.getBlockState(pos).isOpaqueFullCube(world, pos)) return 15;

        if (!selectiverender$virtualSkyPrepared) selectiverender$prepareVirtualSkyLight();
        int localX = pos.getX() - selectiverender$lightMinX;
        int localY = pos.getY() - selectiverender$lightMinY;
        int localZ = pos.getZ() - selectiverender$lightMinZ;
        if (localX < 0 || localX >= selectiverender$lightSizeX
                || localY < 0 || localY >= selectiverender$lightSizeY
                || localZ < 0 || localZ >= selectiverender$lightSizeZ) return 0;
        return Byte.toUnsignedInt(selectiverender$virtualSkyLight[
                selectiverender$lightIndex(localX, localY, localZ)]);
    }

    @Unique
    private void selectiverender$prepareVirtualSkyLight() {
        selectiverender$virtualSkyPrepared = true;
        int minX = volume.getMinX() - selectiverender$lightRadius;
        int maxX = volume.getMaxX() + selectiverender$lightRadius;
        int minY = Math.max(world.getBottomY(), volume.getMinY() - selectiverender$lightRadius);
        int maxY = Math.min(world.getTopY() - 1, volume.getMaxY() + selectiverender$lightRadius);
        int minZ = volume.getMinZ() - selectiverender$lightRadius;
        int maxZ = volume.getMaxZ() + selectiverender$lightRadius;
        selectiverender$lightMinX = minX;
        selectiverender$lightMinY = minY;
        selectiverender$lightMinZ = minZ;
        selectiverender$lightSizeX = maxX - minX + 1;
        selectiverender$lightSizeY = maxY - minY + 1;
        selectiverender$lightSizeZ = maxZ - minZ + 1;
        int cellCount = selectiverender$lightSizeX * selectiverender$lightSizeY * selectiverender$lightSizeZ;
        if (selectiverender$virtualSkyLight == null || selectiverender$virtualSkyLight.length < cellCount) {
            selectiverender$virtualSkyLight = new byte[cellCount];
        } else {
            Arrays.fill(selectiverender$virtualSkyLight, 0, cellCount, (byte) 0);
        }
        if (selectiverender$lightQueue == null || selectiverender$lightQueue.length < cellCount) {
            selectiverender$lightQueue = new int[cellCount];
        }
        int queueHead = 0;
        int queueTail = 0;
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int highest = SelectiveRenderState.highestVisibleOccluder(world, x, z);
                int sourceY = highest == Integer.MIN_VALUE ? minY : Math.max(minY, highest + 1);
                if (sourceY > maxY) continue;
                int localX = x - minX;
                int localZ = z - minZ;
                if (highest >= minY && highest <= maxY) {
                    cursor.set(x, highest, z);
                    if (!world.getBlockState(cursor).isOpaqueFullCube(world, cursor)) {
                        selectiverender$virtualSkyLight[
                                selectiverender$lightIndex(localX, highest - minY, localZ)] = 15;
                    }
                }
                for (int y = sourceY; y <= maxY; y++) {
                    int index = selectiverender$lightIndex(localX, y - minY, localZ);
                    selectiverender$virtualSkyLight[index] = 15;
                    selectiverender$lightQueue[queueTail++] = index;
                }
            }
        }

        while (queueHead < queueTail) {
            int currentIndex = selectiverender$lightQueue[queueHead++];
            int currentLight = Byte.toUnsignedInt(selectiverender$virtualSkyLight[currentIndex]);
            if (currentLight <= 1) continue;
            int nextLight = currentLight - 1;
            int localX = currentIndex % selectiverender$lightSizeX;
            int yz = currentIndex / selectiverender$lightSizeX;
            int localZ = yz % selectiverender$lightSizeZ;
            int localY = yz / selectiverender$lightSizeZ;
            for (int[] direction : selectiverender$directions) {
                int nextX = localX + direction[0];
                int nextY = localY + direction[1];
                int nextZ = localZ + direction[2];
                if (nextX < 0 || nextX >= selectiverender$lightSizeX
                        || nextY < 0 || nextY >= selectiverender$lightSizeY
                        || nextZ < 0 || nextZ >= selectiverender$lightSizeZ) continue;
                int nextIndex = selectiverender$lightIndex(nextX, nextY, nextZ);
                if (Byte.toUnsignedInt(selectiverender$virtualSkyLight[nextIndex]) >= nextLight
                        || !selectiverender$isOpenStep(cursor,
                        nextX + minX, nextY + minY, nextZ + minZ)) continue;
                selectiverender$virtualSkyLight[nextIndex] = (byte) nextLight;
                selectiverender$lightQueue[queueTail++] = nextIndex;
            }
        }
    }

    @Unique
    private int selectiverender$lightIndex(int localX, int localY, int localZ) {
        return (localY * selectiverender$lightSizeZ + localZ) * selectiverender$lightSizeX + localX;
    }

    @Unique
    private boolean selectiverender$isOpenStep(BlockPos.Mutable cursor, int x, int y, int z) {
        cursor.set(x, y, z);
        return !SelectiveRenderState.shouldRender(cursor)
                || world.getBlockState(cursor).getOpacity(world, cursor) == 0;
    }
}
