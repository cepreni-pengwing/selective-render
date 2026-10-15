package de.selectiverender.mixin.iris;
import de.selectiverender.BoundaryShaderContext;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Iris adds these optional getters; absent Iris, all require=0 hooks are inert. */
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder$Vertex",remap=false,priority=900)
abstract class BoundaryShaderMetadataMixin {
    @Inject(method="getBlockId",at=@At("HEAD"),cancellable=true,require=0)
    private void selectiverender$id(CallbackInfoReturnable<Integer> cir) { if(BoundaryShaderContext.active()) cir.setReturnValue(BoundaryShaderContext.id()); }
    @Inject(method="getBlockEmission",at=@At("HEAD"),cancellable=true,require=0)
    private void selectiverender$emission(CallbackInfoReturnable<Byte> cir) { if(BoundaryShaderContext.active()) cir.setReturnValue(BoundaryShaderContext.emission()); }
    @Inject(method="ignoreMidBlock",at=@At("HEAD"),cancellable=true,require=0)
    private void selectiverender$midBlock(CallbackInfoReturnable<Boolean> cir) { if(BoundaryShaderContext.active() && BoundaryShaderContext.unlit()) cir.setReturnValue(true); }
}
