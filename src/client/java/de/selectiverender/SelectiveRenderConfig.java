package de.selectiverender;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SelectiveRenderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("selectiverender");
    private static final Map<String, List<BlockRegion>> PRESETS = new LinkedHashMap<>();
    private static final LinkedHashSet<String> ACTIVE_PRESETS = new LinkedHashSet<>();
    private static final LinkedHashSet<String> HIDDEN_PRESETS = new LinkedHashSet<>();
    private static final LinkedHashSet<String> ACTIVE_HIDDEN_PRESETS = new LinkedHashSet<>();
    private static boolean groupEnabled;
    private static boolean hideGroupEnabled = true;
    private static String sessionOwner;

    private SelectiveRenderConfig() { }

    public static void beginSession(MinecraftClient client) {
        sessionOwner = ownerFor(client);
        if (client.world != null) load(client, client.world);
    }

    public static void load(MinecraftClient client, ClientWorld world) {
        reset();
        if (sessionOwner == null) sessionOwner = ownerFor(client);
        Path path = pathFor(world);
        if (path == null) return;
        ConfigRecovery.Result<StoredConfig> recovery = ConfigRecovery.load(
                path, SelectiveRenderConfig::readStoredConfig);
        StoredConfig stored = recovery.value();
        boolean migratedLegacySingleplayer = false;
        if (recovery.recoveredFromBackup()) {
            SelectiveRenderClient.LOGGER.warn(
                    "Recovered selective render config {} from backup {}",
                    path, ConfigRecovery.backupPath(path));
        }
        if (stored == null && client.getServer() != null) {
            Path legacyPath = pathFor(world, legacyOwnerFor(client));
            if (!legacyPath.equals(path)) {
                ConfigRecovery.Result<StoredConfig> legacyRecovery = ConfigRecovery.load(
                        legacyPath, SelectiveRenderConfig::readStoredConfig);
                stored = legacyRecovery.value();
                migratedLegacySingleplayer = stored != null;
                if (migratedLegacySingleplayer) {
                    SelectiveRenderClient.LOGGER.info(
                            "Migrating selective render config {} to unique world identity {}",
                            legacyPath, path);
                }
            }
        }
        if (stored == null) {
            if (recovery.primaryExisted()) reset();
            return;
        }

        try {
            applyStoredConfig(stored);
            if (recovery.recoveredFromBackup()) write(client, false);
            else if (migratedLegacySingleplayer) write(client);
        } catch (RuntimeException exception) {
            SelectiveRenderClient.LOGGER.error("Could not apply selective render config {}", path, exception);
            reset();
        }
    }

    public static boolean saveSelection(MinecraftClient client, String requestedName) {
        if (isReservedName(requestedName)) return false;
        String name = normalize(requestedName);
        if (PRESETS.containsKey(name) || !SelectiveRenderState.saveSelection()) return false;
        List<BlockRegion> regions = List.of(SelectiveRenderState.selection());
        PRESETS.put(name, regions);
        if (HIDDEN_PRESETS.contains(name)) {
            ACTIVE_HIDDEN_PRESETS.add(name);
            hideGroupEnabled = true;
        } else {
            ACTIVE_PRESETS.add(name);
            groupEnabled = true;
        }
        applyState();
        write(client);
        if ((ACTIVE_PRESETS.contains(name) && groupEnabled)
                || (ACTIVE_HIDDEN_PRESETS.contains(name) && hideGroupEnabled)) {
            SelectiveRenderState.refreshVisibilityRegions(regions);
        }
        return true;
    }

    public static boolean saveRegions(MinecraftClient client, String requestedName,
                                      List<BlockRegion> regions) {
        if (isReservedName(requestedName) || regions == null || regions.isEmpty()) return false;
        String name = normalize(requestedName);
        if (PRESETS.containsKey(name)) return false;
        List<BlockRegion> next = List.copyOf(regions);
        PRESETS.put(name, next);
        HIDDEN_PRESETS.remove(name);
        ACTIVE_HIDDEN_PRESETS.remove(name);
        ACTIVE_PRESETS.add(name);
        groupEnabled = true;
        applyState();
        write(client);
        SelectiveRenderState.refreshVisibilityRegions(next);
        return true;
    }

    public static boolean toggleCurrent(MinecraftClient client) {
        if (ACTIVE_PRESETS.isEmpty()) return false;
        boolean wasEnabled = groupEnabled;
        groupEnabled = !groupEnabled;
        if (groupEnabled && !ACTIVE_HIDDEN_PRESETS.isEmpty()) hideGroupEnabled = true;
        applyState();
        if (wasEnabled) {
            SelectiveRenderState.refreshRenderer();
        } else {
            SelectiveRenderState.refreshVisibilityRegions(activeRegions());
        }
        write(client);
        return true;
    }

    public static boolean togglePreset(MinecraftClient client, String requestedName) {
        String name = normalize(requestedName);
        if (!PRESETS.containsKey(name)) return false;
        boolean wasHiddenVisible = ACTIVE_HIDDEN_PRESETS.contains(name) && hideGroupEnabled;
        if (HIDDEN_PRESETS.remove(name)) {
            ACTIVE_HIDDEN_PRESETS.remove(name);
            ACTIVE_PRESETS.add(name);
        } else if (!ACTIVE_PRESETS.remove(name)) {
            ACTIVE_PRESETS.add(name);
        }
        applyState();
        if (groupEnabled || wasHiddenVisible) SelectiveRenderState.refreshVisibilityRegions(PRESETS.get(name));
        write(client);
        return true;
    }

    public static boolean toggleHiddenPreset(MinecraftClient client, String requestedName) {
        String name = normalize(requestedName);
        if (!PRESETS.containsKey(name)) return false;
        boolean wasRenderVisible = ACTIVE_PRESETS.contains(name) && groupEnabled;
        if (HIDDEN_PRESETS.add(name)) {
            ACTIVE_PRESETS.remove(name);
            ACTIVE_HIDDEN_PRESETS.add(name);
        } else if (!ACTIVE_HIDDEN_PRESETS.remove(name)) {
            ACTIVE_HIDDEN_PRESETS.add(name);
        }
        applyState();
        if (hideGroupEnabled || wasRenderVisible) SelectiveRenderState.refreshVisibilityRegions(PRESETS.get(name));
        write(client);
        return true;
    }

    public static boolean toggleHiddenGroup(MinecraftClient client) {
        if (ACTIVE_HIDDEN_PRESETS.isEmpty()) return false;
        hideGroupEnabled = !hideGroupEnabled;
        applyState();
        SelectiveRenderState.refreshVisibilityRegions(hiddenRegions());
        write(client);
        return true;
    }

    public static boolean toggleAllPresets(MinecraftClient client) {
        List<String> normalNames = PRESETS.keySet().stream()
                .filter(name -> !HIDDEN_PRESETS.contains(name)).toList();
        if (normalNames.isEmpty()) return false;
        List<BlockRegion> changed = regionsFor(normalNames);
        boolean disabling = PresetGroupLogic.toggleAll(ACTIVE_PRESETS, normalNames);
        groupEnabled = true;
        applyState();
        if (disabling) SelectiveRenderState.refreshRenderer();
        else SelectiveRenderState.refreshVisibilityRegions(changed);
        write(client);
        return true;
    }

    public static boolean toggleAllHiddenPresets(MinecraftClient client) {
        if (HIDDEN_PRESETS.isEmpty()) return false;
        List<BlockRegion> changed = regionsFor(HIDDEN_PRESETS);
        PresetGroupLogic.toggleAll(ACTIVE_HIDDEN_PRESETS, HIDDEN_PRESETS);
        hideGroupEnabled = true;
        applyState();
        SelectiveRenderState.refreshVisibilityRegions(changed);
        write(client);
        return true;
    }

    public static boolean deletePreset(MinecraftClient client, String requestedName) {
        String name = normalize(requestedName);
        List<BlockRegion> removed = PRESETS.remove(name);
        if (removed == null) return false;
        boolean visibleChange = PresetVisibility.affectsRendering(
                ACTIVE_PRESETS.contains(name), groupEnabled,
                SelectiveRenderState.plotModeActive(), SelectiveRenderState.enabled(),
                HIDDEN_PRESETS.contains(name), ACTIVE_HIDDEN_PRESETS.contains(name),
                hideGroupEnabled);
        ACTIVE_PRESETS.remove(name);
        HIDDEN_PRESETS.remove(name);
        ACTIVE_HIDDEN_PRESETS.remove(name);
        applyState();
        if (visibleChange) SelectiveRenderState.refreshVisibilityRegions(removed);
        write(client);
        return true;
    }

    public static RenameResult renamePreset(MinecraftClient client, String requestedOldName,
                                             String requestedNewName) {
        String oldName = normalize(requestedOldName);
        String newName = normalize(requestedNewName);
        if (!PRESETS.containsKey(oldName)) return RenameResult.MISSING_SOURCE;
        if (oldName.equals(newName)) return RenameResult.SUCCESS;
        if (isReservedName(newName)) return RenameResult.RESERVED_NAME;
        if (PRESETS.containsKey(newName)) return RenameResult.TARGET_EXISTS;

        List<BlockRegion> regions = PRESETS.remove(oldName);
        PRESETS.put(newName, regions);
        PresetGroupLogic.replaceMembership(ACTIVE_PRESETS, oldName, newName);
        PresetGroupLogic.replaceMembership(HIDDEN_PRESETS, oldName, newName);
        PresetGroupLogic.replaceMembership(ACTIVE_HIDDEN_PRESETS, oldName, newName);
        applyState();
        write(client);
        return RenameResult.SUCCESS;
    }

    public static boolean isPresetActive(String name) {
        return ACTIVE_PRESETS.contains(normalize(name));
    }

    public static List<String> activePresetNames() {
        return List.copyOf(ACTIVE_PRESETS);
    }

    public static boolean groupEnabled() {
        return groupEnabled;
    }

    public static boolean isPresetHidden(String name) {
        return HIDDEN_PRESETS.contains(normalize(name));
    }

    public static boolean isHiddenPresetActive(String name) {
        return ACTIVE_HIDDEN_PRESETS.contains(normalize(name));
    }

    public static List<String> hiddenPresetNames() {
        return List.copyOf(ACTIVE_HIDDEN_PRESETS);
    }

    public static boolean hideGroupEnabled() {
        return hideGroupEnabled;
    }

    public static List<String> presetNames() {
        return List.copyOf(PRESETS.keySet());
    }

    public static boolean presetExists(String name) {
        return PRESETS.containsKey(normalize(name));
    }

    public static BlockRegion presetRegion(String name) {
        List<BlockRegion> regions = PRESETS.get(normalize(name));
        return regions == null || regions.isEmpty() ? null : regions.get(0);
    }

    public static boolean isReservedName(String name) {
        String normalized = normalize(name);
        return "all".equals(normalized) || "a".equals(normalized);
    }

    public static void reset() {
        PRESETS.clear();
        ACTIVE_PRESETS.clear();
        HIDDEN_PRESETS.clear();
        ACTIVE_HIDDEN_PRESETS.clear();
        groupEnabled = false;
        hideGroupEnabled = true;
        SelectiveRenderState.setSavedState(List.of(), false, List.of(), false, List.of());
    }

    public static void endSession() {
        sessionOwner = null;
        reset();
    }

    private static void write(MinecraftClient client) {
        write(client, true);
    }

    public static String contextIdentity(MinecraftClient client, ClientWorld world) {
        return ownerFor(client) + "|" + world.getRegistryKey().getValue();
    }

    public static boolean enableHiddenGroupForIsolation(MinecraftClient client) {
        if (ACTIVE_HIDDEN_PRESETS.isEmpty() || hideGroupEnabled) return false;
        hideGroupEnabled = true;
        applyState();
        write(client);
        return true;
    }

    private static void write(MinecraftClient client, boolean backupExisting) {
        Path path = client.world == null ? null : pathFor(client.world);
        if (path == null) return;
        try {
            Files.createDirectories(DIRECTORY);
            StoredConfig stored = new StoredConfig();
            stored.formatVersion = 7;
            stored.activePresets = List.copyOf(ACTIVE_PRESETS);
            stored.hiddenPresets = List.copyOf(HIDDEN_PRESETS);
            stored.activeHiddenPresets = List.copyOf(ACTIVE_HIDDEN_PRESETS);
            stored.enabled = groupEnabled;
            stored.hideEnabled = hideGroupEnabled;
            stored.regionGroups = new LinkedHashMap<>();
            PRESETS.forEach((name, regions) -> stored.regionGroups.put(name,
                    regions.stream().map(StoredRegion::from).toList()));
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            if (backupExisting && Files.isRegularFile(path)) {
                Files.copy(path, ConfigRecovery.backupPath(path),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(stored, writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            SelectiveRenderClient.LOGGER.error("Could not save selective render config {}", path, exception);
        }
    }

    private static void applyState() {
        LinkedHashSet<String> visibleOverrides = new LinkedHashSet<>(HIDDEN_PRESETS);
        if (hideGroupEnabled) visibleOverrides.removeAll(ACTIVE_HIDDEN_PRESETS);
        SelectiveRenderState.setSavedState(
                regionsFor(ACTIVE_PRESETS), groupEnabled,
                regionsFor(ACTIVE_HIDDEN_PRESETS), hideGroupEnabled,
                regionsFor(visibleOverrides));
    }

    private static List<BlockRegion> activeRegions() {
        return regionsFor(ACTIVE_PRESETS);
    }

    private static List<BlockRegion> hiddenRegions() {
        return regionsFor(ACTIVE_HIDDEN_PRESETS);
    }

    private static List<BlockRegion> regionsFor(Iterable<String> names) {
        java.util.ArrayList<BlockRegion> regions = new java.util.ArrayList<>();
        for (String name : names) regions.addAll(PRESETS.getOrDefault(name, List.of()));
        return List.copyOf(regions);
    }

    public enum RenameResult {
        SUCCESS,
        MISSING_SOURCE,
        TARGET_EXISTS,
        RESERVED_NAME
    }

    private static String ownerFor(MinecraftClient client) {
        if (client.getCurrentServerEntry() != null) {
            return "server:" + client.getCurrentServerEntry().address.toLowerCase(Locale.ROOT);
        } else if (client.getServer() != null) {
            Path saveRoot = client.getServer().getSavePath(WorldSavePath.ROOT)
                    .toAbsolutePath().normalize();
            return "singleplayer:" + saveRoot;
        } else {
            return "local:unknown";
        }
    }

    private static StoredConfig readStoredConfig(Path path) {
        if (!Files.isRegularFile(path)) return null;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            StoredConfig stored = GSON.fromJson(reader, StoredConfig.class);
            if (stored == null) {
                SelectiveRenderClient.LOGGER.error("Selective render config {} is empty", path);
            }
            return stored;
        } catch (RuntimeException | IOException exception) {
            SelectiveRenderClient.LOGGER.error("Could not load selective render config {}", path, exception);
            return null;
        }
    }

    private static void applyStoredConfig(StoredConfig stored) {
        if (stored.formatVersion >= 7 && stored.regionGroups != null) {
            stored.regionGroups.forEach((name, regions) -> {
                if (name != null && regions != null && !regions.isEmpty()) {
                    PRESETS.put(normalize(name), regions.stream()
                            .map(region -> region.toRegion(stored.formatVersion)).toList());
                }
            });
        } else if (stored.presets != null) {
            stored.presets.forEach((name, region) -> {
                if (name != null && region != null) {
                    PRESETS.put(normalize(name), List.of(region.toRegion(stored.formatVersion)));
                }
            });
        } else if (stored.minX != null && stored.maxX != null
                && stored.minZ != null && stored.maxZ != null) {
            PRESETS.put("default", List.of(StoredRegion.fromLegacyChunks(
                    stored.minX, stored.maxX, stored.minZ, stored.maxZ)));
        }

        if (stored.formatVersion >= 4 && stored.activePresets != null) {
            stored.activePresets.stream().map(SelectiveRenderConfig::normalize)
                    .filter(PRESETS::containsKey).forEach(ACTIVE_PRESETS::add);
        } else {
            String requestedActive = normalize(stored.activePreset);
            String migratedActive = PRESETS.containsKey(requestedActive)
                    ? requestedActive : PRESETS.keySet().stream().findFirst().orElse(null);
            if (migratedActive != null) ACTIVE_PRESETS.add(migratedActive);
        }
        groupEnabled = stored.enabled;
        if (stored.formatVersion >= 5 && stored.hiddenPresets != null) {
            stored.hiddenPresets.stream().map(SelectiveRenderConfig::normalize)
                    .filter(PRESETS::containsKey).forEach(HIDDEN_PRESETS::add);
        }
        if (stored.formatVersion >= 6 && stored.activeHiddenPresets != null) {
            stored.activeHiddenPresets.stream().map(SelectiveRenderConfig::normalize)
                    .filter(HIDDEN_PRESETS::contains).forEach(ACTIVE_HIDDEN_PRESETS::add);
        } else {
            ACTIVE_HIDDEN_PRESETS.addAll(HIDDEN_PRESETS);
        }
        ACTIVE_PRESETS.removeAll(HIDDEN_PRESETS);
        hideGroupEnabled = stored.formatVersion >= 5 ? stored.hideEnabled : true;
        applyState();
    }

    private static Path pathFor(ClientWorld world) {
        return pathFor(world, sessionOwner);
    }

    private static Path pathFor(ClientWorld world, String owner) {
        String dimension = world.getRegistryKey().getValue().toString();
        return DIRECTORY.resolve(sha256(owner + "|" + dimension) + ".json");
    }

    private static String legacyOwnerFor(MinecraftClient client) {
        return "singleplayer:" + client.getServer().getSaveProperties().getLevelName();
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
        Map<String, List<StoredRegion>> regionGroups;
        int formatVersion;
        String activePreset;
        List<String> activePresets;
        List<String> hiddenPresets;
        List<String> activeHiddenPresets;
        boolean enabled;
        boolean hideEnabled;
        Integer minX;
        Integer maxX;
        Integer minZ;
        Integer maxZ;
    }

    private static final class StoredRegion {
        int minX;
        int maxX;
        Integer minY;
        Integer maxY;
        int minZ;
        int maxZ;

        static StoredRegion from(BlockRegion region) {
            StoredRegion stored = new StoredRegion();
            stored.minX = region.minX();
            stored.maxX = region.maxX();
            stored.minY = region.minY();
            stored.maxY = region.maxY();
            stored.minZ = region.minZ();
            stored.maxZ = region.maxZ();
            return stored;
        }

        BlockRegion toRegion(int formatVersion) {
            if (formatVersion >= 3 && minY != null && maxY != null) {
                return new BlockRegion(minX, maxX, minY, maxY, minZ, maxZ);
            }
            return ConfigMigration.region(minX, maxX, minY, maxY, minZ, maxZ, formatVersion);
        }

        static BlockRegion fromLegacyChunks(int minX, int maxX, int minZ, int maxZ) {
            return ConfigMigration.legacyChunks(minX, maxX, minZ, maxZ);
        }
    }
}
