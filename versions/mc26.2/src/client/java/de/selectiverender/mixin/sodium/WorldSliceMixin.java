package de.selectiverender.mixin.sodium;

import de.selectiverender.SelectiveRenderState;
import de.selectiverender.SelectiveRenderSettings;
import de.selectiverender.TerrainSkyLightCache;
import org.spongepowered.asm.mixin.injection.Coerce;
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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.lighting.LightEngine;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
abstract class WorldSliceMixin {
    @Unique private static final int selectiverender$lightRadius = SelectiveRenderState.VIRTUAL_LIGHT_RADIUS;
    @Shadow @Final private ClientLevel level;
    @Shadow private BoundingBox volume;
    @Shadow public abstract BlockState getBlockState(int x, int y, int z);
    @Unique private byte[] selectiverender$virtualSkyLight;
    @Unique private byte[] selectiverender$cachedSkyLight;
    @Unique private TerrainSkyLightCache.Ticket selectiverender$lightTicket;
    @Unique private net.minecraft.world.level.chunk.LevelChunk[] selectiverender$sourceChunks;
    @Unique private int selectiverender$sourceChunkMinX;
    @Unique private int selectiverender$sourceChunkMinZ;
    @Unique private int selectiverender$sourceChunkWidth;
    @Unique private byte[] selectiverender$queuedLight;
    @Unique private byte[] selectiverender$lightOpacity;
    @Unique private byte[] selectiverender$lightVisible;
    @Unique private BlockState[] selectiverender$lightStates;
    @Unique private int[] selectiverender$lightQueue;
    @Unique private int selectiverender$lightMinX;
    @Unique private int selectiverender$lightMinY;
    @Unique private int selectiverender$lightMinZ;
    @Unique private int selectiverender$lightSizeX;
    @Unique private int selectiverender$lightSizeY;
    @Unique private int selectiverender$lightSizeZ;
    @Unique private boolean selectiverender$virtualSkyPrepared;
    @Unique private boolean selectiverender$vanillaSkyUnchanged;
    @Unique private boolean selectiverender$sourceRead;
    @Unique private static final Direction[] selectiverender$directions = Direction.values();

    @Inject(method = "copyData", at = @At("HEAD"))
    private void selectiverender$clearLightCache(@Coerce Object context, CallbackInfo ci) {
        selectiverender$virtualSkyPrepared = false;
        selectiverender$vanillaSkyUnchanged = false;
        selectiverender$cachedSkyLight = null;
        selectiverender$sourceChunks = null;
        selectiverender$lightTicket = SelectiveRenderState.filteringActive()
                ? TerrainSkyLightCache.INSTANCE.ticket(context) : null;
    }

    @Inject(method = "prepare", at = @At("RETURN"))
    private static void selectiverender$captureLightSnapshot(CallbackInfoReturnable<Object> cir) {
        if (SelectiveRenderState.filteringActive()) {
            TerrainSkyLightCache.INSTANCE.capture(cir.getReturnValue(),
                    SelectiveRenderState.visibilityGeneration(), selectiverender$lightPolicy());
        }
    }

    @Unique
    private static int selectiverender$lightPolicy() {
        return SelectiveRenderSettings.virtualLightMode().ordinal() * 4
                + SelectiveRenderSettings.hiddenVirtualLightMode().ordinal();
    }

