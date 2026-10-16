package de.selectiverender.mixin.indigo;
import de.selectiverender.IndiumRenderContext;
import de.selectiverender.SelectiveRenderState;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.TerrainRenderContext", remap = false)
abstract class TerrainRenderContextMixin {
    @Inject(method = "tessellateBlock", at = @At("HEAD"), cancellable = true)
    private void selectiverender$begin(BlockState state, BlockPos pos, BakedModel model, MatrixStack matrices, CallbackInfo ci) {
        IndiumRenderContext.end();
        if (!SelectiveRenderState.shouldRender(state, pos.getX(), pos.getY(), pos.getZ())) { ci.cancel(); return; }
        if (SelectiveRenderState.filteringActive()) IndiumRenderContext.begin(pos);
    }
    @Inject(method = "tessellateBlock", at = @At("RETURN"))
    private void selectiverender$end(BlockState state, BlockPos pos, BakedModel model, MatrixStack matrices, CallbackInfo ci) {
        IndiumRenderContext.end();
    }
}
