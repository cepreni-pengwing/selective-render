package de.selectiverender;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.Collection;
import java.util.List;

public final class SelectiveRenderState {
    private static BlockPos first;
    private static BlockPos second;
    private static BlockRegion selection;
    private static List<BlockRegion> activeRegions = List.of();
    private static boolean enabled;

    private SelectiveRenderState() { }

    public static void setFirst(BlockPos position) { first = position; }
    public static void setSecond(BlockPos position) { second = position; }
    public static BlockPos first() { return first; }
    public static BlockPos second() { return second; }
    public static BlockRegion selection() { return selection; }
    public static List<BlockRegion> activeRegions() { return activeRegions; }
    public static boolean enabled() { return enabled && !activeRegions.isEmpty(); }

    public static void setSavedState(Collection<BlockRegion> regions, boolean newEnabled) {
        activeRegions = List.copyOf(regions);
        enabled = newEnabled;
    }

    public static boolean saveSelection() {
        if (first == null || second == null) return false;
        selection = BlockRegion.between(first, second);
        return true;
    }

    public static boolean toggle() {
        if (activeRegions.isEmpty()) return false;
        enabled = !enabled;
        refreshRenderer();
        return true;
    }

    public static boolean shouldRenderSection(int sectionX, int sectionY, int sectionZ) {
        return !enabled() || activeRegions.stream()
                .anyMatch(region -> region.intersectsSection(sectionX, sectionY, sectionZ));
    }

    public static boolean shouldRender(BlockPos position) {
        return !enabled() || containsActive(position);
    }

    public static boolean shouldRender(double x, double y, double z) {
        return !enabled() || activeRegions.stream()
                .anyMatch(region -> region.contains(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z)));
    }

    public static boolean containsActive(BlockPos position) {
        return activeRegions.stream().anyMatch(region -> region.contains(position));
    }

    public static boolean shouldRender(Entity entity) {
        return entity instanceof PlayerEntity || shouldRender(entity.getX(), entity.getY(), entity.getZ());
    }

    public static void resetForDisconnect() {
        first = null;
        second = null;
        selection = null;
        activeRegions = List.of();
        enabled = false;
    }

    public static void refreshRenderer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.worldRenderer != null && client.world != null) {
            client.worldRenderer.reload();
        }
    }
}
