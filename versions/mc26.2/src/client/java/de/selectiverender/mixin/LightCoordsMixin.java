package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import de.selectiverender.VirtualSkyLightSampler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.BlockAndLightGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightCoordsUtil.class)
abstract class LightCoordsMixin {
    @Inject(method = "getLightCoords(Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
    private static void selectiverender$virtualLight(BlockAndLightGetter world, BlockPos pos,
            CallbackInfoReturnable<Integer> cir) {
        if (!SelectiveRenderState.filteringActive() || !(world instanceof ClientLevel level)
                || !SelectiveRenderState.shouldRender(pos)) return;
        int light = cir.getReturnValueI();
        if (LightCoordsUtil.sky(light) >= 15) return;
        int virtual = VirtualSkyLightSampler.sample(level, pos);
        if (virtual >= 0) cir.setReturnValue(LightCoordsUtil.pack(LightCoordsUtil.block(light),
                Math.max(LightCoordsUtil.sky(light), virtual)));
    }
}
