package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(ClientChunkManager.class)
abstract class ClientChunkManagerMixin {
    @Inject(method = "unload", at = @At("HEAD"))
    private void selectiverender$removeLightCacheChunk(ChunkPos chunkPos, CallbackInfo ci) {
        SelectiveRenderState.invalidateLightCacheChunk(chunkPos.x, chunkPos.z);
    }

    @Inject(method = "loadChunkFromPacket", at = @At("RETURN"))
    private void selectiverender$invalidateLoadedLightChunk(
            int chunkX, int chunkZ, PacketByteBuf buffer, NbtCompound nbt,
            Consumer<ChunkData.BlockEntityVisitor> consumer,
            CallbackInfoReturnable<WorldChunk> cir) {
        SelectiveRenderState.invalidateLightCacheChunk(chunkX, chunkZ);
    }
}
