package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderClient;
import de.selectiverender.SelectiveRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftClientMixin {
    @Inject(method = "setLevel", at = @At("TAIL"))
    private void selectiverender$loadDimensionConfig(ClientLevel world, CallbackInfo ci) {
        SelectiveRenderClient.worldChanged((Minecraft) (Object) this, world);
    }

    @Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
    private void selectiverender$blockHiddenItemPick(CallbackInfo ci) {
        HitResult target = ((Minecraft) (Object) this).hitResult;
        if (target instanceof BlockHitResult blockHit
                && !SelectiveRenderState.shouldInteract(blockHit.getBlockPos())) {
            ci.cancel();
        } else if (target instanceof EntityHitResult entityHit
                && !SelectiveRenderState.shouldInteract(entityHit.getEntity())) {
            ci.cancel();
        }
    }
}
