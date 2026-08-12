package de.selectiverender.mixin.sodium;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockBox;
import net.minecraft.world.LightType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Map;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.WorldSlice", remap = false)
abstract class WorldSliceMixin {
    @Shadow @Final private ClientWorld world;
    @Shadow private BlockBox volume;
    @Unique private final Map<Long, Integer> selectiverender$virtualSkyLight = new HashMap<>();
    @Unique private boolean selectiverender$virtualSkyPrepared;
    @Unique private static final int[][] selectiverender$directions = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0},
            {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    @Inject(method = "copyData", at = @At("HEAD"))
    private void selectiverender$clearLightCache(CallbackInfo ci) {
        selectiverender$virtualSkyLight.clear();
        selectiverender$virtualSkyPrepared = false;
    }

    @Inject(method = "getBlockState(III)Lnet/minecraft/block/BlockState;", at = @At("HEAD"), cancellable = true, remap = true)
    private void selectiverender$filterBlockState(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (!SelectiveRenderState.shouldRender(x, y, z)) {
            cir.setReturnValue(Blocks.AIR.getDefaultState());
        }
    }

    @Inject(method = "getLightLevel(Lnet/minecraft/world/LightType;Lnet/minecraft/util/math/BlockPos;)I", at = @At("RETURN"), cancellable = true, remap = true)
    private void selectiverender$filterLightLevel(LightType type, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!SelectiveRenderState.shouldRender(pos)) {
            cir.setReturnValue(type == LightType.SKY ? 15 : 0);
        } else if (type == LightType.SKY && cir.getReturnValueI() < 15) {
            int virtualLight = selectiverender$getVirtualSkyLight(pos);
            if (virtualLight >= 0) cir.setReturnValue(Math.max(cir.getReturnValueI(), virtualLight));
        }
    }

    @Inject(method = "getBaseLightLevel(Lnet/minecraft/util/math/BlockPos;I)I", at = @At("HEAD"), cancellable = true, remap = true)
    private void selectiverender$filterBaseLightLevel(BlockPos pos, int ambientDarkness,
                                                       CallbackInfoReturnable<Integer> cir) {
        if (!SelectiveRenderState.shouldRender(pos)) {
            cir.setReturnValue(Math.max(0, 15 - ambientDarkness));
        }
    }

    @Inject(method = "getBaseLightLevel(Lnet/minecraft/util/math/BlockPos;I)I", at = @At("RETURN"), cancellable = true, remap = true)
    private void selectiverender$applyVirtualBaseLight(BlockPos pos, int ambientDarkness,
                                                        CallbackInfoReturnable<Integer> cir) {
        int virtualLight = selectiverender$getVirtualSkyLight(pos);
        if (virtualLight >= 0) {
            cir.setReturnValue(Math.max(cir.getReturnValueI(), Math.max(0, virtualLight - ambientDarkness)));
        }
    }

    @Unique
    private int selectiverender$getVirtualSkyLight(BlockPos pos) {
        if ((!SelectiveRenderState.enabled() && !SelectiveRenderState.hideEnabled())
                || !SelectiveRenderState.shouldRender(pos)) {
            return -1;
        }

        if (!selectiverender$virtualSkyPrepared) selectiverender$prepareVirtualSkyLight(pos);
        return selectiverender$virtualSkyLight.getOrDefault(pos.asLong(), 0);
    }

    @Unique
    private void selectiverender$prepareVirtualSkyLight(BlockPos origin) {
        selectiverender$virtualSkyPrepared = true;
        int minX = volume.getMinX();
        int maxX = volume.getMaxX();
        int minY = volume.getMinY();
        int maxY = volume.getMaxY();
        int minZ = volume.getMinZ();
        int maxZ = volume.getMaxZ();
        ArrayDeque<LightNode> queue = new ArrayDeque<>();
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (SelectiveRenderState.shouldRender(x, y, z)) continue;
                    long key = BlockPos.asLong(x, y, z);
                    selectiverender$virtualSkyLight.put(key, 15);
                    queue.addLast(new LightNode(x, y, z, 15));
                }
            }
        }

        while (!queue.isEmpty()) {
            LightNode current = queue.removeFirst();
            if (current.light <= 1) continue;
            int nextLight = current.light - 1;
            for (int[] direction : selectiverender$directions) {
                int x = current.x + direction[0];
                int y = current.y + direction[1];
                int z = current.z + direction[2];
                if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) continue;
                long key = BlockPos.asLong(x, y, z);
                if (selectiverender$virtualSkyLight.getOrDefault(key, 0) >= nextLight
                        || !selectiverender$isOpenStep(cursor, x, y, z)) continue;
                selectiverender$virtualSkyLight.put(key, nextLight);
                queue.addLast(new LightNode(x, y, z, nextLight));
            }
        }
    }

    @Unique
    private boolean selectiverender$isOpenStep(BlockPos.Mutable cursor, int x, int y, int z) {
        cursor.set(x, y, z);
        return !SelectiveRenderState.shouldRender(cursor)
                || world.getBlockState(cursor).getOpacity(world, cursor) == 0;
    }

    @Unique
    private record LightNode(int x, int y, int z, int light) { }
}
