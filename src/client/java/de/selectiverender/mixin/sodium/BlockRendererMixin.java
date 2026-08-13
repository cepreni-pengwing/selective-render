package de.selectiverender.mixin.sodium;

import de.selectiverender.SelectiveRenderState;
import de.selectiverender.SelectiveRenderSettings;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import me.jellysquid.mods.sodium.client.model.color.ColorProvider;
import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Pseudo
@Mixin(value = BlockRenderer.class, remap = false)
abstract class BlockRendererMixin {
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
        if (SelectiveRenderSettings.boundaryMode()
                != SelectiveRenderSettings.BoundaryMode.COLORED) return;
        Direction direction = quad.getLightFace();
        if (!SelectiveRenderState.isBoundaryFace(context.pos(), direction)
                || !selectiverender$isBoundaryPlane(quad, direction)) return;
        Arrays.fill(cir.getReturnValue(), ColorABGR.pack(0, 204, 255, 255));
    }

    @Unique
    private static boolean selectiverender$isBoundaryPlane(BakedQuadView quad, Direction direction) {
        float expected = direction.getDirection() == Direction.AxisDirection.POSITIVE ? 1.0f : 0.0f;
        for (int vertex = 0; vertex < 4; vertex++) {
            float coordinate = switch (direction.getAxis()) {
                case X -> quad.getX(vertex);
                case Y -> quad.getY(vertex);
                case Z -> quad.getZ(vertex);
            };
            if (Math.abs(coordinate - expected) > 0.0001f) return false;
        }
        return true;
    }
}
