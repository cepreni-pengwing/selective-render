package de.selectiverender.mixin;

import de.selectiverender.BoundaryColorTexture;
import de.selectiverender.BoundaryGeometry;
import de.selectiverender.SelectiveRenderSettings;
import de.selectiverender.SelectiveRenderState;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(BlockModelRenderer.class)
abstract class BlockModelRendererMixin {
    @Unique private static final ThreadLocal<Boolean> selectiverender$texturedBoundary = ThreadLocal.withInitial(() -> false);
    @Unique private static final ThreadLocal<Boolean> selectiverender$coloredBoundary =
            ThreadLocal.withInitial(() -> false);

    @Inject(method = "renderQuad", at = @At("HEAD"), cancellable = true)
    private void selectiverender$beginBoundaryQuad(BlockRenderView world, BlockState state,
                                                    BlockPos pos, VertexConsumer consumer,
                                                    MatrixStack.Entry entry, BakedQuad quad,
                                                    float brightness0, float brightness1,
                                                    float brightness2, float brightness3,
                                                    int light0, int light1, int light2, int light3,
                                                    int overlay, CallbackInfo ci) {
        if (!SelectiveRenderState.filteringActive()) {
            if (selectiverender$coloredBoundary.get()) selectiverender$coloredBoundary.set(false);
            return;
        }
        SelectiveRenderSettings.BoundaryMode configuredMode =
                SelectiveRenderState.boundaryModeAt(pos.getX(), pos.getY(), pos.getZ());
        if (configuredMode == SelectiveRenderSettings.BoundaryMode.NORMAL) {
            if (selectiverender$coloredBoundary.get()) selectiverender$coloredBoundary.set(false);
            return;
        }
        SelectiveRenderSettings.BoundaryMode extensionMode =
                BoundaryGeometry.boundaryModeForQuad(pos, quad);
        boolean standardBoundary = SelectiveRenderState.isBoundaryFace(pos, quad.getFace());
        if (extensionMode == SelectiveRenderSettings.BoundaryMode.NORMAL && !standardBoundary) {
            selectiverender$coloredBoundary.set(false);
            return;
        }
        SelectiveRenderSettings.BoundaryMode mode =
                extensionMode != SelectiveRenderSettings.BoundaryMode.NORMAL ? extensionMode
                        : SelectiveRenderState.boundaryModeForFace(pos, quad.getFace());
        if (extensionMode == SelectiveRenderSettings.BoundaryMode.CULLED) {
            selectiverender$coloredBoundary.set(false);
            ci.cancel();
            return;
        }
        selectiverender$coloredBoundary.set(BoundaryColorTexture.custom(mode));
        selectiverender$texturedBoundary.set(mode == SelectiveRenderSettings.BoundaryMode.TEXTURE);
    }

    @ModifyArgs(method = "renderQuad", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/VertexConsumer;quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;[FFFFF[IIZ)V"))
    private void selectiverender$colorBoundaryQuad(Args args) {
        if (!selectiverender$coloredBoundary.get()) return;
        BakedQuad quad = args.get(1);
        int[] vertexData = quad.getVertexData().clone();
        int stride = vertexData.length / 4;
        boolean textured = selectiverender$texturedBoundary.get();
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            float x = Float.intBitsToFloat(vertexData[offset]), y = Float.intBitsToFloat(vertexData[offset + 1]), z = Float.intBitsToFloat(vertexData[offset + 2]);
            vertexData[offset + 4] = Float.floatToRawIntBits(BoundaryColorTexture.u(textured, quad.getFace(), x, y, z));
            vertexData[offset + 5] = Float.floatToRawIntBits(BoundaryColorTexture.v(textured, quad.getFace(), x, y, z));
        }
        args.set(1, new BakedQuad(vertexData, -1, quad.getFace(), BoundaryColorTexture.sprite(textured), false));
        int color = BoundaryColorTexture.argb(textured);
        args.set(2, new float[]{1, 1, 1, 1});
        args.set(3, (color >> 16 & 255) / 255f);
        args.set(4, (color >> 8 & 255) / 255f);
        args.set(5, (color & 255) / 255f);
        args.set(6, (color >>> 24) / 255f);
        args.set(9, false);
    }

    @Inject(method = "renderQuad", at = @At("RETURN"))
    private void selectiverender$endBoundaryQuad(BlockRenderView world, BlockState state,
                                                  BlockPos pos, VertexConsumer consumer,
                                                  MatrixStack.Entry entry, BakedQuad quad,
                                                  float brightness0, float brightness1,
                                                  float brightness2, float brightness3,
                                                  int light0, int light1, int light2, int light3,
                                                  int overlay, CallbackInfo ci) {
        if (selectiverender$coloredBoundary.get()) selectiverender$coloredBoundary.set(false);
    }
}
