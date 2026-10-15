package de.selectiverender;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.light.ChunkLightProvider;

import java.util.Arrays;

/** Main-thread virtual skylight sampler for entities and block entities. */
public final class VirtualSkyLightSampler {
    private static final int RADIUS = SelectiveRenderState.VIRTUAL_LIGHT_RADIUS;
    private static final int CORE_SIZE = 16;
    private static final int CORE_CELLS = CORE_SIZE * CORE_SIZE * CORE_SIZE;
    private static final int MAX_VOLUMES = 128;
    private static final int MAX_SOURCE_SECTIONS = 256;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Long2ObjectLinkedOpenHashMap<CoreVolume> VOLUMES =
            new Long2ObjectLinkedOpenHashMap<>();
    /** Reuses captured block/light inputs shared by overlapping sampler volumes. */
    private static final Long2ObjectLinkedOpenHashMap<SourceSection> SOURCE_SECTIONS =
            new Long2ObjectLinkedOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<LongOpenHashSet> VOLUMES_BY_COLUMN =
            new Long2ObjectOpenHashMap<>();
    private static final LongLinkedOpenHashSet DIRTY_VOLUMES = new LongLinkedOpenHashSet();
    private static final LongLinkedOpenHashSet PENDING = new LongLinkedOpenHashSet();
    private static BuildJob activeJob;
    private static long activeKey;
    private static boolean preferDirty;
    private static final Scratch SCRATCH = new Scratch();
    private static ClientWorld cachedWorld;
    private static int cachedGeneration = Integer.MIN_VALUE;

    private VirtualSkyLightSampler() { }

