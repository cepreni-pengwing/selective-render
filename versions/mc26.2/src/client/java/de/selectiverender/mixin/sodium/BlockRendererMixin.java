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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockRenderer.class, remap = false)
abstract class BlockRendererMixin {
    @Unique private boolean selectiverender$emitting;
    @org.spongepowered.asm.mixin.gen.Invoker("processQuad")
    protected abstract void selectiverender$emit(MutableQuadViewImpl quad);
    @Unique private final de.selectiverender.BoundaryQuadTracker selectiverender$quads = new de.selectiverender.BoundaryQuadTracker();
    // Sodium 0.9.2 moved its current block position into a superclass. Keep the
    // renderModel argument instead of shadowing that internal field so minor Sodium
    // layout changes do not prevent the mixin from applying at world load.
    @Unique private BlockPos selectiverender$currentPos;
    @Unique private BlockState selectiverender$currentState;

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterBlock(BlockStateModel model, BlockState state,
            BlockPos position, BlockPos origin, CallbackInfo ci) {
        selectiverender$currentPos = position;
        selectiverender$currentState = state;
        selectiverender$quads.reset();
        if (!SelectiveRenderState.shouldRender(state, position.getX(), position.getY(), position.getZ())) ci.cancel();
    }

    // Sodium 0.9 uses this path for both standard and Fabric renderer models.
    // Quads are mutable copies here; never modify shared baked model data.
    @Inject(method = "processQuad", at = @At("HEAD"), cancellable = true)
    private void selectiverender$boundary(MutableQuadViewImpl quad, CallbackInfo ci) {
        if (selectiverender$emitting) return;
        if (!SelectiveRenderState.filteringActive()
                || !((de.selectiverender.SelectiveRenderConfig.hasBoundaryMode(SelectiveRenderSettings.BoundaryMode.COLOR) || de.selectiverender.SelectiveRenderConfig.hasBoundaryMode(SelectiveRenderSettings.BoundaryMode.TEXTURE)) || de.selectiverender.SelectiveRenderConfig.hasBoundaryMode(SelectiveRenderSettings.BoundaryMode.CULLED))) return;
        var mode = selectiverender$boundaryMode(quad);
        if (mode == SelectiveRenderSettings.BoundaryMode.CULLED) {
            ci.cancel();
        } else if (BoundaryColorTexture.custom(mode)) {
            for (int v = 0; v < 4; v++) selectiverender$quads.vertex(v, quad.getX(v), quad.getY(v), quad.getZ(v));
            if (!selectiverender$quads.accept(quad.getLightFace().ordinal())) { ci.cancel(); return; }
            var source = new de.selectiverender.BoundaryQuadCopy().copyFrom(quad);
            boolean preserve = BoundaryColorTexture.preserveSourceTexture(selectiverender$currentState);
            selectiverender$emitting = true;
            try { for (var layer : de.selectiverender.BoundaryStyle.renderLayers(mode)) {
                if (!de.selectiverender.BoundaryStyle.visibleLayer(layer)) continue;
                quad.copyFrom(source);
                if (layer == SelectiveRenderSettings.BoundaryMode.TEXTURE && preserve) {
                    selectiverender$emit(quad); continue;
                }
                quad.setTintIndex(-1);
                boolean fullbright = de.selectiverender.BoundaryStyle.fullbright(layer);
                quad.setEmissive(fullbright); quad.setDiffuseShade(!fullbright);
                quad.setAmbientOcclusion(fullbright ? net.minecraft.util.TriState.FALSE : net.minecraft.util.TriState.DEFAULT);
                quad.cachedSprite(BoundaryColorTexture.sprite(layer));
                quad.setRenderType(BoundaryColorTexture.translucent(layer) ? ChunkSectionLayer.TRANSLUCENT : ChunkSectionLayer.SOLID);
                for (int vertex = 0; vertex < 4; vertex++) {
                    quad.setColor(vertex, BoundaryColorTexture.argb(layer));
                    quad.setUV(vertex, BoundaryColorTexture.u(layer, quad.getLightFace(), quad.getX(vertex), quad.getY(vertex), quad.getZ(vertex)),
                            BoundaryColorTexture.v(layer, quad.getLightFace(), quad.getX(vertex), quad.getY(vertex), quad.getZ(vertex)));
                }
                selectiverender$emit(quad);
            } } finally { selectiverender$emitting = false; }
            ci.cancel();
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
        return BoundaryGeometry.boundaryModeForQuad(selectiverender$currentPos,
                minX, maxX, minY, maxY, minZ, maxZ);
    }
}
