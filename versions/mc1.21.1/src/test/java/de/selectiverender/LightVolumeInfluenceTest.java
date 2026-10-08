package de.selectiverender;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightVolumeInfluenceTest {
    @Test
    void horizontalSectionBoundsIncludeOnlySectionsWithinTheLightRadius() {
        assertEquals(-1, LightVolumeInfluence.minHorizontalSection(0, 14));
        assertEquals(0, LightVolumeInfluence.maxHorizontalSection(0, 14));
        assertEquals(-1, LightVolumeInfluence.minHorizontalSection(7, 14));
        assertEquals(1, LightVolumeInfluence.maxHorizontalSection(7, 14));
        assertEquals(-2, LightVolumeInfluence.minHorizontalSection(-17, 14));
        assertEquals(-1, LightVolumeInfluence.maxHorizontalSection(-17, 14));
        assertEquals(-134217729,
                LightVolumeInfluence.minHorizontalSection(Integer.MIN_VALUE, 14));
    }

    @Test
    void horizontalBoundsMatchTheExistingInfluencePredicateAcrossNegativeCoordinates() {
        for (int block = -128; block <= 128; block++) {
            int min = LightVolumeInfluence.minHorizontalSection(block, 14);
            int max = LightVolumeInfluence.maxHorizontalSection(block, 14);
            for (int section = -12; section <= 12; section++) {
                boolean candidate = section >= min && section <= max;
                boolean affected = LightVolumeInfluence.blockAffectsSection(
                        section, 0, 0, block, 0, 0, 14);
                assertEquals(affected, candidate, "block=" + block + ", section=" + section);
            }
        }
    }

    @Test
    void blockInvalidationIncludesTheHaloAndSkylightColumnAbove() {
        assertTrue(LightVolumeInfluence.blockAffectsSection(0, 0, 0, -14, -14, -14, 14));
        assertTrue(LightVolumeInfluence.blockAffectsSection(0, 0, 0, 29, 29, 29, 14));
        assertFalse(LightVolumeInfluence.blockAffectsSection(0, 0, 0, -15, 0, 0, 14));
        assertFalse(LightVolumeInfluence.blockAffectsSection(0, 0, 0, 30, 0, 0, 14));
        assertTrue(LightVolumeInfluence.blockAffectsSection(0, 0, 0, 0, 30, 0, 14));
        assertTrue(LightVolumeInfluence.blockAffectsSection(0, 0, 0, 0, 300, 0, 14));
        assertFalse(LightVolumeInfluence.blockAffectsSection(0, 0, 0, 0, -15, 0, 14));
        assertFalse(LightVolumeInfluence.blockAffectsSection(0, 0, 0, 30, 300, 0, 14));
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
