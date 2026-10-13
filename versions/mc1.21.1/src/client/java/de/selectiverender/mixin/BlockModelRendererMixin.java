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

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "renderQuad", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/VertexConsumer;quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;[FFFFF[IIZ)V"))
    private void selectiverender$appearance(VertexConsumer consumer, MatrixStack.Entry entry, BakedQuad quad,
            float[] brightness, float red, float green, float blue, float alpha, int[] lights, int overlay, boolean useColor,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        if (!selectiverender$coloredBoundary.get()) {
            original.call(consumer, entry, quad, brightness, red, green, blue, alpha, lights, overlay, useColor); return;
        }
        de.selectiverender.BoundaryRenderContext.bind(selectiverender$position.get());
            try { for (var layer : de.selectiverender.BoundaryStyle.renderLayers(selectiverender$texturedBoundary.get())) {
            if (!de.selectiverender.BoundaryStyle.visibleLayer(layer)) continue;
            if (layer == SelectiveRenderSettings.BoundaryMode.NORMAL) { original.call(consumer, entry, quad, brightness, red, green, blue, alpha, lights, overlay, useColor); continue; }
            if (layer == SelectiveRenderSettings.BoundaryMode.TEXTURE) de.selectiverender.BoundaryTextureResolver.bind(selectiverender$position.get(), quad.getFace());
            else de.selectiverender.BoundaryTextureResolver.clear();
            var world = net.minecraft.client.MinecraftClient.getInstance().world;
            var state = world == null ? null : world.getBlockState(selectiverender$position.get());
            if (layer == SelectiveRenderSettings.BoundaryMode.TEXTURE && state != null && BoundaryColorTexture.preserveSourceTexture(state) && de.selectiverender.BoundaryTextureResolver.bound() == null) {
                original.call(consumer, entry, quad, brightness, red, green, blue, alpha, lights, overlay, useColor); continue;
            }
            boolean fullbright = de.selectiverender.BoundaryStyle.fullbright(layer);
            int[] data = quad.getVertexData().clone(); int stride = data.length / 4;
            for (int i = 0; i < 4; i++) {
                int offset = i * stride;
                float x = Float.intBitsToFloat(data[offset]), y = Float.intBitsToFloat(data[offset + 1]), z = Float.intBitsToFloat(data[offset + 2]);
                if (layer==SelectiveRenderSettings.BoundaryMode.COLOR && de.selectiverender.BoundaryRenderContext.regionalColor()) data[offset + 3]=BoundaryColorTexture.abgr(layer, selectiverender$position.get(), quad.getFace(), x,y,z);
                float shift=de.selectiverender.BoundaryStyle.overlayOffset(layer);
                data[offset]=Float.floatToRawIntBits(x+quad.getFace().getOffsetX()*shift);
                data[offset+1]=Float.floatToRawIntBits(y+quad.getFace().getOffsetY()*shift);
                data[offset+2]=Float.floatToRawIntBits(z+quad.getFace().getOffsetZ()*shift);
                data[offset + 4] = Float.floatToRawIntBits(BoundaryColorTexture.u(layer, selectiverender$position.get(), quad.getFace(), x, y, z));
                data[offset + 5] = Float.floatToRawIntBits(BoundaryColorTexture.v(layer, selectiverender$position.get(), quad.getFace(), x, y, z));
            }
            var replacement = new BakedQuad(data, -1, quad.getFace(), BoundaryColorTexture.sprite(layer), !fullbright);
            boolean vertexColor=layer==SelectiveRenderSettings.BoundaryMode.COLOR && de.selectiverender.BoundaryRenderContext.regionalColor();
            original.call(consumer, entry, replacement, fullbright ? new float[]{1,1,1,1} : brightness,
                    vertexColor ? 1f : (BoundaryColorTexture.argb(layer) >> 16 & 255) / 255f, vertexColor ? 1f : (BoundaryColorTexture.argb(layer) >> 8 & 255) / 255f, vertexColor ? 1f : (BoundaryColorTexture.argb(layer) & 255) / 255f, 1f, fullbright ? new int[]{0x00F000F0,0x00F000F0,0x00F000F0,0x00F000F0} : lights, overlay, vertexColor);
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
