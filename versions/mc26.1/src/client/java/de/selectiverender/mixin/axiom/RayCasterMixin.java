package de.selectiverender.mixin.axiom;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.RayCaster", remap = false)
public abstract class RayCasterMixin {
    private static final String RAYCAST = "raycast(Lnet/minecraft/world/level/Level;"
            + "Lorg/joml/Vector3d;Lorg/joml/Vector3d;ZZZ)"
            + "Lcom/moulberry/axiom/RayCaster$RaycastResult;";

    @Redirect(method = RAYCAST, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getShape("
                    + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/phys/shapes/CollisionContext;)"
                    + "Lnet/minecraft/world/phys/shapes/VoxelShape;"), require = 0)
    private static VoxelShape selectiverender$filterBlockShape(BlockState state, BlockGetter world,
                                                                BlockPos pos, CollisionContext context) {
        if (!SelectiveRenderState.shouldInteract(pos)) return Shapes.empty();
        return state.getShape(world, pos, context);
    }

    @Redirect(method = RAYCAST, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;getShape("
                    + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)"
                    + "Lnet/minecraft/world/phys/shapes/VoxelShape;"), require = 0)
    private static VoxelShape selectiverender$filterFluidShape(FluidState state, BlockGetter world,
                                                                BlockPos pos) {
        if (!SelectiveRenderState.shouldInteract(pos)) return Shapes.empty();
        return state.getShape(world, pos);
    }
}