    public static int sample(ClientWorld world, BlockPos pos) {
        if (!world.getDimension().hasSkyLight() || !SelectiveRenderState.virtualSkyLightActive()) return -1;
        if (!SelectiveRenderState.shouldRender(pos)) return 15;
        if (pos.getY() >= world.getTopY()) return 15;
        if (pos.getY() < world.getBottomY()) return 0;
        if (!SelectiveRenderState.mayNeedVirtualSkyLight(pos.getX(), pos.getZ())) return -1;

        int generation = SelectiveRenderState.visibilityGeneration();
        if (world != cachedWorld || generation != cachedGeneration) {
            PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.GENERATION_RESET, VOLUMES.size());
            clearVolumes();
            cachedWorld = world;
            cachedGeneration = generation;
        }
        int sectionX = pos.getX() >> 4;
        int sectionY = pos.getY() >> 4;
        int sectionZ = pos.getZ() >> 4;
        long key = ChunkSectionPos.asLong(sectionX, sectionY, sectionZ);
        CoreVolume volume = VOLUMES.getAndMoveToLast(key);
        if (volume == null) {
            PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.SAMPLER_MISS, 1);
            if ((activeJob==null || activeKey!=key) && PENDING.size() < MAX_VOLUMES) PENDING.add(key);
            return -1; // Vanilla light until the budgeted result is ready.
        } else {
            PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.SAMPLER_HIT, 1);
            if (PerformanceDiagnostics.enabled() && DIRTY_VOLUMES.contains(key)) {
                PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.SAMPLER_DIRTY_HIT, 1);
            }
        }
        return volume.sample(pos);
    }

    /** Soft 2ms budget per client tick, checked between small batches and direct columns. */
    public static void tick(ClientWorld world) {
        if(world==null) { clearVolumes(); cachedWorld=null; cachedGeneration=Integer.MIN_VALUE; return; }
        if (world != cachedWorld || !SelectiveRenderState.virtualSkyLightActive()
                || cachedGeneration != SelectiveRenderState.visibilityGeneration()) { clearVolumes(); return; }
        PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.SAMPLER_DIRTY_QUEUE,PENDING.size()+DIRTY_VOLUMES.size()+(activeJob==null?0:1));
        long started=PerformanceDiagnostics.startTimer();
        long deadline=System.nanoTime()+2_000_000L;
        if (activeJob == null) {
            long key;
            if (!DIRTY_VOLUMES.isEmpty() && (preferDirty || PENDING.isEmpty())) key=DIRTY_VOLUMES.removeFirstLong();
            else if (!PENDING.isEmpty()) key=PENDING.removeFirstLong();
            else return;
            preferDirty=!preferDirty;
            int cx=ChunkSectionPos.unpackX(key)<<4, cy=ChunkSectionPos.unpackY(key)<<4, cz=ChunkSectionPos.unpackZ(key)<<4;
            byte[] shared=TerrainSkyLightCache.INSTANCE.copyInterior(world,cachedGeneration,
                    SelectiveRenderSettings.virtualLightMode().ordinal()*4+SelectiveRenderSettings.hiddenVirtualLightMode().ordinal(),
                    cx,cy,cz,world.getBottomY(),world.getTopY()-1);
            if(shared!=null) {
                VOLUMES.putAndMoveToLast(key,new CoreVolume(cx,cy,cz,shared)); indexVolume(key);
                PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.SAMPLER_TERRAIN_REUSE,1);
                DIRTY_VOLUMES.remove(key); PENDING.remove(key);
                trimVolumes();
                PerformanceDiagnostics.finish(PerformanceDiagnostics.Metric.SAMPLER_BUDGET_STEP,started,1,0,0,0);
                return;
            }
            activeKey=key;
            activeJob=new BuildJob(world, cx>>4, cy>>4, cz>>4);
        }
        boolean complete=activeJob.step(deadline);
        PerformanceDiagnostics.finish(PerformanceDiagnostics.Metric.SAMPLER_BUDGET_STEP,started,1,0,0,0);
        if (complete) {
            boolean cold=!VOLUMES.containsKey(activeKey);
            VOLUMES.putAndMoveToLast(activeKey,activeJob.result);
            indexVolume(activeKey);
            PerformanceDiagnostics.count(cold ? PerformanceDiagnostics.Metric.SAMPLER_COLD_BUILD
                    : PerformanceDiagnostics.Metric.SAMPLER_DIRTY_BUILD, CORE_CELLS);
            DIRTY_VOLUMES.remove(activeKey); PENDING.remove(activeKey);
            activeJob=null;
            trimVolumes();
        }
    }

    private static void trimVolumes() {
            if(VOLUMES.size()>MAX_VOLUMES) {
                long evicted=VOLUMES.firstLongKey(); VOLUMES.removeFirst();
                DIRTY_VOLUMES.remove(evicted); unindexVolume(evicted);
            }
    }

    static void verifyBudgetedBuild(ClientWorld world, BlockPos pos) {
        invalidate(); sample(world,pos);
        BuildJob whole=new BuildJob(world,pos.getX()>>4,pos.getY()>>4,pos.getZ()>>4);
        whole.step(Long.MAX_VALUE);
        byte[] expected=whole.result.light.clone();
        BuildJob sliced=new BuildJob(world,pos.getX()>>4,pos.getY()>>4,pos.getZ()>>4);
        int slices=0;
        while(!sliced.step(0)) if(++slices>100000) throw new IllegalStateException("Light job never completes");
        if(slices<2 || !Arrays.equals(expected,sliced.result.light)) throw new IllegalStateException("Budgeted light differs from unsliced result");
        activeJob=null;
        int value=sampleForValidation(world,pos);
        if(value!=Byte.toUnsignedInt(expected[((pos.getY()&15)*16+(pos.getZ()&15))*16+(pos.getX()&15)]))
            throw new IllegalStateException("Queued light differs from completed solve");
        invalidate();
        SelectiveRenderClient.LOGGER.info("[SR skylight budget smoke] PASS: sliced and complete 4096-cell results match");
    }

    /** Explicit test probe: finish queued work without changing the production sampling path. */
    static int sampleForValidation(ClientWorld world, BlockPos pos) {
        int value=sample(world,pos);
        for(int i=0;value<0 && (!PENDING.isEmpty() || activeJob!=null) && i<4096;i++) {
            tick(world); value=sample(world,pos);
        }
        return value;
    }

    public static void invalidate() {
        clearVolumes();
    }

    public static void invalidateBlock(int blockX, int blockY, int blockZ) {
        invalidateSourceBlock(blockX, blockY, blockZ);
        cancelActiveBlock(blockX, blockY, blockZ);
        if (VOLUMES.isEmpty()) return;
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
                            ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackY(key),
                            ChunkSectionPos.unpackZ(key), blockX, blockY, blockZ, RADIUS)) {
                        DIRTY_VOLUMES.add(key);
                        PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.SAMPLER_INVALIDATION, 1);
                    }
                }
            }
        }
    }

    /** Drop the captured inputs for the changed section without forcing a light rebuild. */
    public static void invalidateSourceBlock(int blockX, int blockY, int blockZ) {
        SOURCE_SECTIONS.remove(ChunkSectionPos.asLong(blockX >> 4, blockY >> 4, blockZ >> 4));
    }

    public static void invalidateChunk(int chunkX, int chunkZ) {
        if(activeJob!=null && LightVolumeInfluence.chunkAffectsSection(ChunkSectionPos.unpackX(activeKey),ChunkSectionPos.unpackZ(activeKey),chunkX,chunkZ,RADIUS)) { PENDING.add(activeKey); activeJob=null; }
        var sourceIterator = SOURCE_SECTIONS.long2ObjectEntrySet().fastIterator();
        while (sourceIterator.hasNext()) {
            long key = sourceIterator.next().getLongKey();
            if (ChunkSectionPos.unpackX(key) == chunkX
                    && ChunkSectionPos.unpackZ(key) == chunkZ) sourceIterator.remove();
        }
        var iterator = VOLUMES.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<CoreVolume> entry = iterator.next();
            long key = entry.getLongKey();
            if (LightVolumeInfluence.chunkAffectsSection(
                    ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackZ(key),
                    chunkX, chunkZ, RADIUS)) {
                DIRTY_VOLUMES.add(key);
            }
        }
    }

    private static void cancelActiveBlock(int x,int y,int z) {
        if(activeJob!=null && LightVolumeInfluence.blockAffectsSection(ChunkSectionPos.unpackX(activeKey),ChunkSectionPos.unpackY(activeKey),ChunkSectionPos.unpackZ(activeKey),x,y,z,RADIUS)) { PENDING.add(activeKey); activeJob=null; }
    }

    public static CacheStatus cacheStatus(BlockPos pos) {
        long key = ChunkSectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        return new CacheStatus(VOLUMES.containsKey(key), DIRTY_VOLUMES.contains(key) || PENDING.contains(key) || activeJob!=null && activeKey==key, 0);
    }

    public record CacheStatus(boolean present, boolean dirty, int rebuildDelay) { }

    public static String diagnosticStatus() {
        return "volumes=" + VOLUMES.size() + " dirty=" + DIRTY_VOLUMES.size()
                + " source_sections=" + SOURCE_SECTIONS.size()
                + " pending=" + PENDING.size() + " active=" + (activeJob!=null)
                + " indexed_columns=" + VOLUMES_BY_COLUMN.size();
    }

    private static long columnKey(int sectionX, int sectionZ) {
        return (sectionX & 0xffffffffL) | ((long) sectionZ << 32);
    }

    private static void indexVolume(long key) {
        long columnKey = columnKey(ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackZ(key));
        LongOpenHashSet column = VOLUMES_BY_COLUMN.get(columnKey);
        if (column == null) {
            column = new LongOpenHashSet();
            VOLUMES_BY_COLUMN.put(columnKey, column);
        }
        column.add(key);
    }

    private static void unindexVolume(long key) {
        long columnKey = columnKey(ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackZ(key));
        LongOpenHashSet column = VOLUMES_BY_COLUMN.get(columnKey);
        if (column == null) return;
        column.remove(key);
        if (column.isEmpty()) VOLUMES_BY_COLUMN.remove(columnKey);
    }

    private static void clearVolumes() {
        activeJob=null; PENDING.clear();
        VOLUMES.clear();
        SOURCE_SECTIONS.clear();
        VOLUMES_BY_COLUMN.clear();
        DIRTY_VOLUMES.clear();
    }

    /** One cooperative job owns scratch until completion; no world reads leave the client thread. */
    private static final class BuildJob {
        final ClientWorld world;
        final int coreMinX, coreMinY, coreMinZ, minX, minY, minZ, maxX, maxY, maxZ;
        final int sizeX, sizeY, sizeZ, cells, minSectionX, minSectionY, minSectionZ, sectionCountX, sectionCountZ;
        final SourceSection[] sourceSections;
        final Scratch scratch = SCRATCH;
        final BlockPos.Mutable cursor = new BlockPos.Mutable(), above = new BlockPos.Mutable();
        final BlockPos.Mutable currentPos = above, nextPos = cursor;
        int phase, captured, column, frontier, queueHead, queueTail, queueSize;
        CoreVolume result;
        BuildJob(ClientWorld world, int sectionX, int sectionY, int sectionZ) {
            this.world=world;
            coreMinX=sectionX<<4; coreMinY=sectionY<<4; coreMinZ=sectionZ<<4;
            minX=coreMinX-RADIUS; maxX=coreMinX+15+RADIUS;
            minY=Math.max(world.getBottomY(),coreMinY-RADIUS); maxY=Math.min(world.getTopY()-1,coreMinY+15+RADIUS);
            minZ=coreMinZ-RADIUS; maxZ=coreMinZ+15+RADIUS;
            sizeX=maxX-minX+1; sizeY=maxY-minY+1; sizeZ=maxZ-minZ+1; cells=sizeX*sizeY*sizeZ;
            scratch.prepare(cells,sizeX,sizeZ);
            minSectionX=minX>>4; minSectionY=minY>>4; minSectionZ=minZ>>4;
            sectionCountX=(maxX>>4)-minSectionX+1; sectionCountZ=(maxZ>>4)-minSectionZ+1;
            int sectionCountY=(maxY>>4)-minSectionY+1;
            sourceSections=new SourceSection[sectionCountX*sectionCountY*sectionCountZ];
            for(int sy=0;sy<sectionCountY;sy++) for(int sx=0;sx<sectionCountX;sx++) for(int sz=0;sz<sectionCountZ;sz++)
                sourceSections[(sy*sectionCountX+sx)*sectionCountZ+sz]=sourceSection(world,minSectionX+sx,minSectionY+sy,minSectionZ+sz);
        }
        boolean step(long deadline) {
            do {
                if(phase==0) {
                    int end=Math.min(cells,captured+64);
                    for(;captured<end;captured++) {
                        int index=captured, x=minX+index%sizeX, z=minZ+(index/sizeX)%sizeZ, y=minY+index/(sizeX*sizeZ);
                        int sectionIndex = (((y >> 4) - minSectionY) * sectionCountX
                                + (x >> 4) - minSectionX) * sectionCountZ + (z >> 4) - minSectionZ;
                        int localIndex = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
                        SourceSection source = sourceSections[sectionIndex];
                        captureSourceCell(world, source, localIndex, x, y, z, cursor, PerformanceDiagnostics.enabled());
                        scratch.states[index] = source.states[localIndex];
                        scratch.visible[index] = source.visible[localIndex];
                        scratch.opacity[index] = source.opacity[localIndex];
                    }
                    if(captured==cells) phase=1;
                } else if(phase==1) {
                    int x=minX+column/sizeZ, z=minZ+column%sizeZ;
                    seedColumn(x,z);
                    if(++column==sizeX*sizeZ) phase=2;
                } else if(phase==2) {
                    int end=Math.min(cells,frontier+128);
                    queueSize+=VirtualLightPropagation.seedFrontierRange(scratch.light,scratch.queued,scratch.queue,sizeX,sizeY,sizeZ,frontier,end,queueSize);
                    frontier=end;
                    if(frontier==cells) { queueTail=queueSize%cells; phase=3; }
                } else {
                    propagate();
                    if(queueSize==0) {
                        byte[] coreLight = new byte[CORE_CELLS];
                        int fromY = Math.max(world.getBottomY(), coreMinY);
                        int toY = Math.min(world.getTopY() - 1, coreMinY + 15);
                        for (int y = fromY; y <= toY; y++) {
                            int localY = y - minY;
                            int coreY = y - coreMinY;
                            for (int coreZ = 0; coreZ < CORE_SIZE; coreZ++) {
                                int source = scratch.index(RADIUS, localY, RADIUS + coreZ);
                                int target = (coreY * CORE_SIZE + coreZ) * CORE_SIZE;
                                System.arraycopy(scratch.light, source, coreLight, target, CORE_SIZE);
                            }
                        }

                        result=new CoreVolume(coreMinX,coreMinY,coreMinZ,coreLight);
                        return true;
                    }
                }
            } while(System.nanoTime()<deadline);
            return false;
        }
        private void seedColumn(int x,int z) {
            int localX = x - minX;
            int localZ = z - minZ;
            int worldSurface = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
            int visibleTop = SelectiveRenderState.visibleColumnTop(x, z,
                    Math.min(world.getTopY() - 1, worldSurface));
            if (!SelectiveRenderState.shouldScanVirtualSkyColumn(
                    visibleTop != Integer.MIN_VALUE)) return;
            int scanTop = visibleTop == Integer.MIN_VALUE ? maxY : Math.max(maxY, visibleTop);
            int directLight = 15;
            BlockState aboveState = sourceState(world, above, x, scanTop + 1, z);
            for (int y = scanTop; y >= minY; y--) {
                cursor.set(x, y, z);
                BlockState state;
                int stateOpacity;
                if (y <= maxY) {
                    int index = scratch.index(localX, y - minY, localZ);
                    state = scratch.states[index];
                    stateOpacity = Byte.toUnsignedInt(scratch.opacity[index]);
                } else {
                    state = sourceState(world, cursor, x, y, z);
                    stateOpacity = opacity(world, state, cursor);
                }
                int realisticOpacity = ChunkLightProvider.getRealisticOpacity(
                        world, aboveState, above, state, cursor, Direction.DOWN, stateOpacity);
                directLight = SkyLightColumn.passDown(directLight, realisticOpacity);
                above.set(x, y, z);
                aboveState = state;
                if (directLight <= 0) break;
                if (y > maxY) continue;
                int index = scratch.index(localX, y - minY, localZ);
                if (!SelectiveRenderState.shouldSeedVirtualSkyColumn(
                        scratch.visible[index] != 0, x, y, z)) continue;
                scratch.light[index] = (byte) directLight;
                // Queue only useful sources after all direct columns are populated.
            }
        }
        private void propagate() {
            int limit=64;
            while(queueSize>0 && limit-->0) {
                int currentIndex = scratch.queue[queueHead];
                queueHead = (queueHead + 1) % cells;
                queueSize--;
                scratch.queued[currentIndex] = 0;
                int currentLight = Byte.toUnsignedInt(scratch.light[currentIndex]);
                if (currentLight <= 1) continue;
                int localX = currentIndex % sizeX;
                int yz = currentIndex / sizeX;
                int localZ = yz % sizeZ;
                int localY = yz / sizeZ;
                currentPos.set(minX + localX, minY + localY, minZ + localZ);
                BlockState currentState = scratch.states[currentIndex];
                for (Direction direction : DIRECTIONS) {
                    int nextX = localX + direction.getOffsetX();
                    int nextY = localY + direction.getOffsetY();
                    int nextZ = localZ + direction.getOffsetZ();
                    if (nextX < 0 || nextX >= sizeX || nextY < 0 || nextY >= sizeY
                            || nextZ < 0 || nextZ >= sizeZ) continue;
                    int nextIndex = scratch.index(nextX, nextY, nextZ);
                    int existingLight = Byte.toUnsignedInt(scratch.light[nextIndex]);
                    if (!VirtualLightPropagation.canImprove(currentLight, existingLight)) continue;
                    if (!VirtualLightPropagation.canPass(currentLight, existingLight,
                            Byte.toUnsignedInt(scratch.opacity[nextIndex]))) continue;
                    if (!SelectiveRenderState.shouldPropagateVirtualSkyLight(
                            scratch.visible[currentIndex] != 0, scratch.visible[nextIndex] != 0,
                            currentPos.getX(), currentPos.getY(), currentPos.getZ(),
                            minX + nextX, minY + nextY, minZ + nextZ)) continue;
                    nextPos.set(minX + nextX, minY + nextY, minZ + nextZ);
                    int realisticOpacity = ChunkLightProvider.getRealisticOpacity(
                            world, currentState, currentPos, scratch.states[nextIndex], nextPos,
                            direction, Math.max(1, Byte.toUnsignedInt(scratch.opacity[nextIndex])));
                    int nextLight = currentLight - realisticOpacity;
                    if (nextLight <= existingLight) continue;
                    scratch.light[nextIndex] = (byte) nextLight;
                    if (scratch.queued[nextIndex] == 0) {
                        scratch.queue[queueTail] = nextIndex;
                        queueTail = (queueTail + 1) % cells;
                        scratch.queued[nextIndex] = 1;
                        queueSize++;
                    }
                }
            }
        }
    }

    private static BlockState sourceState(ClientWorld world, BlockPos.Mutable cursor,
                                          int x, int y, int z) {
        cursor.set(x, y, z);
        if (!SelectiveRenderState.shouldRender(cursor)) return Blocks.AIR.getDefaultState();
        BlockState state = world.getBlockState(cursor);
        return state == null || !SelectiveRenderState.passesBlockFilters(state, x, y, z)
                ? Blocks.AIR.getDefaultState() : state;
    }

    private static SourceSection sourceSection(ClientWorld world,
                                               int sectionX, int sectionY, int sectionZ) {
        long key = ChunkSectionPos.asLong(sectionX, sectionY, sectionZ);
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

    private static void captureSourceCell(ClientWorld world, SourceSection source, int index,
                                          int x, int y, int z, BlockPos.Mutable cursor,
                                          boolean diagnose) {
        if (source.captured[index] != 0) {
            if (diagnose) PerformanceDiagnostics.count(
                    PerformanceDiagnostics.Metric.SAMPLER_SOURCE_CELL_HIT, 1);
            return;
        }
        BlockState state = Blocks.AIR.getDefaultState();
        boolean coordinateVisible = SelectiveRenderState.shouldRender(x, y, z);
        if (coordinateVisible && y >= world.getBottomY() && y < world.getTopY()) {
            cursor.set(x, y, z);
            state = world.getBlockState(cursor);
            if (state == null || !SelectiveRenderState.passesBlockFilters(state, x, y, z)) {
                state = Blocks.AIR.getDefaultState();
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

    private static int opacity(ClientWorld world, BlockState state, BlockPos pos) {
        return Math.min(15, Math.max(0, state.getOpacity(world, pos)));
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
