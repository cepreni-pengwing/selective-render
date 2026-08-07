package de.selectiverender;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public final class SelectiveRenderState {
    private static BlockPos first;
    private static BlockPos second;
    private static BlockRegion region;
    private static boolean enabled;

    private SelectiveRenderState() { }

    public static void setFirst(BlockPos position) { first = position; }
    public static void setSecond(BlockPos position) { second = position; }
    public static BlockPos first() { return first; }
    public static BlockPos second() { return second; }
    public static BlockRegion region() { return region; }
    public static boolean enabled() { return enabled && region != null; }

    public static void setSavedState(BlockRegion newRegion, boolean newEnabled) {
        region = newRegion;
        enabled = newEnabled && newRegion != null;
    }

    public static boolean saveSelection() {
        if (first == null || second == null) return false;
        region = BlockRegion.between(first, second);
        return true;
    }

    public static boolean toggle() {
        if (region == null) return false;
        enabled = !enabled;
        refreshRenderer();
        return true;
    }

    public static boolean shouldRenderSection(int sectionX, int sectionY, int sectionZ) {
        BlockRegion current = region;
        return !enabled || current == null || current.intersectsSection(sectionX, sectionY, sectionZ);
    }

    public static boolean shouldRender(BlockPos position) {
        BlockRegion current = region;
        return !enabled || current == null || current.contains(position);
    }

    public static boolean shouldRender(double x, double y, double z) {
        BlockRegion current = region;
        return !enabled || current == null || current.contains(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
    }

    public static boolean shouldRender(Entity entity) {
        return entity instanceof PlayerEntity || shouldRender(entity.getX(), entity.getY(), entity.getZ());
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
