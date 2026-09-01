package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import de.selectiverender.SelectiveRenderSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Shadow @Final private MinecraftClient client;

    @ModifyArg(method = "updateTargetedEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/projectile/ProjectileUtil;raycast("
                    + "Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;"
                    + "Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;"
                    + "Ljava/util/function/Predicate;D)Lnet/minecraft/util/hit/EntityHitResult;"),
            index = 4)
    private Predicate<Entity> selectiverender$filterEntityTargets(Predicate<Entity> original) {
        if (SelectiveRenderSettings.interactionMode()
                == SelectiveRenderSettings.InteractionMode.EVERYWHERE) return original;
        return entity -> original.test(entity) && SelectiveRenderState.shouldInteract(entity);
    }

    @Inject(method = "updateTargetedEntity", at = @At("RETURN"))
    private void selectiverender$validateInteractionTarget(float tickDelta, CallbackInfo ci) {
        HitResult target = client.crosshairTarget;
        boolean allowed = !(target instanceof BlockHitResult blockHit)
                || SelectiveRenderState.shouldInteract(blockHit.getBlockPos());
        if (target instanceof EntityHitResult entityHit) {
            allowed = SelectiveRenderState.shouldInteract(entityHit.getEntity());
        }
        if (allowed || target == null || target.getType() == HitResult.Type.MISS) return;

        org.joml.Vector3f view = client.gameRenderer.getCamera().getHorizontalPlane();
        Direction side = target instanceof BlockHitResult blockHit
                ? blockHit.getSide()
                : Direction.getFacing(view.x(), view.y(), view.z());
        BlockPos pos = BlockPos.ofFloored(target.getPos());
        client.crosshairTarget = BlockHitResult.createMissed(target.getPos(), side, pos);
        client.targetedEntity = null;
    }
}
