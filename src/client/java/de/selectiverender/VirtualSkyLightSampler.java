package de.selectiverender;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
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
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Long2ObjectLinkedOpenHashMap<CoreVolume> VOLUMES =
            new Long2ObjectLinkedOpenHashMap<>();
    private static final Scratch SCRATCH = new Scratch();
    private static ClientWorld cachedWorld;
    private static int cachedGeneration = Integer.MIN_VALUE;

    private VirtualSkyLightSampler() { }

    public static int sample(ClientWorld world, BlockPos pos) {
        if (!world.getDimension().hasSkyLight()
                || (!SelectiveRenderState.enabled() && !SelectiveRenderState.hideEnabled())) return -1;
        if (!SelectiveRenderState.shouldRender(pos)) return 15;
        if (pos.getY() >= world.getTopY()) return 15;
        if (pos.getY() < world.getBottomY()) return 0;
        if (!SelectiveRenderState.mayNeedVirtualSkyLight(pos.getX(), pos.getZ(), RADIUS)) return -1;

        int generation = SelectiveRenderState.visibilityGeneration();
        if (world != cachedWorld || generation != cachedGeneration) {
            VOLUMES.clear();
            cachedWorld = world;
            cachedGeneration = generation;
        }
        int sectionX = pos.getX() >> 4;
        int sectionY = pos.getY() >> 4;
        int sectionZ = pos.getZ() >> 4;
        long key = ChunkSectionPos.asLong(sectionX, sectionY, sectionZ);
        CoreVolume volume = VOLUMES.getAndMoveToLast(key);
        if (volume == null) {
            volume = build(world, sectionX, sectionY, sectionZ);
            VOLUMES.putAndMoveToLast(key, volume);
            if (VOLUMES.size() > MAX_VOLUMES) VOLUMES.removeFirst();
        }
        return volume.sample(pos);
    }

    public static void invalidate() {
        VOLUMES.clear();
    }

    public static void invalidateBlock(int blockX, int blockY, int blockZ) {
        var iterator = VOLUMES.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<CoreVolume> entry = iterator.next();
            long key = entry.getLongKey();
            if (LightVolumeInfluence.blockAffectsSection(
                    ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackY(key),
                    ChunkSectionPos.unpackZ(key), blockX, blockY, blockZ, RADIUS)) {
                iterator.remove();
            }
        }
    }

    public static void invalidateChunk(int chunkX, int chunkZ) {
        var iterator = VOLUMES.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<CoreVolume> entry = iterator.next();
            long key = entry.getLongKey();
            if (LightVolumeInfluence.chunkAffectsSection(
                    ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackZ(key),
                    chunkX, chunkZ, RADIUS)) iterator.remove();
        }
    }

    private static CoreVolume build(ClientWorld world, int sectionX, int sectionY, int sectionZ) {
        int coreMinX = sectionX << 4;
        int coreMinY = sectionY << 4;
        int coreMinZ = sectionZ << 4;
        int minX = coreMinX - RADIUS;
        int maxX = coreMinX + 15 + RADIUS;
        int minY = Math.max(world.getBottomY(), coreMinY - RADIUS);
        int maxY = Math.min(world.getTopY() - 1, coreMinY + 15 + RADIUS);
        int minZ = coreMinZ - RADIUS;
        int maxZ = coreMinZ + 15 + RADIUS;
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        int cells = sizeX * sizeY * sizeZ;
        SCRATCH.prepare(cells, sizeX, sizeZ);
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    int index = SCRATCH.index(x - minX, y - minY, z - minZ);
                    BlockState state = sourceState(world, cursor, x, y, z);
                    SCRATCH.states[index] = state;
                    SCRATCH.opacity[index] = (byte) opacity(world, state, cursor);
                }
            }
        }

        int queueHead = 0;
        int queueTail = 0;
        int queueSize = 0;
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
                    int realisticOpacity = ChunkLightProvider.getRealisticOpacity(
                            world, aboveState, above, state, cursor, Direction.DOWN, stateOpacity);
                    directLight = SkyLightColumn.passDown(directLight, realisticOpacity);
                    above.set(x, y, z);
                    aboveState = state;
                    if (directLight <= 0) break;
                    if (y > maxY) continue;
                    int index = SCRATCH.index(localX, y - minY, localZ);
                    SCRATCH.light[index] = (byte) directLight;
                    SCRATCH.queue[queueTail] = index;
                    queueTail = (queueTail + 1) % cells;
                    SCRATCH.queued[index] = 1;
                    queueSize++;
                }
            }
        }

        BlockPos.Mutable currentPos = above;
        BlockPos.Mutable nextPos = cursor;
        while (queueSize > 0) {
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
                int nextX = localX + direction.getOffsetX();
                int nextY = localY + direction.getOffsetY();
                int nextZ = localZ + direction.getOffsetZ();
                if (nextX < 0 || nextX >= sizeX || nextY < 0 || nextY >= sizeY
                        || nextZ < 0 || nextZ >= sizeZ) continue;
                int nextIndex = SCRATCH.index(nextX, nextY, nextZ);
                int existingLight = Byte.toUnsignedInt(SCRATCH.light[nextIndex]);
                if (!VirtualLightPropagation.canImprove(currentLight, existingLight)) continue;
                nextPos.set(minX + nextX, minY + nextY, minZ + nextZ);
                int realisticOpacity = ChunkLightProvider.getRealisticOpacity(
                        world, currentState, currentPos, SCRATCH.states[nextIndex], nextPos,
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

        byte[] coreLight = new byte[CORE_CELLS];
        int fromY = Math.max(world.getBottomY(), coreMinY);
        int toY = Math.min(world.getTopY() - 1, coreMinY + 15);
        for (int y = fromY; y <= toY; y++) {
            int localY = y - minY;
            int coreY = y - coreMinY;
            for (int coreZ = 0; coreZ < CORE_SIZE; coreZ++) {
                int source = SCRATCH.index(RADIUS, localY, RADIUS + coreZ);
                int target = (coreY * CORE_SIZE + coreZ) * CORE_SIZE;
                System.arraycopy(SCRATCH.light, source, coreLight, target, CORE_SIZE);
            }
        }
        return new CoreVolume(coreMinX, coreMinY, coreMinZ, coreLight);
    }

    private static BlockState sourceState(ClientWorld world, BlockPos.Mutable cursor,
                                          int x, int y, int z) {
        cursor.set(x, y, z);
        if (!SelectiveRenderState.shouldRender(cursor)) return Blocks.AIR.getDefaultState();
        BlockState state = world.getBlockState(cursor);
        return state == null ? Blocks.AIR.getDefaultState() : state;
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

    private static final class Scratch {
        private byte[] light = new byte[0];
        private byte[] queued = new byte[0];
        private byte[] opacity = new byte[0];
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
