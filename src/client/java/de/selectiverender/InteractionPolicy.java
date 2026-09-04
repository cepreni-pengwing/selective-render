package de.selectiverender;

final class InteractionPolicy {
    private InteractionPolicy() { }

    static boolean active(SelectiveRenderSettings.InteractionMode mode, boolean renderingActive,
                          boolean keepWhileInactive, boolean hasSelectedRegions) {
        return mode != SelectiveRenderSettings.InteractionMode.EVERYWHERE
                && (renderingActive || (keepWhileInactive && hasSelectedRegions));
    }

    static boolean allows(SelectiveRenderSettings.InteractionMode mode, boolean inside) {
        return switch (mode) {
            case NONE -> false;
            case INSIDE -> inside;
            case OUTSIDE -> !inside;
            case EVERYWHERE -> true;
        };
    }
}
