package de.selectiverender.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import de.selectiverender.SelectiveRenderSettings;
import de.selectiverender.SelectiveRenderState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.function.Predicate;

@Mixin(LocalPlayer.class)
abstract class LocalPlayerTargetMixin {
    @ModifyExpressionValue(method = {"pick", "raycastHitResult"}, at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/EntitySelector;CAN_BE_PICKED:Ljava/util/function/Predicate;"))
    private static Predicate<Entity> selectiverender$interactionTargets(Predicate<Entity> original) {
        if (SelectiveRenderSettings.interactionMode() == SelectiveRenderSettings.InteractionMode.EVERYWHERE)
            return original;
        return entity -> original.test(entity) && SelectiveRenderState.shouldInteract(entity);
    }
}
