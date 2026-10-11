package de.selectiverender;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FilterCompletionTest {
    private final List<String> selectors = List.of("minecraft:stone", "minecraft:oak_log",
            "architects:thin_stripped_oak_log", "#minecraft:slabs", "#selectiverender:beams");

    @Test void completesIdsTagsAndLegacyPrefixes() {
        assertEquals(List.of("minecraft:stone"), FilterCompletion.matches("minecraft:st", selectors));
        assertEquals(List.of("#minecraft:slabs"), FilterCompletion.matches("#minecraft:s", selectors));
        assertEquals(List.of("tag:minecraft:slabs"), FilterCompletion.matches("tag:minecraft:s", selectors));
        assertEquals(List.of("id:minecraft:stone"), FilterCompletion.matches("id:minecraft:st", selectors));
        assertEquals(List.of("minecraft:oak_log"), FilterCompletion.matches("oak", selectors));
    }

    @Test void batchesKeepPreviouslyEnteredSelectorsAndSpacing() {
        assertEquals(List.of("minecraft:stone, #selectiverender:beams"),
                FilterCompletion.matches("minecraft:stone, #selectiverender:b", selectors));
        assertEquals(List.of("minecraft:stone,minecraft:oak_log"),
                FilterCompletion.matches("minecraft:stone,oak", selectors));
    }

    @Test void invalidPrefixesHaveNoCandidatesAndMatchesAreSortedAndDistinct() {
        assertTrue(FilterCompletion.matches("missing:nope", selectors).isEmpty());
        assertEquals(List.of("minecraft:oak_log", "minecraft:stone"),
                FilterCompletion.matches("minecraft:", List.of("minecraft:stone", "minecraft:oak_log", "minecraft:stone")));
    }
}
