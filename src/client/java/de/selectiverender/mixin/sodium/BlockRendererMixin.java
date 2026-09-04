package de.selectiverender.mixin.sodium;

import de.selectiverender.BoundaryColorTexture;
import de.selectiverender.BoundaryGeometry;
import de.selectiverender.SelectiveRenderState;
import de.selectiverender.SelectiveRenderSettings;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import me.jellysquid.mods.sodium.client.model.color.ColorProvider;
import me.jellysquid.mods.sodium.client.model.light.LightPipeline;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.List;

@Pseudo
@Mixin(value = BlockRenderer.class, remap = false)
abstract class BlockRendererMixin {
    @Unique private static final ThreadLocal<Direction> selectiverender$quadDirection =
            new ThreadLocal<>();
    @Unique private static final ThreadLocal<Boolean> selectiverender$solidColor =
            ThreadLocal.withInitial(() -> false);

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterBlock(BlockRenderContext context,
                                             ChunkBuildBuffers buffers, CallbackInfo ci) {
        if (!SelectiveRenderState.shouldRender(context.pos())) ci.cancel();
    }

    @Inject(method = "getVertexColors", at = @At("RETURN"))
    private void selectiverender$colorBoundaryFace(BlockRenderContext context,
                                                    ColorProvider<BlockState> colorProvider,
                                                    BakedQuadView quad,
                                                    CallbackInfoReturnable<int[]> cir) {
        if (selectiverender$solidColor.get()) selectiverender$solidColor.set(false);
        if (!SelectiveRenderState.filteringActive()
                || SelectiveRenderSettings.boundaryMode()
                != SelectiveRenderSettings.BoundaryMode.BLACK) return;
        SelectiveRenderSettings.BoundaryMode extensionMode =
                selectiverender$boundaryMode(context, quad);
        if (extensionMode == SelectiveRenderSettings.BoundaryMode.BLACK) {
            Arrays.fill(cir.getReturnValue(), ColorABGR.pack(0, 0, 0, 255));
            return;
        }
        Direction direction = selectiverender$quadDirection.get();
        if (direction == null) {
            direction = quad.getLightFace();
            if (!selectiverender$isOnBlockFace(quad, direction)) return;
        }
        if (SelectiveRenderState.boundaryModeForFace(context.pos(), direction)
                != SelectiveRenderSettings.BoundaryMode.BLACK) return;
        if (!SelectiveRenderState.isBoundaryFace(context.pos(), direction)) return;
        selectiverender$solidColor.set(true);
        Arrays.fill(cir.getReturnValue(), ColorABGR.pack(0, 0, 0, 255));
    }

    @Inject(method = "writeGeometry", at = @At("HEAD"), cancellable = true)
    private void selectiverender$cullBoundaryExtension(BlockRenderContext context,
                                                        ChunkModelBuilder builder, Vec3d offset,
                                                        Material material, BakedQuadView quad,
                                                        int[] colors, QuadLightData light,
                                                        CallbackInfo ci) {
        if (!SelectiveRenderState.filteringActive()
                || SelectiveRenderSettings.boundaryMode()
                != SelectiveRenderSettings.BoundaryMode.CULLED) return;
        if (selectiverender$boundaryMode(context, quad)
                == SelectiveRenderSettings.BoundaryMode.CULLED) ci.cancel();
    }

    @Redirect(method = "writeGeometry", at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/model/quad/BakedQuadView;getTexU(I)F"))
    private float selectiverender$solidBoundaryU(BakedQuadView quad, int vertex) {
        return selectiverender$solidColor.get() ? BoundaryColorTexture.u() : quad.getTexU(vertex);
    }

    @Redirect(method = "writeGeometry", at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/model/quad/BakedQuadView;getTexV(I)F"))
    private float selectiverender$solidBoundaryV(BakedQuadView quad, int vertex) {
        return selectiverender$solidColor.get() ? BoundaryColorTexture.v() : quad.getTexV(vertex);
    }

    @Inject(method = "renderQuadList", at = @At("HEAD"))
    private void selectiverender$beginQuadList(BlockRenderContext context, Material material,
                                                LightPipeline lighter,
                                                ColorProvider<BlockState> colorProvider,
                                                Vec3d offset, ChunkModelBuilder builder,
                                                List<BakedQuad> quads, Direction direction,
                                                CallbackInfo ci) {
        if (!SelectiveRenderState.filteringActive()
                || SelectiveRenderSettings.boundaryMode()
                != SelectiveRenderSettings.BoundaryMode.BLACK) return;
        selectiverender$quadDirection.set(direction);
    }

    @Inject(method = "renderQuadList", at = @At("RETURN"))
    private void selectiverender$endQuadList(BlockRenderContext context, Material material,
                                              LightPipeline lighter,
                                              ColorProvider<BlockState> colorProvider,
                                              Vec3d offset, ChunkModelBuilder builder,
                                              List<BakedQuad> quads, Direction direction,
                                              CallbackInfo ci) {
        if (selectiverender$quadDirection.get() != null) selectiverender$quadDirection.remove();
    }

    @Unique
    private static boolean selectiverender$isOnBlockFace(BakedQuadView quad, Direction direction) {
        float plane = direction.getDirection() == Direction.AxisDirection.POSITIVE ? 1.0f : 0.0f;
        for (int vertex = 0; vertex < 4; vertex++) {
            float coordinate = switch (direction.getAxis()) {
                case X -> quad.getX(vertex);
                case Y -> quad.getY(vertex);
                case Z -> quad.getZ(vertex);
            };
            if (Math.abs(coordinate - plane) > 0.0001f) return false;
        }
        return true;
    }

    @Unique
    private static SelectiveRenderSettings.BoundaryMode selectiverender$boundaryMode(
            BlockRenderContext context, BakedQuadView quad) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < 4; vertex++) {
            float x = quad.getX(vertex);
            float y = quad.getY(vertex);
            float z = quad.getZ(vertex);
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }
        return BoundaryGeometry.boundaryModeForQuad(
                context.pos(), minX, maxX, minY, maxY, minZ, maxZ);
    }
}
