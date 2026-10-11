package de.selectiverender;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OptionRowLayoutTest {
    @Test void fitsAllValuesOnAWideRow() {
        assertEquals(6, OptionRowLayout.end(700, new int[]{24, 84, 90, 48, 60, 78}, 0));
    }

    @Test void wrapsWithoutOverlapsOrLosingValues() {
        int[] widths = {24, 84, 90, 48, 60, 78};
        int start = 0;
        int rows = 0;
        while (start < widths.length) {
            int end = OptionRowLayout.end(228, widths, start);
            assertTrue(end > start);
            int used = 0;
            for (int i = start; i < end; i++) {
                used += (i == start ? 0 : 6) + Math.min(228, Math.max(36, widths[i] + 16));
            }
            assertTrue(used <= 228);
            start = end;
            rows++;
        }
        assertTrue(rows > 1);
    }

    @Test void oversizedValueStillMakesProgress() {
        assertEquals(1, OptionRowLayout.end(80, new int[]{400, 20}, 0));
    }
}
