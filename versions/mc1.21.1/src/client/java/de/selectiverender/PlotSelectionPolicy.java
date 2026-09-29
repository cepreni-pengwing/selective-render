package de.selectiverender;

final class PlotSelectionPolicy {
    private PlotSelectionPolicy() { }

    static boolean renderingAfterAdd(boolean selectionWasEmpty, boolean renderingEnabled) {
        return selectionWasEmpty || renderingEnabled;
    }

    static boolean needsMeshUpdate(boolean wasRendering, boolean nowRendering) {
        return wasRendering || nowRendering;
    }
}
