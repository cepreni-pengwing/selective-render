package me.pepperbell.continuity.api.client;
/** Minimal test double for Continuity's optional public feature-state API. */
public interface ContinuityFeatureStates {
    interface FeatureState { boolean isEnabled(); void enable(); void disable(); }
    FeatureState getConnectedTexturesState();
    FeatureState getEmissiveTexturesState();
}
