package de.selectiverender;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class RegionBorderRenderer {
    private RegionBorderRenderer() { }

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(RegionBorderRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        if (!SelectiveRenderSettings.debugBoxes() || context.consumers() == null) return;
        VertexConsumer consumer = context.consumers().getBuffer(RenderLayer.getLines());
        Vec3d camera = context.camera().getPos();
        for (BlockRegion region : SelectiveRenderState.borderRegions()) {
            Box box = new Box(region.minX(), region.minY(), region.minZ(),
                    region.maxX() + 1.0, region.maxY() + 1.0, region.maxZ() + 1.0)
                    .offset(-camera.x, -camera.y, -camera.z);
            WorldRenderer.drawBox(context.matrixStack(), consumer, box, 1.0f, 1.0f, 1.0f, 1.0f);
        }
    }
}
