package de.selectiverender;
import java.util.HashSet;
import java.util.Set;
/** Per-model geometry deduplication: tint/UV overlay copies must not multiply boundary opacity. */
public final class BoundaryQuadTracker {
    private record Key(int face, int plane, long a, long b, long c, long d) { }
    private final Set<Key> emitted = new HashSet<>();
    private final float[] positions = new float[12];
    public void reset() { emitted.clear(); }
    public void vertex(int index, float x, float y, float z) { int p = index * 3; positions[p] = x; positions[p + 1] = y; positions[p + 2] = z; }
    private static int q(float value) { return Math.round(value * 100000); }
    private long pair(int vertex, int x, int y) { return (long)q(positions[vertex * 3 + x]) << 32 | (q(positions[vertex * 3 + y]) & 0xFFFFFFFFL); }
    public boolean accept(int face) {
        int axis = face / 2 == 0 ? 1 : face / 2 == 1 ? 2 : 0;
        int plane = q(positions[axis]);
        for (int v = 1; v < 4; v++) if (q(positions[v * 3 + axis]) != plane) return true; // Never deduplicate non-flat custom geometry.
        int x = axis == 0 ? 1 : 0, y = axis == 2 ? 1 : 2;
        long a = pair(0, x, y), b = pair(1, x, y), c = pair(2, x, y), d = pair(3, x, y), swap;
        if (a > b) { swap = a; a = b; b = swap; } if (c > d) { swap = c; c = d; d = swap; }
        if (a > c) { swap = a; a = c; c = swap; } if (b > d) { swap = b; b = d; d = swap; }
        if (b > c) { swap = b; b = c; c = swap; }
        return emitted.add(new Key(face, plane, a, b, c, d));
    }
}
