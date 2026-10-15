package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
abstract class ScreenEffectVisibilityMixin {
    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    private static void selectiverender$hideWaterEffectForHiddenView(
            CallbackInfo ci) {
        if (selectiverender$viewIsHidden(Minecraft.getInstance())) ci.cancel();
    }

    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void selectiverender$hideFireEffectForHiddenView(
            CallbackInfo ci) {
        if (selectiverender$viewIsHidden(Minecraft.getInstance())) ci.cancel();
    }

    @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
    private static void selectiverender$hideBlockEffectForHiddenView(
            CallbackInfo ci) {
        if (selectiverender$viewIsHidden(Minecraft.getInstance())) ci.cancel();
    }

    private static boolean selectiverender$viewIsHidden(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) return false;
        BlockPos view = minecraft.gameRenderer.mainCamera().blockPosition();
        BlockState state = minecraft.level.getBlockState(view);
        return !SelectiveRenderState.shouldRender(state, view.getX(), view.getY(), view.getZ());
    }
}
