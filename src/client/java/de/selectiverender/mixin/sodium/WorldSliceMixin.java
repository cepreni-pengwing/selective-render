package de.selectiverender.mixin.sodium;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.light.ChunkLightProvider;
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
    @Unique private BlockState[] selectiverender$lightStates;
    @Unique private int[] selectiverender$lightQueue;
    @Unique private int selectiverender$lightMinX;
    @Unique private int selectiverender$lightMinY;
    @Unique private int selectiverender$lightMinZ;
    @Unique private int selectiverender$lightSizeX;
    @Unique private int selectiverender$lightSizeY;
    @Unique private int selectiverender$lightSizeZ;
    @Unique private boolean selectiverender$virtualSkyPrepared;
    @Unique private boolean selectiverender$sourceRead;
    @Unique private static final Direction[] selectiverender$directions = Direction.values();

    @Inject(method = "copyData", at = @At("HEAD"))
    private void selectiverender$clearLightCache(CallbackInfo ci) {
        selectiverender$virtualSkyPrepared = false;
    }

    @Inject(method = "getBlockState(III)Lnet/minecraft/block/BlockState;", at = @At("HEAD"), cancellable = true, remap = true)
    private void selectiverender$filterBlockState(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (selectiverender$sourceRead || !SelectiveRenderState.filteringActive()) return;
        if (!SelectiveRenderState.shouldRender(x, y, z)) {
            cir.setReturnValue(Blocks.AIR.getDefaultState());
        }
    }

    @Inject(method = "getLightLevel(Lnet/minecraft/world/LightType;Lnet/minecraft/util/math/BlockPos;)I", at = @At("RETURN"), cancellable = true, remap = true)
    private void selectiverender$filterLightLevel(LightType type, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!SelectiveRenderState.filteringActive()) return;
        if (!SelectiveRenderState.shouldRender(pos)) {
            cir.setReturnValue(type == LightType.SKY && world.getDimension().hasSkyLight() ? 15 : 0);
        } else if (type == LightType.SKY && world.getDimension().hasSkyLight()
                && cir.getReturnValueI() < 15) {
            int virtualLight = selectiverender$getVirtualSkyLight(pos);
            if (virtualLight >= 0) cir.setReturnValue(Math.max(cir.getReturnValueI(), virtualLight));
        }
    }

    @Inject(method = "getBaseLightLevel(Lnet/minecraft/util/math/BlockPos;I)I", at = @At("HEAD"), cancellable = true, remap = true)
    private void selectiverender$filterBaseLightLevel(BlockPos pos, int ambientDarkness,
                                                       CallbackInfoReturnable<Integer> cir) {
        if (!SelectiveRenderState.filteringActive()) return;
        if (!SelectiveRenderState.shouldRender(pos)) {
            cir.setReturnValue(world.getDimension().hasSkyLight()
                    ? Math.max(0, 15 - ambientDarkness) : 0);
        }
    }

    @Inject(method = "getBaseLightLevel(Lnet/minecraft/util/math/BlockPos;I)I", at = @At("RETURN"), cancellable = true, remap = true)
    private void selectiverender$applyVirtualBaseLight(BlockPos pos, int ambientDarkness,
                                                        CallbackInfoReturnable<Integer> cir) {
        if (!SelectiveRenderState.filteringActive()) return;
        int maximumSkyLight = Math.max(0, 15 - ambientDarkness);
        if (!world.getDimension().hasSkyLight() || cir.getReturnValueI() >= maximumSkyLight) return;
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
        if (!world.getDimension().hasSkyLight()) return -1;
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
        if (selectiverender$lightStates == null || selectiverender$lightStates.length < cellCount) {
            selectiverender$lightStates = new BlockState[cellCount];
        }
        int queueHead = 0;
        int queueTail = 0;
        int queueSize = 0;
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    int index = selectiverender$lightIndex(x - minX, y - minY, z - minZ);
                    BlockState state = selectiverender$sourceState(cursor, x, y, z);
                    selectiverender$lightStates[index] = state;
                    selectiverender$lightOpacity[index] = (byte)
                            selectiverender$sourceOpacity(state, cursor);
                }
            }
        }

        BlockPos.Mutable above = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int localX = x - minX;
                int localZ = z - minZ;
                int worldSurface = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
                int visibleTop = SelectiveRenderState.visibleColumnTop(x, z,
                        Math.min(world.getTopY() - 1, worldSurface));
                int scanTop = visibleTop == Integer.MIN_VALUE ? maxY : Math.max(maxY, visibleTop);
                int directLight = 15;
                BlockState aboveState = selectiverender$sourceState(above, x, scanTop + 1, z);
                for (int y = scanTop; y >= minY; y--) {
                    cursor.set(x, y, z);
                    BlockState state;
                    int opacity;
                    if (y <= maxY) {
                        int index = selectiverender$lightIndex(localX, y - minY, localZ);
                        state = selectiverender$lightStates[index];
                        opacity = Byte.toUnsignedInt(selectiverender$lightOpacity[index]);
                    } else {
                        state = selectiverender$sourceState(cursor, x, y, z);
                        opacity = selectiverender$sourceOpacity(state, cursor);
                    }
                    int realisticOpacity = ChunkLightProvider.getRealisticOpacity(
                            world, aboveState, above, state, cursor, Direction.DOWN, opacity);
                    directLight = de.selectiverender.SkyLightColumn.passDown(
                            directLight, realisticOpacity);
                    above.set(x, y, z);
                    aboveState = state;
                    if (directLight <= 0) break;
                    if (y > maxY) continue;
                    int index = selectiverender$lightIndex(localX, y - minY, localZ);
                    selectiverender$virtualSkyLight[index] = (byte) directLight;
                    selectiverender$lightQueue[queueTail] = index;
                    queueTail = (queueTail + 1) % cellCount;
                    selectiverender$queuedLight[index] = 1;
                    queueSize++;
                }
            }
        }

        BlockPos.Mutable currentPos = above;
        BlockPos.Mutable nextPos = cursor;
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
            currentPos.set(selectiverender$lightMinX + localX,
                    selectiverender$lightMinY + localY,
                    selectiverender$lightMinZ + localZ);
            BlockState currentState = selectiverender$lightStates[currentIndex];
            for (Direction direction : selectiverender$directions) {
                int nextX = localX + direction.getOffsetX();
                int nextY = localY + direction.getOffsetY();
                int nextZ = localZ + direction.getOffsetZ();
                if (nextX < 0 || nextX >= selectiverender$lightSizeX
                        || nextY < 0 || nextY >= selectiverender$lightSizeY
                        || nextZ < 0 || nextZ >= selectiverender$lightSizeZ) continue;
                int nextIndex = selectiverender$lightIndex(nextX, nextY, nextZ);
                int existingLight = Byte.toUnsignedInt(selectiverender$virtualSkyLight[nextIndex]);
                if (!de.selectiverender.VirtualLightPropagation.canImprove(
                        currentLight, existingLight)) continue;
                int opacity = Byte.toUnsignedInt(selectiverender$lightOpacity[nextIndex]);
                nextPos.set(selectiverender$lightMinX + nextX,
                        selectiverender$lightMinY + nextY,
                        selectiverender$lightMinZ + nextZ);
                int realisticOpacity = ChunkLightProvider.getRealisticOpacity(
                        world, currentState, currentPos,
                        selectiverender$lightStates[nextIndex], nextPos,
                        direction, Math.max(1, opacity));
                int nextLight = currentLight - realisticOpacity;
                if (nextLight <= existingLight) continue;
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
    private BlockState selectiverender$sourceState(BlockPos.Mutable cursor, int x, int y, int z) {
        cursor.set(x, y, z);
        if (!SelectiveRenderState.shouldRender(cursor)) return Blocks.AIR.getDefaultState();
        return selectiverender$getSourceBlockState(cursor);
    }

    @Unique
    private int selectiverender$sourceOpacity(BlockState state, BlockPos pos) {
        return Math.min(15, Math.max(0, state.getOpacity(world, pos)));
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
