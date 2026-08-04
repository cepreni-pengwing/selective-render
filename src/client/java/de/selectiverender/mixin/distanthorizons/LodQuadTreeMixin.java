package de.selectiverender.mixin.distanthorizons;

import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.QuadTree.LodQuadTree;
import com.seibel.distanthorizons.core.render.QuadTree.LodRenderSection;
import de.selectiverender.SelectiveRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Pseudo
@Mixin(value = LodQuadTree.class, remap = false)
abstract class LodQuadTreeMixin {
    @Inject(method = "populateListWithEnabledRenderSections", at = @At("RETURN"))
    private void selectiverender$filterLodSections(ArrayList<LodRenderSection> sections, CallbackInfo ci) {
        if (!SelectiveRenderState.enabled()) return;
        sections.removeIf(section -> {
            long position = section.pos;
            long minX = DhSectionPos.getMinCornerBlockX(position);
            long minZ = DhSectionPos.getMinCornerBlockZ(position);
            long width = DhSectionPos.getBlockWidth(position);
            return !SelectiveRenderState.shouldRenderBlockArea(minX, minZ, minX + width, minZ + width);
        });
    }
}
