package de.selectiverender;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import java.util.Arrays;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LightEngine;

/** Main-thread virtual skylight sampler for entities and block entities. */
public final class VirtualSkyLightSampler {
    private static final int RADIUS = SelectiveRenderState.VIRTUAL_LIGHT_RADIUS;
    private static final int CORE_SIZE = 16;
    private static final int CORE_CELLS = CORE_SIZE * CORE_SIZE * CORE_SIZE;
    private static final int MAX_VOLUMES = 128;
    private static final int MAX_SOURCE_SECTIONS = 256;
    private static final int REBUILD_DELAY_TICKS = 4;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Long2ObjectLinkedOpenHashMap<CoreVolume> VOLUMES =
            new Long2ObjectLinkedOpenHashMap<>();
    /** Reuses captured block/light inputs shared by overlapping sampler volumes. */
    private static final Long2ObjectLinkedOpenHashMap<SourceSection> SOURCE_SECTIONS =
            new Long2ObjectLinkedOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<LongOpenHashSet> VOLUMES_BY_COLUMN =
            new Long2ObjectOpenHashMap<>();
    private static final LongLinkedOpenHashSet DIRTY_VOLUMES = new LongLinkedOpenHashSet();
    private static final Scratch SCRATCH = new Scratch();
    private static ClientLevel cachedWorld;
    private static int cachedGeneration = Integer.MIN_VALUE;
    private static int rebuildDelay;

    private VirtualSkyLightSampler() { }

