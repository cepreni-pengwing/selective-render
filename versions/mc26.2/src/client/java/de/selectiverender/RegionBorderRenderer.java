package de.selectiverender;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;

public final class RegionBorderRenderer {
    private RegionBorderRenderer() { }

    public static void initialize() {
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            if (!SelectiveRenderSettings.debugBoxes()) return;
            try (var collection = Minecraft.getInstance().levelExtractor.collectPerFrameMainThreadGizmos()) {
                for (BlockRegion region : SelectiveRenderState.borderRegions()) {
                    Gizmos.cuboid(new AABB(region.minX(), region.minY(), region.minZ(),
                            region.maxX() + 1.0, region.maxY() + 1.0, region.maxZ() + 1.0),
                            GizmoStyle.stroke(0xFFFFFFFF)).setAlwaysOnTop();
                }
            }
        });
    }
}
