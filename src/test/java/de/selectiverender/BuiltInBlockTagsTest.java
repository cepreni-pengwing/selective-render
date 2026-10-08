package de.selectiverender;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInBlockTagsTest {
    @Test
    void beamSelectorMatchesBeamVariantsFromConquestAndArchitects() {
        BlockFilterRule rule = new BlockFilterRule(BlockFilterRule.Mode.HIDE,
                BlockFilterRule.Kind.TAG, BuiltInBlockTags.BEAMS);

        assertTrue(rule.matches("conquest:oak_wood_beam_horizontal", Set.of()));
        assertTrue(rule.matches("conquest:oak_wood_beam_stairs", Set.of()));
        assertTrue(rule.matches("conquest:oak_wood_beam_vertical_corner_slab", Set.of()));
        assertTrue(rule.matches("architects:diagonal_oak_beam_brace", Set.of()));
        assertTrue(rule.matches("architects:steeper_diagonal_stripped_oak_log", Set.of()));
        assertTrue(rule.matches("architects:thin_stripped_oak_log", Set.of()));
        assertTrue(rule.matches("architects:half_diagonal_stripped_spruce_log", Set.of()));
        assertTrue(rule.matches("architects:diagonal_oak_lintel", Set.of()));
        assertTrue(rule.matches("architects:limestone_lintel", Set.of()));
        assertTrue(rule.matches("conquest:oak_wood_beam_door_frame_lintel", Set.of()));
        assertTrue(rule.matches("conquest:wooden_pole", Set.of()));
        assertTrue(rule.matches("conquest:bean_pole", Set.of()));
        assertTrue(rule.matches("conquest:clothes_hanging_pole", Set.of()));
        assertTrue(rule.matches("conquest:pole_lathe_bench", Set.of()));
        assertTrue(rule.matches("conquest:pole_lathe_stick", Set.of()));
    }

    @Test
    void beamSelectorDoesNotMatchOtherNamespacesOrUnrelatedBlocks() {
        assertFalse(BuiltInBlockTags.isBeam("minecraft:oak_log"));
        assertFalse(BuiltInBlockTags.isBeam("create:metal_beam"));
        assertFalse(BuiltInBlockTags.isBeam("conquest:oak_log"));
        assertFalse(BuiltInBlockTags.isBeam("architects:oak_planks"));
        assertFalse(BuiltInBlockTags.isBeam(null));
    }
}
