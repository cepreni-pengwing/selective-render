package de.selectiverender;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;

public final class SelectiveRenderSettings {
    private static final int SETTINGS_FORMAT_VERSION = 4;
    static final int DEFAULT_FULL_RELOAD_THRESHOLD = 8192;
    static final int MIN_FULL_RELOAD_THRESHOLD = 256;
    static final int MAX_FULL_RELOAD_THRESHOLD = 65536;
    static final int DEFAULT_PLOT_MIN_Y = -64;
    private static final class SettingsFile {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private static final Path PATH = FabricLoader.getInstance().getConfigDir()
                .resolve("selectiverender").resolve("settings.json");
    }
    private static volatile PlayerVisibility playerVisibility = PlayerVisibility.EVERYWHERE;
    private static volatile InteractionMode interactionMode = InteractionMode.EVERYWHERE;
    private static volatile BoundaryMode boundaryMode = BoundaryMode.NORMAL;
    private static volatile boolean cullWaterBoundaryFaces;
    private static volatile boolean debugBoxes;
    private static volatile boolean filterInteractionsWhenInactive;
    private static volatile boolean blockFiltersEnabled = true;
    private static volatile boolean interactWithHiddenRegions;
    private static volatile VirtualLightMode virtualLightMode = VirtualLightMode.BOTH;
    private static volatile VirtualLightMode hiddenVirtualLightMode = VirtualLightMode.BOTH;
    private static volatile int fullReloadThreshold = DEFAULT_FULL_RELOAD_THRESHOLD;
    private static volatile int defaultPlotMinY = DEFAULT_PLOT_MIN_Y;

    private SelectiveRenderSettings() { }

    public static void load() {
        Path path = SettingsFile.PATH;
        ConfigRecovery.Result<StoredSettings> recovery = ConfigRecovery.load(path,
                SelectiveRenderSettings::read);
        StoredSettings stored = recovery.value();
        if (stored == null) {
            if (recovery.primaryExisted()) {
                SelectiveRenderClient.LOGGER.error(
                        "Could not load selective render settings or backup for {}", path);
            }
            return;
        }
        playerVisibility = stored.playerVisibility == null
                ? PlayerVisibility.EVERYWHERE : stored.playerVisibility;
        interactionMode = stored.interactionMode == null
                ? InteractionMode.EVERYWHERE : stored.interactionMode;
        boundaryMode = stored.boundaryMode == null ? BoundaryMode.NORMAL : stored.boundaryMode;
        cullWaterBoundaryFaces = stored.cullWaterBoundaryFaces;
        debugBoxes = stored.debugBoxes;
        filterInteractionsWhenInactive = stored.filterInteractionsWhenInactive;
        blockFiltersEnabled = stored.blockFiltersEnabled == null || stored.blockFiltersEnabled;
        interactWithHiddenRegions = stored.interactWithHiddenRegions;
        virtualLightMode = stored.virtualLightMode == null ? VirtualLightMode.BOTH : stored.virtualLightMode;
        hiddenVirtualLightMode = stored.hiddenVirtualLightMode == null
                ? VirtualLightMode.BOTH : stored.hiddenVirtualLightMode;
        fullReloadThreshold = clampReloadThreshold(stored.fullReloadThreshold == 0
                ? DEFAULT_FULL_RELOAD_THRESHOLD : stored.fullReloadThreshold);
        boolean migrateLegacyPlotMinimum = stored.formatVersion < SETTINGS_FORMAT_VERSION
                && stored.defaultPlotMinY != null && stored.defaultPlotMinY == -100;
        defaultPlotMinY = stored.defaultPlotMinY == null || migrateLegacyPlotMinimum
                ? DEFAULT_PLOT_MIN_Y : stored.defaultPlotMinY;
        if (recovery.recoveredFromBackup() || stored.formatVersion < SETTINGS_FORMAT_VERSION) save(false);
    }

