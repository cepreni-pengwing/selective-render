package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
abstract class MinecraftClientMixin {
    @Inject(method = "setWorld", at = @At("TAIL"))
    private void selectiverender$loadDimensionConfig(ClientWorld world, CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (world == null) {
            SelectiveRenderConfig.reset();
        } else {
            SelectiveRenderConfig.load(client);
        }
    }
}
