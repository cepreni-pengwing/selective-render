package de.selectiverender;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Cached registry identity and tags used by block filters during terrain scans. */
record BlockFilterMetadata(String id, Set<String> tags) {
    private static final ConcurrentHashMap<Block, BlockFilterMetadata> CACHE = new ConcurrentHashMap<>();

    static BlockFilterMetadata of(Block block) {
        return CACHE.computeIfAbsent(block, key -> new BlockFilterMetadata(
                Registries.BLOCK.getId(key).toString(),
                key.getRegistryEntry().streamTags()
                        .map(tag -> tag.id().toString())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet())));
    }

    static void clear() { CACHE.clear(); }
}
