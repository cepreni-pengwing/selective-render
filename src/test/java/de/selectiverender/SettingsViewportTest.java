package de.selectiverender;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsViewportTest {
    @Test void staysOnScreenAtLargeAndSmallerGuiScales() {
        for (int[] screen : new int[][]{{320, 240}, {480, 270}, {640, 360}, {960, 540}, {1920, 1080}}) {
            int width = screen[0], height = screen[1];
            var viewport = SettingsViewport.of(width, height, 28, 999);
            assertTrue(viewport.left() >= 12);
            assertTrue(viewport.paneLeft() >= viewport.left() + viewport.navigationWidth() + 12);
            assertTrue(viewport.paneLeft() + viewport.paneWidth() <= width - 12);
            assertTrue(36 + (viewport.visibleRows() - 1) * 24 + 20 <= height - 26);
            assertTrue(36 + 4 * 26 + 20 <= height - 26);
            assertTrue(viewport.scroll() >= 0);
            assertTrue(viewport.scroll() + viewport.visibleRows() >= 28);
        }
    }

    @Test void usesTheLastAvailableRowAboveTheFooter() {
        for (int height : new int[]{240, 270, 360, 540}) {
            var viewport = SettingsViewport.of(640, height, 99, 0);
            int lastBottom = 36 + (viewport.visibleRows() - 1) * 24 + 20;
            assertTrue(lastBottom <= height - 26);
            assertTrue(lastBottom + 24 > height - 26);
        }
    }

    @Test void clampsScrollWithoutMovingNavigation() {
        var top = SettingsViewport.of(480, 270, 28, -99);
        var bottom = SettingsViewport.of(480, 270, 28, 999);
        assertEquals(0, top.scroll());
        assertEquals(28 - bottom.visibleRows(), bottom.scroll());
        assertEquals(top.left(), bottom.left());
        assertEquals(top.paneLeft(), bottom.paneLeft());
    }
}
