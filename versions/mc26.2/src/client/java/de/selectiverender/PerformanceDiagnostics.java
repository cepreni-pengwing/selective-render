package de.selectiverender;

import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Opt-in bounded wall-time diagnostics. Timings are nested, not additive CPU time.
 * Disabled probes do not read the clock, allocate, synchronize, or write logs.
 */
public final class PerformanceDiagnostics {
    public enum Metric {
        FRAME_INTERVAL, CLIENT_TICK, SODIUM_UPDATE_CHUNKS, SODIUM_PROCESS_UPLOAD, MESH_BUILD,
        PARTICLE_LIGHT, ENTITY_LIGHT, BLOCK_ENTITY_LIGHT,
        OCCLUDER_QUERY, SAMPLER_COLD_BUILD, SAMPLER_DIRTY_BUILD,
        SAMPLER_STATES, SAMPLER_COLUMNS, SAMPLER_FRONTIER, SAMPLER_PROPAGATION,
        SAMPLER_HIT, SAMPLER_MISS, SAMPLER_DIRTY_HIT, SAMPLER_EVICTION,
        SAMPLER_INVALIDATION, SAMPLER_DIRTY_QUEUE,
        SAMPLER_SOURCE_SECTION_HIT, SAMPLER_SOURCE_SECTION_MISS,
        SAMPLER_SOURCE_CELL_HIT, SAMPLER_SOURCE_CELL_MISS,
        TERRAIN_BUILD, TERRAIN_STATES, TERRAIN_COLUMNS, TERRAIN_FRONTIER, TERRAIN_PROPAGATION,
        TERRAIN_CACHE_LOOKUP, TERRAIN_CACHE_WRITE, TERRAIN_CACHE_HIT, TERRAIN_CACHE_MISS,
        TERRAIN_CACHE_STALE, TERRAIN_CACHE_REJECTED_WRITE, TERRAIN_CACHE_EVICTION,
        TERRAIN_SOURCE_CACHE_HIT, TERRAIN_SOURCE_CACHE_MISS, TERRAIN_SOURCE_CACHE_EVICTION,
        TERRAIN_VANILLA_FAST_PATH, TERRAIN_FAST_PATH_CHECK, TERRAIN_CACHE_NO_TICKET, TERRAIN_COLUMN_INVALIDATION,
        TERRAIN_COPIED_READ, TERRAIN_LIVE_READ, TERRAIN_CHUNK_LOOKUP,
        TERRAIN_PROPAGATED_CELLS, SAMPLER_PROPAGATED_CELLS,
        BLOCK_UPDATE, OPTICAL_UPDATE, LIGHT_EQUIVALENT_UPDATE, CHUNK_INVALIDATION,
        FULL_RENDER_RELOAD, REGION_REFRESH, PARTICLE_DIRECT_SKY, GENERATION_RESET, FILTER_SAMPLE
    }
    private static final Stats[] STATS = new Stats[Metric.values().length];
    private static final long REPORT_INTERVAL = 5_000_000_000L;
    private static final long SLOW_THRESHOLD = 4_000_000L;
    private static volatile boolean enabled;
    private static final AtomicInteger UPDATE_SAMPLES = new AtomicInteger();
    private static final AtomicReferenceArray<String> UPDATES = new AtomicReferenceArray<>(4);
    private static Consumer<String> sink = ignored -> {};
    private static long deadline, lastReport, lastFrame, tickStart;
    private static long gcCount, gcTime;
    private static final ThreadLocal<int[]> FILTER_SAMPLES = ThreadLocal.withInitial(() -> new int[1]);
    private record Peak(long nanos, String detail) {}
    private static final Peak ZERO_PEAK = new Peak(0, null);
    private static final class Stats {
        final LongAdder calls = new LongAdder(), nanos = new LongAdder(), work = new LongAdder();
        final AtomicReference<Peak> peak = new AtomicReference<>(ZERO_PEAK);
        final AtomicLong maxWork = new AtomicLong();
    }
    static { for (int i = 0; i < STATS.length; i++) STATS[i] = new Stats(); }
    private PerformanceDiagnostics() {}

    public static boolean enabled() { return enabled; }
    public static long startTimer() { return enabled ? System.nanoTime() : 0; }
    public static long sampledFilterTimer() {
        if (!enabled) return 0;
        return (FILTER_SAMPLES.get()[0]++ & 255) == 0 ? System.nanoTime() : 0;
    }

    public static synchronized void start(int seconds, Consumer<String> logger) {
        if (seconds < 1 || seconds > 600) throw new IllegalArgumentException("Duration must be 1..600 seconds");
        if (enabled) stop();
        for (Stats stats : STATS) { drain(stats); }
        UPDATE_SAMPLES.set(0);
        for (int i = 0; i < UPDATES.length(); i++) UPDATES.set(i, null);
        sink = logger;
        long now = System.nanoTime();
        lastReport = now; deadline = now + seconds * 1_000_000_000L;
        lastFrame = 0; tickStart = 0;
        gcCount = gc(false); gcTime = gc(true);
        enabled = true;
        sink.accept("[SR performance] START duration_s=" + seconds
                + " report_interval_s=5 slow_threshold_ms=4 timings=wall_time_nested_not_additive"
                + " processors=" + Runtime.getRuntime().availableProcessors());
        sink.accept("[SR performance] FILTER_SAMPLE sampling=1/256_per_thread; FRAME_INTERVAL includes VSync/FPS cap/GPU waits; zero positions on whole-frame/job metrics are not block coordinates");
    }

