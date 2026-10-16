package de.selectiverender;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, dimension-local editor snapshot; never changes the live renderer itself. */
record RegionDocument(String dimension, Map<String, List<BlockRegion>> regions,
                      Set<String> selected, Set<String> hidden, Set<String> selectedHidden,
                      Map<String, List<BlockFilterRule>> filters, boolean rendering, boolean hiding, Map<String, RegionOptions> options) {
    RegionDocument(String dimension, Map<String, List<BlockRegion>> regions,
                   Set<String> selected, Set<String> hidden, Set<String> selectedHidden,
                   Map<String, List<BlockFilterRule>> filters, boolean rendering, boolean hiding) {
        this(dimension, regions, selected, hidden, selectedHidden, filters, rendering, hiding, Map.of());
    }
    RegionDocument {
        options = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(options));
        Map<String, List<BlockRegion>> boxes = new LinkedHashMap<>();
        regions.forEach((name, value) -> boxes.put(name, List.copyOf(value)));
        regions = java.util.Collections.unmodifiableMap(boxes);
        selected = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(selected));
        hidden = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(hidden));
        selectedHidden = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(selectedHidden));
        Map<String, List<BlockFilterRule>> rules = new LinkedHashMap<>();
        filters.forEach((name, value) -> rules.put(name, List.copyOf(value)));
        filters = java.util.Collections.unmodifiableMap(rules);
    }

    boolean isSelected(String name) { return (hidden.contains(name) ? selectedHidden : selected).contains(name); }

    RegionDocument edit(String oldName, String newName, List<BlockRegion> boxes,
                        boolean hide, boolean active, List<BlockFilterRule> rules) {
        newName = newName.trim().toLowerCase(java.util.Locale.ROOT);
        if (!regions.containsKey(oldName)) throw new IllegalArgumentException("This region no longer exists");
        if (!newName.equals(oldName) && (!newName.matches("[0-9a-z_.+-]+")
                || newName.equals("all") || newName.equals("a"))) {
            throw new IllegalArgumentException("Use only letters, digits and _ - . +; 'all' and 'a' are reserved");
        }
        if (!oldName.equals(newName) && regions.containsKey(newName)) {
            throw new IllegalArgumentException("A region with that name already exists in this dimension");
        }
        if (boxes.isEmpty()) throw new IllegalArgumentException("A region needs at least one bounding box");
        if (!regions.get(oldName).equals(boxes) && regions.get(oldName).size() != 1) {
            throw new IllegalArgumentException("Only single-box regions can be resized here");
        }
        Map<String, List<BlockRegion>> nextRegions = new LinkedHashMap<>();
        for (var entry : regions.entrySet()) nextRegions.put(entry.getKey().equals(oldName) ? newName : entry.getKey(),
                entry.getKey().equals(oldName) ? List.copyOf(boxes) : entry.getValue());
        Set<String> nextSelected = new LinkedHashSet<>(selected);
        Set<String> nextHidden = new LinkedHashSet<>(hidden);
        Set<String> nextSelectedHidden = new LinkedHashSet<>(selectedHidden);
        nextSelected.remove(oldName); nextHidden.remove(oldName); nextSelectedHidden.remove(oldName);
        if (hide) nextHidden.add(newName);
        if (active) (hide ? nextSelectedHidden : nextSelected).add(newName);
        Map<String, List<BlockFilterRule>> nextFilters = new LinkedHashMap<>(filters);
        nextFilters.remove(oldName);
        if (!rules.isEmpty()) nextFilters.put(newName, List.copyOf(new LinkedHashSet<>(rules)));
        Map<String, RegionOptions> nextOptions = new LinkedHashMap<>(options);
        RegionOptions oldOptions = nextOptions.remove(oldName);
        if (oldOptions != null) nextOptions.put(newName, oldOptions);
        return new RegionDocument(dimension, nextRegions, nextSelected, nextHidden, nextSelectedHidden,
                nextFilters, rendering, hiding, nextOptions);
    }

    RegionDocument remove(String name) {
        if (!regions.containsKey(name)) throw new IllegalArgumentException("This region no longer exists");
        var nextRegions = new LinkedHashMap<>(regions); nextRegions.remove(name);
        var nextSelected = new LinkedHashSet<>(selected); nextSelected.remove(name);
        var nextHidden = new LinkedHashSet<>(hidden); nextHidden.remove(name);
        var nextSelectedHidden = new LinkedHashSet<>(selectedHidden); nextSelectedHidden.remove(name);
        var nextFilters = new LinkedHashMap<>(filters); nextFilters.remove(name);
        var nextOptions = new LinkedHashMap<>(options); nextOptions.remove(name);
        return new RegionDocument(dimension, nextRegions, nextSelected, nextHidden, nextSelectedHidden,
                nextFilters, rendering, hiding, nextOptions);
    }

    static BlockRegion corner(BlockRegion old, String text, boolean minimum) {
        String[] values = text.trim().replace(',', ' ').split("\\s+");
        if (values.length != 3) throw new IllegalArgumentException("Enter three integer coordinates: X Y Z");
        try {
            int x = Integer.parseInt(values[0]), y = Integer.parseInt(values[1]), z = Integer.parseInt(values[2]);
            return minimum ? new BlockRegion(x, old.maxX(), y, old.maxY(), z, old.maxZ())
                    : new BlockRegion(old.minX(), x, old.minY(), y, old.minZ(), z);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Use integer coordinates with minimum no greater than maximum");
        }
    }
}
