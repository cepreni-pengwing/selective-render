package de.selectiverender;

final class BoundaryPolicy {
    private BoundaryPolicy() { }

    static SelectiveRenderSettings.BoundaryMode mode(
            SelectiveRenderSettings.BoundaryMode configured,
            boolean insideVisible, boolean outsideVisible, boolean outsideHidden) {
        if (configured == SelectiveRenderSettings.BoundaryMode.NORMAL
                || !insideVisible || outsideVisible || outsideHidden) {
            return SelectiveRenderSettings.BoundaryMode.NORMAL;
        }
        return configured;
    }
}
