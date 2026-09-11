package de.selectiverender;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
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
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Long2ObjectLinkedOpenHashMap<CoreVolume> VOLUMES =
            new Long2ObjectLinkedOpenHashMap<>();
    private static final Scratch SCRATCH = new Scratch();
    private static ClientLevel cachedWorld;
    private static int cachedGeneration = Integer.MIN_VALUE;

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
            VOLUMES.clear();
            cachedWorld = world;
            cachedGeneration = generation;
        }
        int sectionX = pos.getX() >> 4;
        int sectionY = pos.getY() >> 4;
        int sectionZ = pos.getZ() >> 4;
        long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
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
                    SectionPos.x(key), SectionPos.y(key),
                    SectionPos.z(key), blockX, blockY, blockZ, RADIUS)) {
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
                    SectionPos.x(key), SectionPos.z(key),
                    chunkX, chunkZ, RADIUS)) iterator.remove();
        }
    }

    private static CoreVolume build(ClientLevel world, int sectionX, int sectionY, int sectionZ) {
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
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

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
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int localX = x - minX;
                int localZ = z - minZ;
                int worldSurface = world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                int visibleTop = SelectiveRenderState.visibleColumnTop(x, z,
                        Math.min(world.getMaxY(), worldSurface));
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
                    SCRATCH.light[index] = (byte) directLight;
                    SCRATCH.queue[queueTail] = index;
                    queueTail = (queueTail + 1) % cells;
                    SCRATCH.queued[index] = 1;
                    queueSize++;
                }
            }
        }

        BlockPos.MutableBlockPos currentPos = above;
        BlockPos.MutableBlockPos nextPos = cursor;
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
                int nextX = localX + direction.getStepX();
                int nextY = localY + direction.getStepY();
                int nextZ = localZ + direction.getStepZ();
                if (nextX < 0 || nextX >= sizeX || nextY < 0 || nextY >= sizeY
                        || nextZ < 0 || nextZ >= sizeZ) continue;
                int nextIndex = SCRATCH.index(nextX, nextY, nextZ);
                int existingLight = Byte.toUnsignedInt(SCRATCH.light[nextIndex]);
                if (!VirtualLightPropagation.canImprove(currentLight, existingLight)) continue;
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
        return new CoreVolume(coreMinX, coreMinY, coreMinZ, coreLight);
    }

    private static BlockState sourceState(ClientLevel world, BlockPos.MutableBlockPos cursor,
                                          int x, int y, int z) {
        cursor.set(x, y, z);
        if (!SelectiveRenderState.shouldRender(cursor)) return Blocks.AIR.defaultBlockState();
        BlockState state = world.getBlockState(cursor);
        return state == null ? Blocks.AIR.defaultBlockState() : state;
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
