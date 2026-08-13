package de.selectiverender;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkyLightColumnTest {
    @Test
    void unobstructedDirectSkyRemainsMaximum() {
        assertEquals(15, SkyLightColumn.passDown(15, 0));
    }

    @Test
    void partialOpacityAttenuatesByItsActualValue() {
        assertEquals(13, SkyLightColumn.passDown(15, 2));
        assertEquals(12, SkyLightColumn.passDown(13, 1));
    }

    @Test
    void opaqueBlocksStopDirectSky() {
        assertEquals(0, SkyLightColumn.passDown(15, 15));
    }
}
