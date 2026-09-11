package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
abstract class ClientPlayerInteractionManagerMixin {
    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void selectiverender$blockAttack(BlockPos pos, Direction direction,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (!SelectiveRenderState.shouldInteract(pos)) cir.setReturnValue(false);
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void selectiverender$blockBreakingProgress(BlockPos pos, Direction direction,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (!SelectiveRenderState.shouldInteract(pos)) cir.setReturnValue(false);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void selectiverender$blockInteraction(LocalPlayer player, InteractionHand hand,
                                                   BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        BlockPos target = hit.getBlockPos();
        Item item = player.getItemInHand(hand).getItem();
        boolean placesOutsideRenderedArea = isPlacementItem(item)
                && !SelectiveRenderState.shouldInteract(target.relative(hit.getDirection()));
        if (!SelectiveRenderState.shouldInteract(target) || placesOutsideRenderedArea) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    private static boolean isPlacementItem(Item item) {
        return item instanceof BlockItem
                || item instanceof BucketItem
                || item instanceof ArmorStandItem
                || item instanceof BoatItem
                || item instanceof HangingEntityItem
                || item instanceof EndCrystalItem
                || item instanceof MinecartItem
                || item instanceof SpawnEggItem;
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void selectiverender$blockUseItem(Player player, InteractionHand hand,
                                               CallbackInfoReturnable<InteractionResult> cir) {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        Item item = player.getItemInHand(hand).getItem();
        if (client.hitResult instanceof BlockHitResult hit
                && (!SelectiveRenderState.shouldInteract(hit.getBlockPos())
                || isPlacementItem(item)
                && !SelectiveRenderState.shouldInteract(hit.getBlockPos().relative(hit.getDirection())))) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void selectiverender$blockEntityAttack(Player player, Entity target, CallbackInfo ci) {
        if (!SelectiveRenderState.shouldInteract(target)) ci.cancel();
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void selectiverender$blockEntityInteractionAt(Player player, Entity target,
                                                           EntityHitResult hit, InteractionHand hand,
                                                           CallbackInfoReturnable<InteractionResult> cir) {
        if (!SelectiveRenderState.shouldInteract(target)) cir.setReturnValue(InteractionResult.FAIL);
    }
}
