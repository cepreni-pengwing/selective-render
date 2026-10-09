package de.selectiverender.mixin.sodium;

import de.selectiverender.SelectiveRenderState;
import de.selectiverender.SelectiveRenderSettings;
import de.selectiverender.TerrainSkyLightCache;
import de.selectiverender.PerformanceDiagnostics;
import org.spongepowered.asm.mixin.injection.Coerce;
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
    @Unique private byte[] selectiverender$cachedSkyLight;
    @Unique private TerrainSkyLightCache.Ticket selectiverender$lightTicket;
    @Unique private net.minecraft.world.chunk.WorldChunk[] selectiverender$sourceChunks;
    @Unique private BlockState[][] selectiverender$sourceSections;
    @Unique private byte[] selectiverender$sourceSectionStatus;
    @Unique private int selectiverender$sourceSectionMinY;
    @Unique private int selectiverender$sourceSectionCountY;
    @Unique private int selectiverender$sourceChunkMinX;
    @Unique private int selectiverender$sourceChunkMinZ;
    @Unique private int selectiverender$sourceChunkWidth;
    @Unique private boolean selectiverender$diagnosticReads;
    @Unique private long selectiverender$copiedReads, selectiverender$liveReads, selectiverender$chunkLookups;
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
        selectiverender$sourceSections = null;
        selectiverender$sourceSectionStatus = null;
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

    @Inject(method = "getBlockState(III)Lnet/minecraft/block/BlockState;", at = @At("HEAD"), cancellable = true, remap = true)
    private void selectiverender$filterBlockState(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (selectiverender$sourceRead || !SelectiveRenderState.filteringActive()) return;
        if (!SelectiveRenderState.shouldRender(x, y, z)) {
            cir.setReturnValue(Blocks.AIR.getDefaultState());
        }
    }

    @Inject(method = "getBlockState(III)Lnet/minecraft/block/BlockState;", at = @At("RETURN"), cancellable = true, remap = true)
    private void selectiverender$filterBlockByRule(int x, int y, int z,
                                                    CallbackInfoReturnable<BlockState> cir) {
        if (selectiverender$sourceRead || !SelectiveRenderState.filteringActive()) return;
        if (!SelectiveRenderState.shouldRender(cir.getReturnValue(), x, y, z)) {
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
        long totalStarted = PerformanceDiagnostics.startTimer();
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
        TerrainSkyLightCache.Key cacheKey = new TerrainSkyLightCache.Key(world,
                SelectiveRenderState.visibilityGeneration(), selectiverender$lightPolicy(),
                minX, minY, minZ, maxX, maxY, maxZ);
        long phaseStarted = PerformanceDiagnostics.startTimer();
        TerrainSkyLightCache.Result cached = TerrainSkyLightCache.INSTANCE.get(
                cacheKey, selectiverender$lightTicket);
        PerformanceDiagnostics.finish(PerformanceDiagnostics.Metric.TERRAIN_CACHE_LOOKUP,
                phaseStarted, 0, minX, minY, minZ);
        if (cached != null) {
            selectiverender$vanillaSkyUnchanged = cached.vanilla();
            selectiverender$cachedSkyLight = cached.light();
            return;
        }
        phaseStarted = PerformanceDiagnostics.startTimer();
        boolean vanilla = selectiverender$canUseVanillaSky(minX, minY, minZ, maxX, maxZ);
        PerformanceDiagnostics.finish(PerformanceDiagnostics.Metric.TERRAIN_FAST_PATH_CHECK,
                phaseStarted, 0, minX, minY, minZ);
        if (vanilla) {
            PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.TERRAIN_VANILLA_FAST_PATH, 1);
            selectiverender$vanillaSkyUnchanged = true;
            selectiverender$publishLight(cacheKey, null, 0);
            return;
        }
        // The halo is mostly outside Sodium's copied slice. Resolve each live
        // chunk once instead of repeating a world/provider lookup for every cell.
        selectiverender$sourceChunkMinX = minX >> 4;
        selectiverender$sourceChunkMinZ = minZ >> 4;
        selectiverender$sourceChunkWidth = (maxX >> 4) - selectiverender$sourceChunkMinX + 1;
        int chunkDepth = (maxZ >> 4) - selectiverender$sourceChunkMinZ + 1;
        selectiverender$sourceChunks = new net.minecraft.world.chunk.WorldChunk[selectiverender$sourceChunkWidth * chunkDepth];
        selectiverender$sourceSectionMinY = minY >> 4;
        selectiverender$sourceSectionCountY = (maxY >> 4) - selectiverender$sourceSectionMinY + 1;
        selectiverender$sourceSections = new BlockState[
                selectiverender$sourceChunkWidth * chunkDepth * selectiverender$sourceSectionCountY][];
        selectiverender$sourceSectionStatus = new byte[selectiverender$sourceSections.length];
        int cellCount = selectiverender$lightSizeX * selectiverender$lightSizeY * selectiverender$lightSizeZ;
        selectiverender$diagnosticReads = totalStarted != 0;
        selectiverender$copiedReads = selectiverender$liveReads = selectiverender$chunkLookups = 0;
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
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        phaseStarted = PerformanceDiagnostics.startTimer();
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

        PerformanceDiagnostics.finish(PerformanceDiagnostics.Metric.TERRAIN_STATES,
                phaseStarted, cellCount, minX, minY, minZ);
        phaseStarted = PerformanceDiagnostics.startTimer();
        BlockPos.Mutable above = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int localX = x - minX;
                int localZ = z - minZ;
                int worldSurface = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
                int visibleTop = SelectiveRenderState.visibleColumnTop(x, z,
                        Math.min(world.getTopY() - 1, worldSurface));
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
                    int realisticOpacity = ChunkLightProvider.getRealisticOpacity(
                            world, aboveState, above, state, cursor, Direction.DOWN, opacity);
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

        PerformanceDiagnostics.finish(PerformanceDiagnostics.Metric.TERRAIN_COLUMNS,
                phaseStarted, (long) selectiverender$lightSizeX * selectiverender$lightSizeZ, minX, minY, minZ);
        phaseStarted = PerformanceDiagnostics.startTimer();
        queueSize = de.selectiverender.VirtualLightPropagation.seedFrontier(
                selectiverender$virtualSkyLight, selectiverender$queuedLight,
                selectiverender$lightQueue, selectiverender$lightSizeX,
                selectiverender$lightSizeY, selectiverender$lightSizeZ);
        queueTail = queueSize % cellCount;
        PerformanceDiagnostics.finish(PerformanceDiagnostics.Metric.TERRAIN_FRONTIER,
                phaseStarted, queueSize, minX, minY, minZ);
        phaseStarted = PerformanceDiagnostics.startTimer();
        int propagatedCells = 0;

        BlockPos.Mutable currentPos = above;
        BlockPos.Mutable nextPos = cursor;
        while (queueSize > 0) {
            if (phaseStarted != 0) propagatedCells++;
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
        PerformanceDiagnostics.finish(PerformanceDiagnostics.Metric.TERRAIN_PROPAGATION,
                phaseStarted, propagatedCells, minX, minY, minZ);
        selectiverender$publishLight(cacheKey, selectiverender$virtualSkyLight, cellCount);
        PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.TERRAIN_COPIED_READ, selectiverender$copiedReads);
        PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.TERRAIN_LIVE_READ, selectiverender$liveReads);
        PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.TERRAIN_CHUNK_LOOKUP, selectiverender$chunkLookups);
        PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.TERRAIN_PROPAGATED_CELLS, propagatedCells);
        PerformanceDiagnostics.finish(PerformanceDiagnostics.Metric.TERRAIN_BUILD,
                totalStarted, cellCount, minX, minY, minZ);
        selectiverender$diagnosticReads = false;
    }

    @Unique
    private void selectiverender$publishLight(TerrainSkyLightCache.Key key, byte[] light, int length) {
        long started = PerformanceDiagnostics.startTimer();
        TerrainSkyLightCache.INSTANCE.put(key, selectiverender$lightTicket, light, length);
        PerformanceDiagnostics.finish(PerformanceDiagnostics.Metric.TERRAIN_CACHE_WRITE,
                started, length, key.minX(), key.minY(), key.minZ());
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
                if (world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1 > ceiling) return false;
            }
        }
        return true;
    }

    @Unique
    private int selectiverender$lightIndex(int localX, int localY, int localZ) {
        return (localY * selectiverender$lightSizeZ + localZ) * selectiverender$lightSizeX + localX;
    }

    @Unique
    private BlockState selectiverender$sourceState(BlockPos.Mutable cursor, int x, int y, int z) {
        cursor.set(x, y, z);
        if (!SelectiveRenderState.shouldRender(cursor)) return Blocks.AIR.getDefaultState();
        BlockState state = selectiverender$getSourceBlockState(cursor);
        if (state == null) state = Blocks.AIR.getDefaultState();
        return SelectiveRenderState.shouldRender(state, x, y, z) ? state : Blocks.AIR.getDefaultState();
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
                if (selectiverender$diagnosticReads) selectiverender$copiedReads++;
                state = getBlockState(x, y, z);
            } finally {
                selectiverender$sourceRead = false;
            }
        }
        if (state == null && selectiverender$sourceChunks != null
                && y >= world.getBottomY()
                && y < world.getTopY()) {
            int chunkX = (x >> 4) - selectiverender$sourceChunkMinX;
            int chunkZ = (z >> 4) - selectiverender$sourceChunkMinZ;
            int index = chunkZ * selectiverender$sourceChunkWidth + chunkX;
            if (chunkX >= 0 && chunkX < selectiverender$sourceChunkWidth
                    && chunkZ >= 0 && index < selectiverender$sourceChunks.length) {
                net.minecraft.world.chunk.WorldChunk chunk = selectiverender$sourceChunks[index];
                if (chunk == null) {
                    if (selectiverender$diagnosticReads) selectiverender$chunkLookups++;
                    chunk = world.getChunk(x >> 4, z >> 4);
                    selectiverender$sourceChunks[index] = chunk;
                }
                if (selectiverender$diagnosticReads) selectiverender$liveReads++;
                int sectionY = y >> 4;
                int localSectionY = sectionY - selectiverender$sourceSectionMinY;
                if (localSectionY >= 0 && localSectionY < selectiverender$sourceSectionCountY) {
                    int sectionIndex = localSectionY * selectiverender$sourceChunks.length + index;
                    if (selectiverender$sourceSectionStatus[sectionIndex] == 0) {
                        selectiverender$sourceSectionStatus[sectionIndex] = 2;
                        net.minecraft.world.chunk.WorldChunk sourceChunk = chunk;
                        selectiverender$sourceSections[sectionIndex] = TerrainSkyLightCache.INSTANCE.sourceSection(
                                world, SelectiveRenderState.visibilityGeneration(), selectiverender$lightPolicy(),
                                x >> 4, sectionY, z >> 4, selectiverender$lightTicket,
                                () -> selectiverender$captureSourceSection(sourceChunk, sectionY));
                        if (selectiverender$sourceSections[sectionIndex] != null) {
                            selectiverender$sourceSectionStatus[sectionIndex] = 1;
                        }
                    }
                    BlockState[] section = selectiverender$sourceSections[sectionIndex];
                    if (section != null) {
                        int sectionBlockIndex = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
                        BlockState cachedState = section[sectionBlockIndex];
                        if (cachedState != null) return cachedState;
                        // A partial/unavailable chunk section may contain nulls. Fall back
                        // to the live chunk/world lookup instead of leaking null to filters.
                    }
                }
                state = chunk.getBlockState(pos);
            }
        }
        if (state == null) state = world.getBlockState(pos);
        return state == null ? Blocks.AIR.getDefaultState() : state;
    }

    @Unique
    private BlockState[] selectiverender$captureSourceSection(
            net.minecraft.world.chunk.WorldChunk chunk, int sectionY) {
        BlockState[] states = new BlockState[4096];
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int minY = sectionY << 4;
        for (int localY = 0; localY < 16; localY++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    cursor.set((chunk.getPos().x << 4) + localX, minY + localY,
                            (chunk.getPos().z << 4) + localZ);
                    states[(localY << 8) | (localZ << 4) | localX] = chunk.getBlockState(cursor);
                }
            }
        }
        return states;
    }
}
