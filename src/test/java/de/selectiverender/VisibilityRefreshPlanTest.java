package de.selectiverender;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VisibilityRefreshPlanTest {
    private static final BlockRegion REGION = new BlockRegion(0, 15, 0, 15, 0, 15);
    private static final BlockRegion HIDE = new BlockRegion(2, 3, 2, 3, 2, 3);

    private VisibilitySnapshot state(boolean enabled, List<BlockRegion> regions, List<BlockRegion> hidden) {
        return VisibilitySnapshot.create(regions, enabled, hidden, true, List.of(), List.of(), false, false, 1);
    }

    @Test void removingLastWhitelistRegionAlsoRefreshesTheComplement() {
        var plan = VisibilityRefreshPlan.between(state(true, List.of(REGION), List.of()),
                state(true, List.of(), List.of()));
        assertTrue(plan.scanComplement());
        assertEquals(List.of(REGION), plan.changedRegions());
        assertTrue(VisibilityRefreshPlan.sectionChanged(SectionVisibility.HIDDEN, SectionVisibility.FULL_VISIBLE));
    }

    @Test void enablingFirstRegionAlsoRefreshesTheComplement() {
        assertTrue(VisibilityRefreshPlan.between(VisibilitySnapshot.EMPTY,
                state(true, List.of(REGION), List.of())).scanComplement());
    }

    @Test void hiddenToggleDoesNotRebuildTheUnchangedWhitelist() {
        var plan = VisibilityRefreshPlan.between(state(true, List.of(REGION), List.of()),
                state(true, List.of(REGION), List.of(HIDE)));
        assertFalse(plan.scanComplement());
        assertEquals(List.of(HIDE), plan.changedRegions());
    }

    @Test void inactiveSelectionChangesNeedNoMeshWork() {
        assertTrue(VisibilityRefreshPlan.between(VisibilitySnapshot.EMPTY,
                state(false, List.of(REGION), List.of())).isEmpty());
    }

    @Test void plotToggleAndClearUseTheSameTransitionPolicy() {
        var plot = VisibilitySnapshot.EMPTY.withPlotState(List.of(REGION), true, true, 2);
        assertTrue(VisibilityRefreshPlan.between(plot,
                plot.withPlotState(List.of(REGION), true, false, 3)).scanComplement());
        assertTrue(VisibilityRefreshPlan.between(plot, VisibilitySnapshot.EMPTY).scanComplement());
    }

    @Test void unchangedWholeSectionsNeedNoComplementRebuild() {
        assertFalse(VisibilityRefreshPlan.sectionChanged(SectionVisibility.HIDDEN, SectionVisibility.HIDDEN));
        assertFalse(VisibilityRefreshPlan.sectionChanged(SectionVisibility.FULL_VISIBLE, SectionVisibility.FULL_VISIBLE));
        assertTrue(VisibilityRefreshPlan.sectionChanged(SectionVisibility.PARTIAL, SectionVisibility.PARTIAL));
    }
}
