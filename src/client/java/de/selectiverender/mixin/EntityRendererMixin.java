package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin<T extends Entity> {
    @Inject(method = "getSkyLight", at = @At("RETURN"), cancellable = true)
    private void selectiverender$applyVirtualPlayerSkyLight(T entity, BlockPos pos,
                                                            CallbackInfoReturnable<Integer> cir) {
        if (!(entity instanceof PlayerEntity)
                || (!SelectiveRenderState.enabled() && !SelectiveRenderState.hideEnabled())
                || cir.getReturnValueI() >= 15) {
            return;
        }

        World world = entity.getWorld();
        BlockPos.Mutable cursor = new BlockPos.Mutable(pos.getX(), pos.getY(), pos.getZ());
        int top = SelectiveRenderState.visibleColumnTop(pos.getX(), pos.getZ(), world.getTopY() - 1);
        for (int y = Math.max(pos.getY() + 1,
                SelectiveRenderState.visibleColumnBottom(pos.getX(), pos.getZ(), world.getBottomY()));
             y <= top; y++) {
            cursor.setY(y);
            if (!SelectiveRenderState.shouldRender(cursor)) continue;
            BlockState state = world.getBlockState(cursor);
            if (state.getOpacity(world, cursor) > 0) return;
        }
        cir.setReturnValue(15);
    }
}
