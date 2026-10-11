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
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


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

    @WrapOperation(method = "renderQuad", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/VertexConsumer;quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;[FFFF[IIZ)V"))
    private void selectiverender$appearance(VertexConsumer consumer, MatrixStack.Entry entry, BakedQuad quad,
            float[] brightness, float red, float green, float blue, int[] light, int overlay, boolean useColor,
            Operation<Void> original) {
        if (!selectiverender$coloredBoundary.get()) {
            original.call(consumer, entry, quad, brightness, red, green, blue, light, overlay, useColor);
            return;
        }
        boolean textured = selectiverender$texturedBoundary.get();
        int color = BoundaryColorTexture.argb(textured);
        if ((color >>> 24) == 0) return;
        int[] data = quad.getVertexData(); int stride = data.length / 4;
        var normal = new org.joml.Vector3f(quad.getFace().getOffsetX(), quad.getFace().getOffsetY(), quad.getFace().getOffsetZ());
        entry.getNormalMatrix().transform(normal);
        var position = new org.joml.Vector3f();
        for (int i = 0; i < 4; i++) {
            int offset = i * stride;
            float x = Float.intBitsToFloat(data[offset]), y = Float.intBitsToFloat(data[offset + 1]), z = Float.intBitsToFloat(data[offset + 2]);
            position.set(x, y, z); entry.getPositionMatrix().transformPosition(position);
            consumer.vertex(position.x(), position.y(), position.z(),
                    (color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, (color >>> 24) / 255f,
                    BoundaryColorTexture.u(textured, quad.getFace(), x, y, z), BoundaryColorTexture.v(textured, quad.getFace(), x, y, z),
                    overlay, light[i], normal.x(), normal.y(), normal.z());
        }
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
