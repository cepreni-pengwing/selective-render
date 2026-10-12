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
    @Unique private boolean selectiverender$emitting;
    @org.spongepowered.asm.mixin.gen.Invoker("processQuad")
    protected abstract void selectiverender$emit(MutableQuadViewImpl quad);
    @Unique private final de.selectiverender.BoundaryQuadTracker selectiverender$quads = new de.selectiverender.BoundaryQuadTracker();
    @Unique private BlockPos selectiverender$currentPos;
    @Unique private BlockState selectiverender$currentState;

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterBlock(BakedModel model, BlockState state,
                                              BlockPos pos, BlockPos origin,
                                              CallbackInfo ci) {
        selectiverender$currentPos = pos;
        selectiverender$currentState = state;
        selectiverender$quads.reset();
        if (!SelectiveRenderState.shouldRender(state, pos.getX(), pos.getY(), pos.getZ())) ci.cancel();
    }

    @Inject(method = "processQuad", at = @At("HEAD"), cancellable = true)
    private void selectiverender$applyBoundaryMode(MutableQuadViewImpl quad, CallbackInfo ci) {
        if (selectiverender$emitting) return;
        if (!SelectiveRenderState.filteringActive()
                || !((de.selectiverender.SelectiveRenderConfig.hasBoundaryMode(SelectiveRenderSettings.BoundaryMode.COLOR) || de.selectiverender.SelectiveRenderConfig.hasBoundaryMode(SelectiveRenderSettings.BoundaryMode.TEXTURE)) || de.selectiverender.SelectiveRenderConfig.hasBoundaryMode(SelectiveRenderSettings.BoundaryMode.CULLED))) return;

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
        } else if (BoundaryColorTexture.custom(mode)) {
            for (int v = 0; v < 4; v++) selectiverender$quads.vertex(v, quad.x(v), quad.y(v), quad.z(v));
            if (!selectiverender$quads.accept(quad.lightFace().ordinal())) { ci.cancel(); return; }
            var source = new de.selectiverender.BoundaryQuadCopy().copyFrom(quad);
            boolean preserve = BoundaryColorTexture.preserveSourceTexture(selectiverender$currentState);
            selectiverender$emitting = true;
            try { for (var layer : de.selectiverender.BoundaryStyle.renderLayers(mode)) {
                if (!de.selectiverender.BoundaryStyle.visibleLayer(layer)) continue;
                quad.copyFrom(source);
                if (layer == SelectiveRenderSettings.BoundaryMode.TEXTURE && preserve) {
                    selectiverender$emit(quad); continue;
                }
                quad.colorIndex(-1); quad.material(de.selectiverender.BoundaryMaterial.get(layer));
                quad.cachedSprite(BoundaryColorTexture.sprite(layer));
                for (int vertex = 0; vertex < 4; vertex++) {
                    quad.color(vertex, BoundaryColorTexture.argb(layer));
                    quad.uv(vertex, BoundaryColorTexture.u(layer, quad.lightFace(), quad.x(vertex), quad.y(vertex), quad.z(vertex)),
                            BoundaryColorTexture.v(layer, quad.lightFace(), quad.x(vertex), quad.y(vertex), quad.z(vertex)));
                }
                selectiverender$emit(quad);
            } } finally { selectiverender$emitting = false; }
            ci.cancel();
        }
    }
}
