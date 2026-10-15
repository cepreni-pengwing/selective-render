package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClipContext.class)
abstract class RaycastContextMixin {
    @Unique private boolean selectiverender$interactionRay;

    @Inject(method = "<init>(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;Lnet/minecraft/world/entity/Entity;)V", at = @At("RETURN"))
    private void selectiverender$identifyInteraction(Vec3 start, Vec3 end,
            ClipContext.Block shapeType, ClipContext.Fluid fluidHandling,
            Entity entity, CallbackInfo ci) {
        if (entity == null || !entity.level().isClientSide()
                || shapeType != ClipContext.Block.OUTLINE) return;
        Minecraft client = Minecraft.getInstance();
        selectiverender$interactionRay = client.isSameThread()
                && (entity == client.player || entity == client.getCameraEntity());
    }
    @Inject(method = "getBlockShape", at = @At("HEAD"), cancellable = true)
    private void selectiverender$skipInvisibleBlock(BlockState state, BlockGetter world, BlockPos pos,
                                                     CallbackInfoReturnable<VoxelShape> cir) {
        if (selectiverender$interactionRay && !SelectiveRenderState.shouldInteract(pos)) cir.setReturnValue(Shapes.empty());
    }

    @Inject(method = "getFluidShape", at = @At("HEAD"), cancellable = true)
    private void selectiverender$skipInvisibleFluid(net.minecraft.world.level.material.FluidState state,
                                                     BlockGetter world, BlockPos pos,
                                                     CallbackInfoReturnable<VoxelShape> cir) {
        if (selectiverender$interactionRay && !SelectiveRenderState.shouldInteract(pos)) cir.setReturnValue(Shapes.empty());
    }
}
