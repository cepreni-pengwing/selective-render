package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RaycastContext.class)
abstract class RaycastContextMixin {
    @Unique private boolean selectiverender$interactionRay;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void selectiverender$identifyInteraction(Vec3d start, Vec3d end,
            RaycastContext.ShapeType shapeType, RaycastContext.FluidHandling fluidHandling,
            Entity entity, CallbackInfo ci) {
        if (entity == null || !entity.getWorld().isClient
                || shapeType != RaycastContext.ShapeType.OUTLINE) return;
        MinecraftClient client = MinecraftClient.getInstance();
        selectiverender$interactionRay = client.isOnThread()
                && (entity == client.player || entity == client.getCameraEntity());
    }
    @Inject(method = "getBlockShape", at = @At("HEAD"), cancellable = true)
    private void selectiverender$skipInvisibleBlock(BlockState state, BlockView world, BlockPos pos,
                                                     CallbackInfoReturnable<VoxelShape> cir) {
        if (selectiverender$interactionRay && !SelectiveRenderState.shouldInteract(pos)) cir.setReturnValue(VoxelShapes.empty());
    }

    @Inject(method = "getFluidShape", at = @At("HEAD"), cancellable = true)
    private void selectiverender$skipInvisibleFluid(net.minecraft.fluid.FluidState state,
                                                     BlockView world, BlockPos pos,
                                                     CallbackInfoReturnable<VoxelShape> cir) {
        if (selectiverender$interactionRay && !SelectiveRenderState.shouldInteract(pos)) cir.setReturnValue(VoxelShapes.empty());
    }
}
