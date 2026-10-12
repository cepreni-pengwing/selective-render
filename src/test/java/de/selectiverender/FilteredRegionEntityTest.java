package de.selectiverender;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class FilteredRegionEntityTest {
    @Test void onlyFiltersExcludeEntitiesInsideButNotOutsideTheirBounds() {
        var region = new FilteredRegion(new BlockRegion(0, 10, 0, 10, 0, 10),
                Set.of(new BlockFilterRule(BlockFilterRule.Mode.ONLY, BlockFilterRule.Kind.ID, "minecraft:stone")));
        assertTrue(region.excludesEntities(0, 0, 0));
        assertTrue(region.excludesEntities(10, 10, 10));
        assertFalse(region.excludesEntities(-1, 5, 5));
        assertFalse(region.excludesEntities(5, 11, 5));
        assertFalse(region.excludesEntities(5, 5, 11));
    }
    @Test void hideAndEmptyFiltersDoNotChangeEntityVisibility() {
        var bounds = new BlockRegion(0, 10, 0, 10, 0, 10);
        assertFalse(new FilteredRegion(bounds, Set.of()).excludesEntities(5, 5, 5));
        assertFalse(new FilteredRegion(bounds, Set.of(new BlockFilterRule(BlockFilterRule.Mode.HIDE,
                BlockFilterRule.Kind.TAG, "minecraft:slabs"))).excludesEntities(5, 5, 5));
    }
}