    public static void count(Metric metric, long work) {
        if (!enabled) return;
        Stats stats = STATS[metric.ordinal()];
        stats.calls.increment(); stats.work.add(work);
        stats.maxWork.accumulateAndGet(work, Math::max);
    }

    public static void finish(Metric metric, long started, long work, int x, int y, int z) {
        if (started == 0 || !enabled) return;
        record(metric, Math.max(0, System.nanoTime() - started), work, x, y, z);
    }

    static void record(Metric metric, long elapsed, long work, int x, int y, int z) {
        if (!enabled) return;
        Stats stats = STATS[metric.ordinal()];
        stats.calls.increment(); stats.nanos.add(elapsed); stats.work.add(work);
        stats.maxWork.accumulateAndGet(work, Math::max);
        Peak previous = stats.peak.get();
        while (elapsed > previous.nanos) {
            String detail = elapsed >= SLOW_THRESHOLD
                    ? "pos=" + x + "," + y + "," + z + " thread=" + Thread.currentThread().getName() : null;
            if (stats.peak.compareAndSet(previous, new Peak(elapsed, detail))) break;
            previous = stats.peak.get();
        }
    }

    public static void frame() {
        if (!enabled) return;
        long now = System.nanoTime();
        if (lastFrame != 0) record(Metric.FRAME_INTERVAL, now - lastFrame, 0, 0, 0, 0);
        lastFrame = now;
    }

    public static void blockUpdate(long started, boolean optical, int x, int y, int z,
                                   Object oldState, Object newState) {
        if (started == 0 || !enabled) return;
        finish(Metric.BLOCK_UPDATE, started, 1, x, y, z);
        int slot = UPDATE_SAMPLES.getAndIncrement();
        if (slot < UPDATES.length()) {
            UPDATES.set(slot, "[SR performance] UPDATE_SAMPLE pos=" + x + "," + y + "," + z
                    + " optical=" + optical + " old=" + safeLabel(String.valueOf(oldState), 240)
                    + " new=" + safeLabel(String.valueOf(newState), 240));
        }
    }

    public static void tickStart() { if (enabled) tickStart = System.nanoTime(); }

    /** Called only from the client thread. Returns true when a summary was emitted. */
    public static boolean tickEnd() {
        if (!enabled) return false;
        finish(Metric.CLIENT_TICK, tickStart, 0, 0, 0, 0);
        tickStart = 0;
        long now = System.nanoTime();
        if (now >= deadline) { stop(); return true; }
        if (now - lastReport >= REPORT_INTERVAL) { report(); return true; }
        return false;
    }

    public static synchronized void mark(String label) {
        if (!enabled) return;
        report();
        // Command labels cannot inject extra log lines.
        sink.accept("[SR performance] MARK " + safeLabel(label, 128));
    }

    public static synchronized void stop() {
        if (!enabled) return;
        report();
        enabled = false;
        sink.accept("[SR performance] STOP");
        sink = ignored -> {};
    }

    public static synchronized void report() {
        if (!enabled) return;
        long now = System.nanoTime(), count = gc(false), millis = gc(true);
        Runtime runtime = Runtime.getRuntime();
        sink.accept(String.format(Locale.ROOT,
                "[SR performance] SUMMARY window_ms=%.1f heap_used_mb=%d heap_committed_mb=%d heap_max_mb=%d gc_count=%d gc_ms=%d",
                (now - lastReport) / 1e6, (runtime.totalMemory() - runtime.freeMemory()) / 1048576,
                runtime.totalMemory() / 1048576, runtime.maxMemory() / 1048576,
                Math.max(0, count - gcCount), Math.max(0, millis - gcTime)));
        gcCount = count; gcTime = millis; lastReport = now;
        UPDATE_SAMPLES.set(0);
        for (int i = 0; i < UPDATES.length(); i++) {
            String update = UPDATES.getAndSet(i, null);
            if (update != null) sink.accept(update);
        }
        for (Metric metric : Metric.values()) {
            Stats stats = STATS[metric.ordinal()];
            long calls = stats.calls.sumThenReset(), nanos = stats.nanos.sumThenReset(),
                    work = stats.work.sumThenReset();
            Peak peak = stats.peak.getAndSet(ZERO_PEAK);
            long max = peak.nanos;
            long maxWork = stats.maxWork.getAndSet(0);
            String detail = peak.detail;
            if (calls == 0) continue;
            sink.accept(String.format(Locale.ROOT,
                    "[SR performance] metric=%s calls=%d total_ms=%.3f avg_ms=%.3f max_ms=%.3f work_units=%d max_work_units=%d%s",
                    metric, calls, nanos / 1e6, nanos / 1e6 / calls, max / 1e6,
                    work, maxWork, detail == null ? "" : " slowest_" + detail));
        }
    }

    private static void drain(Stats stats) {
        stats.calls.reset(); stats.nanos.reset(); stats.work.reset(); stats.peak.set(ZERO_PEAK);
        stats.maxWork.set(0);
    }
    private static long gc(boolean time) {
        long total = 0;
        for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0, time ? bean.getCollectionTime() : bean.getCollectionCount());
        }
        return total;
    }

    private static String safeLabel(String text, int limit) {
        String bounded = text.length() > limit ? text.substring(0, limit) : text;
        return bounded.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
    }
}
