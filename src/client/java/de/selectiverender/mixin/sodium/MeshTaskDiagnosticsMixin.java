package de.selectiverender.mixin.sodium;

import de.selectiverender.PerformanceDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask", remap = false)
abstract class MeshTaskDiagnosticsMixin {
    @Unique private long selectiverender$buildStarted;
    @Inject(method = "execute(Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lme/jellysquid/mods/sodium/client/util/task/CancellationToken;)Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;", at = @At("HEAD"))
    private void selectiverender$startBuild(CallbackInfoReturnable<Object> cir) {
        selectiverender$buildStarted = PerformanceDiagnostics.startTimer();
    }
    @Inject(method = "execute(Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lme/jellysquid/mods/sodium/client/util/task/CancellationToken;)Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;", at = @At("RETURN"))
    private void selectiverender$endBuild(CallbackInfoReturnable<Object> cir) {
        PerformanceDiagnostics.finish(PerformanceDiagnostics.Metric.MESH_BUILD,
                selectiverender$buildStarted, 1, 0, 0, 0);
    }
}
