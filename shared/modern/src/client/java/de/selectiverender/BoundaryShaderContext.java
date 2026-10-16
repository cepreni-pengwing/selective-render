package de.selectiverender;
import java.lang.reflect.*;
import java.util.Map;
/** Scope shader material metadata to the boundary layer, never its underlying grass/modded block. */
public final class BoundaryShaderContext {
    private static final ThreadLocal<Frame> FRAME = ThreadLocal.withInitial(Frame::new);
    private static final class Frame { boolean active, unlit; int id=-1; byte emission; }
    private static volatile boolean tried;
    private static Object settings; private static Method stateIds;
    private BoundaryShaderContext() { }
    public static boolean active() { return FRAME.get().active; }
    public static int id() { return FRAME.get().id; }
    public static byte emission() { return FRAME.get().emission; }
    public static boolean unlit() { return FRAME.get().unlit; }
    public static void begin(SelectiveRenderSettings.BoundaryMode mode) {
        var frame=FRAME.get(); frame.active=true; frame.unlit=BoundaryStyle.fullbright(mode); frame.id=-1; frame.emission=0;
        if (mode == SelectiveRenderSettings.BoundaryMode.COLOR) { frame.emission=(byte)(frame.unlit?15:0); return; }
        if (mode != SelectiveRenderSettings.BoundaryMode.TEXTURE) return;
        var state=BoundaryTextureResolver.selectedState(); frame.emission=(byte)Math.max(0,Math.min(15,state.getLightEmission()));
        initialize();
        if (stateIds != null) try { Object values=stateIds.invoke(settings); if (values instanceof Map<?,?> map) {
            Object value=map.get(state); if (value instanceof Number number) frame.id=number.intValue();
        } } catch (ReflectiveOperationException ignored) { }
    }
    public static void end() { FRAME.get().active=false; }
    private static synchronized void initialize() {
        if (tried) return; tried=true;
        try { var type=Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings");
            settings=type.getField("INSTANCE").get(null); stateIds=type.getMethod("getBlockStateIds");
        } catch (ReflectiveOperationException ignored) { }
    }
    private record Legacy(Object holder, Field id, Field emission, Field ignore, short oldId, byte oldEmission, boolean oldIgnore) { }
    public static Object legacyBegin(Object encoder) {
        if (!active()) return null;
        try {
            var field=encoder.getClass().getDeclaredField("contextHolder"); field.setAccessible(true); var holder=field.get(encoder); if (holder==null) return null;
            var type=holder.getClass(); var idField=type.getField("blockId"); var emissionField=type.getField("lightValue"); var ignoreField=type.getField("ignoreMidBlock");
            var previous=new Legacy(holder,idField,emissionField,ignoreField,idField.getShort(holder),emissionField.getByte(holder),ignoreField.getBoolean(holder));
            idField.setShort(holder,(short)id()); emissionField.setByte(holder,emission());
            if (unlit()) ignoreField.setBoolean(holder,true);
            return previous;
        } catch (ReflectiveOperationException ignored) { return null; }
    }
    public static void legacyEnd(Object value) {
        if (!(value instanceof Legacy old)) return;
        try { old.id().setShort(old.holder(),old.oldId()); old.emission().setByte(old.holder(),old.oldEmission()); old.ignore().setBoolean(old.holder(),old.oldIgnore()); }
        catch (ReflectiveOperationException ignored) { }
    }
}
