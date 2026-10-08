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
                || SelectiveRenderSettings.boundaryMode() == SelectiveRenderSettings.BoundaryMode.NORMAL) return;
        var mode = BoundaryGeometry.boundaryModeForQuad(pos, quad);
        if (mode == SelectiveRenderSettings.BoundaryMode.NORMAL) return;
        if (mode == SelectiveRenderSettings.BoundaryMode.BLACK) {
            long uv = UVPair.pack(BoundaryColorTexture.u(), BoundaryColorTexture.v());
            var material = quad.materialInfo();
            var solid = new BakedQuad.MaterialInfo(material.sprite(), ChunkSectionLayer.SOLID,
                    material.itemRenderType(), -1, material.shade(), material.lightEmission());
            var black = new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                    uv, uv, uv, uv, quad.direction(), solid);
            // This instance is reused by vanilla; restore it even if the output throws.
            int c0 = quadInstance.getColor(0), c1 = quadInstance.getColor(1);
            int c2 = quadInstance.getColor(2), c3 = quadInstance.getColor(3);
            try {
                quadInstance.setColor(0xFF000000);
                output.put(x, y, z, black, quadInstance);
            } finally {
                quadInstance.setColor(0, c0); quadInstance.setColor(1, c1);
                quadInstance.setColor(2, c2); quadInstance.setColor(3, c3);
            }
        }
        ci.cancel();
    }
}
