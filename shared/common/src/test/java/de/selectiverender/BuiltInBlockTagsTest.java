package de.selectiverender;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInBlockTagsTest {
    @Test void includesStructuralFamiliesAndAllBranchSizesWithoutVolumeBuildingShapes() {
        for(String id:new String[]{"antique_wooden_shelf","old_red_5_way_flange","heavy_wooden_door",
                "wooden_fancy_corbel","red_acacia_wood_plank_wall","horizontal_birch_wood_railing",
                "horizontal_oak_wood_railing","horizontal_oak_log_railing","tall_old_oak_door",
                "simple_arch_window_pane","simple_picket_fence","black_pipe","rustic_cross_fence_gate",
                "rustic_fence","old_cross_flange","oak_platform_boards","spruce_platform_boards",
                "acacia_log_branch","acacia_log_small_branch","acacia_log_large_branch"})
            assertTrue(BuiltInBlockTags.isBeam("conquest:"+id),id);
        assertTrue(BuiltInBlockTags.isBeam("architects:steeper_diagonal_spruce_board"));
        for(String id:new String[]{"brown_red_wood_shingles_quarter_slab","tree_decorated_window_vertical_slab",
                "oak_platform_stairs","oak_platform_slab","oak_platform_quarter_slab","oak_platform_eighth_slab",
                "red_acacia_wood_planks","oak_log","conquest_oak_platform_boards"})
            assertFalse(BuiltInBlockTags.isBeam("conquest:"+id),id);
    }

    @Test
    void beamSelectorMatchesBeamVariantsFromConquestAndArchitects() {
        BlockFilterRule rule = new BlockFilterRule(BlockFilterRule.Mode.HIDE,
                BlockFilterRule.Kind.TAG, BuiltInBlockTags.BEAMS);

        assertTrue(rule.matches("conquest:oak_wood_beam_horizontal", Set.of()));
        assertFalse(rule.matches("conquest:oak_wood_beam_stairs", Set.of()));
        assertFalse(rule.matches("conquest:oak_wood_beam_vertical_corner_slab", Set.of()));
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
