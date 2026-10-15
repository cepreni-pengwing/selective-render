package de.selectiverender;

import net.minecraft.core.BlockPos;

/** Rate-limited runtime diagnostics enabled together with debug region boxes. */
public final class LightingDiagnostics {
    private static final int MAX_MESSAGES_PER_SECOND = 24;
    private static long windowStartNanos;
    private static int messages;

    private LightingDiagnostics() { }

    public static synchronized void record(String kind, BlockPos pos, int vanillaSky,
                                           int centerVirtual, int nearbyVirtual,
                                           boolean directSky, int finalSky) {
        if (!SelectiveRenderSettings.debugBoxes()) return;
        long now = System.nanoTime();
        if (now - windowStartNanos >= 1_000_000_000L) {
            windowStartNanos = now;
            messages = 0;
        }
        if (messages++ >= MAX_MESSAGES_PER_SECOND) return;
        SelectiveRenderClient.LOGGER.info(
                "[SR light diagnostics] kind={} pos={},{},{} mode={} vanillaSky={} centerVirtual={} nearbyVirtual={} directSky={} finalSky={} cache={}",
                kind, pos.getX(), pos.getY(), pos.getZ(),
                SelectiveRenderSettings.virtualLightMode(), vanillaSky,
                centerVirtual, nearbyVirtual, directSky, finalSky,
                VirtualSkyLightSampler.cacheStatus(pos));
    }
}