    public static PlayerVisibility playerVisibility() { return playerVisibility; }
    public static InteractionMode interactionMode() { return interactionMode; }
    public static BoundaryMode boundaryMode() { return boundaryMode; }
    public static boolean cullWaterBoundaryFaces() { return cullWaterBoundaryFaces; }
    public static boolean debugBoxes() { return debugBoxes; }
    public static boolean filterInteractionsWhenInactive() { return filterInteractionsWhenInactive; }
    public static boolean blockFiltersEnabled() { return blockFiltersEnabled; }
    public static boolean interactWithHiddenRegions() { return interactWithHiddenRegions; }
    public static VirtualLightMode virtualLightMode() { return virtualLightMode; }
    public static VirtualLightMode hiddenVirtualLightMode() { return hiddenVirtualLightMode; }
    public static int fullReloadThreshold() { return fullReloadThreshold; }
    public static int defaultPlotMinY() { return defaultPlotMinY; }

    public static void setPlayerVisibility(PlayerVisibility value) {
        if (playerVisibility == value) return;
        playerVisibility = value;
        save();
        SelectiveRenderState.refreshOptionalVisuals();
    }

    public static void setInteractionMode(InteractionMode value) {
        interactionMode = value;
        save();
    }

    public static void setBoundaryMode(BoundaryMode value) {
        if (boundaryMode == value) return;
        boundaryMode = value;
        save();
        if (SelectiveRenderState.enabled()) {
            SelectiveRenderState.refreshVisibilityRegions(SelectiveRenderState.traversalRegions());
        }
    }

    public static void setCullWaterBoundaryFaces(boolean value) {
        if (cullWaterBoundaryFaces == value) return;
        cullWaterBoundaryFaces = value;
        save();
        if (SelectiveRenderState.filteringActive()) {
            SelectiveRenderState.refreshVisibilityRegions(SelectiveRenderState.traversalRegions());
        }
    }

    public static void setDebugBoxes(boolean value) {
        debugBoxes = value;
        save();
    }

    public static void setFilterInteractionsWhenInactive(boolean value) {
        if (filterInteractionsWhenInactive == value) return;
        filterInteractionsWhenInactive = value;
        save();
        SelectiveRenderConfig.refreshBlockFilters();
        VirtualSkyLightSampler.invalidate();
        if (SelectiveRenderState.filteringActive()) SelectiveRenderState.refreshRenderer();
    }

    public static void setBlockFiltersEnabled(boolean value) {
        if (blockFiltersEnabled == value) return;
        blockFiltersEnabled = value;
        save();
        SelectiveRenderConfig.refreshBlockFilters();
    }

    public static void setInteractWithHiddenRegions(boolean value) {
        if (interactWithHiddenRegions == value) return;
        interactWithHiddenRegions = value;
        save();
    }

    public static void setVirtualLightMode(VirtualLightMode value) {
        if (virtualLightMode == value) return;
        virtualLightMode = value;
        save();
        VirtualSkyLightSampler.invalidate();
        if (SelectiveRenderState.filteringActive()) {
            SelectiveRenderState.refreshRenderer();
        }
    }

    public static void setHiddenVirtualLightMode(VirtualLightMode value) {
        if (hiddenVirtualLightMode == value) return;
        hiddenVirtualLightMode = value;
        save();
        VirtualSkyLightSampler.invalidate();
        if (SelectiveRenderState.filteringActive()) SelectiveRenderState.refreshRenderer();
    }

    public static void setFullReloadThreshold(int value) {
        int next = clampReloadThreshold(value);
        if (fullReloadThreshold == next) return;
        fullReloadThreshold = next;
        save();
    }

    public static void setDefaultPlotMinY(int value) {
        if (defaultPlotMinY == value) return;
        defaultPlotMinY = value;
        save();
    }

    private static int clampReloadThreshold(int value) {
        return Math.max(MIN_FULL_RELOAD_THRESHOLD, Math.min(MAX_FULL_RELOAD_THRESHOLD, value));
    }

