package de.selectiverender;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class VirtualSkySearch {
    private static final int[][] DIRECTIONS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0},
            {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private VirtualSkySearch() { }

    public static int find(int startX, int startY, int startZ, int maxDistance,
                           PositionTest openSky, PositionTest passable) {
        Node start = new Node(startX, startY, startZ, 0);
        ArrayDeque<Node> queue = new ArrayDeque<>();
        Set<Coordinate> visited = new HashSet<>();
        queue.add(start);
        visited.add(start.coordinate());

        while (!queue.isEmpty()) {
            Node current = queue.removeFirst();
            if (openSky.test(current.x, current.y, current.z)) return 15 - current.distance;
            if (current.distance >= maxDistance) continue;

            for (int[] direction : DIRECTIONS) {
                int x = current.x + direction[0];
                int y = current.y + direction[1];
                int z = current.z + direction[2];
                Coordinate coordinate = new Coordinate(x, y, z);
                if (!visited.add(coordinate) || !passable.test(x, y, z)) continue;
                queue.addLast(new Node(x, y, z, current.distance + 1));
            }
        }
        return 0;
    }

    @FunctionalInterface
    public interface PositionTest {
        boolean test(int x, int y, int z);
    }

    private record Node(int x, int y, int z, int distance) {
        Coordinate coordinate() {
            return new Coordinate(x, y, z);
        }
    }

    private record Coordinate(int x, int y, int z) { }
}
