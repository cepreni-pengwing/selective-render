package de.selectiverender.mixin.worldedit;

import de.selectiverender.WorldEditCuiBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe CUI without replacing its network receiver or changing its displayed selection. */
@Pseudo
@Mixin(targets = "org.enginehub.worldeditcui.event.CUIEventDispatcher", remap = false)
abstract class CuiEventDispatcherMixin {
    @Inject(method = "raiseEvent", at = @At("HEAD"), require = 0)
    private void selectiverender$selection(@Coerce Object event, CallbackInfo ci) {
        WorldEditCuiBridge.observe(event);
    }
}
