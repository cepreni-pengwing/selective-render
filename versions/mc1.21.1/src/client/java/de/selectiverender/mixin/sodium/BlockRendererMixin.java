package de.selectiverender.mixin.sodium;

import de.selectiverender.BoundaryColorTexture;
import de.selectiverender.BoundaryGeometry;
import de.selectiverender.SelectiveRenderState;
import de.selectiverender.SelectiveRenderSettings;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableQuadViewImpl;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer",
        remap = false)
abstract class BlockRendererMixin {
    @Unique private BlockPos selectiverender$currentPos;

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterBlock(BakedModel model, BlockState state,
                                              BlockPos pos, BlockPos origin,
                                              CallbackInfo ci) {
        selectiverender$currentPos = pos;
        if (!SelectiveRenderState.shouldRender(pos)) ci.cancel();
    }

    @Inject(method = "processQuad", at = @At("HEAD"), cancellable = true)
    private void selectiverender$applyBoundaryMode(MutableQuadViewImpl quad, CallbackInfo ci) {
        if (!SelectiveRenderState.filteringActive()
                || SelectiveRenderSettings.boundaryMode()
                == SelectiveRenderSettings.BoundaryMode.NORMAL) return;

        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < 4; vertex++) {
            float x = quad.x(vertex);
            float y = quad.y(vertex);
            float z = quad.z(vertex);
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }
        SelectiveRenderSettings.BoundaryMode mode = BoundaryGeometry.boundaryModeForQuad(
                selectiverender$currentPos, minX, maxX, minY, maxY, minZ, maxZ);
        if (mode == SelectiveRenderSettings.BoundaryMode.CULLED) {
            ci.cancel();
        } else if (mode == SelectiveRenderSettings.BoundaryMode.BLACK) {
            int black = ColorABGR.pack(0, 0, 0, 255);
            float u = BoundaryColorTexture.u();
            float v = BoundaryColorTexture.v();
            for (int vertex = 0; vertex < 4; vertex++) {
                quad.color(vertex, black);
                quad.uv(vertex, u, v);
            }
        }
    }
}
