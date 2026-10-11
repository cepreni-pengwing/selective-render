package de.selectiverender.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import de.selectiverender.BoundaryColorTexture;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(targets = "net.minecraft.client.render.chunk.SectionBuilder")
abstract class BoundaryLayerMixin {
    @WrapOperation(method = "build", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/RenderLayers;getBlockLayer(Lnet/minecraft/block/BlockState;)Lnet/minecraft/client/render/RenderLayer;"))
    private RenderLayer selectiverender$layer(BlockState state, Operation<RenderLayer> original, @Local(ordinal = 2) BlockPos pos) {
        return BoundaryColorTexture.translucent(pos) ? RenderLayer.getTranslucent() : original.call(state);
    }
}
