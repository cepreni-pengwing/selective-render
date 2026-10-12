package me.pepperbell.continuity.client.model;
import me.pepperbell.continuity.api.client.ContinuityFeatureStates;
/** Test-only scratch state; deliberately uses counted enable/disable semantics like Continuity. */
public class ModelObjectsContainer {
    public static final ThreadLocal<ModelObjectsContainer> THREAD_LOCAL=ThreadLocal.withInitial(ModelObjectsContainer::new);
    public final Transform ctmQuadTransform=new Transform();
    public final States featureStates=new States();
    public static class Transform { public boolean active; public boolean isActive() { return active; } }
    public static class Flag implements ContinuityFeatureStates.FeatureState {
        public int count=1;
        public boolean isEnabled() { return count>0; }
        public void enable() { count++; }
        public void disable() { count--; }
    }
    public static class States implements ContinuityFeatureStates {
        public final Flag connected=new Flag(), emissive=new Flag();
        public FeatureState getConnectedTexturesState() { return connected; }
        public FeatureState getEmissiveTexturesState() { return emissive; }
    }
}
