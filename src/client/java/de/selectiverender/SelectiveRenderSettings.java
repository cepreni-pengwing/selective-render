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
    static final int DEFAULT_FULL_RELOAD_THRESHOLD = 8192;
    static final int MIN_FULL_RELOAD_THRESHOLD = 256;
    static final int MAX_FULL_RELOAD_THRESHOLD = 65536;
    static final int DEFAULT_PLOT_MIN_Y = -100;
    private static final class SettingsFile {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private static final Path PATH = FabricLoader.getInstance().getConfigDir()
                .resolve("selectiverender").resolve("settings.json");
    }
    private static volatile PlayerVisibility playerVisibility = PlayerVisibility.EVERYWHERE;
    private static volatile InteractionMode interactionMode = InteractionMode.EVERYWHERE;
    private static volatile BoundaryMode boundaryMode = BoundaryMode.NORMAL;
    private static volatile boolean debugBoxes;
    private static volatile boolean filterInteractionsWhenInactive;
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
        debugBoxes = stored.debugBoxes;
        filterInteractionsWhenInactive = stored.filterInteractionsWhenInactive;
        fullReloadThreshold = clampReloadThreshold(stored.fullReloadThreshold == 0
                ? DEFAULT_FULL_RELOAD_THRESHOLD : stored.fullReloadThreshold);
        defaultPlotMinY = stored.defaultPlotMinY == null
                ? DEFAULT_PLOT_MIN_Y : stored.defaultPlotMinY;
        if (recovery.recoveredFromBackup()) save(false);
    }

    public static PlayerVisibility playerVisibility() { return playerVisibility; }
    public static InteractionMode interactionMode() { return interactionMode; }
    public static BoundaryMode boundaryMode() { return boundaryMode; }
    public static boolean debugBoxes() { return debugBoxes; }
    public static boolean filterInteractionsWhenInactive() { return filterInteractionsWhenInactive; }
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
        SelectiveRenderState.refreshRenderer();
    }

    public static void setDebugBoxes(boolean value) {
        debugBoxes = value;
        save();
    }

    public static void setFilterInteractionsWhenInactive(boolean value) {
        if (filterInteractionsWhenInactive == value) return;
        filterInteractionsWhenInactive = value;
        save();
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
            stored.playerVisibility = playerVisibility;
            stored.interactionMode = interactionMode;
            stored.boundaryMode = boundaryMode;
            stored.debugBoxes = debugBoxes;
            stored.filterInteractionsWhenInactive = filterInteractionsWhenInactive;
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
        EVERYWHERE("Everywhere");

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

    private static final class StoredSettings {
        PlayerVisibility playerVisibility;
        InteractionMode interactionMode;
        BoundaryMode boundaryMode;
        boolean debugBoxes;
        boolean filterInteractionsWhenInactive;
        int fullReloadThreshold;
        Integer defaultPlotMinY;
    }
}
