package de.plotrender.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import de.plotrender.PlotRenderState;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ParticleManager.class)
abstract class ParticleManagerMixin {
    @WrapWithCondition(
            method = "renderParticles",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;buildGeometry(Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/client/render/Camera;F)V"))
    private boolean plotrender$filterParticle(Particle particle, VertexConsumer consumer, Camera camera, float tickDelta) {
        ParticlePositionView position = (ParticlePositionView) particle;
        return PlotRenderState.shouldRender(position.plotrender$getX(), position.plotrender$getZ());
    }
}
