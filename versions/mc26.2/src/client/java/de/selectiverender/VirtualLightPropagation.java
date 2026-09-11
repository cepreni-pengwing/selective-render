package de.selectiverender;

public final class VirtualLightPropagation {
    private VirtualLightPropagation() { }

    public static boolean canImprove(int currentLight, int existingNeighborLight) {
        return currentLight > 1 && existingNeighborLight < currentLight - 1;
    }
}
