package de.selectiverender.mixin.sodium;

import de.selectiverender.BoundaryColorTexture;
import de.selectiverender.BoundaryGeometry;
import de.selectiverender.SelectiveRenderSettings;
import de.selectiverender.SelectiveRenderState;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockRenderer.class, remap = false)
abstract class BlockRendererMixin {
    @Shadow protected BlockPos pos;

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterBlock(BlockStateModel model, BlockState state,
            BlockPos position, BlockPos origin, CallbackInfo ci) {
        if (!SelectiveRenderState.shouldRender(position)) ci.cancel();
    }

    // Sodium 0.9 uses this path for both standard and Fabric renderer models.
    // Quads are mutable copies here; never modify shared baked model data.
    @Inject(method = "processQuad", at = @At("HEAD"), cancellable = true)
    private void selectiverender$boundary(MutableQuadViewImpl quad, CallbackInfo ci) {
        if (!SelectiveRenderState.filteringActive()
                || SelectiveRenderSettings.boundaryMode() == SelectiveRenderSettings.BoundaryMode.NORMAL) return;
        var mode = selectiverender$boundaryMode(quad);
        if (mode == SelectiveRenderSettings.BoundaryMode.CULLED) {
            ci.cancel();
        } else if (mode == SelectiveRenderSettings.BoundaryMode.BLACK) {
            quad.setTintIndex(-1);
            quad.setRenderType(ChunkSectionLayer.SOLID);
            float u = BoundaryColorTexture.u(), v = BoundaryColorTexture.v();
            for (int vertex = 0; vertex < 4; vertex++) {
                quad.setColor(vertex, 0xFF000000);
                quad.setUV(vertex, u, v);
            }
        }
    }

    @Unique
    private SelectiveRenderSettings.BoundaryMode selectiverender$boundaryMode(MutableQuadViewImpl quad) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < 4; vertex++) {
            float x = quad.getX(vertex), y = quad.getY(vertex), z = quad.getZ(vertex);
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }
        return BoundaryGeometry.boundaryModeForQuad(pos, minX, maxX, minY, maxY, minZ, maxZ);
    }
}
