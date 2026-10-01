package de.selectiverender;

import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class VirtualLightFrontierTest {
    @Test void uniformOpenSkyNeedsNoPropagationQueue() {
        byte[] light = new byte[44 * 44 * 44];
        Arrays.fill(light, (byte) 15);
        assertEquals(0, VirtualLightPropagation.seedFrontier(
                light, new byte[light.length], new int[light.length], 44, 44, 44));
    }

    @Test void queuesOnlyTheLitFaceBesideAShadow() {
        int side = 44;
        byte[] light = new byte[side * side * side];
        Arrays.fill(light, light.length / 2, light.length, (byte) 15);
        assertEquals(side * side, VirtualLightPropagation.seedFrontier(
                light, new byte[light.length], new int[light.length], side, side, side));
    }

    @Test void matchesAllSeedReferenceWithObstaclesAndDirectionalPolicies() {
        Random random = new Random(94322);
        for (int trial = 0; trial < 250; trial++) {
            int sx = 1 + random.nextInt(9), sy = 1 + random.nextInt(9), sz = 1 + random.nextInt(9);
            int cells = sx * sy * sz;
            byte[] seeds = new byte[cells];
            int[][] costs = new int[cells][6];
            for (int i = 0; i < cells; i++) {
                seeds[i] = (byte) (random.nextBoolean() ? 0 : random.nextInt(16));
                for (int d = 0; d < 6; d++) costs[i][d] = 1 + random.nextInt(16);
            }
            assertArrayEquals(propagate(seeds, costs, sx, sy, sz, false),
                    propagate(seeds, costs, sx, sy, sz, true), "trial " + trial);
        }
    }

    private static byte[] propagate(byte[] seeds, int[][] costs, int sx, int sy, int sz,
                                    boolean frontier) {
        byte[] light = seeds.clone();
        ArrayDeque<Integer> work = new ArrayDeque<>();
        if (frontier) {
            int[] queue = new int[light.length];
            byte[] queued = new byte[light.length];
            int count = VirtualLightPropagation.seedFrontier(light, queued, queue, sx, sy, sz);
            for (int i = 0; i < count; i++) work.add(queue[i]);
        } else {
            for (int i = 0; i < light.length; i++) if (light[i] > 1) work.add(i);
        }
        int plane = sx * sz;
        while (!work.isEmpty()) {
            int i = work.remove();
            int x = i % sx, z = (i / sx) % sz, y = i / plane;
            int[] neighbors = {x > 0 ? i - 1 : -1, x + 1 < sx ? i + 1 : -1,
                    z > 0 ? i - sx : -1, z + 1 < sz ? i + sx : -1,
                    y > 0 ? i - plane : -1, y + 1 < sy ? i + plane : -1};
            for (int d = 0; d < 6; d++) {
                int n = neighbors[d];
                if (n < 0) continue;
                int value = light[i] - costs[i][d];
                if (value > light[n]) {
                    light[n] = (byte) value;
                    work.add(n);
                }
            }
        }
        return light;
    }
}
