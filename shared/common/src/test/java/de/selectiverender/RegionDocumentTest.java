package de.selectiverender;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegionDocumentTest {
    @Test void changingTypePreservesEachRegionsActivationAndOtherRegions() {
        for(boolean active:new boolean[]{false,true}) for(boolean hidden:new boolean[]{false,true}) {
            var source=new RegionDocument("minecraft:overworld",Map.of("test",List.of(BOX),"other",List.of(BOX)),
                    active && !hidden?Set.of("test","other"):Set.of("other"),hidden?Set.of("test"):Set.of(),
                    active && hidden?Set.of("test"):Set.of(),Map.of(),true,true);
            var changed=source.edit("test","test",List.of(BOX),!hidden,source.isSelected("test"),List.of());
            assertEquals(active,changed.isSelected("test")); assertTrue(changed.isSelected("other"));
            assertEquals(!hidden,changed.hidden().contains("test"));
        }
    }

    @Test void convertingAnInactiveHiddenRegionSelectsItOnlyInTheRenderGroup() {
        var hidden=new RegionDocument("minecraft:overworld",Map.of("test",List.of(BOX)),
                Set.of(),Set.of("test"),Set.of(),Map.of(),false,true);
        var render=hidden.edit("test","test",List.of(BOX),false,true,List.of());
        assertTrue(render.isSelected("test")); assertTrue(render.selected().contains("test"));
        assertTrue(render.hidden().isEmpty()); assertTrue(render.selectedHidden().isEmpty());
        var off=VisibilitySnapshot.create(render.regions().get("test"),false,List.of(),true,List.of(),List.of(),false,false,1);
        var on=off.toggleConfiguredState(2);
        assertFalse(off.enabled()); assertTrue(on.enabled());
        assertTrue(on.toggleConfiguredState(3).configuredRegions().contains(BOX));
    }
    @Test void removalClearsOnlyTheSelectedRegionAndPreservesTheOriginal() {
        var original = new RegionDocument("minecraft:the_nether", Map.of("test", List.of(BOX), "other", List.of(BOX)),
                Set.of("other"), Set.of("test"), Set.of("test"), Map.of("test", List.of(RULE)),
                true, false, Map.of("test", RegionOptions.DEFAULT, "other", RegionOptions.DEFAULT));
        var changed = original.remove("test");
        assertEquals(Set.of("other"), changed.regions().keySet());
        assertEquals(Set.of("other"), changed.selected());
        assertTrue(changed.hidden().isEmpty());
        assertTrue(changed.selectedHidden().isEmpty());
        assertTrue(changed.filters().isEmpty());
        assertEquals(Set.of("other"), changed.options().keySet());
        assertTrue(changed.rendering());
        assertFalse(changed.hiding());
        assertEquals("minecraft:the_nether", changed.dimension());
        assertTrue(original.regions().containsKey("test"));
        assertThrows(IllegalArgumentException.class, () -> changed.remove("test"));
        assertTrue(changed.remove("other").regions().isEmpty());
    }

    @Test void existingNamesRemainEditableAndCommandCompatibleNamesAreAllowed() {
        var original = new RegionDocument("minecraft:overworld", Map.of("old name", List.of(BOX)),
                Set.of(), Set.of(), Set.of(), Map.of(), false, true);
        assertTrue(original.edit("old name", "old name", List.of(BOX), true, true, List.of()).hidden().contains("old name"));
        assertTrue(original.edit("old name", "custom-region.2", List.of(BOX), false, false, List.of()).regions().containsKey("custom-region.2"));
    }

    private static final BlockRegion BOX = new BlockRegion(0, 10, -64, 20, 0, 10);
    private static final BlockFilterRule RULE = new BlockFilterRule(BlockFilterRule.Mode.HIDE, BlockFilterRule.Kind.ID, "minecraft:stone");
    private RegionDocument source(String dimension) {
        return new RegionDocument(dimension, Map.of("test", List.of(BOX), "other", List.of(BOX)),
                Set.of("test"), Set.of(), Set.of(), Map.of("test", List.of(RULE)), false, true);
    }

    @Test void renamePreservesFiltersAndGroupStateWithoutMutatingOriginal() {
        var original = source("minecraft:overworld");
        var changed = original.edit("test", "NEW", List.of(BOX), false, true, List.of(RULE));
        assertTrue(changed.regions().containsKey("new"));
        assertFalse(changed.regions().containsKey("test"));
        assertTrue(changed.selected().contains("new"));
        assertEquals(List.of(RULE), changed.filters().get("new"));
        assertFalse(changed.rendering());
        assertTrue(changed.hiding());
        assertTrue(original.regions().containsKey("test"));
        assertEquals(Set.of("test"), original.selected());
    }

    @Test void movingToHiddenTransfersSelectionWithoutEnablingEitherGroup() {
        var changed = source("minecraft:overworld").edit("test", "test", List.of(BOX), true, true, List.of(RULE));
        assertTrue(changed.hidden().contains("test"));
        assertTrue(changed.selectedHidden().contains("test"));
        assertFalse(changed.selected().contains("test"));
        assertFalse(changed.rendering());
    }

    @Test void deselectionAndFilterUpdatesRemainDimensionLocal() {
        var overworld = source("minecraft:overworld");
        var nether = source("minecraft:the_nether");
        var changed = overworld.edit("test", "test", List.of(BOX), false, false, List.of());
        assertFalse(changed.isSelected("test"));
        assertFalse(changed.filters().containsKey("test"));
        assertTrue(nether.isSelected("test"));
        assertEquals(List.of(RULE), nether.filters().get("test"));
        assertEquals("minecraft:overworld", changed.dimension());
        assertEquals("minecraft:the_nether", nether.dimension());
    }

    @Test void invalidEditsDoNotModifyTheSource() {
        var original = source("minecraft:overworld");
        for (String name : List.of("", "all", "a", "other", "two names", "custom:region", "größe", "slash/name")) {
            assertThrows(IllegalArgumentException.class,
                    () -> original.edit("test", name, List.of(BOX), false, true, List.of(RULE)));
        }
        assertThrows(IllegalArgumentException.class,
                () -> original.edit("test", "test", List.of(), false, true, List.of()));
        assertEquals(Set.of("test"), original.selected());
    }

    @Test void multiBoxRegionsCanBeRenamedButNotResized() {
        var original = new RegionDocument("minecraft:overworld", Map.of("test", List.of(BOX, BOX)),
                Set.of(), Set.of(), Set.of(), Map.of(), true, false);
        assertThrows(IllegalArgumentException.class,
                () -> original.edit("test", "test", List.of(BOX), false, false, List.of()));
        assertEquals(2, original.edit("test", "new", List.of(BOX, BOX), false, false, List.of()).regions().get("new").size());
    }

    @Test void cornersAcceptSignedIntegersAndRejectBadOrReversedBounds() {
        assertEquals(new BlockRegion(-20, 10, -70, 20, -5, 10), RegionDocument.corner(BOX, "-20 -70 -5", true));
        assertEquals(new BlockRegion(0, 40, -64, 60, 0, 50), RegionDocument.corner(BOX, "40, 60, 50", false));
        for (String invalid : List.of("", "1 2", "a 2 3", "2147483648 0 0", "20 0 0")) {
            assertThrows(IllegalArgumentException.class, () -> RegionDocument.corner(BOX, invalid, true));
        }
    }

    @Test void snapshotsCannotBeMutatedAndDuplicateFiltersAreDeduplicated() {
        var original = source("minecraft:overworld");
        assertThrows(UnsupportedOperationException.class, () -> original.regions().clear());
        assertThrows(UnsupportedOperationException.class, () -> original.selected().clear());
        assertEquals(List.of(RULE), original.edit("test", "test", List.of(BOX), false, true, List.of(RULE, RULE)).filters().get("test"));
    }
}
