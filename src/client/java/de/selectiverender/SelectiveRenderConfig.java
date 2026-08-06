package de.selectiverender;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SelectiveRenderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("selectiverender");
    private static final Map<String, ChunkRegion> PRESETS = new LinkedHashMap<>();
    private static String activePreset;

    private SelectiveRenderConfig() { }

    public static void load(MinecraftClient client) {
        reset();
        Path path = pathFor(client);
        if (path == null || !Files.isRegularFile(path)) return;

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            StoredConfig stored = GSON.fromJson(reader, StoredConfig.class);
            if (stored == null) return;

            if (stored.presets != null) {
                stored.presets.forEach((name, region) -> {
                    if (name != null && region != null) {
                        PRESETS.put(normalize(name), region.toRegion());
                    }
                });
            } else if (stored.minX != null && stored.maxX != null
                    && stored.minZ != null && stored.maxZ != null) {
                PRESETS.put("default", new ChunkRegion(
                        stored.minX, stored.maxX, stored.minZ, stored.maxZ));
            }

            String requestedActive = normalize(stored.activePreset);
            activePreset = PRESETS.containsKey(requestedActive)
                    ? requestedActive
                    : PRESETS.keySet().stream().findFirst().orElse(null);
            SelectiveRenderState.setSavedState(
                    activePreset == null ? null : PRESETS.get(activePreset),
                    stored.enabled && activePreset != null);
        } catch (RuntimeException | IOException exception) {
            SelectiveRenderClient.LOGGER.error("Could not load selective render config {}", path, exception);
            reset();
        }
    }

    public static boolean saveSelection(MinecraftClient client, String requestedName) {
        if (!SelectiveRenderState.saveSelection()) return false;
        String name = normalize(requestedName);
        PRESETS.put(name, SelectiveRenderState.region());
        activePreset = name;
        write(client);
        SelectiveRenderState.refreshRenderer();
        return true;
    }

    public static boolean toggleCurrent(MinecraftClient client) {
        if (!SelectiveRenderState.toggle()) return false;
        write(client);
        return true;
    }

    public static boolean togglePreset(MinecraftClient client, String requestedName) {
        String name = normalize(requestedName);
        ChunkRegion region = PRESETS.get(name);
        if (region == null) return false;
        boolean newEnabled = !(SelectiveRenderState.enabled() && name.equals(activePreset));
        activePreset = name;
        SelectiveRenderState.setSavedState(region, newEnabled);
        SelectiveRenderState.refreshRenderer();
        write(client);
        return true;
    }

    public static boolean deletePreset(MinecraftClient client, String requestedName) {
        String name = normalize(requestedName);
        if (PRESETS.remove(name) == null) return false;
        if (name.equals(activePreset)) {
            activePreset = PRESETS.keySet().stream().findFirst().orElse(null);
            SelectiveRenderState.setSavedState(
                    activePreset == null ? null : PRESETS.get(activePreset), false);
            SelectiveRenderState.refreshRenderer();
        }
        write(client);
        return true;
    }

    public static String activePreset() {
        return activePreset;
    }

    public static List<String> presetNames() {
        return List.copyOf(PRESETS.keySet());
    }

    public static void reset() {
        PRESETS.clear();
        activePreset = null;
        SelectiveRenderState.setSavedState(null, false);
    }

    private static void write(MinecraftClient client) {
        Path path = pathFor(client);
        if (path == null) return;
        try {
            Files.createDirectories(DIRECTORY);
            StoredConfig stored = new StoredConfig();
            stored.activePreset = activePreset;
            stored.enabled = SelectiveRenderState.enabled();
            stored.presets = new LinkedHashMap<>();
            PRESETS.forEach((name, region) -> stored.presets.put(name, StoredRegion.from(region)));
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(stored, writer);
            }
        } catch (IOException exception) {
            SelectiveRenderClient.LOGGER.error("Could not save selective render config {}", path, exception);
        }
    }

    private static Path pathFor(MinecraftClient client) {
        if (client.world == null) return null;
        String owner;
        if (client.getCurrentServerEntry() != null) {
            owner = "server:" + client.getCurrentServerEntry().address.toLowerCase(Locale.ROOT);
        } else if (client.getServer() != null) {
            owner = "singleplayer:" + client.getServer().getSaveProperties().getLevelName();
        } else {
            owner = "local:unknown";
        }
        String dimension = client.world.getRegistryKey().getValue().toString();
        return DIRECTORY.resolve(sha256(owner + "|" + dimension) + ".json");
    }

    private static String normalize(String name) {
        return name == null ? null : name.toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class StoredConfig {
        Map<String, StoredRegion> presets;
        String activePreset;
        boolean enabled;
        Integer minX;
        Integer maxX;
        Integer minZ;
        Integer maxZ;
    }

    private static final class StoredRegion {
        int minX;
        int maxX;
        int minZ;
        int maxZ;

        static StoredRegion from(ChunkRegion region) {
            StoredRegion stored = new StoredRegion();
            stored.minX = region.minX();
            stored.maxX = region.maxX();
            stored.minZ = region.minZ();
            stored.maxZ = region.maxZ();
            return stored;
        }

        ChunkRegion toRegion() {
            return new ChunkRegion(minX, maxX, minZ, maxZ);
        }
    }
}
