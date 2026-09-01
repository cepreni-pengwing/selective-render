package de.selectiverender.mixin.axiom;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.RayCaster", remap = false)
public abstract class RayCasterMixin {
    private static final String RAYCAST = "raycast(Lnet/minecraft/class_1937;Lorg/joml/Vector3d;"
            + "Lorg/joml/Vector3d;ZZZ)Lcom/moulberry/axiom/RayCaster$RaycastResult;";

    @Redirect(method = RAYCAST, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/class_2680;method_26172(Lnet/minecraft/class_1922;"
                    + "Lnet/minecraft/class_2338;Lnet/minecraft/class_3726;)Lnet/minecraft/class_265;"),
            require = 0)
    private static VoxelShape selectiverender$filterBlockShape(BlockState state, BlockView world,
                                                                BlockPos pos, ShapeContext context) {
        if (!SelectiveRenderState.shouldInteract(pos)) return VoxelShapes.empty();
        return state.getCollisionShape(world, pos, context);
    }

    @Redirect(method = RAYCAST, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/class_3610;method_17776(Lnet/minecraft/class_1922;"
                    + "Lnet/minecraft/class_2338;)Lnet/minecraft/class_265;"), require = 0)
    private static VoxelShape selectiverender$filterFluidShape(FluidState state, BlockView world,
                                                                BlockPos pos) {
        if (!SelectiveRenderState.shouldInteract(pos)) return VoxelShapes.empty();
        return state.getShape(world, pos);
    }
}
