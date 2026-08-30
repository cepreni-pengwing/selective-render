package de.selectiverender;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.light.ChunkLightProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/** Main-thread virtual skylight sampler for entities and block entities. */
public final class VirtualSkyLightSampler {
    private static final int RADIUS = SelectiveRenderState.VIRTUAL_LIGHT_RADIUS;
    private static final int MAX_VOLUMES = 8;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<SectionKey, Volume> VOLUMES = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<SectionKey, Volume> eldest) {
            return size() > MAX_VOLUMES;
        }
    };
    private static ClientWorld cachedWorld;
    private static int cachedGeneration = Integer.MIN_VALUE;

    private VirtualSkyLightSampler() { }

    public static int sample(ClientWorld world, BlockPos pos) {
        if (!SelectiveRenderState.enabled() && !SelectiveRenderState.hideEnabled()) return -1;
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
        SectionKey key = new SectionKey(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        return VOLUMES.computeIfAbsent(key, ignored -> build(world, key)).sample(pos);
    }

    public static void invalidate() {
        VOLUMES.clear();
    }

    private static Volume build(ClientWorld world, SectionKey section) {
        int minX = (section.x << 4) - RADIUS;
        int maxX = (section.x << 4) + 15 + RADIUS;
        int minY = Math.max(world.getBottomY(), (section.y << 4) - RADIUS);
        int maxY = Math.min(world.getTopY() - 1, (section.y << 4) + 15 + RADIUS);
        int minZ = (section.z << 4) - RADIUS;
        int maxZ = (section.z << 4) + 15 + RADIUS;
        Volume volume = new Volume(minX, minY, minZ,
                maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    int index = volume.index(x - minX, y - minY, z - minZ);
                    BlockState state = sourceState(world, cursor, x, y, z);
                    volume.states[index] = state;
                    volume.opacity[index] = (byte) opacity(world, state, cursor);
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
                int highestOccluder = SelectiveRenderState.highestVisibleOccluder(world, x, z);
                int scanTop = highestOccluder == Integer.MIN_VALUE
                        ? maxY : Math.max(maxY, highestOccluder);
                int directLight = 15;
                BlockState aboveState = sourceState(world, above, x, scanTop + 1, z);
                for (int y = scanTop; y >= minY; y--) {
                    cursor.set(x, y, z);
                    BlockState state;
                    int stateOpacity;
                    if (y <= maxY) {
                        int index = volume.index(localX, y - minY, localZ);
                        state = volume.states[index];
                        stateOpacity = Byte.toUnsignedInt(volume.opacity[index]);
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
                    int index = volume.index(localX, y - minY, localZ);
                    volume.light[index] = (byte) directLight;
                    volume.queue[queueTail] = index;
                    queueTail = (queueTail + 1) % volume.light.length;
                    volume.queued[index] = 1;
                    queueSize++;
                }
            }
        }

        BlockPos.Mutable currentPos = above;
        BlockPos.Mutable nextPos = cursor;
        while (queueSize > 0) {
            int currentIndex = volume.queue[queueHead];
            queueHead = (queueHead + 1) % volume.light.length;
            queueSize--;
            volume.queued[currentIndex] = 0;
            int currentLight = Byte.toUnsignedInt(volume.light[currentIndex]);
            if (currentLight <= 1) continue;
            int localX = currentIndex % volume.sizeX;
            int yz = currentIndex / volume.sizeX;
            int localZ = yz % volume.sizeZ;
            int localY = yz / volume.sizeZ;
            currentPos.set(minX + localX, minY + localY, minZ + localZ);
            BlockState currentState = volume.states[currentIndex];
            for (Direction direction : DIRECTIONS) {
                int nextX = localX + direction.getOffsetX();
                int nextY = localY + direction.getOffsetY();
                int nextZ = localZ + direction.getOffsetZ();
                if (nextX < 0 || nextX >= volume.sizeX || nextY < 0 || nextY >= volume.sizeY
                        || nextZ < 0 || nextZ >= volume.sizeZ) continue;
                int nextIndex = volume.index(nextX, nextY, nextZ);
                nextPos.set(minX + nextX, minY + nextY, minZ + nextZ);
                int realisticOpacity = ChunkLightProvider.getRealisticOpacity(
                        world, currentState, currentPos, volume.states[nextIndex], nextPos,
                        direction, Math.max(1, Byte.toUnsignedInt(volume.opacity[nextIndex])));
                int nextLight = currentLight - realisticOpacity;
                if (nextLight <= 0 || Byte.toUnsignedInt(volume.light[nextIndex]) >= nextLight) continue;
                volume.light[nextIndex] = (byte) nextLight;
                if (volume.queued[nextIndex] == 0) {
                    volume.queue[queueTail] = nextIndex;
                    queueTail = (queueTail + 1) % volume.light.length;
                    volume.queued[nextIndex] = 1;
                    queueSize++;
                }
            }
        }
        return volume;
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

    private record SectionKey(int x, int y, int z) { }

    private static final class Volume {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final byte[] light;
        private final byte[] queued;
        private final byte[] opacity;
        private final BlockState[] states;
        private final int[] queue;

        private Volume(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            int cells = Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
            light = new byte[cells];
            queued = new byte[cells];
            opacity = new byte[cells];
            states = new BlockState[cells];
            queue = new int[cells];
        }

        private int sample(BlockPos pos) {
            return Byte.toUnsignedInt(light[index(
                    pos.getX() - minX, pos.getY() - minY, pos.getZ() - minZ)]);
        }

        private int index(int localX, int localY, int localZ) {
            return (localY * sizeZ + localZ) * sizeX + localX;
        }
    }
}
