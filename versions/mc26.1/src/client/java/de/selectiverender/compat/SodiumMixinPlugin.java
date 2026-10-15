package de.selectiverender.compat;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Select only bindings that exist in the installed Sodium; one JAR serves all three 26.1 patches. */
public final class SodiumMixinPlugin implements IMixinConfigPlugin {
    private boolean legacy;
    @Override public void onLoad(String mixinPackage) {
        String version=FabricLoader.getInstance().getModContainer("sodium").orElseThrow().getMetadata().getVersion().getFriendlyString();
        legacy=version.startsWith("0.8.");
    }
    @Override public boolean shouldApplyMixin(String target,String mixin) {
        if(mixin.contains(".sodium.legacy.")) return legacy;
        if(mixin.endsWith(".sodium.VisibleChunkCollectorMixin") || mixin.endsWith(".sodium.SectionManagerDiagnosticsMixin")) return !legacy;
        return true;
    }
    @Override public String getRefMapperConfig(){return null;}
    @Override public void acceptTargets(Set<String> mine,Set<String> others){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String name,ClassNode node,String mixin,IMixinInfo info){}
    @Override public void postApply(String name,ClassNode node,String mixin,IMixinInfo info){}
}
