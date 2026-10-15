package de.selectiverender;

/** Layout in scaled GUI coordinates; navigation and footer never scroll with the content. */
record SettingsViewport(int left, int navigationWidth, int paneLeft, int paneWidth,
                        int visibleRows, int scroll) {
    static SettingsViewport of(int width, int height, int rowCount, int requestedScroll) {
        int totalWidth = Math.min(960, width - 24);
        int left = (width - totalWidth) / 2;
        int navigationWidth = Math.max(72, Math.min(112, totalWidth / 5));
        int paneLeft = left + navigationWidth + 12;
        int paneWidth = totalWidth - navigationWidth - 12;
        int visibleRows = Math.max(1, (height - 58) / 24);
        int scroll = Math.max(0, Math.min(requestedScroll, Math.max(0, rowCount - visibleRows)));
        return new SettingsViewport(left, navigationWidth, paneLeft, paneWidth, visibleRows, scroll);
    }
}
