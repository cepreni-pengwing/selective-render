package de.selectiverender.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticlePositionView {
    @Accessor("x") double selectiverender$getX();
    @Accessor("z") double selectiverender$getZ();
}
