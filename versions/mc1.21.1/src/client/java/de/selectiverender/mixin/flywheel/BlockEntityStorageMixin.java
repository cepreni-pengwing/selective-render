package de.selectiverender.mixin.flywheel;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage", remap = false)
public abstract class BlockEntityStorageMixin {
    @Inject(method = "willAccept(Ljava/lang/Object;)Z", at = @At("HEAD"), cancellable = true,
            require = 0)
    private void selectiverender$rejectHiddenBlockEntity(Object candidate,
                                                         CallbackInfoReturnable<Boolean> cir) {
        if (candidate instanceof BlockEntity blockEntity
                && !SelectiveRenderState.shouldRender(blockEntity.getPos())) {
            cir.setReturnValue(false);
        }
    }
}
