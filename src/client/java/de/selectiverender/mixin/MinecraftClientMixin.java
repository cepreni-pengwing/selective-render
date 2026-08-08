package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderConfig;
import de.selectiverender.SelectiveRenderState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
abstract class MinecraftClientMixin {
    @Inject(method = "setWorld", at = @At("TAIL"))
    private void selectiverender$loadDimensionConfig(ClientWorld world, CallbackInfo ci) {
        if (world != null) {
            SelectiveRenderConfig.load((MinecraftClient) (Object) this, world);
        }
    }

    @Inject(method = "doItemPick", at = @At("HEAD"), cancellable = true)
    private void selectiverender$blockHiddenItemPick(CallbackInfo ci) {
        HitResult target = ((MinecraftClient) (Object) this).crosshairTarget;
        if (target instanceof BlockHitResult blockHit
                && SelectiveRenderState.isActivelyHidden(blockHit.getBlockPos())) {
            ci.cancel();
        } else if (target instanceof EntityHitResult entityHit
                && !(entityHit.getEntity() instanceof PlayerEntity)
                && SelectiveRenderState.isActivelyHidden(entityHit.getEntity())) {
            ci.cancel();
        }
    }
}
