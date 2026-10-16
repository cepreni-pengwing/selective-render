package de.selectiverender.mixin.iris;
import de.selectiverender.BoundaryShaderContext;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="net.irisshaders.iris.compat.sodium.impl.vertex_format.terrain_xhfp.XHFPTerrainVertex",remap=false)
abstract class BoundaryShaderMetadataMixin {
    @Unique private final ThreadLocal<Object> selectiverender$previous= new ThreadLocal<>();
    @Inject(method="write",at=@At("HEAD"),require=0)
    private void selectiverender$begin(CallbackInfoReturnable<Long> cir) { selectiverender$previous.set(BoundaryShaderContext.legacyBegin(this)); }
    @Inject(method="write",at=@At("RETURN"),require=0)
    private void selectiverender$end(CallbackInfoReturnable<Long> cir) { BoundaryShaderContext.legacyEnd(selectiverender$previous.get()); selectiverender$previous.remove(); }
}
