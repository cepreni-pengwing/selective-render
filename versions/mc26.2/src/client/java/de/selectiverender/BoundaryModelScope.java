package de.selectiverender;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Optional CTM scratch context. Nested boundary reads must not reuse an active terrain transform. */
public final class BoundaryModelScope implements AutoCloseable {
    private static final ThreadLocal<Object> SCRATCH = new ThreadLocal<>();
    private static volatile boolean initialized;
    private static ThreadLocal<Object> contexts;
    private static Constructor<?> constructor;
    private static Field featureStates, transform;
    private static Method connected, emissive, enabled, enable, disable, active;
    private final Object previous;
    private final boolean nested;
    private boolean completed;

    private BoundaryModelScope(Object previous, boolean nested) { this.previous = previous; this.nested = nested; }

    @SuppressWarnings("unchecked")
    private static synchronized void initialize() {
        if (initialized) return;
        try {
            var type = Class.forName("me.pepperbell.continuity.client.model.ModelObjectsContainer");
            var state = Class.forName("me.pepperbell.continuity.api.client.ContinuityFeatureStates$FeatureState");
            var states = Class.forName("me.pepperbell.continuity.api.client.ContinuityFeatureStates");
            constructor = type.getConstructor(); featureStates = type.getField("featureStates");
            transform = type.getField("ctmQuadTransform"); active = transform.getType().getMethod("isActive");
            connected = states.getMethod("getConnectedTexturesState"); emissive = states.getMethod("getEmissiveTexturesState");
            enabled = state.getMethod("isEnabled"); enable = state.getMethod("enable"); disable = state.getMethod("disable");
            contexts = (ThreadLocal<Object>)type.getField("THREAD_LOCAL").get(null);
        } catch (ClassNotFoundException ignored) {
            // Continuity is optional; native models and other renderer providers still work.
        } catch (ReflectiveOperationException error) {
            SelectiveRenderClient.LOGGER.warn("Boundary CTM context isolation unavailable: {}", error.toString());
        } finally {
            initialized = true;
        }
    }

    public static BoundaryModelScope open() {
        if (!initialized) initialize();
        if (contexts == null) return new BoundaryModelScope(null, false);
        try {
            Object previous = contexts.get(), scratch = SCRATCH.get();
            if (scratch == null || scratch == previous) { scratch = constructor.newInstance(); SCRATCH.set(scratch); }
            Object from = featureStates.get(previous), to = featureStates.get(scratch);
            copyState(connected.invoke(from), connected.invoke(to));
            copyState(emissive.invoke(from), emissive.invoke(to));
            boolean nested = Boolean.TRUE.equals(active.invoke(transform.get(previous)));
            contexts.set(scratch);
            return new BoundaryModelScope(previous, nested);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not isolate boundary CTM context", error);
        }
    }

    private static void copyState(Object from, Object to) throws ReflectiveOperationException {
        boolean desired=Boolean.TRUE.equals(enabled.invoke(from));
        if (desired != Boolean.TRUE.equals(enabled.invoke(to))) (desired ? enable : disable).invoke(to);
    }

    public void complete() { completed = true; }
    public boolean nested() { return nested; }

    @Override public void close() {
        if (previous == null) return;
        contexts.set(previous);
        // A failed model may leave its transform active; never reuse that scratch context.
        if (!completed) SCRATCH.remove();
    }
}
