package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
abstract class ScreenEffectVisibilityMixin {
    @Inject(method = "renderOverlays", at = @At("HEAD"), cancellable = true)
    private static void selectiverender$hideEffectsForHiddenView(
            MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
        if (client.world == null || client.player == null) return;
        BlockPos view = client.gameRenderer.getCamera().getBlockPos();
        if (!SelectiveRenderState.shouldRender(client.world.getBlockState(view),
                view.getX(), view.getY(), view.getZ())) ci.cancel();
    }
}
