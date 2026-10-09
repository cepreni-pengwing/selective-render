package de.selectiverender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceDiagnosticsTest {
    private final List<String> lines = new ArrayList<>();
    @AfterEach void stop() { PerformanceDiagnostics.stop(); }

    @Test void disabledProbesDoNotRecordOrReadTheClock() {
        PerformanceDiagnostics.stop();
        assertFalse(PerformanceDiagnostics.enabled());
        assertEquals(0, PerformanceDiagnostics.startTimer());
        PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.TERRAIN_CACHE_HIT, 1);
        PerformanceDiagnostics.finish(PerformanceDiagnostics.Metric.TERRAIN_BUILD, 1, 85000, 0, 0, 0);
        PerformanceDiagnostics.report();
        assertTrue(lines.isEmpty());
    }

    @Test void summariesContainCountsTimesLocationsAndThreadNames() {
        PerformanceDiagnostics.start(120, lines::add);
        PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.TERRAIN_CACHE_HIT, 3);
        PerformanceDiagnostics.record(PerformanceDiagnostics.Metric.SAMPLER_COLD_BUILD,
                12_000_000, 85184, 16, 64, -16);
        PerformanceDiagnostics.report();
        assertTrue(lines.stream().anyMatch(line -> line.contains("wall_time_nested_not_additive")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("SAMPLER_COLD_BUILD")
                && line.contains("max_ms=12.000") && line.contains("work_units=85184")
                && line.contains("pos=16,64,-16") && line.contains("thread=")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("heap_used_mb=") && line.contains("gc_ms=")));
    }

    @Test void reportsDrainCountersAndMarkersCannotInjectLines() {
        PerformanceDiagnostics.start(120, lines::add);
        PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.OPTICAL_UPDATE, 1);
        PerformanceDiagnostics.mark("painting\nroof\ttest");
        assertTrue(lines.stream().anyMatch(line -> line.endsWith("MARK painting roof test")));
        lines.clear();
        PerformanceDiagnostics.report();
        assertFalse(lines.stream().anyMatch(line -> line.contains("OPTICAL_UPDATE")));
    }

    @Test void stopProducesFinalSummaryAndDisablesFurtherProbes() {
        PerformanceDiagnostics.start(120, lines::add);
        PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.BLOCK_UPDATE, 1);
        PerformanceDiagnostics.stop();
        int count = lines.size();
        PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.BLOCK_UPDATE, 1);
        PerformanceDiagnostics.report();
        assertEquals(count, lines.size());
        assertEquals("[SR performance] STOP", lines.get(lines.size() - 1));
        assertFalse(PerformanceDiagnostics.enabled());
    }

    @Test void concurrentWorkersDoNotLoseCounters() throws Exception {
        PerformanceDiagnostics.start(120, lines::add);
        var pool = Executors.newFixedThreadPool(4);
        try {
            var tasks = new ArrayList<java.util.concurrent.Callable<Void>>();
            for (int thread = 0; thread < 4; thread++) tasks.add(() -> {
                for (int i = 0; i < 1000; i++) {
                    PerformanceDiagnostics.record(PerformanceDiagnostics.Metric.TERRAIN_BUILD,
                            1000, 1, 0, 0, 0);
                }
                return null;
            });
            for (var task : pool.invokeAll(tasks)) task.get();
        } finally { pool.shutdownNow(); }
        PerformanceDiagnostics.report();
        assertTrue(lines.stream().anyMatch(line -> line.contains("TERRAIN_BUILD")
                && line.contains("calls=4000") && line.contains("work_units=4000")));
    }

    @Test void recordingAutomaticallyStopsAtTheDeadline() throws Exception {
        PerformanceDiagnostics.start(1, lines::add);
        Thread.sleep(1100);
        assertTrue(PerformanceDiagnostics.tickEnd());
        assertFalse(PerformanceDiagnostics.enabled());
        assertTrue(lines.contains("[SR performance] STOP"));
    }

    @Test void invalidDurationDoesNotEnableRecording() {
        assertThrows(IllegalArgumentException.class, () -> PerformanceDiagnostics.start(0, lines::add));
        assertThrows(IllegalArgumentException.class, () -> PerformanceDiagnostics.start(601, lines::add));
        assertFalse(PerformanceDiagnostics.enabled());
    }

    @Test void filterSamplingIsBoundedAndDisabledByDefault() {
        assertEquals(0, PerformanceDiagnostics.sampledFilterTimer());
        PerformanceDiagnostics.start(120, lines::add);
        int sampled = 0;
        for (int i = 0; i < 512; i++) {
            if (PerformanceDiagnostics.sampledFilterTimer() != 0) sampled++;
        }
        assertEquals(2, sampled);
    }

    @Test void updateExamplesAreBoundedAndSanitized() {
        PerformanceDiagnostics.start(120, lines::add);
        for (int i = 0; i < 100; i++) {
            PerformanceDiagnostics.blockUpdate(PerformanceDiagnostics.startTimer(), true,
                    i, 64, 0, "old\nblock", "new");
        }
        PerformanceDiagnostics.report();
        assertEquals(4, lines.stream().filter(line -> line.contains("UPDATE_SAMPLE")).count());
        assertTrue(lines.stream().noneMatch(line -> line.contains("\n")));
        lines.clear();
        PerformanceDiagnostics.report();
        assertTrue(lines.stream().noneMatch(line -> line.contains("UPDATE_SAMPLE")));
    }
}
