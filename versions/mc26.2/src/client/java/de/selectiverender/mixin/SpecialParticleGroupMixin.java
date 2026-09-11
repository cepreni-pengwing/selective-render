package de.selectiverender.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import de.selectiverender.SelectiveRenderState;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ElderGuardianParticleGroup;
import net.minecraft.client.particle.ItemPickupParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.stream.Stream;

@Mixin({ElderGuardianParticleGroup.class, ItemPickupParticleGroup.class})
abstract class SpecialParticleGroupMixin {
    @ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Ljava/util/Queue;stream()Ljava/util/stream/Stream;"))
    private Stream<Particle> selectiverender$filterParticles(Stream<Particle> original) {
        if (!SelectiveRenderState.filteringActive()) return original;
        return original.filter(particle -> {
            ParticlePositionView pos = (ParticlePositionView) particle;
            return SelectiveRenderState.shouldRender(pos.selectiverender$getX(),
                    pos.selectiverender$getY(), pos.selectiverender$getZ());
        });
    }
}