    private static void save() {
        save(true);
    }

    private static void save(boolean backupExisting) {
        Path path = SettingsFile.PATH;
        try {
            Files.createDirectories(path.getParent());
            StoredSettings stored = new StoredSettings();
            stored.formatVersion = SETTINGS_FORMAT_VERSION;
            stored.playerVisibility = playerVisibility;
            stored.interactionMode = interactionMode;
            stored.boundaryMode = boundaryMode;
            stored.cullWaterBoundaryFaces = cullWaterBoundaryFaces;
            stored.debugBoxes = debugBoxes;
            stored.filterInteractionsWhenInactive = filterInteractionsWhenInactive;
            stored.blockFiltersEnabled = blockFiltersEnabled;
            stored.interactWithHiddenRegions = interactWithHiddenRegions;
            stored.virtualLightMode = virtualLightMode;
            stored.hiddenVirtualLightMode = hiddenVirtualLightMode;
            stored.fullReloadThreshold = fullReloadThreshold;
            stored.defaultPlotMinY = defaultPlotMinY;
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                SettingsFile.GSON.toJson(stored, writer);
            }
            if (backupExisting && Files.isRegularFile(path)) {
                Files.copy(path, ConfigRecovery.backupPath(path),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporary, path,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            SelectiveRenderClient.LOGGER.error("Could not save selective render settings {}", path, exception);
        }
    }

    private static StoredSettings read(Path path) {
        if (!Files.isRegularFile(path)) return null;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return SettingsFile.GSON.fromJson(reader, StoredSettings.class);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    public enum PlayerVisibility {
        NONE("None"),
        INSIDE("Inside regions"),
        OUTSIDE("Outside regions"),
        EVERYWHERE("Everywhere"),
        OWN_ONLY("Only own player"),
        EXCEPT_OWN("All except own player");

        private final String label;

        PlayerVisibility(String label) { this.label = label; }
        public String label() { return label; }
        public PlayerVisibility next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public enum BoundaryMode {
        NORMAL("Normal"),
        @SerializedName(value = "BLACK", alternate = {"COLORED"})
        BLACK("Black"),
        CULLED("Culled");

        private final String label;

        BoundaryMode(String label) { this.label = label; }
        public String label() { return label; }
        public BoundaryMode next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public enum InteractionMode {
        NONE("None"),
        INSIDE("Inside regions"),
        OUTSIDE("Outside regions"),
        EVERYWHERE("Everywhere");

        private final String label;

        InteractionMode(String label) { this.label = label; }
        public String label() { return label; }
        public InteractionMode next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public enum VirtualLightMode {
        BOTH("Top and sides", true, true),
        TOP("Top only", true, false),
        SIDES("Sides only", false, true),
        NONE("None", false, false);

        private final String label;
        private final boolean top;
        private final boolean sides;

        VirtualLightMode(String label, boolean top, boolean sides) {
            this.label = label;
            this.top = top;
            this.sides = sides;
        }

        public String label() { return label; }
        public boolean seedsColumn(boolean visibleColumn) {
            return visibleColumn ? top : sides;
        }
        public boolean allowsPropagation(boolean fromVisible, boolean toVisible) {
            if (this == BOTH) return true;
            if (this == TOP) return fromVisible && toVisible;
            if (this == SIDES) return !fromVisible || toVisible;
            return false;
        }
        public VirtualLightMode next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private static final class StoredSettings {
        int formatVersion;
        PlayerVisibility playerVisibility;
        InteractionMode interactionMode;
        BoundaryMode boundaryMode;
        boolean cullWaterBoundaryFaces;
        boolean debugBoxes;
        boolean filterInteractionsWhenInactive;
        Boolean blockFiltersEnabled;
        boolean interactWithHiddenRegions;
        VirtualLightMode virtualLightMode;
        VirtualLightMode hiddenVirtualLightMode;
        int fullReloadThreshold;
        Integer defaultPlotMinY;
    }
}
