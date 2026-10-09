package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
abstract class CameraSubmersionVisibilityMixin {
    @Inject(method = "getFluidInCamera", at = @At("RETURN"), cancellable = true)
    private void selectiverender$hideSubmersionForHiddenView(CallbackInfoReturnable<FogType> cir) {
        if (cir.getReturnValue() == FogType.NONE) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        BlockPos view = ((Camera) (Object) this).blockPosition();
        BlockState state = client.level.getBlockState(view);
        if (!SelectiveRenderState.shouldRender(state, view.getX(), view.getY(), view.getZ())) {
            cir.setReturnValue(FogType.NONE);
        }
    }
}
