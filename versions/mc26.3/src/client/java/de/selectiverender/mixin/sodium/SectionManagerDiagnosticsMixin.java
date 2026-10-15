package de.selectiverender.mixin.sodium;

import de.selectiverender.PerformanceDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Main-thread scheduling/waiting and result processing, not worker CPU time. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
abstract class SectionManagerDiagnosticsMixin {
    @Unique private long selectiverender$updateStarted;
    @Unique private long selectiverender$uploadStarted;
    @Inject(method = "updateChunks", at = @At("HEAD"))
    private void selectiverender$startUpdate(CallbackInfo ci) {
        selectiverender$updateStarted = PerformanceDiagnostics.startTimer();
    }
    @Inject(method = "updateChunks", at = @At("RETURN"))
    private void selectiverender$endUpdate(CallbackInfo ci) {
        PerformanceDiagnostics.finish(PerformanceDiagnostics.Metric.SODIUM_UPDATE_CHUNKS,
                selectiverender$updateStarted, 1, 0, 0, 0);
    }
    @Inject(method = "processChunkBuilds", at = @At("HEAD"))
    private void selectiverender$startUpload(CallbackInfo ci) {
        selectiverender$uploadStarted = PerformanceDiagnostics.startTimer();
    }
    @Inject(method = "processChunkBuilds", at = @At("RETURN"))
    private void selectiverender$endUpload(CallbackInfo ci) {
        PerformanceDiagnostics.finish(PerformanceDiagnostics.Metric.SODIUM_PROCESS_UPLOAD,
                selectiverender$uploadStarted, 1, 0, 0, 0);
    }
}
