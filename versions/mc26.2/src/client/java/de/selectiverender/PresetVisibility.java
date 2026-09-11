package de.selectiverender;

final class PresetVisibility {
    private PresetVisibility() { }

    static boolean affectsRendering(boolean activeNormal, boolean normalGroupEnabled,
                                    boolean plotModeActive, boolean whitelistEnabled,
                                    boolean registeredHidden, boolean activeHidden,
                                    boolean hideGroupEnabled) {
        return activeNormal && normalGroupEnabled && !plotModeActive
                || activeHidden && hideGroupEnabled
                || registeredHidden && whitelistEnabled
                && (!hideGroupEnabled || !activeHidden);
    }
}
