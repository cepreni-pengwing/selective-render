package de.selectiverender;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class RegionBorderRenderer {
    private static final RenderLayer SEE_THROUGH_LINES = new RenderLayer(
            "selectiverender_see_through_lines", VertexFormats.LINES, VertexFormat.DrawMode.LINES,
            256, false, false,
            () -> {
                RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
            },
            () -> {
                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
                RenderSystem.disableBlend();
            }) { };

    private RegionBorderRenderer() { }

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(RegionBorderRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        SelectiveRenderSettings.BorderMode mode = SelectiveRenderSettings.borderMode();
        if (mode == SelectiveRenderSettings.BorderMode.OFF || context.consumers() == null) return;
        VertexConsumer consumer = context.consumers().getBuffer(
                mode == SelectiveRenderSettings.BorderMode.SEE_THROUGH
                        ? SEE_THROUGH_LINES : RenderLayer.getLines());
        Vec3d camera = context.camera().getPos();
        float red = SelectiveRenderSettings.borderRed() / 255.0f;
        float green = SelectiveRenderSettings.borderGreen() / 255.0f;
        float blue = SelectiveRenderSettings.borderBlue() / 255.0f;
        for (BlockRegion region : SelectiveRenderState.borderRegions()) {
            Box box = new Box(region.minX(), region.minY(), region.minZ(),
                    region.maxX() + 1.0, region.maxY() + 1.0, region.maxZ() + 1.0)
                    .offset(-camera.x, -camera.y, -camera.z);
            WorldRenderer.drawBox(context.matrixStack(), consumer, box, red, green, blue, 1.0f);
        }
    }
}
