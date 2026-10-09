package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
abstract class CameraSubmersionVisibilityMixin {
    @Inject(method = "getSubmersionType", at = @At("RETURN"), cancellable = true)
    private void selectiverender$hideSubmersionForHiddenView(
            CallbackInfoReturnable<CameraSubmersionType> cir) {
        if (cir.getReturnValue() == CameraSubmersionType.NONE) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        BlockPos view = ((Camera) (Object) this).getBlockPos();
        if (!SelectiveRenderState.shouldRender(client.world.getBlockState(view),
                view.getX(), view.getY(), view.getZ())) {
            cir.setReturnValue(CameraSubmersionType.NONE);
        }
    }
}
