package de.selectiverender.mixin.sodium;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockBox;
import net.minecraft.world.LightType;
import net.minecraft.world.Heightmap;
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
    @Unique private static final int selectiverender$lightRadius = SelectiveRenderState.VIRTUAL_LIGHT_RADIUS;
    @Shadow @Final private ClientWorld world;
    @Shadow private BlockBox volume;
    @Shadow public abstract BlockState getBlockState(int x, int y, int z);
    @Unique private byte[] selectiverender$virtualSkyLight;
    @Unique private byte[] selectiverender$queuedLight;
    @Unique private byte[] selectiverender$lightOpacity;
    @Unique private int[] selectiverender$lightQueue;
    @Unique private int selectiverender$lightMinX;
    @Unique private int selectiverender$lightMinY;
    @Unique private int selectiverender$lightMinZ;
    @Unique private int selectiverender$lightSizeX;
    @Unique private int selectiverender$lightSizeY;
    @Unique private int selectiverender$lightSizeZ;
    @Unique private boolean selectiverender$virtualSkyPrepared;
    @Unique private boolean selectiverender$sourceRead;
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
        if (selectiverender$sourceRead) return;
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
        if (selectiverender$queuedLight == null || selectiverender$queuedLight.length < cellCount) {
            selectiverender$queuedLight = new byte[cellCount];
        } else {
            Arrays.fill(selectiverender$queuedLight, 0, cellCount, (byte) 0);
        }
        if (selectiverender$lightOpacity == null || selectiverender$lightOpacity.length < cellCount) {
            selectiverender$lightOpacity = new byte[cellCount];
        }
        int queueHead = 0;
        int queueTail = 0;
        int queueSize = 0;
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    selectiverender$lightOpacity[selectiverender$lightIndex(
                            x - minX, y - minY, z - minZ)] = (byte)
                            selectiverender$sourceOpacity(cursor, x, y, z);
                }
            }
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int localX = x - minX;
                int localZ = z - minZ;
                int worldSurface = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
                int visibleTop = SelectiveRenderState.visibleColumnTop(x, z,
                        Math.min(world.getTopY() - 1, worldSurface));
                int scanTop = visibleTop == Integer.MIN_VALUE ? maxY : Math.max(maxY, visibleTop);
                int directLight = 15;
                for (int y = scanTop; y >= minY; y--) {
                    directLight = de.selectiverender.SkyLightColumn.passDown(directLight,
                            y <= maxY
                                    ? Byte.toUnsignedInt(selectiverender$lightOpacity[
                                    selectiverender$lightIndex(localX, y - minY, localZ)])
                                    : selectiverender$sourceOpacity(cursor, x, y, z));
                    if (y > maxY || directLight <= 0) continue;
                    int index = selectiverender$lightIndex(localX, y - minY, localZ);
                    selectiverender$virtualSkyLight[index] = (byte) directLight;
                    selectiverender$lightQueue[queueTail] = index;
                    queueTail = (queueTail + 1) % cellCount;
                    selectiverender$queuedLight[index] = 1;
                    queueSize++;
                }
            }
        }

        while (queueSize > 0) {
            int currentIndex = selectiverender$lightQueue[queueHead];
            queueHead = (queueHead + 1) % cellCount;
            queueSize--;
            selectiverender$queuedLight[currentIndex] = 0;
            int currentLight = Byte.toUnsignedInt(selectiverender$virtualSkyLight[currentIndex]);
            if (currentLight <= 1) continue;
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
                int opacity = Byte.toUnsignedInt(selectiverender$lightOpacity[nextIndex]);
                int nextLight = currentLight - Math.max(1, opacity);
                if (nextLight <= 0
                        || Byte.toUnsignedInt(selectiverender$virtualSkyLight[nextIndex]) >= nextLight) continue;
                selectiverender$virtualSkyLight[nextIndex] = (byte) nextLight;
                if (selectiverender$queuedLight[nextIndex] == 0) {
                    selectiverender$lightQueue[queueTail] = nextIndex;
                    queueTail = (queueTail + 1) % cellCount;
                    selectiverender$queuedLight[nextIndex] = 1;
                    queueSize++;
                }
            }
        }
    }

    @Unique
    private int selectiverender$lightIndex(int localX, int localY, int localZ) {
        return (localY * selectiverender$lightSizeZ + localZ) * selectiverender$lightSizeX + localX;
    }

    @Unique
    private int selectiverender$sourceOpacity(BlockPos.Mutable cursor, int x, int y, int z) {
        cursor.set(x, y, z);
        if (!SelectiveRenderState.shouldRender(cursor)) return 0;
        return Math.min(15, Math.max(0,
                selectiverender$getSourceBlockState(cursor).getOpacity(world, cursor)));
    }

    @Unique
    private BlockState selectiverender$getSourceBlockState(BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        BlockState state = null;
        if (x >= volume.getMinX() && x <= volume.getMaxX()
                && y >= volume.getMinY() && y <= volume.getMaxY()
                && z >= volume.getMinZ() && z <= volume.getMaxZ()) {
            selectiverender$sourceRead = true;
            try {
                state = getBlockState(x, y, z);
            } finally {
                selectiverender$sourceRead = false;
            }
        }
        if (state == null) state = world.getBlockState(pos);
        return state == null ? Blocks.AIR.getDefaultState() : state;
    }
}
