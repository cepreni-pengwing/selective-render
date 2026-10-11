package de.selectiverender.mixin.indigo;

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
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractBlockRenderContext", remap = false)
abstract class AbstractBlockRenderContextMixin {
    @Inject(method = "renderQuad", at = @At(value = "INVOKE",
            target = "Llink/infra/indium/renderer/render/AbstractBlockRenderContext;transform(Lnet/fabricmc/fabric/api/renderer/v1/mesh/MutableQuadView;)Z",
            shift = At.Shift.AFTER), cancellable = true, require = 0)
    private void selectiverender$cullTransformedBoundaryExtension(@Coerce Object rawQuad,
                                                                  CallbackInfo ci) {
        if (!SelectiveRenderState.filteringActive()) return;
        if (!(rawQuad instanceof MutableQuadView quad)) return;
        BlockPos position = IndiumRenderContext.position();
        if (position == null) return;
        var mode = selectiverender$boundaryMode(position, quad);
        if (mode == SelectiveRenderSettings.BoundaryMode.CULLED) ci.cancel();
        else if (BoundaryColorTexture.custom(mode)) quad.material(de.selectiverender.BoundaryMaterial.get(mode == SelectiveRenderSettings.BoundaryMode.TEXTURE));
    }

    @Inject(method = "colorizeQuad", at = @At("RETURN"), require = 0)
    private void selectiverender$colorBoundaryFace(@Coerce Object rawQuad, int colorIndex,
                                                    CallbackInfo ci) {
        if (!(de.selectiverender.SelectiveRenderConfig.hasBoundaryMode(SelectiveRenderSettings.BoundaryMode.COLOR) || de.selectiverender.SelectiveRenderConfig.hasBoundaryMode(SelectiveRenderSettings.BoundaryMode.TEXTURE))) return;
        if (!(rawQuad instanceof MutableQuadView quad)) return;
        BlockPos position = IndiumRenderContext.position();
        if (position == null) return;
        var mode = selectiverender$boundaryMode(position, quad);
        if (BoundaryColorTexture.custom(mode)) {
            selectiverender$makeBlack(quad, mode);
            return;
        }
        Direction direction = quad.cullFace();
        if (direction == null) {
            direction = quad.nominalFace();
            if (direction == null || !selectiverender$isOnBlockFace(quad, direction)) return;
        }
        mode = SelectiveRenderState.boundaryModeForFace(position, direction);
        if (!BoundaryColorTexture.custom(mode)) return;
        if (!SelectiveRenderState.isBoundaryFace(position, direction)) return;
        selectiverender$makeBlack(quad, mode);
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

    private static void selectiverender$makeBlack(MutableQuadView quad, SelectiveRenderSettings.BoundaryMode mode) {
        boolean textured = mode == SelectiveRenderSettings.BoundaryMode.TEXTURE;
        int color = BoundaryColorTexture.argb(textured);
        quad.color(color, color, color, color); quad.colorIndex(-1);
        Direction face = quad.nominalFace() == null ? Direction.UP : quad.nominalFace();
        for (int vertex = 0; vertex < 4; vertex++) quad.uv(vertex,
                BoundaryColorTexture.u(textured, face, quad.x(vertex), quad.y(vertex), quad.z(vertex)),
                BoundaryColorTexture.v(textured, face, quad.x(vertex), quad.y(vertex), quad.z(vertex)));
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
