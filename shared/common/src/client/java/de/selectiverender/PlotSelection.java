package de.selectiverender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PlotSelection {
    enum Change { ADDED, UPDATED, REMOVED }

    private record Entry(String name, List<BlockRegion> source, Integer minY, Integer maxY, int margin,
                         boolean defaults, List<BlockRegion> regions) { }

    private final LinkedHashMap<List<BlockRegion>, Entry> plots = new LinkedHashMap<>();
    private List<BlockRegion> lastChanged = List.of();
    boolean renderingEnabled = true;

    Change toggle(List<BlockRegion> identity, String name, List<BlockRegion> source, Integer minY, Integer maxY,
                  int margin, boolean defaults, List<BlockRegion> regions) {
        List<BlockRegion> key = key(identity);
        Entry existing = plots.get(key);
        Entry next = new Entry(name, List.copyOf(source), minY, maxY, margin, defaults, List.copyOf(regions));
        if (existing != null && existing.regions().equals(next.regions())) {
            plots.remove(key);
            lastChanged = existing.regions();
            return Change.REMOVED;
        }
        plots.put(key, next);
        lastChanged = existing == null ? next.regions() : union(existing.regions(), next.regions());
        return existing == null ? Change.ADDED : Change.UPDATED;
    }

    boolean applyDefaultHeights(int minY, int maxY) {
        if (minY > maxY) return false;
        List<BlockRegion> changed = new ArrayList<>();
        for (Map.Entry<List<BlockRegion>, Entry> item : plots.entrySet()) {
            Entry entry = item.getValue();
            if (!entry.defaults()) continue;
            List<BlockRegion> regions = PlotRegionTransform.apply(entry.source(), minY, maxY, entry.margin());
            if (regions.isEmpty() || regions.equals(entry.regions())) continue;
            changed.addAll(entry.regions());
            changed.addAll(regions);
            item.setValue(new Entry(entry.name(), entry.source(), minY, maxY, entry.margin(), true, List.copyOf(regions)));
        }
        lastChanged = List.copyOf(changed);
        return !changed.isEmpty();
    }

    List<BlockRegion> regions() {
        return plots.values().stream().flatMap(plot -> plot.regions().stream()).toList();
    }

    List<BlockRegion> lastChanged() { return lastChanged; }
    boolean isEmpty() { return plots.isEmpty(); }
    int size() { return plots.size(); }
    void clear() { plots.clear(); lastChanged = List.of(); }

    private static List<BlockRegion> key(List<BlockRegion> identity) {
        return identity.stream().map(region -> new BlockRegion(region.minX(), region.maxX(), 0, 0, region.minZ(), region.maxZ()))
                .distinct().sorted(Comparator.comparingInt(BlockRegion::minX).thenComparingInt(BlockRegion::maxX)
                        .thenComparingInt(BlockRegion::minZ).thenComparingInt(BlockRegion::maxZ)).toList();
    }

    private static List<BlockRegion> union(List<BlockRegion> first, List<BlockRegion> second) {
        List<BlockRegion> result = new ArrayList<>(first);
        result.addAll(second);
        return List.copyOf(result);
    }
}
