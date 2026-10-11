package de.selectiverender;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlockFilterRuleTest {
    @Test
    void acceptsMixedSelectorsAndLegacyPrefixes() {
        var rules = BlockFilterRule.parse(BlockFilterRule.Mode.HIDE,
                "minecraft:stone,#minecraft:slabs,id:minecraft:dirt,tag:minecraft:stairs");
        assertEquals(4, rules.size());
        assertEquals("minecraft:stone", rules.get(0).selector());
        assertEquals("#minecraft:slabs", rules.get(1).selector());
        assertEquals(BlockFilterRule.Kind.ID, rules.get(2).kind());
        assertEquals(BlockFilterRule.Kind.TAG, rules.get(3).kind());
    }

    @Test
    void normalizesWhitespaceAndDeduplicates() {
        var rules = BlockFilterRule.parse(BlockFilterRule.Mode.ONLY,
                " Minecraft:Stone ,id:minecraft:stone, #minecraft:slabs ");
        assertEquals(2, rules.size());
    }

    @Test
    void invalidBatchIsRejectedBeforeReturningAnyRules() {
        for (String value : new String[]{"minecraft:stone,", ",minecraft:stone",
                "minecraft:stone,invalid", "minecraft:stone,#", ""}) {
            assertThrows(IllegalArgumentException.class,
                    () -> BlockFilterRule.parse(BlockFilterRule.Mode.HIDE, value));
        }
    }

    @Test
    void hideWinsAndOnlyRulesAreCombined() {
        var rules = new java.util.LinkedHashSet<>(BlockFilterRule.parse(BlockFilterRule.Mode.ONLY,
                "minecraft:stone,#minecraft:slabs"));
        rules.addAll(BlockFilterRule.parse(BlockFilterRule.Mode.HIDE, "minecraft:stone"));
        assertFalse(BlockFilterRule.allows(rules, "minecraft:stone", Set.of()));
        assertTrue(BlockFilterRule.allows(rules, "minecraft:oak_slab", Set.of("minecraft:slabs")));
        assertFalse(BlockFilterRule.allows(rules, "minecraft:dirt", Set.of()));
    }
}
