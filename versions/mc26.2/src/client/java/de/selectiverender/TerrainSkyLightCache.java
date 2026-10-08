package de.selectiverender;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bounded, thread-safe terrain light results. Tickets are captured when Sodium
 * takes its block snapshot, not when a worker eventually starts meshing it.
 */
public final class TerrainSkyLightCache {
    public static final TerrainSkyLightCache INSTANCE = new TerrainSkyLightCache();
    private static final long MAX_BYTES = 12L * 1024 * 1024;
    private static final int MAX_ENTRIES = 128;
    private final Map<Long, Long> columns = new HashMap<>();
    private final Map<Object, Ticket> contexts = new WeakHashMap<>();
    private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>(16, .75f, true);
    private long sequence;
    private long epoch;
    private long bytes;

    public record Ticket(long epoch, long sequence, int generation, int policy) {}
    public record Key(Object world, int generation, int policy,
                      int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}
    public record Result(byte[] light) {
        public boolean vanilla() { return light == null; }
    }
    private record Entry(long revision, Result result) {}

    public synchronized void capture(Object context, int generation, int policy) {
        if (context != null) contexts.put(context, new Ticket(epoch, sequence, generation, policy));
    }

    public synchronized Ticket ticket(Object context) { return contexts.get(context); }

    public synchronized Result get(Key key, Ticket ticket) {
        long revision = revision(key);
        if (!valid(key, ticket, revision)) return null;
        Entry entry = entries.get(key);
        return entry != null && entry.revision == revision ? entry.result : null;
    }

    public synchronized void put(Key key, Ticket ticket, byte[] light, int length) {
        long revision = revision(key);
        // An update during the solve must never publish stale snapshot lighting.
        if (!valid(key, ticket, revision)) return;
        Result result = new Result(light == null ? null : Arrays.copyOf(light, length));
        Entry previous = entries.put(key, new Entry(revision, result));
        if (previous != null) bytes -= size(previous);
        bytes += size(entries.get(key));
        while (entries.size() > MAX_ENTRIES || bytes > MAX_BYTES) {
            var iterator = entries.entrySet().iterator();
            bytes -= size(iterator.next().getValue());
            iterator.remove();
        }
    }

    public synchronized void invalidateColumn(int chunkX, int chunkZ) {
        // Bound revision metadata even on very long exploration sessions.
        if (columns.size() >= 16384) clear();
        columns.put(column(chunkX, chunkZ), ++sequence);
    }

    public synchronized void clear() {
        epoch++;
        sequence = 0;
        columns.clear();
        contexts.clear();
        entries.clear();
        bytes = 0;
    }

    private boolean valid(Key key, Ticket ticket, long revision) {
        return ticket != null && ticket.epoch == epoch && ticket.sequence >= revision
                && ticket.generation == key.generation && ticket.policy == key.policy;
    }

    private long revision(Key key) {
        long revision = 0;
        for (int z = key.minZ >> 4; z <= (key.maxZ >> 4); z++) {
            for (int x = key.minX >> 4; x <= (key.maxX >> 4); x++) {
                revision = Math.max(revision, columns.getOrDefault(column(x, z), 0L));
            }
        }
        return revision;
    }

    private static long column(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }
    private static int size(Entry entry) { return entry.result.light == null ? 0 : entry.result.light.length; }
}
