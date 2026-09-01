package de.selectiverender;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightVolumeInfluenceTest {
    @Test
    void blockInvalidationIncludesHorizontalHaloAndAllBlocksAboveTheLowerYHalo() {
        assertTrue(LightVolumeInfluence.blockAffectsSection(0, 0, 0, -14, -14, -14, 14));
        assertTrue(LightVolumeInfluence.blockAffectsSection(0, 0, 0, 29, 29, 29, 14));
        assertTrue(LightVolumeInfluence.blockAffectsSection(0, 0, 0, 0, 30, 0, 14));
        assertTrue(LightVolumeInfluence.blockAffectsSection(0, 0, 0, 0, 200, 0, 14));
        assertFalse(LightVolumeInfluence.blockAffectsSection(0, 0, 0, -15, 0, 0, 14));
        assertFalse(LightVolumeInfluence.blockAffectsSection(0, 0, 0, 30, 0, 0, 14));
        assertFalse(LightVolumeInfluence.blockAffectsSection(0, 0, 0, 0, -15, 0, 14));
    }

    @Test
    void chunkInvalidationIncludesChunksIntersectingTheHalo() {
        assertTrue(LightVolumeInfluence.chunkAffectsSection(0, 0, -1, 0, 14));
        assertTrue(LightVolumeInfluence.chunkAffectsSection(0, 0, 1, 0, 14));
        assertFalse(LightVolumeInfluence.chunkAffectsSection(0, 0, -2, 0, 14));
        assertFalse(LightVolumeInfluence.chunkAffectsSection(0, 0, 2, 0, 14));
    }

    @Test
    void invalidationMathHandlesExtremeCoordinates() {
        assertTrue(LightVolumeInfluence.blockAffectsSection(
                Integer.MIN_VALUE >> 4, 0, Integer.MAX_VALUE >> 4,
                Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 14));
    }
}
