package de.selectiverender.mixin;

import de.selectiverender.BoundaryTextureResolver;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve pack transforms while bypassing renderer-specific vanilla-adapter casts only for our collector. */
@Mixin(value = BakedModel.class, priority = 800)
public interface BoundaryBakedModelMixin {
    @Inject(method = "emitBlockQuads", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    default void selectiverender$collectVanillaModel(BlockRenderView world, BlockState state, BlockPos pos,
            Supplier<Random> random, RenderContext context, CallbackInfo ci) {
        if (context instanceof BoundaryTextureResolver.ModelContext) {
            context.bakedModelConsumer().accept((BakedModel)(Object)this, state);
            ci.cancel();
        }
    }
}
