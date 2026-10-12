package de.selectiverender;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.GsonBuilder;
import de.selectiverender.mixin.ClientWorldNetworkAccess;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;
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
    private static final Map<String, List<BlockRegion>> REGIONS = new LinkedHashMap<>();
    private static final LinkedHashSet<String> ACTIVE_REGIONS = new LinkedHashSet<>();
    private static final LinkedHashSet<String> HIDDEN_REGIONS = new LinkedHashSet<>();
    private static final LinkedHashSet<String> ACTIVE_HIDDEN_REGIONS = new LinkedHashSet<>();
    private static final Map<String, RegionOptions> OPTIONS = new LinkedHashMap<>();
    private static volatile RegionPolicyIndex policyIndex = RegionPolicyIndex.of(Map.of(), Map.of());
    public static RegionOptions optionsAt(int x, int y, int z) { return policyIndex.optionsAt(x, y, z); }
    public static SelectiveRenderSettings.BoundaryMode boundaryOverrideAt(int x, int y, int z) { return policyIndex.boundaryOverrideAt(x, y, z); }
    public static Boolean fluidOverrideAt(int x, int y, int z) { return policyIndex.fluidOverrideAt(x, y, z); }
    public static boolean hasBoundaryMode(SelectiveRenderSettings.BoundaryMode mode) {
        RegionPolicyIndex index = policyIndex;
        return SelectiveRenderSettings.boundaryMode() == mode
                || (SelectiveRenderSettings.boundaryMode() == SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE
                    && (mode == SelectiveRenderSettings.BoundaryMode.COLOR || mode == SelectiveRenderSettings.BoundaryMode.TEXTURE))
                || (mode == SelectiveRenderSettings.BoundaryMode.COLOR && index.blackOverride)
                || (mode == SelectiveRenderSettings.BoundaryMode.TEXTURE && index.textureOverride)
                || (mode == SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE && index.combinedOverride)
                || (mode == SelectiveRenderSettings.BoundaryMode.CULLED && index.culledOverride);
    }

    private static final Map<String, LinkedHashSet<BlockFilterRule>> FILTERS = new LinkedHashMap<>();
    private static boolean groupEnabled;
    private static boolean hideGroupEnabled = true;
    private static volatile RegionIndex allNormalRegionIndex = RegionIndex.empty();
    private static volatile RegionIndex allHiddenRegionIndex = RegionIndex.empty();
    private static String sessionOwner;

    private SelectiveRenderConfig() { }

    public static void beginSession(Minecraft client, ClientLevel world) {
        sessionOwner = ownerFor(client, world);
        load(client, world);
    }

    public static void load(Minecraft client, ClientLevel world) {
        reset();
        if (sessionOwner == null) sessionOwner = ownerFor(client, world);
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
        if (stored == null && client.getSingleplayerServer() != null) {
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

    public static boolean saveSelection(Minecraft client, String requestedName) {
        if (isReservedName(requestedName)) return false;
        String name = normalize(requestedName);
        if (REGIONS.containsKey(name) || !SelectiveRenderState.saveSelection()) return false;
        List<BlockRegion> regions = List.of(SelectiveRenderState.selection());
        REGIONS.put(name, regions);
        if (HIDDEN_REGIONS.contains(name)) {
            ACTIVE_HIDDEN_REGIONS.add(name);
            hideGroupEnabled = true;
        } else {
            ACTIVE_REGIONS.add(name);
            groupEnabled = true;
        }
        applyState();
        write(client);

        return true;
    }

    public static boolean saveRegion(Minecraft client, String requestedName,
                                     BlockRegion region, boolean hidden) {
        if (isReservedName(requestedName) || region == null) return false;
        String name = normalize(requestedName);
        if (REGIONS.containsKey(name)) return false;
        REGIONS.put(name, List.of(region));
        if (hidden) {
            HIDDEN_REGIONS.add(name);
            ACTIVE_HIDDEN_REGIONS.add(name);
            hideGroupEnabled = true;
        } else {
            ACTIVE_REGIONS.add(name);
            groupEnabled = true;
        }
        applyState();
        write(client);
        return true;
    }

    public static boolean redefineRegion(Minecraft client, String requestedName, BlockRegion region) {
        String name = normalize(requestedName);
        if (!REGIONS.containsKey(name) || region == null) return false;
        REGIONS.put(name, List.of(region));
        applyState();
        write(client);
        return true;
    }

    public static boolean saveRegions(Minecraft client, String requestedName,
                                      List<BlockRegion> regions) {
        if (isReservedName(requestedName) || regions == null || regions.isEmpty()) return false;
        String name = normalize(requestedName);
        if (REGIONS.containsKey(name)) return false;
        List<BlockRegion> next = List.copyOf(regions);
        REGIONS.put(name, next);
        HIDDEN_REGIONS.remove(name);
        ACTIVE_HIDDEN_REGIONS.remove(name);
        FILTERS.remove(name);
        OPTIONS.remove(name);
        ACTIVE_REGIONS.add(name);
        groupEnabled = true;
        applyState();
        write(client);
        return true;
    }

    public static boolean toggleCurrent(Minecraft client) {
        if (ACTIVE_REGIONS.isEmpty()) return false;
        groupEnabled = !groupEnabled;
        applyState();

        write(client);
        return true;
    }

    public static boolean toggleRegion(Minecraft client, String requestedName) {
        String name = normalize(requestedName);
        if (!REGIONS.containsKey(name)) return false;
        if (HIDDEN_REGIONS.remove(name)) {
            ACTIVE_HIDDEN_REGIONS.remove(name);
            ACTIVE_REGIONS.add(name);
        } else if (!ACTIVE_REGIONS.remove(name)) {
            ACTIVE_REGIONS.add(name);
        }
        applyState();
        write(client);
        return true;
    }

    public static boolean toggleHiddenRegion(Minecraft client, String requestedName) {
        String name = normalize(requestedName);
        if (!REGIONS.containsKey(name)) return false;
        if (HIDDEN_REGIONS.add(name)) {
            ACTIVE_REGIONS.remove(name);
            ACTIVE_HIDDEN_REGIONS.add(name);
        } else if (!ACTIVE_HIDDEN_REGIONS.remove(name)) {
            ACTIVE_HIDDEN_REGIONS.add(name);
        }
        applyState();
        write(client);
        return true;
    }

    public static boolean toggleHiddenGroup(Minecraft client) {
        if (ACTIVE_HIDDEN_REGIONS.isEmpty()) return false;
        hideGroupEnabled = !hideGroupEnabled;
        applyState();
        write(client);
        return true;
    }

    public static boolean toggleAllRegions(Minecraft client) {
        List<String> normalNames = REGIONS.keySet().stream()
                .filter(name -> !HIDDEN_REGIONS.contains(name)).toList();
        if (normalNames.isEmpty()) return false;
        RegionGroupLogic.toggleAll(ACTIVE_REGIONS, normalNames);
        groupEnabled = true;
        applyState();
        write(client);
        return true;
    }

    public static boolean toggleAllHiddenRegions(Minecraft client) {
        if (HIDDEN_REGIONS.isEmpty()) return false;
        RegionGroupLogic.toggleAll(ACTIVE_HIDDEN_REGIONS, HIDDEN_REGIONS);
        hideGroupEnabled = true;
        applyState();
        write(client);
        return true;
    }

    public static boolean deleteRegion(Minecraft client, String requestedName) {
        String name = normalize(requestedName);
        List<BlockRegion> removed = REGIONS.remove(name);
        if (removed == null) return false;
        ACTIVE_REGIONS.remove(name);
        HIDDEN_REGIONS.remove(name);
        ACTIVE_HIDDEN_REGIONS.remove(name);
        FILTERS.remove(name);
        OPTIONS.remove(name);
        applyState();
        write(client);
        return true;
    }

    public static RenameResult renameRegion(Minecraft client, String requestedOldName,
                                             String requestedNewName) {
        String oldName = normalize(requestedOldName);
        String newName = normalize(requestedNewName);
        if (!REGIONS.containsKey(oldName)) return RenameResult.MISSING_SOURCE;
        if (oldName.equals(newName)) return RenameResult.SUCCESS;
        if (isReservedName(newName)) return RenameResult.RESERVED_NAME;
        if (REGIONS.containsKey(newName)) return RenameResult.TARGET_EXISTS;

        List<BlockRegion> regions = REGIONS.remove(oldName);
        REGIONS.put(newName, regions);
        RegionOptions renamedOptions = OPTIONS.remove(oldName);
        if (renamedOptions != null) OPTIONS.put(newName, renamedOptions);
        LinkedHashSet<BlockFilterRule> rules = FILTERS.remove(oldName);
        if (rules != null) FILTERS.put(newName, rules);
        RegionGroupLogic.replaceMembership(ACTIVE_REGIONS, oldName, newName);
        RegionGroupLogic.replaceMembership(HIDDEN_REGIONS, oldName, newName);
        RegionGroupLogic.replaceMembership(ACTIVE_HIDDEN_REGIONS, oldName, newName);
        applyState();
        write(client);
        return RenameResult.SUCCESS;
    }

    public static boolean isRegionActive(String name) {
        return ACTIVE_REGIONS.contains(normalize(name));
    }

    public static List<String> activeRegionNames() {
        return List.copyOf(ACTIVE_REGIONS);
    }

    public static boolean groupEnabled() {
        return groupEnabled;
    }

    public static boolean isRegionHidden(String name) {
        return HIDDEN_REGIONS.contains(normalize(name));
    }

    public static boolean isHiddenRegionActive(String name) {
        return ACTIVE_HIDDEN_REGIONS.contains(normalize(name));
    }

    public static List<String> hiddenRegionNames() {
        return List.copyOf(ACTIVE_HIDDEN_REGIONS);
    }

    public static boolean hideGroupEnabled() {
        return hideGroupEnabled;
    }

    public static List<String> regionNames() {
        return List.copyOf(REGIONS.keySet());
    }

    public static List<BlockRegion> allRegions() {
        return regionsFor(REGIONS.keySet());
    }

    public static boolean setBlockFilter(Minecraft client, String requestedName, BlockFilterRule rule) {
        return setBlockFilters(client, requestedName, List.of(rule));
    }

    public static boolean setBlockFilters(Minecraft client, String requestedName, List<BlockFilterRule> rules) {
        String name = normalize(requestedName);
        if (!REGIONS.containsKey(name) || rules.isEmpty()) return false;
        if (!FILTERS.computeIfAbsent(name, ignored -> new LinkedHashSet<>()).addAll(rules)) return true;
        applyState();
        write(client);
        return true;
    }

    public static List<BlockRegion> regionBoxes(String name) {
        return List.copyOf(REGIONS.getOrDefault(normalize(name), List.of()));
    }

    public static boolean clearBlockFilters(Minecraft client, String requestedName) {
        String name = normalize(requestedName);
        if (!REGIONS.containsKey(name) || FILTERS.remove(name) == null) return false;
        applyState(); write(client); return true;
    }

    public static void refreshBlockFilters() {
        applyState();
    }

    public static List<BlockFilterRule> blockFilters(String requestedName) {
        return List.copyOf(FILTERS.getOrDefault(normalize(requestedName), new LinkedHashSet<>()));
    }

    public static boolean containsSavedRegion(int x, int y, int z, boolean includeHidden) {
        return allNormalRegionIndex.contains(x, y, z)
                || (includeHidden && allHiddenRegionIndex.contains(x, y, z));
    }

    public static boolean hasSavedRegions() {
        return !REGIONS.isEmpty();
    }

    public static boolean regionExists(String name) {
        return REGIONS.containsKey(normalize(name));
    }

    public static BlockRegion regionRegion(String name) {
        List<BlockRegion> regions = REGIONS.get(normalize(name));
        return regions == null || regions.isEmpty() ? null : regions.get(0);
    }

    public static boolean isReservedName(String name) {
        String normalized = normalize(name);
        return "all".equals(normalized) || "a".equals(normalized);
    }

    public static void reset() {
        REGIONS.clear();
        ACTIVE_REGIONS.clear();
        HIDDEN_REGIONS.clear();
        ACTIVE_HIDDEN_REGIONS.clear();
        FILTERS.clear();
        OPTIONS.clear();
        policyIndex = RegionPolicyIndex.of(Map.of(), Map.of());
        groupEnabled = false;
        hideGroupEnabled = true;
        allNormalRegionIndex = RegionIndex.empty();
        allHiddenRegionIndex = RegionIndex.empty();
        SelectiveRenderState.setSavedState(List.of(), false, List.of(), false, List.of(), List.of());
    }

    public static void endSession() {
        sessionOwner = null;
        reset();
    }

    private static void write(Minecraft client) {
        write(client, true);
    }

    public static String contextIdentity(Minecraft client, ClientLevel world) {
        return ownerFor(client, world) + "|" + world.dimension().identifier();
    }

    public static boolean enableHiddenGroupForIsolation(Minecraft client) {
        if (ACTIVE_HIDDEN_REGIONS.isEmpty() || hideGroupEnabled) return false;
        hideGroupEnabled = true;
        applyState();
        write(client);
        return true;
    }

    private static void write(Minecraft client, boolean backupExisting) {
        if (client.level == null || sessionOwner == null) return;
        String dimension = client.level.dimension().identifier().toString();
        writeStored(editorPath(dimension), snapshotStored(dimension), backupExisting);
    }

    /** Discover only this world's/server's files; empty dimensions are not shown. */
    static List<RegionDocument> editorDocuments(Minecraft client) {
        if (client.level == null || sessionOwner == null) return List.of();
        java.util.Set<String> dimensions = new java.util.TreeSet<>();
        dimensions.add(client.level.dimension().identifier().toString());
        if (client.getConnection() != null) client.getConnection().levels()
                .forEach(key -> dimensions.add(key.identifier().toString()));
        // New metadata also finds saved dimensions no longer announced by the server.
        if (Files.isDirectory(DIRECTORY)) try (var files = Files.list(DIRECTORY)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json")).forEach(path -> {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    var json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("ownerHash") && json.has("dimension")
                            && sha256(sessionOwner).equals(json.get("ownerHash").getAsString())) {
                        String dimension = json.get("dimension").getAsString();
                        if (path.equals(editorPath(dimension))) dimensions.add(dimension);
                    }
                } catch (RuntimeException | IOException ignored) { }
            });
        } catch (IOException exception) {
            SelectiveRenderClient.LOGGER.warn("Could not enumerate saved region dimensions", exception);
        }
        List<RegionDocument> result = new java.util.ArrayList<>();
        for (String dimension : dimensions) {
            RegionDocument document = editorDocument(client, dimension);
            if (document != null && !document.regions().isEmpty()) result.add(document);
        }
        return List.copyOf(result);
    }

    private static Path editorPath(String dimension) {
        return DIRECTORY.resolve(sha256(sessionOwner + "|" + dimension) + ".json");
    }

    private static StoredConfig snapshotStored(String dimension) {
        StoredConfig stored = new StoredConfig();
        stored.formatVersion = 8;
        stored.ownerHash = sha256(sessionOwner);
        stored.dimension = dimension;
        stored.activeRegions = List.copyOf(ACTIVE_REGIONS);
        stored.hiddenRegions = List.copyOf(HIDDEN_REGIONS);
        stored.activeHiddenRegions = List.copyOf(ACTIVE_HIDDEN_REGIONS);
        stored.enabled = groupEnabled;
        stored.hideEnabled = hideGroupEnabled;
        stored.regionGroups = new LinkedHashMap<>();
        REGIONS.forEach((name, boxes) -> stored.regionGroups.put(name, boxes.stream().map(StoredRegion::from).toList()));
        stored.regionOptions = new LinkedHashMap<>(OPTIONS);
        stored.blockFilters = new LinkedHashMap<>();
        FILTERS.forEach((name, rules) -> stored.blockFilters.put(name, rules.stream().map(StoredBlockFilter::from).toList()));
        return stored;
    }

    static RegionDocument editorDocument(Minecraft client, String dimension) {
        if (client.level == null || sessionOwner == null) return null;
        String current = client.level.dimension().identifier().toString();
        StoredConfig stored = current.equals(dimension) ? snapshotStored(dimension)
                : ConfigRecovery.load(editorPath(dimension), SelectiveRenderConfig::readStoredConfig).value();
        return stored == null ? null : toDocument(dimension, stored);
    }

    private static RegionDocument toDocument(String dimension, StoredConfig stored) {
        Map<String, List<BlockRegion>> regions = new LinkedHashMap<>();
        if (stored.formatVersion >= 7 && stored.regionGroups != null) {
            stored.regionGroups.forEach((name, boxes) -> {
                if (!boxes.isEmpty()) regions.put(normalize(name), boxes.stream().map(box -> box.toRegion(stored.formatVersion)).toList());
            });
        } else if (stored.regions != null) {
            stored.regions.forEach((name, box) -> regions.put(normalize(name), List.of(box.toRegion(stored.formatVersion))));
        } else if (stored.minX != null && stored.maxX != null && stored.minZ != null && stored.maxZ != null) {
            regions.put("default", List.of(StoredRegion.fromLegacyChunks(stored.minX, stored.maxX, stored.minZ, stored.maxZ)));
        }
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        if (stored.formatVersion >= 4 && stored.activeRegions != null) {
            stored.activeRegions.stream().map(SelectiveRenderConfig::normalize).filter(regions::containsKey).forEach(selected::add);
        } else {
            String name = normalize(stored.activeRegion);
            if (!regions.containsKey(name)) name = regions.keySet().stream().findFirst().orElse(null);
            if (name != null) selected.add(name);
        }
        LinkedHashSet<String> hidden = new LinkedHashSet<>();
        if (stored.formatVersion >= 5 && stored.hiddenRegions != null) stored.hiddenRegions.stream()
                .map(SelectiveRenderConfig::normalize).filter(regions::containsKey).forEach(hidden::add);
        LinkedHashSet<String> selectedHidden = new LinkedHashSet<>();
        if (stored.formatVersion >= 6 && stored.activeHiddenRegions != null) stored.activeHiddenRegions.stream()
                .map(SelectiveRenderConfig::normalize).filter(hidden::contains).forEach(selectedHidden::add);
        else selectedHidden.addAll(hidden);
        selected.removeAll(hidden);
        Map<String, List<BlockFilterRule>> rules = new LinkedHashMap<>();
        if (stored.formatVersion >= 8 && stored.blockFilters != null) stored.blockFilters.forEach((name, filters) -> {
            if (regions.containsKey(normalize(name))) rules.put(normalize(name), filters.stream().map(StoredBlockFilter::toRule).toList());
        });
        return new RegionDocument(dimension, regions, selected, hidden, selectedHidden, rules,
                stored.enabled, stored.formatVersion >= 5 ? stored.hideEnabled : true,
                stored.regionOptions == null ? Map.of() : stored.regionOptions);
    }

    /** Validate/write first. Only edits to the current dimension may refresh live rendering. */
    static void editRegion(Minecraft client, String dimension, String oldName, String newName,
                           List<BlockRegion> boxes, boolean hidden, boolean selected, List<BlockFilterRule> rules) {
        editRegion(client, dimension, oldName, newName, boxes, hidden, selected, rules, null);
    }
    static void editRegion(Minecraft client, String dimension, String oldName, String newName,
                           List<BlockRegion> boxes, boolean hidden, boolean selected, List<BlockFilterRule> rules,
                           RegionOptions options) {
        RegionDocument current = editorDocument(client, dimension);
        if (current == null) throw new IllegalArgumentException("The region file is no longer available");
        RegionDocument edited = current.edit(oldName, newName, boxes, hidden, selected, rules);
        persistEditorDocument(client, dimension, current, edited, oldName, newName, boxes, options);
    }

    static void deleteEditorRegion(Minecraft client, String dimension, String name) {
        RegionDocument current = editorDocument(client, dimension);
        if (current == null) throw new IllegalArgumentException("The region file is no longer available");
        persistEditorDocument(client, dimension, current, current.remove(name), name, name,
                current.regions().get(name), null);
    }

    private static void persistEditorDocument(Minecraft client, String dimension,
            RegionDocument current, RegionDocument edited, String oldName, String newName,
            List<BlockRegion> boxes, RegionOptions options) {
        StoredConfig stored = new StoredConfig();
        stored.formatVersion = 8;
        stored.ownerHash = sha256(sessionOwner);
        stored.dimension = dimension;
        stored.regionOptions = new LinkedHashMap<>(edited.options());
        if (options != null) stored.regionOptions.put(newName.trim().toLowerCase(java.util.Locale.ROOT), options);
        stored.enabled = edited.rendering();
        stored.hideEnabled = edited.hiding();
        stored.activeRegions = List.copyOf(edited.selected());
        stored.hiddenRegions = List.copyOf(edited.hidden());
        stored.activeHiddenRegions = List.copyOf(edited.selectedHidden());
        stored.regionGroups = new LinkedHashMap<>();
        edited.regions().forEach((name, value) -> stored.regionGroups.put(name, value.stream().map(StoredRegion::from).toList()));
        stored.blockFilters = new LinkedHashMap<>();
        edited.filters().forEach((name, value) -> stored.blockFilters.put(name, value.stream().map(StoredBlockFilter::from).toList()));
        // A recovered backup must not be replaced with a corrupt primary file.
        boolean backupPrimary = readStoredConfig(editorPath(dimension)) != null;
        if (!writeStored(editorPath(dimension), stored, backupPrimary)) throw new IllegalArgumentException("Could not save the region file");
        if (client.level.dimension().identifier().toString().equals(dimension)) {
            VisibilitySnapshot previous = SelectiveRenderState.snapshot();
            reset();
            applyStoredConfig(stored);
            SelectiveRenderState.refreshChange(previous);
            RegionOptions before = current.options().getOrDefault(oldName, RegionOptions.DEFAULT);
            RegionOptions after = stored.regionOptions.getOrDefault(newName.trim().toLowerCase(java.util.Locale.ROOT), RegionOptions.DEFAULT);
            if (SelectiveRenderState.filteringActive() && (before.boundary() != after.boundary()
                    || !java.util.Objects.equals(before.cullFluids(), after.cullFluids()))) {
                SelectiveRenderState.refreshVisibilityRegions(boxes);
            }
        }
    }

    private static boolean writeStored(Path path, StoredConfig stored, boolean backupExisting) {
        try {
            Files.createDirectories(DIRECTORY);
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            if (backupExisting && Files.isRegularFile(path)) Files.copy(path, ConfigRecovery.backupPath(path),
                    StandardCopyOption.REPLACE_EXISTING);
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(stored, writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            SelectiveRenderClient.LOGGER.error("Could not save selective render config {}", path, exception);
            return false;
        }
    }

    private static void applyState() { applyState(true); }

    private static void applyState(boolean refresh) {
        VisibilitySnapshot previous = SelectiveRenderState.snapshot();
        policyIndex = RegionPolicyIndex.of(REGIONS, OPTIONS);
        LinkedHashSet<String> visibleOverrides = new LinkedHashSet<>(HIDDEN_REGIONS);
        if (hideGroupEnabled) visibleOverrides.removeAll(ACTIVE_HIDDEN_REGIONS);
        LinkedHashSet<String> normalRegions = new LinkedHashSet<>(REGIONS.keySet());
        normalRegions.removeAll(HIDDEN_REGIONS);
        allNormalRegionIndex = RegionIndex.of(regionsFor(normalRegions));
        allHiddenRegionIndex = RegionIndex.of(regionsFor(HIDDEN_REGIONS));
        SelectiveRenderState.setSavedState(
                regionsFor(ACTIVE_REGIONS), groupEnabled,
                regionsFor(ACTIVE_HIDDEN_REGIONS), hideGroupEnabled,
                regionsFor(visibleOverrides), filteredRegions());
        if (refresh) SelectiveRenderState.refreshChange(previous);
    }

    private static List<BlockRegion> activeRegions() {
        return regionsFor(ACTIVE_REGIONS);
    }

    private static List<BlockRegion> hiddenRegions() {
        return regionsFor(ACTIVE_HIDDEN_REGIONS);
    }

    private static List<BlockRegion> regionsFor(Iterable<String> names) {
        java.util.ArrayList<BlockRegion> regions = new java.util.ArrayList<>();
        for (String name : names) regions.addAll(REGIONS.getOrDefault(name, List.of()));
        return List.copyOf(regions);
    }

    private static List<FilteredRegion> filteredRegions() {
        if (!SelectiveRenderSettings.blockFiltersEnabled() || FILTERS.isEmpty()) return List.of();
        java.util.ArrayList<FilteredRegion> result = new java.util.ArrayList<>();
        for (Map.Entry<String, LinkedHashSet<BlockFilterRule>> entry : FILTERS.entrySet()) {
            String name = entry.getKey();
            if (entry.getValue().isEmpty() || !OPTIONS.getOrDefault(name, RegionOptions.DEFAULT).filtersActive()) continue;
            for (BlockRegion region : REGIONS.getOrDefault(name, List.of())) {
                result.add(new FilteredRegion(region, entry.getValue()));
            }
        }
        return List.copyOf(result);
    }

    public enum RenameResult {
        SUCCESS,
        MISSING_SOURCE,
        TARGET_EXISTS,
        RESERVED_NAME
    }

    private static String ownerFor(Minecraft client, ClientLevel world) {
        // setWorld runs before client.player exists; resolve the connection from the incoming world.
        var serverInfo = ((ClientWorldNetworkAccess) world).selectiverender$getNetworkHandler().getServerData();
        if (serverInfo != null) {
            return "server:" + serverInfo.ip.toLowerCase(Locale.ROOT);
        } else if (client.getSingleplayerServer() != null) {
            Path saveRoot = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT)
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
            if (stored != null) validateStoredConfig(stored);
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
        FILTERS.clear();
        OPTIONS.clear();
        policyIndex = RegionPolicyIndex.of(Map.of(), Map.of());
        if (stored.formatVersion >= 7 && stored.regionGroups != null) {
            stored.regionGroups.forEach((name, regions) -> {
                if (name != null && regions != null && !regions.isEmpty()) {
                    REGIONS.put(normalize(name), regions.stream()
                            .map(region -> region.toRegion(stored.formatVersion)).toList());
                }
            });
        } else if (stored.regions != null) {
            stored.regions.forEach((name, region) -> {
                if (name != null && region != null) {
                    REGIONS.put(normalize(name), List.of(region.toRegion(stored.formatVersion)));
                }
            });
        } else if (stored.minX != null && stored.maxX != null
                && stored.minZ != null && stored.maxZ != null) {
            REGIONS.put("default", List.of(StoredRegion.fromLegacyChunks(
                    stored.minX, stored.maxX, stored.minZ, stored.maxZ)));
        }
        if (stored.regionOptions != null) stored.regionOptions.forEach((name, options) -> {
            if (name != null && options != null && REGIONS.containsKey(normalize(name))) OPTIONS.put(normalize(name), options);
        });
        if (stored.formatVersion >= 8 && stored.blockFilters != null) {
            stored.blockFilters.forEach((name, rules) -> {
                if (name == null || rules == null || !REGIONS.containsKey(normalize(name))) return;
                LinkedHashSet<BlockFilterRule> parsed = new LinkedHashSet<>();
                for (StoredBlockFilter rule : rules) if (rule != null) parsed.add(rule.toRule());
                if (!parsed.isEmpty()) FILTERS.put(normalize(name), parsed);
            });
        }

        if (stored.formatVersion >= 4 && stored.activeRegions != null) {
            stored.activeRegions.stream().map(SelectiveRenderConfig::normalize)
                    .filter(REGIONS::containsKey).forEach(ACTIVE_REGIONS::add);
        } else {
            String requestedActive = normalize(stored.activeRegion);
            String migratedActive = REGIONS.containsKey(requestedActive)
                    ? requestedActive : REGIONS.keySet().stream().findFirst().orElse(null);
            if (migratedActive != null) ACTIVE_REGIONS.add(migratedActive);
        }
        groupEnabled = stored.enabled;
        if (stored.formatVersion >= 5 && stored.hiddenRegions != null) {
            stored.hiddenRegions.stream().map(SelectiveRenderConfig::normalize)
                    .filter(REGIONS::containsKey).forEach(HIDDEN_REGIONS::add);
        }
        if (stored.formatVersion >= 6 && stored.activeHiddenRegions != null) {
            stored.activeHiddenRegions.stream().map(SelectiveRenderConfig::normalize)
                    .filter(HIDDEN_REGIONS::contains).forEach(ACTIVE_HIDDEN_REGIONS::add);
        } else {
            ACTIVE_HIDDEN_REGIONS.addAll(HIDDEN_REGIONS);
        }
        ACTIVE_REGIONS.removeAll(HIDDEN_REGIONS);
        hideGroupEnabled = stored.formatVersion >= 5 ? stored.hideEnabled : true;
        applyState(false);
    }

    private static void validateStoredConfig(StoredConfig stored) {
        if (stored.regionOptions != null) stored.regionOptions.forEach((name, value) -> {
            if (name == null || value == null) throw new IllegalArgumentException("Invalid region options");
        });
        // Validate before accepting the primary file, so semantic corruption also tries .bak.
        if (stored.formatVersion >= 7 && stored.regionGroups != null) {
            stored.regionGroups.forEach((name, regions) -> {
                if (name == null || regions == null) throw new IllegalArgumentException("Invalid region group");
                for (StoredRegion region : regions) {
                    if (region == null) throw new IllegalArgumentException("Null region");
                    region.toRegion(stored.formatVersion);
                }
            });
        } else if (stored.regions != null) {
            stored.regions.forEach((name, region) -> {
                if (name == null || region == null) throw new IllegalArgumentException("Invalid region");
                region.toRegion(stored.formatVersion);
            });
        } else if (stored.minX != null && stored.maxX != null
                && stored.minZ != null && stored.maxZ != null) {
            StoredRegion.fromLegacyChunks(stored.minX, stored.maxX, stored.minZ, stored.maxZ);
        }
        if (stored.formatVersion >= 8 && stored.blockFilters != null) stored.blockFilters.forEach((name, rules) -> {
            if (name == null || rules == null) throw new IllegalArgumentException("Invalid block filters");
            for (StoredBlockFilter rule : rules) { if (rule == null) throw new IllegalArgumentException("Null block filter"); rule.toRule(); }
        });
    }

    private static Path pathFor(ClientLevel world) {
        return pathFor(world, sessionOwner);
    }

    private static Path pathFor(ClientLevel world, String owner) {
        String dimension = world.dimension().identifier().toString();
        return DIRECTORY.resolve(sha256(owner + "|" + dimension) + ".json");
    }

    private static String legacyOwnerFor(Minecraft client) {
        return "singleplayer:" + client.getSingleplayerServer().getWorldData().getLevelName();
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
        String ownerHash;
        String dimension;
        @SerializedName("presets")
        Map<String, StoredRegion> regions;
        Map<String, List<StoredRegion>> regionGroups;
        Map<String, List<StoredBlockFilter>> blockFilters;
        Map<String, RegionOptions> regionOptions;
        int formatVersion;
        @SerializedName("activePreset")
        String activeRegion;
        @SerializedName("activePresets")
        List<String> activeRegions;
        @SerializedName("hiddenPresets")
        List<String> hiddenRegions;
        @SerializedName("activeHiddenPresets")
        List<String> activeHiddenRegions;
        boolean enabled;
        boolean hideEnabled;
        Integer minX;
        Integer maxX;
        Integer minZ;
        Integer maxZ;
    }

    private static final class StoredBlockFilter {
        String mode; String kind; String value;
        static StoredBlockFilter from(BlockFilterRule rule) {
            StoredBlockFilter stored = new StoredBlockFilter();
            stored.mode = rule.mode().name(); stored.kind = rule.kind().name(); stored.value = rule.value(); return stored;
        }
        BlockFilterRule toRule() {
            return new BlockFilterRule(BlockFilterRule.Mode.valueOf(mode), BlockFilterRule.Kind.valueOf(kind), value);
        }
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
