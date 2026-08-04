package de.selectiverender;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;

public final class SelectiveRenderState {
    private static ChunkPos first;
    private static ChunkPos second;
    private static ChunkRegion region;
    private static boolean enabled;

    private SelectiveRenderState() { }

    public static void setFirst(ChunkPos position) { first = position; }
    public static void setSecond(ChunkPos position) { second = position; }
    public static ChunkPos first() { return first; }
    public static ChunkPos second() { return second; }
    public static ChunkRegion region() { return region; }
    public static boolean enabled() { return enabled && region != null; }

    public static void setSavedState(ChunkRegion newRegion, boolean newEnabled) {
        region = newRegion;
        enabled = newEnabled && newRegion != null;
    }

    public static boolean saveSelection() {
        if (first == null || second == null) return false;
        region = ChunkRegion.between(first, second);
        return true;
    }

    public static boolean toggle() {
        if (region == null) return false;
        enabled = !enabled;
        refreshRenderer();
        return true;
    }

    public static boolean shouldRenderChunk(int chunkX, int chunkZ) {
        ChunkRegion current = region;
        return !enabled || current == null || current.contains(chunkX, chunkZ);
    }

    public static boolean shouldRender(BlockPos position) {
        return shouldRenderChunk(position.getX() >> 4, position.getZ() >> 4);
    }

    public static boolean shouldRender(double x, double z) {
        return shouldRenderChunk(MathHelper.floor(x) >> 4, MathHelper.floor(z) >> 4);
    }

    public static boolean shouldRender(Entity entity) {
        return entity instanceof PlayerEntity || shouldRender(entity.getX(), entity.getZ());
    }

    public static void resetForDisconnect() {
        first = null;
        second = null;
        region = null;
        enabled = false;
    }

    public static void refreshRenderer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.worldRenderer != null && client.world != null) {
            client.worldRenderer.reload();
        }
    }
}
