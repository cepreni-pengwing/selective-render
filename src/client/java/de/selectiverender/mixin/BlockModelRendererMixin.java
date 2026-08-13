package de.selectiverender.mixin;

import de.selectiverender.BoundaryColorTexture;
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
    @Unique private static final ThreadLocal<Boolean> selectiverender$coloredBoundary =
            ThreadLocal.withInitial(() -> false);

    @Inject(method = "renderQuad", at = @At("HEAD"))
    private void selectiverender$beginBoundaryQuad(BlockRenderView world, BlockState state,
                                                    BlockPos pos, VertexConsumer consumer,
                                                    MatrixStack.Entry entry, BakedQuad quad,
                                                    float brightness0, float brightness1,
                                                    float brightness2, float brightness3,
                                                    int light0, int light1, int light2, int light3,
                                                    int overlay, CallbackInfo ci) {
        selectiverender$coloredBoundary.set(
                SelectiveRenderSettings.boundaryMode()
                        == SelectiveRenderSettings.BoundaryMode.COLORED
                        && SelectiveRenderState.isBoundaryFace(pos, quad.getFace()));
    }

    @ModifyArgs(method = "renderQuad", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/VertexConsumer;quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;[FFFF[IIZ)V"))
    private void selectiverender$colorBoundaryQuad(Args args) {
        if (!selectiverender$coloredBoundary.get()) return;
        BakedQuad quad = args.get(1);
        int[] vertexData = quad.getVertexData().clone();
        int stride = vertexData.length / 4;
        int u = Float.floatToRawIntBits(BoundaryColorTexture.u());
        int v = Float.floatToRawIntBits(BoundaryColorTexture.v());
        for (int vertex = 0; vertex < 4; vertex++) {
            vertexData[vertex * stride + 4] = u;
            vertexData[vertex * stride + 5] = v;
        }
        args.set(1, new BakedQuad(vertexData, quad.getColorIndex(), quad.getFace(),
                quad.getSprite(), quad.hasShade()));
        args.set(3, 0.0f);
        args.set(4, 0.0f);
        args.set(5, 0.0f);
    }

    @Inject(method = "renderQuad", at = @At("RETURN"))
    private void selectiverender$endBoundaryQuad(BlockRenderView world, BlockState state,
                                                  BlockPos pos, VertexConsumer consumer,
                                                  MatrixStack.Entry entry, BakedQuad quad,
                                                  float brightness0, float brightness1,
                                                  float brightness2, float brightness3,
                                                  int light0, int light1, int light2, int light3,
                                                  int overlay, CallbackInfo ci) {
        selectiverender$coloredBoundary.set(false);
    }
}
