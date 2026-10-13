package de.selectiverender;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Persisted navigation only; never contains pending edits or armed deletion confirmations. */
final class MenuNavigationState {
    String tab = "GENERAL";
    Map<String, Integer> scrolls = new HashMap<>();
    Map<String, RegionView> regions = new HashMap<>();

    int scroll(String tab) { return Math.max(0, scrolls.getOrDefault(tab, 0)); }
    RegionView region(String context) { return regions.computeIfAbsent(context, key -> new RegionView()); }
    void normalize() {
        if (tab == null) tab = "GENERAL";
        if (scrolls == null) scrolls = new HashMap<>();
        if (regions == null) regions = new HashMap<>();
        scrolls.values().removeIf(java.util.Objects::isNull);
        regions.values().removeIf(java.util.Objects::isNull);
        for (RegionView view : regions.values()) if (view.collapsed == null) view.collapsed = new HashSet<>();
    }
    static final class RegionView {
        String dimension, selected;
        Set<String> collapsed = new HashSet<>();
        int scroll, navigationScroll;
        boolean filtersOpen, boundariesOpen, interactionsOpen, boxesOpen;
    }
}
