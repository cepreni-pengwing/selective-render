package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import de.selectiverender.SelectiveRenderSettings;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

@Mixin(Minecraft.class)
abstract class GameRendererMixin {
    @Inject(method = "pick", at = @At("RETURN"))
    private void selectiverender$validateInteractionTarget(float tickDelta, CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        HitResult target = client.hitResult;
        boolean allowed = !(target instanceof BlockHitResult blockHit)
                || SelectiveRenderState.shouldInteract(blockHit.getBlockPos());
        if (target instanceof EntityHitResult entityHit) {
            allowed = SelectiveRenderState.shouldInteract(entityHit.getEntity());
        }
        if (allowed || target == null || target.getType() == HitResult.Type.MISS) return;

        org.joml.Vector3fc view = client.gameRenderer.mainCamera().forwardVector();
        Direction side = target instanceof BlockHitResult blockHit
                ? blockHit.getDirection()
                : Direction.getApproximateNearest(view.x(), view.y(), view.z());
        BlockPos pos = BlockPos.containing(target.getLocation());
        client.hitResult = BlockHitResult.miss(target.getLocation(), side, pos);
        client.crosshairPickEntity = null;
    }
}
