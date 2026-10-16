package de.selectiverender.mixin.indium;

import de.selectiverender.BoundaryColorTexture;
import de.selectiverender.BoundaryGeometry;
import de.selectiverender.IndiumRenderContext;
import de.selectiverender.SelectiveRenderSettings;
import de.selectiverender.SelectiveRenderState;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "link.infra.indium.renderer.render.AbstractBlockRenderContext", remap = false)
abstract class AbstractBlockRenderContextMixin {
    @org.spongepowered.asm.mixin.Unique private boolean selectiverender$emitting;
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "renderQuad", at = @At(value = "INVOKE",
            target = "Llink/infra/indium/renderer/render/AbstractBlockRenderContext;transform(Lnet/fabricmc/fabric/api/renderer/v1/mesh/MutableQuadView;)Z"), require = 0)
    private boolean selectiverender$skipRepeatedTransforms(@Coerce Object context, MutableQuadView quad,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
        return selectiverender$emitting || original.call(context, quad);
    }
    @Inject(method = "renderQuad", at = @At(value = "INVOKE",
            target = "Llink/infra/indium/renderer/render/AbstractBlockRenderContext;transform(Lnet/fabricmc/fabric/api/renderer/v1/mesh/MutableQuadView;)Z",
            shift = At.Shift.AFTER), cancellable = true, require = 0)
    private void selectiverender$cullTransformedBoundaryExtension(@Coerce Object rawQuad,
                                                                  boolean vanillaModel,
                                                                  CallbackInfo ci) {
        if (selectiverender$emitting || !SelectiveRenderState.filteringActive()) return;
        if (!(rawQuad instanceof MutableQuadView quad)) return;
        BlockPos position = IndiumRenderContext.position();
        if (position == null) return;
        var mode = selectiverender$boundaryMode(position, quad);
        if (mode == SelectiveRenderSettings.BoundaryMode.CULLED
                || (mode == SelectiveRenderSettings.BoundaryMode.COLOR && de.selectiverender.BoundaryRenderContext.opacityAt(position) == 0 && !de.selectiverender.BoundaryRenderContext.normalAt(position))) ci.cancel();
        else if (BoundaryColorTexture.custom(mode)) {
            var tracker = IndiumRenderContext.boundaryQuads();
            for (int v = 0; v < 4; v++) tracker.vertex(v, quad.x(v), quad.y(v), quad.z(v));
            if (!tracker.accept(quad.lightFace().ordinal())) {
                if(de.selectiverender.BoundaryStyle.nativeOverlay(mode, de.selectiverender.BoundaryRenderContext.normalAt(position), de.selectiverender.BoundaryRenderContext.opacityAt(position))) {
                    selectiverender$emitting=true;
                    try { var emitter=((net.fabricmc.fabric.api.renderer.v1.render.RenderContext)(Object)this).getEmitter(); emitter.copyFrom(quad).emit(); }
                    finally { selectiverender$emitting=false; }
                }
                ci.cancel(); return;
            }
            // Snapshot before the context's reusable emitter mutates or resets its storage.
            var meshBuilder = net.fabricmc.fabric.api.renderer.v1.RendererAccess.INSTANCE.getRenderer().meshBuilder();
            meshBuilder.getEmitter().copyFrom(quad).emit();
            var mesh = meshBuilder.build();
            selectiverender$emitting = true;
            try { de.selectiverender.BoundaryRenderContext.bind(position);
            for (var layer : de.selectiverender.BoundaryStyle.renderLayers(mode)) {
                if (!de.selectiverender.BoundaryStyle.visibleLayer(layer)) continue;
                if (layer == SelectiveRenderSettings.BoundaryMode.TEXTURE) de.selectiverender.BoundaryTextureResolver.bind(position, quad.lightFace());
                else de.selectiverender.BoundaryTextureResolver.clear();
                var emitter = ((net.fabricmc.fabric.api.renderer.v1.render.RenderContext)(Object)this).getEmitter();
                mesh.forEach(emitter::copyFrom);
                if (layer == SelectiveRenderSettings.BoundaryMode.NORMAL) { de.selectiverender.BoundaryShaderContext.end(); emitter.emit(); continue; }
                var world = net.minecraft.client.MinecraftClient.getInstance().world;
                if (layer == SelectiveRenderSettings.BoundaryMode.TEXTURE && world != null
                        && BoundaryColorTexture.preserveSourceTexture(world.getBlockState(position)) && de.selectiverender.BoundaryTextureResolver.bound() == null) {
                    de.selectiverender.BoundaryShaderContext.begin(layer); emitter.emit(); continue;
                }
                float shift=de.selectiverender.BoundaryStyle.overlayOffset(layer);
                for(int v=0;v<4;v++) emitter.pos(v,emitter.x(v)+emitter.lightFace().getOffsetX()*shift,
                        emitter.y(v)+emitter.lightFace().getOffsetY()*shift,emitter.z(v)+emitter.lightFace().getOffsetZ()*shift);
                emitter.material(de.selectiverender.BoundaryMaterial.get(layer));
                selectiverender$makeBlack(emitter, layer, position);
                de.selectiverender.BoundaryShaderContext.begin(layer); emitter.emit();
            } } finally { selectiverender$emitting = false; de.selectiverender.BoundaryTextureResolver.clear(); de.selectiverender.BoundaryShaderContext.end(); de.selectiverender.BoundaryRenderContext.clear(); }
            ci.cancel();
        }
    }

    @Inject(method = "colorizeQuad", at = @At("RETURN"), require = 0)
    private void selectiverender$colorBoundaryFace(@Coerce Object rawQuad, int colorIndex,
                                                    CallbackInfo ci) {
        if (selectiverender$emitting) return;
        if (!(de.selectiverender.SelectiveRenderConfig.hasBoundaryMode(SelectiveRenderSettings.BoundaryMode.COLOR) || de.selectiverender.SelectiveRenderConfig.hasBoundaryMode(SelectiveRenderSettings.BoundaryMode.TEXTURE))) return;
        if (!(rawQuad instanceof MutableQuadView quad)) return;
        BlockPos position = IndiumRenderContext.position();
        if (position == null) return;
        var mode = selectiverender$boundaryMode(position, quad);
        if (BoundaryColorTexture.custom(mode)) {
            selectiverender$makeBlack(quad, mode, position);
            return;
        }
        // A nominal/cull direction does not prove that decorative geometry is a cut face.
        return;
    }

    private static SelectiveRenderSettings.BoundaryMode selectiverender$boundaryMode(
            BlockPos position, MutableQuadView quad) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < 4; vertex++) {
            float x = quad.x(vertex), y = quad.y(vertex), z = quad.z(vertex);
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }
        return BoundaryGeometry.boundaryModeForQuad(
                position, minX, maxX, minY, maxY, minZ, maxZ);
    }

    private static void selectiverender$makeBlack(MutableQuadView quad, SelectiveRenderSettings.BoundaryMode mode, BlockPos position) {
        int color = BoundaryColorTexture.argb(mode);
        quad.color(color, color, color, color); quad.colorIndex(-1);
        Direction face = quad.nominalFace() == null ? Direction.UP : quad.nominalFace();
        for (int vertex = 0; vertex < 4; vertex++) {
            quad.color(vertex,BoundaryColorTexture.argb(mode,position,face,quad.x(vertex),quad.y(vertex),quad.z(vertex)));
            quad.uv(vertex,
                BoundaryColorTexture.u(mode, position, face, quad.x(vertex), quad.y(vertex), quad.z(vertex)),
                BoundaryColorTexture.v(mode, position, face, quad.x(vertex), quad.y(vertex), quad.z(vertex)));
        }
        if (mode==SelectiveRenderSettings.BoundaryMode.COLOR)
            de.selectiverender.BoundaryRainbow.recordSmokeColors(quad.color(0),quad.color(1),quad.color(2),quad.color(3));
    }

    private static boolean selectiverender$isOnBlockFace(MutableQuadView quad,
                                                           Direction direction) {
        float plane = direction.getDirection() == Direction.AxisDirection.POSITIVE ? 1.0f : 0.0f;
        for (int vertex = 0; vertex < 4; vertex++) {
            float coordinate = switch (direction.getAxis()) {
                case X -> quad.x(vertex);
                case Y -> quad.y(vertex);
                case Z -> quad.z(vertex);
            };
            if (Math.abs(coordinate - plane) > 0.0001f) return false;
        }
        return true;
    }
}
