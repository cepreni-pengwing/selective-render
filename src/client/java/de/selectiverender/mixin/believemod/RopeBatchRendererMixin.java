package de.selectiverender.mixin.believemod;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Pseudo
@Mixin(targets = "net.legendaryporpoise.believemod.client.RopeBatchRenderer", remap = false)
abstract class RopeBatchRendererMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 1), require = 0)
    private static boolean selectiverender$filterRope(List<Object> ropes, Object candidate) {
        if (candidate instanceof Entity entity && !SelectiveRenderState.shouldRender(entity)) {
            return false;
        }
        return ropes.add(candidate);
    }
}
