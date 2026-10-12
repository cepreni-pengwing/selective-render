package de.selectiverender.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;
import de.selectiverender.BoundaryColorTexture;
import de.selectiverender.BoundaryGeometry;
import de.selectiverender.SelectiveRenderSettings;
import de.selectiverender.SelectiveRenderState;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModelBlockRenderer.class)
abstract class BlockModelRendererMixin {
    @org.spongepowered.asm.mixin.Unique private static final ThreadLocal<de.selectiverender.BoundaryQuadTracker> selectiverender$quads = ThreadLocal.withInitial(de.selectiverender.BoundaryQuadTracker::new);
    @Inject(method = "tesselateBlock", at = @At("HEAD"))
    private void selectiverender$resetBoundaryCopies(CallbackInfo ci) { selectiverender$quads.get().reset(); }

    @Shadow @Final private QuadInstance quadInstance;

    @Inject(method = "tesselateBlock", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filter(BlockQuadOutput output, float x, float y, float z,
            BlockAndTintGetter world, BlockPos pos, BlockState state, BlockStateModel model,
            long seed, CallbackInfo ci) {
        if (!SelectiveRenderState.shouldRender(state, pos.getX(), pos.getY(), pos.getZ())) ci.cancel();
    }

    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private void selectiverender$expose(BlockAndTintGetter world, BlockState state,
            Direction direction, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!SelectiveRenderState.filteringActive()) return;
        BlockState neighbor = world.getBlockState(pos.relative(direction));
        if (SelectiveRenderState.shouldRender(state, pos.getX(), pos.getY(), pos.getZ())
                && !SelectiveRenderState.shouldRender(neighbor, pos.getX() + direction.getStepX(),
                pos.getY() + direction.getStepY(), pos.getZ() + direction.getStepZ())) {
            cir.setReturnValue(SelectiveRenderState.boundaryModeForFace(pos, direction)
                    != SelectiveRenderSettings.BoundaryMode.CULLED);
        }
    }

    @Inject(method = "putQuadWithTint", at = @At("HEAD"), cancellable = true)
    private void selectiverender$boundary(BlockQuadOutput output, float x, float y, float z,
            BlockAndTintGetter world, BlockState state, BlockPos pos, BakedQuad quad, CallbackInfo ci) {
        if (!SelectiveRenderState.filteringActive()
                || !((de.selectiverender.SelectiveRenderConfig.hasBoundaryMode(SelectiveRenderSettings.BoundaryMode.COLOR) || de.selectiverender.SelectiveRenderConfig.hasBoundaryMode(SelectiveRenderSettings.BoundaryMode.TEXTURE)) || de.selectiverender.SelectiveRenderConfig.hasBoundaryMode(SelectiveRenderSettings.BoundaryMode.CULLED))) return;
        var mode = BoundaryGeometry.boundaryModeForQuad(pos, quad);
        if (mode == SelectiveRenderSettings.BoundaryMode.NORMAL) return;
        if (BoundaryColorTexture.custom(mode)) {
            var vertices = new org.joml.Vector3fc[]{quad.position0(), quad.position1(), quad.position2(), quad.position3()};
            for (int v = 0; v < 4; v++) selectiverender$quads.get().vertex(v, vertices[v].x(), vertices[v].y(), vertices[v].z());
            if (!selectiverender$quads.get().accept(quad.direction().ordinal())) { ci.cancel(); return; }

            for (var layer : de.selectiverender.BoundaryStyle.renderLayers(mode)) {
            if (!de.selectiverender.BoundaryStyle.visibleLayer(layer)) continue;
            if (layer == SelectiveRenderSettings.BoundaryMode.TEXTURE && BoundaryColorTexture.preserveSourceTexture(state)) {
                output.put(x, y, z, quad, quadInstance); continue;
            }
            boolean fullbright = de.selectiverender.BoundaryStyle.fullbright(layer);
            var material = quad.materialInfo();
            var custom = new BakedQuad.MaterialInfo(BoundaryColorTexture.sprite(layer),
                    BoundaryColorTexture.translucent(layer) ? ChunkSectionLayer.TRANSLUCENT : ChunkSectionLayer.SOLID,
                    material.itemRenderType(), -1, !fullbright, fullbright ? 15 : 0);
            var positions = new org.joml.Vector3fc[]{quad.position0(), quad.position1(), quad.position2(), quad.position3()};
            long[] uv = new long[4];
            for (int i = 0; i < 4; i++) {
                var p = positions[i];
                uv[i] = UVPair.pack(BoundaryColorTexture.u(layer, quad.direction(), p.x(), p.y(), p.z()),
                        BoundaryColorTexture.v(layer, quad.direction(), p.x(), p.y(), p.z()));
            }
            var replacement = new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                    uv[0], uv[1], uv[2], uv[3], quad.direction(), custom);
            int c0 = quadInstance.getColor(0), c1 = quadInstance.getColor(1);
            int c2 = quadInstance.getColor(2), c3 = quadInstance.getColor(3);
            int l0 = quadInstance.getLightCoords(0), l1 = quadInstance.getLightCoords(1), l2 = quadInstance.getLightCoords(2), l3 = quadInstance.getLightCoords(3);
            try {
                if (fullbright) quadInstance.setLightCoords(0x00F000F0);
                quadInstance.setColor(BoundaryColorTexture.argb(layer));
                output.put(x, y, z, replacement, quadInstance);
            } finally {
                quadInstance.setLightCoords(0, l0); quadInstance.setLightCoords(1, l1); quadInstance.setLightCoords(2, l2); quadInstance.setLightCoords(3, l3);
                quadInstance.setColor(0, c0); quadInstance.setColor(1, c1);
                quadInstance.setColor(2, c2); quadInstance.setColor(3, c3);
            }
            }
        }
        ci.cancel();
    }
}