    public static int sample(ClientLevel world, BlockPos pos) {
        if (!world.dimensionType().hasSkyLight()
                || (!SelectiveRenderState.enabled() && !SelectiveRenderState.hideEnabled())) return -1;
        if (!SelectiveRenderState.shouldRender(pos)) return 15;
        if (pos.getY() >= (world.getMaxY() + 1)) return 15;
        if (pos.getY() < world.getMinY()) return 0;
        if (!SelectiveRenderState.mayNeedVirtualSkyLight(pos.getX(), pos.getZ())) return -1;

        int generation = SelectiveRenderState.visibilityGeneration();
        if (world != cachedWorld || generation != cachedGeneration) {
            PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.GENERATION_RESET, VOLUMES.size());
            clearVolumes();
            rebuildDelay = 0;
            cachedWorld = world;
            cachedGeneration = generation;
        }
        int sectionX = pos.getX() >> 4;
        int sectionY = pos.getY() >> 4;
        int sectionZ = pos.getZ() >> 4;
        long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
        CoreVolume volume = VOLUMES.getAndMoveToLast(key);
        if (volume == null) {
            PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.SAMPLER_MISS, 1);
            volume = build(world, sectionX, sectionY, sectionZ,
                    PerformanceDiagnostics.Metric.SAMPLER_COLD_BUILD);
            VOLUMES.putAndMoveToLast(key, volume);
            indexVolume(key);
            if (VOLUMES.size() > MAX_VOLUMES) {
                long evicted = VOLUMES.firstLongKey();
                PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.SAMPLER_EVICTION, 1);
                VOLUMES.removeFirst();
                DIRTY_VOLUMES.remove(evicted);
                unindexVolume(evicted);
            }
        } else {
            PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.SAMPLER_HIT, 1);
            if (PerformanceDiagnostics.enabled() && DIRTY_VOLUMES.contains(key)) {
                PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.SAMPLER_DIRTY_HIT, 1);
            }
        }
        return volume.sample(pos);
    }

    /** Rebuilds at most one stale volume per client tick to avoid block-update frame spikes. */
    public static void tick(ClientLevel world) {
        if (PerformanceDiagnostics.enabled()) PerformanceDiagnostics.count(
                PerformanceDiagnostics.Metric.SAMPLER_DIRTY_QUEUE, DIRTY_VOLUMES.size());
        if (world == null || world != cachedWorld || DIRTY_VOLUMES.isEmpty()) return;
        if (rebuildDelay > 0) {
            rebuildDelay--;
            return;
        }
        int generation = SelectiveRenderState.visibilityGeneration();
        if (generation != cachedGeneration) {
            clearVolumes();
            rebuildDelay = 0;
            cachedGeneration = generation;
            return;
        }
        long key = DIRTY_VOLUMES.removeFirstLong();
        if (!VOLUMES.containsKey(key)) return;
        CoreVolume volume = build(world, SectionPos.x(key), SectionPos.y(key), SectionPos.z(key),
                PerformanceDiagnostics.Metric.SAMPLER_DIRTY_BUILD);
        VOLUMES.putAndMoveToLast(key, volume);
        // Building a halo volume is intentionally expensive. Pace rebuilds even when
        // a burst invalidated several adjacent volumes at once.
        rebuildDelay = REBUILD_DELAY_TICKS;
    }

    public static void invalidate() {
        clearVolumes();
        rebuildDelay = 0;
    }

    public static void invalidateBlock(int blockX, int blockY, int blockZ) {
        invalidateSourceBlock(blockX, blockY, blockZ);
        if (VOLUMES.isEmpty()) return;
        boolean affected = false;
        int minSectionX = LightVolumeInfluence.minHorizontalSection(blockX, RADIUS);
        int maxSectionX = LightVolumeInfluence.maxHorizontalSection(blockX, RADIUS);
        int minSectionZ = LightVolumeInfluence.minHorizontalSection(blockZ, RADIUS);
        int maxSectionZ = LightVolumeInfluence.maxHorizontalSection(blockZ, RADIUS);
        for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
            for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
                LongOpenHashSet column = VOLUMES_BY_COLUMN.get(columnKey(sectionX, sectionZ));
                if (column == null) continue;
                var keys = column.iterator();
                while (keys.hasNext()) {
                    long key = keys.nextLong();
                    if (DIRTY_VOLUMES.contains(key)) continue;
                    if (LightVolumeInfluence.blockAffectsSection(
                            SectionPos.x(key), SectionPos.y(key),
                            SectionPos.z(key), blockX, blockY, blockZ, RADIUS)) {
                        DIRTY_VOLUMES.add(key);
                        PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.SAMPLER_INVALIDATION, 1);
                        affected = true;
                    }
                }
            }
        }
        // Do not restart an active countdown for every block update. Continuous mining
        // would otherwise starve dirty volumes indefinitely.
        if (affected && rebuildDelay == 0) rebuildDelay = REBUILD_DELAY_TICKS;
    }

    /** Drop the captured inputs for the changed section without forcing a light rebuild. */
    public static void invalidateSourceBlock(int blockX, int blockY, int blockZ) {
        SOURCE_SECTIONS.remove(SectionPos.asLong(blockX >> 4, blockY >> 4, blockZ >> 4));
    }

    public static void invalidateChunk(int chunkX, int chunkZ) {
        var sourceIterator = SOURCE_SECTIONS.long2ObjectEntrySet().fastIterator();
        while (sourceIterator.hasNext()) {
            long key = sourceIterator.next().getLongKey();
            if (SectionPos.x(key) == chunkX && SectionPos.z(key) == chunkZ) sourceIterator.remove();
        }
        var iterator = VOLUMES.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<CoreVolume> entry = iterator.next();
            long key = entry.getLongKey();
            if (LightVolumeInfluence.chunkAffectsSection(
                    SectionPos.x(key), SectionPos.z(key),
                    chunkX, chunkZ, RADIUS)) {
                unindexVolume(key);
                iterator.remove();
                DIRTY_VOLUMES.remove(key);
            }
        }
    }

    public static CacheStatus cacheStatus(BlockPos pos) {
        long key = SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        return new CacheStatus(VOLUMES.containsKey(key), DIRTY_VOLUMES.contains(key), rebuildDelay);
    }

    public record CacheStatus(boolean present, boolean dirty, int rebuildDelay) { }

    public static String diagnosticStatus() {
        return "volumes=" + VOLUMES.size() + " dirty=" + DIRTY_VOLUMES.size()
                + " source_sections=" + SOURCE_SECTIONS.size()
                + " indexed_columns=" + VOLUMES_BY_COLUMN.size() + " delay_ticks=" + rebuildDelay;
    }

    private static long columnKey(int sectionX, int sectionZ) {
        return (sectionX & 0xffffffffL) | ((long) sectionZ << 32);
    }

    private static void indexVolume(long key) {
        long columnKey = columnKey(SectionPos.x(key), SectionPos.z(key));
        LongOpenHashSet column = VOLUMES_BY_COLUMN.get(columnKey);
        if (column == null) {
            column = new LongOpenHashSet();
            VOLUMES_BY_COLUMN.put(columnKey, column);
        }
        column.add(key);
    }

    private static void unindexVolume(long key) {
        long columnKey = columnKey(SectionPos.x(key), SectionPos.z(key));
        LongOpenHashSet column = VOLUMES_BY_COLUMN.get(columnKey);
        if (column == null) return;
        column.remove(key);
        if (column.isEmpty()) VOLUMES_BY_COLUMN.remove(columnKey);
    }

    private static void clearVolumes() {
        VOLUMES.clear();
        SOURCE_SECTIONS.clear();
        VOLUMES_BY_COLUMN.clear();
        DIRTY_VOLUMES.clear();
    }

    private static CoreVolume build(ClientLevel world, int sectionX, int sectionY, int sectionZ,
                                    PerformanceDiagnostics.Metric reason) {
        long totalStarted = PerformanceDiagnostics.startTimer();
        int coreMinX = sectionX << 4;
        int coreMinY = sectionY << 4;
        int coreMinZ = sectionZ << 4;
        int minX = coreMinX - RADIUS;
        int maxX = coreMinX + 15 + RADIUS;
        int minY = Math.max(world.getMinY(), coreMinY - RADIUS);
        int maxY = Math.min(world.getMaxY(), coreMinY + 15 + RADIUS);
        int minZ = coreMinZ - RADIUS;
        int maxZ = coreMinZ + 15 + RADIUS;
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        int cells = sizeX * sizeY * sizeZ;
        SCRATCH.prepare(cells, sizeX, sizeZ);
        int minSectionX = minX >> 4;
        int minSectionY = minY >> 4;
        int minSectionZ = minZ >> 4;
        int sectionCountX = (maxX >> 4) - minSectionX + 1;
        int sectionCountY = (maxY >> 4) - minSectionY + 1;
        int sectionCountZ = (maxZ >> 4) - minSectionZ + 1;
        SourceSection[] sourceSections = new SourceSection[sectionCountX * sectionCountY * sectionCountZ];
        for (int localSectionY = 0; localSectionY < sectionCountY; localSectionY++) {
            for (int localSectionX = 0; localSectionX < sectionCountX; localSectionX++) {
                for (int localSectionZ = 0; localSectionZ < sectionCountZ; localSectionZ++) {
                    int index = (localSectionY * sectionCountX + localSectionX) * sectionCountZ + localSectionZ;
                    sourceSections[index] = sourceSection(world,
                            minSectionX + localSectionX, minSectionY + localSectionY,
                            minSectionZ + localSectionZ);
                }
            }
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        long phaseStarted = PerformanceDiagnostics.startTimer();

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    int index = SCRATCH.index(x - minX, y - minY, z - minZ);
                    int sectionIndex = (((y >> 4) - minSectionY) * sectionCountX
                            + (x >> 4) - minSectionX) * sectionCountZ + (z >> 4) - minSectionZ;
                    int localIndex = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
                    SourceSection source = sourceSections[sectionIndex];
                    captureSourceCell(world, source, localIndex, x, y, z, cursor, totalStarted != 0);
                    SCRATCH.states[index] = source.states[localIndex];
                    SCRATCH.visible[index] = source.visible[localIndex];
                    SCRATCH.opacity[index] = source.opacity[localIndex];
                }
            }
        }

        PerformanceDiagnostics.finish(PerformanceDiagnostics.Metric.SAMPLER_STATES,
                phaseStarted, cells, minX, minY, minZ);
        phaseStarted = PerformanceDiagnostics.startTimer();
        int queueHead = 0;
        int queueTail = 0;
        int queueSize = 0;
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int localX = x - minX;
                int localZ = z - minZ;
                int worldSurface = world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                int visibleTop = SelectiveRenderState.visibleColumnTop(x, z,
                        Math.min(world.getMaxY(), worldSurface));
                if (!SelectiveRenderState.shouldScanVirtualSkyColumn(
                        visibleTop != Integer.MIN_VALUE)) continue;
                int scanTop = visibleTop == Integer.MIN_VALUE ? maxY : Math.max(maxY, visibleTop);
                int directLight = 15;
                BlockState aboveState = sourceState(world, above, x, scanTop + 1, z);
                for (int y = scanTop; y >= minY; y--) {
                    cursor.set(x, y, z);
                    BlockState state;
                    int stateOpacity;
                    if (y <= maxY) {
                        int index = SCRATCH.index(localX, y - minY, localZ);
                        state = SCRATCH.states[index];
                        stateOpacity = Byte.toUnsignedInt(SCRATCH.opacity[index]);
                    } else {
                        state = sourceState(world, cursor, x, y, z);
                        stateOpacity = opacity(world, state, cursor);
                    }
                    int realisticOpacity = LightEngine.getLightDampeningInto(
                            aboveState, state, Direction.DOWN, stateOpacity);
                    directLight = SkyLightColumn.passDown(directLight, realisticOpacity);
                    above.set(x, y, z);
                    aboveState = state;
                    if (directLight <= 0) break;
                    if (y > maxY) continue;
                    int index = SCRATCH.index(localX, y - minY, localZ);
                    if (!SelectiveRenderState.shouldSeedVirtualSkyColumn(
                            SCRATCH.visible[index] != 0, x, y, z)) continue;
                    SCRATCH.light[index] = (byte) directLight;
                    // Queue only useful sources after all direct columns are populated.
                }
            }
        }

        PerformanceDiagnostics.finish(PerformanceDiagnostics.Metric.SAMPLER_COLUMNS,
                phaseStarted, (long) sizeX * sizeZ, minX, minY, minZ);
        phaseStarted = PerformanceDiagnostics.startTimer();
        queueSize = VirtualLightPropagation.seedFrontier(
                SCRATCH.light, SCRATCH.queued, SCRATCH.queue, sizeX, sizeY, sizeZ);
        queueTail = queueSize % cells;
        PerformanceDiagnostics.finish(PerformanceDiagnostics.Metric.SAMPLER_FRONTIER,
                phaseStarted, queueSize, minX, minY, minZ);
        phaseStarted = PerformanceDiagnostics.startTimer();
        int propagatedCells = 0;

        BlockPos.MutableBlockPos currentPos = above;
        BlockPos.MutableBlockPos nextPos = cursor;
        while (queueSize > 0) {
            if (phaseStarted != 0) propagatedCells++;
            int currentIndex = SCRATCH.queue[queueHead];
            queueHead = (queueHead + 1) % cells;
            queueSize--;
            SCRATCH.queued[currentIndex] = 0;
            int currentLight = Byte.toUnsignedInt(SCRATCH.light[currentIndex]);
            if (currentLight <= 1) continue;
            int localX = currentIndex % sizeX;
            int yz = currentIndex / sizeX;
            int localZ = yz % sizeZ;
            int localY = yz / sizeZ;
            currentPos.set(minX + localX, minY + localY, minZ + localZ);
            BlockState currentState = SCRATCH.states[currentIndex];
            for (Direction direction : DIRECTIONS) {
                int nextX = localX + direction.getStepX();
                int nextY = localY + direction.getStepY();
                int nextZ = localZ + direction.getStepZ();
                if (nextX < 0 || nextX >= sizeX || nextY < 0 || nextY >= sizeY
                        || nextZ < 0 || nextZ >= sizeZ) continue;
                int nextIndex = SCRATCH.index(nextX, nextY, nextZ);
                int existingLight = Byte.toUnsignedInt(SCRATCH.light[nextIndex]);
                if (!VirtualLightPropagation.canImprove(currentLight, existingLight)) continue;
                if (!VirtualLightPropagation.canPass(currentLight, existingLight,
                        Byte.toUnsignedInt(SCRATCH.opacity[nextIndex]))) continue;
                if (!SelectiveRenderState.shouldPropagateVirtualSkyLight(
                        SCRATCH.visible[currentIndex] != 0, SCRATCH.visible[nextIndex] != 0,
                        currentPos.getX(), currentPos.getY(), currentPos.getZ(),
                        minX + nextX, minY + nextY, minZ + nextZ)) continue;
                nextPos.set(minX + nextX, minY + nextY, minZ + nextZ);
                int realisticOpacity = LightEngine.getLightDampeningInto(
                        currentState, SCRATCH.states[nextIndex],
                        direction, Math.max(1, Byte.toUnsignedInt(SCRATCH.opacity[nextIndex])));
                int nextLight = currentLight - realisticOpacity;
                if (nextLight <= existingLight) continue;
                SCRATCH.light[nextIndex] = (byte) nextLight;
                if (SCRATCH.queued[nextIndex] == 0) {
                    SCRATCH.queue[queueTail] = nextIndex;
                    queueTail = (queueTail + 1) % cells;
                    SCRATCH.queued[nextIndex] = 1;
                    queueSize++;
                }
            }
        }

        PerformanceDiagnostics.finish(PerformanceDiagnostics.Metric.SAMPLER_PROPAGATION,
                phaseStarted, propagatedCells, minX, minY, minZ);
        PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.SAMPLER_PROPAGATED_CELLS, propagatedCells);
        byte[] coreLight = new byte[CORE_CELLS];
        int fromY = Math.max(world.getMinY(), coreMinY);
        int toY = Math.min(world.getMaxY(), coreMinY + 15);
        for (int y = fromY; y <= toY; y++) {
            int localY = y - minY;
            int coreY = y - coreMinY;
            for (int coreZ = 0; coreZ < CORE_SIZE; coreZ++) {
                int source = SCRATCH.index(RADIUS, localY, RADIUS + coreZ);
                int target = (coreY * CORE_SIZE + coreZ) * CORE_SIZE;
                System.arraycopy(SCRATCH.light, source, coreLight, target, CORE_SIZE);
            }
        }
        PerformanceDiagnostics.finish(reason, totalStarted, cells, coreMinX, coreMinY, coreMinZ);
        return new CoreVolume(coreMinX, coreMinY, coreMinZ, coreLight);
    }

    private static BlockState sourceState(ClientLevel world, BlockPos.MutableBlockPos cursor,
                                          int x, int y, int z) {
        cursor.set(x, y, z);
        if (!SelectiveRenderState.shouldRender(cursor)) return Blocks.AIR.defaultBlockState();
        BlockState state = world.getBlockState(cursor);
        return state == null || !SelectiveRenderState.shouldRender(state, x, y, z)
                ? Blocks.AIR.defaultBlockState() : state;
    }

    private static SourceSection sourceSection(ClientLevel world,
                                               int sectionX, int sectionY, int sectionZ) {
        long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
        SourceSection cached = SOURCE_SECTIONS.getAndMoveToLast(key);
        if (cached != null) {
            PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.SAMPLER_SOURCE_SECTION_HIT, 1);
            return cached;
        }
        PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.SAMPLER_SOURCE_SECTION_MISS, 1);
        SourceSection result = new SourceSection(new BlockState[4096], new byte[4096],
                new byte[4096], new byte[4096]);
        SOURCE_SECTIONS.putAndMoveToLast(key, result);
        while (SOURCE_SECTIONS.size() > MAX_SOURCE_SECTIONS) SOURCE_SECTIONS.removeFirst();
        return result;
    }

    private static void captureSourceCell(ClientLevel world, SourceSection source, int index,
                                          int x, int y, int z, BlockPos.MutableBlockPos cursor,
                                          boolean diagnose) {
        if (source.captured[index] != 0) {
            if (diagnose) PerformanceDiagnostics.count(
                    PerformanceDiagnostics.Metric.SAMPLER_SOURCE_CELL_HIT, 1);
            return;
        }
        BlockState state = Blocks.AIR.defaultBlockState();
        boolean coordinateVisible = SelectiveRenderState.shouldRender(x, y, z);
        if (coordinateVisible && y >= world.getMinY() && y <= world.getMaxY()) {
            cursor.set(x, y, z);
            state = world.getBlockState(cursor);
            if (state == null || !SelectiveRenderState.shouldRender(state, x, y, z)) {
                state = Blocks.AIR.defaultBlockState();
            }
        }
        cursor.set(x, y, z);
        source.states[index] = state;
        source.visible[index] = (byte) (coordinateVisible ? 1 : 0);
        source.opacity[index] = (byte) opacity(world, state, cursor);
        source.captured[index] = 1;
        if (diagnose) PerformanceDiagnostics.count(
                PerformanceDiagnostics.Metric.SAMPLER_SOURCE_CELL_MISS, 1);
    }

    private static int opacity(ClientLevel world, BlockState state, BlockPos pos) {
        return Math.min(15, Math.max(0, state.getLightDampening()));
    }

    private record CoreVolume(int minX, int minY, int minZ, byte[] light) {
        private int sample(BlockPos pos) {
            int localX = pos.getX() - minX;
            int localY = pos.getY() - minY;
            int localZ = pos.getZ() - minZ;
            return Byte.toUnsignedInt(light[(localY * CORE_SIZE + localZ) * CORE_SIZE + localX]);
        }
    }

    private record SourceSection(BlockState[] states, byte[] visible, byte[] opacity, byte[] captured) { }

    private static final class Scratch {
        private byte[] light = new byte[0];
        private byte[] queued = new byte[0];
        private byte[] opacity = new byte[0];
        private byte[] visible = new byte[0];
        private BlockState[] states = new BlockState[0];
        private int[] queue = new int[0];
        private int sizeX;
        private int sizeZ;

        private void prepare(int cells, int sizeX, int sizeZ) {
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            if (light.length < cells) {
                light = new byte[cells];
                queued = new byte[cells];
                opacity = new byte[cells];
                visible = new byte[cells];
                states = new BlockState[cells];
                queue = new int[cells];
            } else {
                Arrays.fill(light, 0, cells, (byte) 0);
                Arrays.fill(queued, 0, cells, (byte) 0);
            }
        }

        private int index(int localX, int localY, int localZ) {
            return (localY * sizeZ + localZ) * sizeX + localX;
        }
    }
}
