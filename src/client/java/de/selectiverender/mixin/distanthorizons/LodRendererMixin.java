package de.selectiverender.mixin.distanthorizons;

import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.util.objects.SortedArraySet;
import de.selectiverender.SelectiveRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Comparator;
import java.util.IdentityHashMap;

@Pseudo
@Mixin(value = LodRenderer.class, remap = false)
abstract class LodRendererMixin {
    @ModifyArg(
            method = "renderTerrain(Lcom/seibel/distanthorizons/core/wrapperInterfaces/render/renderPass/IDhTerrainRenderer;Lcom/seibel/distanthorizons/core/render/RenderBufferHandler;Lcom/seibel/distanthorizons/core/render/RenderParams;ZLcom/seibel/distanthorizons/core/wrapperInterfaces/minecraft/IProfilerWrapper;)V",
            at = @At(value = "INVOKE", target = "Lcom/seibel/distanthorizons/core/wrapperInterfaces/render/renderPass/IDhTerrainRenderer;render(Lcom/seibel/distanthorizons/core/render/RenderParams;ZLcom/seibel/distanthorizons/core/util/objects/SortedArraySet;Lcom/seibel/distanthorizons/core/wrapperInterfaces/minecraft/IProfilerWrapper;)V"),
            index = 2)
    private SortedArraySet<LodBufferContainer> selectiverender$filterDrawBuffers(
            SortedArraySet<LodBufferContainer> buffers) {
        if (!SelectiveRenderState.enabled()) return buffers;

        IdentityHashMap<LodBufferContainer, Integer> order = new IdentityHashMap<>();
        for (int i = 0; i < buffers.size(); i++) {
            order.put(buffers.get(i), i);
        }

        SortedArraySet<LodBufferContainer> filtered = new SortedArraySet<>(
                Comparator.comparingInt(order::get));
        for (int i = 0; i < buffers.size(); i++) {
            LodBufferContainer buffer = buffers.get(i);
            long minX = DhSectionPos.getMinCornerBlockX(buffer.pos);
            long minZ = DhSectionPos.getMinCornerBlockZ(buffer.pos);
            long width = DhSectionPos.getBlockWidth(buffer.pos);
            if (SelectiveRenderState.shouldRenderBlockArea(minX, minZ, minX + width, minZ + width)) {
                filtered.add(buffer);
            }
        }
        return filtered;
    }
}
