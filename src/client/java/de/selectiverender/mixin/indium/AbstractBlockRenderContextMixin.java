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
    @Inject(method = "renderQuad", at = @At(value = "INVOKE",
            target = "Llink/infra/indium/renderer/render/AbstractBlockRenderContext;transform(Lnet/fabricmc/fabric/api/renderer/v1/mesh/MutableQuadView;)Z",
            shift = At.Shift.AFTER), cancellable = true, require = 0)
    private void selectiverender$cullTransformedBoundaryExtension(@Coerce Object rawQuad,
                                                                  boolean vanillaModel,
                                                                  CallbackInfo ci) {
        if (SelectiveRenderSettings.boundaryMode()
                != SelectiveRenderSettings.BoundaryMode.CULLED) return;
        if (!(rawQuad instanceof MutableQuadView quad)) return;
        BlockPos position = IndiumRenderContext.position();
        if (position == null) return;
        if (BoundaryGeometry.extensionBoundaryMode(position, selectiverender$extensionData(quad))
                == SelectiveRenderSettings.BoundaryMode.CULLED) ci.cancel();
    }

    @Inject(method = "colorizeQuad", at = @At("RETURN"), require = 0)
    private void selectiverender$colorBoundaryFace(@Coerce Object rawQuad, int colorIndex,
                                                    CallbackInfo ci) {
        if (SelectiveRenderSettings.boundaryMode() != SelectiveRenderSettings.BoundaryMode.BLACK) return;
        if (!(rawQuad instanceof MutableQuadView quad)) return;
        BlockPos position = IndiumRenderContext.position();
        if (position == null) return;
        if (BoundaryGeometry.extensionBoundaryMode(position, selectiverender$extensionData(quad))
                == SelectiveRenderSettings.BoundaryMode.BLACK) {
            selectiverender$makeBlack(quad);
            return;
        }
        Direction direction = quad.cullFace();
        if (direction == null) {
            direction = quad.nominalFace();
            if (direction == null || !selectiverender$isOnBlockFace(quad, direction)) return;
        }
        if (SelectiveRenderState.boundaryModeForFace(position, direction)
                != SelectiveRenderSettings.BoundaryMode.BLACK) return;
        if (!SelectiveRenderState.isBoundaryFace(position, direction)) return;
        selectiverender$makeBlack(quad);
    }

    private static int selectiverender$extensionData(MutableQuadView quad) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < 4; vertex++) {
            float x = quad.x(vertex), y = quad.y(vertex), z = quad.z(vertex);
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }
        return BoundaryGeometry.extensionData(minX, maxX, minY, maxY, minZ, maxZ);
    }

    private static void selectiverender$makeBlack(MutableQuadView quad) {
        int color = 0xFF000000;
        quad.color(color, color, color, color);
        float u = BoundaryColorTexture.u();
        float v = BoundaryColorTexture.v();
        for (int vertex = 0; vertex < 4; vertex++) quad.uv(vertex, u, v);
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
