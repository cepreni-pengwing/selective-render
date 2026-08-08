package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
abstract class ClientPlayerInteractionManagerMixin {
    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void selectiverender$blockAttack(BlockPos pos, Direction direction,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (SelectiveRenderState.isActivelyHidden(pos)) cir.setReturnValue(false);
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
    private void selectiverender$blockBreakingProgress(BlockPos pos, Direction direction,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (SelectiveRenderState.isActivelyHidden(pos)) cir.setReturnValue(false);
    }

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void selectiverender$blockInteraction(ClientPlayerEntity player, Hand hand,
                                                   BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        BlockPos target = hit.getBlockPos();
        Item item = player.getStackInHand(hand).getItem();
        boolean placesIntoHidden = (item instanceof BlockItem || item instanceof BucketItem)
                && SelectiveRenderState.isActivelyHidden(target.offset(hit.getSide()));
        if (SelectiveRenderState.isActivelyHidden(target) || placesIntoHidden) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void selectiverender$blockEntityAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (SelectiveRenderState.isActivelyHidden(target)) ci.cancel();
    }

    @Inject(method = "interactEntity", at = @At("HEAD"), cancellable = true)
    private void selectiverender$blockEntityInteraction(PlayerEntity player, Entity target, Hand hand,
                                                         CallbackInfoReturnable<ActionResult> cir) {
        if (SelectiveRenderState.isActivelyHidden(target)) cir.setReturnValue(ActionResult.FAIL);
    }

    @Inject(method = "interactEntityAtLocation", at = @At("HEAD"), cancellable = true)
    private void selectiverender$blockEntityInteractionAt(PlayerEntity player, Entity target,
                                                           EntityHitResult hit, Hand hand,
                                                           CallbackInfoReturnable<ActionResult> cir) {
        if (SelectiveRenderState.isActivelyHidden(target)) cir.setReturnValue(ActionResult.FAIL);
    }
}
