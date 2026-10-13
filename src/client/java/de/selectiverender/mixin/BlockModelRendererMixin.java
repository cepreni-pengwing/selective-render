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
    @org.spongepowered.asm.mixin.Unique private static final ThreadLocal<de.selectiverender.BoundaryQuadTracker> selectiverender$quads = ThreadLocal.withInitial(de.selectiverender.BoundaryQuadTracker::new);
    @Inject(method = "render", at = @At("HEAD"))
    private void selectiverender$resetBoundaryCopies(CallbackInfo ci) { selectiverender$quads.get().reset(); }

    @Unique private static final ThreadLocal<BlockPos> selectiverender$position = new ThreadLocal<>();
    @Unique private static final ThreadLocal<SelectiveRenderSettings.BoundaryMode> selectiverender$texturedBoundary = ThreadLocal.withInitial(() -> SelectiveRenderSettings.BoundaryMode.COLOR);
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
        SelectiveRenderSettings.BoundaryMode mode = extensionMode;
        if (mode == SelectiveRenderSettings.BoundaryMode.NORMAL) {
            selectiverender$coloredBoundary.set(false);
            return;
        }
        if (extensionMode == SelectiveRenderSettings.BoundaryMode.CULLED) {
            selectiverender$coloredBoundary.set(false);
            ci.cancel();
            return;
        }
        if (mode == SelectiveRenderSettings.BoundaryMode.COLOR && de.selectiverender.BoundaryRenderContext.opacityAt(pos) == 0 && !de.selectiverender.BoundaryRenderContext.normalAt(pos)) { ci.cancel(); return; }
        if (BoundaryColorTexture.custom(mode)) {
            int[] vertices = quad.getVertexData(); int stride = vertices.length / 4;
            for (int v = 0; v < 4; v++) selectiverender$quads.get().vertex(v, Float.intBitsToFloat(vertices[v * stride]),
                    Float.intBitsToFloat(vertices[v * stride + 1]), Float.intBitsToFloat(vertices[v * stride + 2]));
            if (!selectiverender$quads.get().accept(quad.getFace().ordinal())) {
                selectiverender$coloredBoundary.set(false);
                if(mode == SelectiveRenderSettings.BoundaryMode.COLOR && de.selectiverender.BoundaryRenderContext.normalAt(pos)) return;
                ci.cancel(); return;
            }
        }
        selectiverender$coloredBoundary.set(BoundaryColorTexture.custom(mode));
        selectiverender$texturedBoundary.set(mode);
        selectiverender$position.set(pos);
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
        var mode = selectiverender$texturedBoundary.get();

        int[] data = quad.getVertexData(); int stride = data.length / 4;
        var normal = new org.joml.Vector3f(quad.getFace().getOffsetX(), quad.getFace().getOffsetY(), quad.getFace().getOffsetZ());
        entry.getNormalMatrix().transform(normal);
        var position = new org.joml.Vector3f();
        de.selectiverender.BoundaryRenderContext.bind(selectiverender$position.get());
            try { for (var layer : de.selectiverender.BoundaryStyle.renderLayers(mode)) {
            if (!de.selectiverender.BoundaryStyle.visibleLayer(layer)) continue;
            if (layer == SelectiveRenderSettings.BoundaryMode.NORMAL) { original.call(consumer, entry, quad, brightness, red, green, blue, light, overlay, useColor); continue; }
            if (layer == SelectiveRenderSettings.BoundaryMode.TEXTURE) de.selectiverender.BoundaryTextureResolver.bind(selectiverender$position.get(), quad.getFace());
            else de.selectiverender.BoundaryTextureResolver.clear();
            var world = net.minecraft.client.MinecraftClient.getInstance().world;
            var state = world == null ? null : world.getBlockState(selectiverender$position.get());
            if (layer == SelectiveRenderSettings.BoundaryMode.TEXTURE && state != null && BoundaryColorTexture.preserveSourceTexture(state) && de.selectiverender.BoundaryTextureResolver.bound() == null) {
                original.call(consumer, entry, quad, brightness, red, green, blue, light, overlay, useColor); continue;
            }
            boolean fullbright = de.selectiverender.BoundaryStyle.fullbright(layer);
            for (int i = 0; i < 4; i++) {
                int offset = i * stride;
                float x = Float.intBitsToFloat(data[offset]), y = Float.intBitsToFloat(data[offset + 1]), z = Float.intBitsToFloat(data[offset + 2]);
                float shift=de.selectiverender.BoundaryStyle.overlayOffset(layer);
                position.set(x+quad.getFace().getOffsetX()*shift, y+quad.getFace().getOffsetY()*shift, z+quad.getFace().getOffsetZ()*shift); entry.getPositionMatrix().transformPosition(position);
                float shade = fullbright ? 1 : brightness[i];
                int color = BoundaryColorTexture.argb(layer, selectiverender$position.get(), quad.getFace(), x, y, z);
                consumer.vertex(position.x(), position.y(), position.z(), (color >> 16 & 255) / 255f * shade, (color >> 8 & 255) / 255f * shade, (color & 255) / 255f * shade, 1,
                        BoundaryColorTexture.u(layer, selectiverender$position.get(), quad.getFace(), x, y, z), BoundaryColorTexture.v(layer, selectiverender$position.get(), quad.getFace(), x, y, z),
                        overlay, fullbright ? 0x00F000F0 : light[i], normal.x(), normal.y(), normal.z());
            }
        } } finally { de.selectiverender.BoundaryTextureResolver.clear(); de.selectiverender.BoundaryRenderContext.clear(); }

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
