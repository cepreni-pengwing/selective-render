package de.selectiverender.mixin;

import com.google.common.collect.Iterators;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import de.selectiverender.SelectiveRenderState;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.QuadParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.Iterator;

@Mixin(QuadParticleGroup.class)
abstract class ParticleManagerMixin {
    @ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Ljava/util/Queue;iterator()Ljava/util/Iterator;"))
    private Iterator<Particle> selectiverender$filterParticles(Iterator<Particle> original) {
        if (!SelectiveRenderState.filteringActive()) return original;
        return Iterators.filter(original, particle -> {
            ParticlePositionView pos = (ParticlePositionView) particle;
            return SelectiveRenderState.shouldRender(pos.selectiverender$getX(),
                    pos.selectiverender$getY(), pos.selectiverender$getZ());
        });
    }
}