    @Inject(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterBlockState(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (selectiverender$sourceRead || !SelectiveRenderState.filteringActive()) return;
        if (!SelectiveRenderState.shouldRender(x, y, z)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    @Inject(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"), cancellable = true, remap = true)
    private void selectiverender$filterBlockByRule(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (selectiverender$sourceRead || !SelectiveRenderState.filteringActive()) return;
        if (!SelectiveRenderState.shouldRender(cir.getReturnValue(), x, y, z)) cir.setReturnValue(Blocks.AIR.defaultBlockState());
    }

    @Inject(method = "getBrightness(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"), cancellable = true)
    private void selectiverender$filterLightLevel(LightLayer type, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!SelectiveRenderState.filteringActive()) return;
        if (!SelectiveRenderState.shouldRender(pos)) {
            cir.setReturnValue(type == LightLayer.SKY && level.dimensionType().hasSkyLight() ? 15 : 0);
        } else if (type == LightLayer.SKY && level.dimensionType().hasSkyLight()
                && cir.getReturnValueI() < 15) {
            int virtualLight = selectiverender$getVirtualSkyLight(pos);
            if (virtualLight >= 0) cir.setReturnValue(Math.max(cir.getReturnValueI(), virtualLight));
        }
    }

    @Inject(method = "getRawBrightness(Lnet/minecraft/core/BlockPos;I)I", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterBaseLightLevel(BlockPos pos, int ambientDarkness,
                                                       CallbackInfoReturnable<Integer> cir) {
        if (!SelectiveRenderState.filteringActive()) return;
        if (!SelectiveRenderState.shouldRender(pos)) {
            cir.setReturnValue(level.dimensionType().hasSkyLight()
                    ? Math.max(0, 15 - ambientDarkness) : 0);
        }
    }

    @Inject(method = "getRawBrightness(Lnet/minecraft/core/BlockPos;I)I", at = @At("RETURN"), cancellable = true)
    private void selectiverender$applyVirtualBaseLight(BlockPos pos, int ambientDarkness,
                                                        CallbackInfoReturnable<Integer> cir) {
        if (!SelectiveRenderState.filteringActive()) return;
        int maximumSkyLight = Math.max(0, 15 - ambientDarkness);
        if (!level.dimensionType().hasSkyLight() || cir.getReturnValueI() >= maximumSkyLight) return;
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
        if (!level.dimensionType().hasSkyLight()) return -1;
        if (!SelectiveRenderState.mayNeedVirtualSkyLight(pos.getX(), pos.getZ())) return -1;
        if (!SelectiveRenderState.shouldRender(pos)) return -1;

        if (!selectiverender$virtualSkyPrepared) selectiverender$prepareVirtualSkyLight();
        if (selectiverender$vanillaSkyUnchanged) return -1;
        int localX = pos.getX() - selectiverender$lightMinX;
        int localY = pos.getY() - selectiverender$lightMinY;
        int localZ = pos.getZ() - selectiverender$lightMinZ;
        if (localX < 0 || localX >= selectiverender$lightSizeX
                || localY < 0 || localY >= selectiverender$lightSizeY
                || localZ < 0 || localZ >= selectiverender$lightSizeZ) return 0;
        return Byte.toUnsignedInt((selectiverender$cachedSkyLight == null
                ? selectiverender$virtualSkyLight : selectiverender$cachedSkyLight)[
                selectiverender$lightIndex(localX, localY, localZ)]);
    }

    @Unique
    private void selectiverender$prepareVirtualSkyLight() {
        selectiverender$virtualSkyPrepared = true;
        int minX = volume.minX() - selectiverender$lightRadius;
        int maxX = volume.maxX() + selectiverender$lightRadius;
        int minY = Math.max(level.getMinY(), volume.minY() - selectiverender$lightRadius);
        int maxY = Math.min(level.getMaxY(), volume.maxY() + selectiverender$lightRadius);
        int minZ = volume.minZ() - selectiverender$lightRadius;
        int maxZ = volume.maxZ() + selectiverender$lightRadius;
        selectiverender$lightMinX = minX;
        selectiverender$lightMinY = minY;
        selectiverender$lightMinZ = minZ;
        selectiverender$lightSizeX = maxX - minX + 1;
        selectiverender$lightSizeY = maxY - minY + 1;
        selectiverender$lightSizeZ = maxZ - minZ + 1;
        TerrainSkyLightCache.Key cacheKey = new TerrainSkyLightCache.Key(level,
                SelectiveRenderState.visibilityGeneration(), selectiverender$lightPolicy(),
                minX, minY, minZ, maxX, maxY, maxZ);
        TerrainSkyLightCache.Result cached = TerrainSkyLightCache.INSTANCE.get(
                cacheKey, selectiverender$lightTicket);
        if (cached != null) {
            selectiverender$vanillaSkyUnchanged = cached.vanilla();
            selectiverender$cachedSkyLight = cached.light();
            return;
        }
        if (selectiverender$canUseVanillaSky(minX, minY, minZ, maxX, maxZ)) {
            selectiverender$vanillaSkyUnchanged = true;
            TerrainSkyLightCache.INSTANCE.put(cacheKey, selectiverender$lightTicket, null, 0);
            return;
        }
        // The halo is mostly outside Sodium's copied slice. Resolve each live
        // chunk once instead of repeating a world/provider lookup for every cell.
        selectiverender$sourceChunkMinX = minX >> 4;
        selectiverender$sourceChunkMinZ = minZ >> 4;
        selectiverender$sourceChunkWidth = (maxX >> 4) - selectiverender$sourceChunkMinX + 1;
        int chunkDepth = (maxZ >> 4) - selectiverender$sourceChunkMinZ + 1;
        selectiverender$sourceChunks = new net.minecraft.world.level.chunk.LevelChunk[selectiverender$sourceChunkWidth * chunkDepth];
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
        if (selectiverender$lightVisible == null || selectiverender$lightVisible.length < cellCount) {
            selectiverender$lightVisible = new byte[cellCount];
        }
        if (selectiverender$lightStates == null || selectiverender$lightStates.length < cellCount) {
            selectiverender$lightStates = new BlockState[cellCount];
        }
        int queueHead = 0;
        int queueTail = 0;
        int queueSize = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    int index = selectiverender$lightIndex(x - minX, y - minY, z - minZ);
                    BlockState state = selectiverender$sourceState(cursor, x, y, z);
                    selectiverender$lightStates[index] = state;
                    selectiverender$lightVisible[index] = (byte)
                            (SelectiveRenderState.shouldRender(x, y, z) ? 1 : 0);
                    selectiverender$lightOpacity[index] = (byte)
                            selectiverender$sourceOpacity(state, cursor);
                }
            }
        }

        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int localX = x - minX;
                int localZ = z - minZ;
                int worldSurface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                int visibleTop = SelectiveRenderState.visibleColumnTop(x, z,
                        Math.min(level.getMaxY(), worldSurface));
                if (!SelectiveRenderState.shouldScanVirtualSkyColumn(
                        visibleTop != Integer.MIN_VALUE)) continue;
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
                    int realisticOpacity = LightEngine.getLightDampeningInto(
                            aboveState, state, Direction.DOWN, opacity);
                    directLight = de.selectiverender.SkyLightColumn.passDown(
                            directLight, realisticOpacity);
                    above.set(x, y, z);
                    aboveState = state;
                    if (directLight <= 0) break;
                    if (y > maxY) continue;
                    int index = selectiverender$lightIndex(localX, y - minY, localZ);
                    if (!SelectiveRenderState.shouldSeedVirtualSkyColumn(
                            selectiverender$lightVisible[index] != 0, x, y, z)) continue;
                    selectiverender$virtualSkyLight[index] = (byte) directLight;
                    // Queue only useful sources after all direct columns are populated.
                }
            }
        }

        queueSize = de.selectiverender.VirtualLightPropagation.seedFrontier(
                selectiverender$virtualSkyLight, selectiverender$queuedLight,
                selectiverender$lightQueue, selectiverender$lightSizeX,
                selectiverender$lightSizeY, selectiverender$lightSizeZ);
        queueTail = queueSize % cellCount;

        BlockPos.MutableBlockPos currentPos = above;
        BlockPos.MutableBlockPos nextPos = cursor;
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
                int nextX = localX + direction.getStepX();
                int nextY = localY + direction.getStepY();
                int nextZ = localZ + direction.getStepZ();
                if (nextX < 0 || nextX >= selectiverender$lightSizeX
                        || nextY < 0 || nextY >= selectiverender$lightSizeY
                        || nextZ < 0 || nextZ >= selectiverender$lightSizeZ) continue;
                int nextIndex = selectiverender$lightIndex(nextX, nextY, nextZ);
                int existingLight = Byte.toUnsignedInt(selectiverender$virtualSkyLight[nextIndex]);
                if (!de.selectiverender.VirtualLightPropagation.canImprove(
                        currentLight, existingLight)) continue;
                int opacity = Byte.toUnsignedInt(selectiverender$lightOpacity[nextIndex]);
                if (!de.selectiverender.VirtualLightPropagation.canPass(
                        currentLight, existingLight, opacity)) continue;
                if (!SelectiveRenderState.shouldPropagateVirtualSkyLight(
                        selectiverender$lightVisible[currentIndex] != 0,
                        selectiverender$lightVisible[nextIndex] != 0,
                        currentPos.getX(), currentPos.getY(), currentPos.getZ(),
                        selectiverender$lightMinX + nextX,
                        selectiverender$lightMinY + nextY,
                        selectiverender$lightMinZ + nextZ)) continue;
                nextPos.set(selectiverender$lightMinX + nextX,
                        selectiverender$lightMinY + nextY,
                        selectiverender$lightMinZ + nextZ);
                int realisticOpacity = LightEngine.getLightDampeningInto(
                        currentState, selectiverender$lightStates[nextIndex],
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
        TerrainSkyLightCache.INSTANCE.put(cacheKey, selectiverender$lightTicket,
                selectiverender$virtualSkyLight, cellCount);
    }

    @Unique
    private boolean selectiverender$canUseVanillaSky(int minX, int minY, int minZ, int maxX, int maxZ) {
        int ceiling = SelectiveRenderState.unfilteredLightCeiling(minX, minY, minZ, maxX, maxZ);
        if (ceiling == Integer.MIN_VALUE) return false;
        if (ceiling == Integer.MAX_VALUE) return true;
        // WORLD_SURFACE includes every non-air block, including slabs, leaves and fluids.
        // A column extending beyond the enclosing region may hide an occluder: retain
        // the full solver in that case, even when the queried section is deep underground.
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                if (level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1 > ceiling) return false;
            }
        }
        return true;
    }

    @Unique
    private int selectiverender$lightIndex(int localX, int localY, int localZ) {
        return (localY * selectiverender$lightSizeZ + localZ) * selectiverender$lightSizeX + localX;
    }

    @Unique
    private BlockState selectiverender$sourceState(BlockPos.MutableBlockPos cursor, int x, int y, int z) {
        cursor.set(x, y, z);
        if (!SelectiveRenderState.shouldRender(cursor)) return Blocks.AIR.defaultBlockState();
        BlockState state = selectiverender$getSourceBlockState(cursor);
        return SelectiveRenderState.shouldRender(state, x, y, z) ? state : Blocks.AIR.defaultBlockState();
    }

    @Unique
    private int selectiverender$sourceOpacity(BlockState state, BlockPos pos) {
        return Math.min(15, Math.max(0, state.getLightDampening()));
    }

    @Unique
    private BlockState selectiverender$getSourceBlockState(BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        BlockState state = null;
        if (x >= volume.minX() && x <= volume.maxX()
                && y >= volume.minY() && y <= volume.maxY()
                && z >= volume.minZ() && z <= volume.maxZ()) {
            selectiverender$sourceRead = true;
            try {
                state = getBlockState(x, y, z);
            } finally {
                selectiverender$sourceRead = false;
            }
        }
        if (state == null && selectiverender$sourceChunks != null
                && y >= level.getMinY()
                && y <= level.getMaxY()) {
            int chunkX = (x >> 4) - selectiverender$sourceChunkMinX;
            int chunkZ = (z >> 4) - selectiverender$sourceChunkMinZ;
            int index = chunkZ * selectiverender$sourceChunkWidth + chunkX;
            if (chunkX >= 0 && chunkX < selectiverender$sourceChunkWidth
                    && chunkZ >= 0 && index < selectiverender$sourceChunks.length) {
                net.minecraft.world.level.chunk.LevelChunk chunk = selectiverender$sourceChunks[index];
                if (chunk == null) {
                    chunk = level.getChunk(x >> 4, z >> 4);
                    selectiverender$sourceChunks[index] = chunk;
                }
                state = chunk.getBlockState(pos);
            }
        }
        if (state == null) state = level.getBlockState(pos);
        return state == null ? Blocks.AIR.defaultBlockState() : state;
    }
}
