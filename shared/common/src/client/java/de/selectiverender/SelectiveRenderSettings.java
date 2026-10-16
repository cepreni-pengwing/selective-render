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
    private static final int SETTINGS_FORMAT_VERSION = 5;
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
    private static volatile PlayerRenderPolicy.Group playerGroup = PlayerRenderPolicy.Group.EVERYONE;
    private static volatile PlayerRenderPolicy.Scope playerScope = PlayerRenderPolicy.Scope.EVERYWHERE;
    private static volatile InteractionMode interactionMode = InteractionMode.INSIDE;
    private static volatile BoundaryMode boundaryMode = BoundaryMode.NORMAL;
    private static volatile int boundaryColor;
    private static volatile int boundaryOpacity = 100;
    private static volatile boolean boundaryColorLighting;
    private static volatile boolean boundaryNormal = true;
    private static volatile boolean boundaryRainbow;
    private static volatile String boundaryTexture = "minecraft:stone";
    private static volatile boolean cullFluidBoundaryFaces;
    private static volatile boolean worldEditSelection = true;
    private static volatile boolean debugBoxes;
    private static volatile boolean filterInteractionsWhenInactive;
    private static volatile boolean blockFiltersEnabled = true;
    private static volatile boolean interactWithHiddenRegions;
    private static volatile VirtualLightMode virtualLightMode = VirtualLightMode.BOTH;
    private static volatile int fullReloadThreshold = DEFAULT_FULL_RELOAD_THRESHOLD;
    private static volatile int defaultPlotMaxY = 319;
    private static volatile int defaultPlotMinY = DEFAULT_PLOT_MIN_Y;

    private static java.util.Map<String, PlotGrid> plotGrids = new java.util.HashMap<>();
    static PlotGrid plotGrid(String context) { return plotGrids.get(context); }
    static void setPlotGrid(String context, PlotGrid grid) {
        if (grid == null) plotGrids.remove(context); else plotGrids.put(context, grid);
        save();
    }

    private static MenuNavigationState menuNavigation = new MenuNavigationState();
    static MenuNavigationState menuNavigation() { return menuNavigation; }
    static void saveMenuNavigation() { save(); }

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
        plotGrids = stored.plotGrids == null ? new java.util.HashMap<>() : new java.util.HashMap<>(stored.plotGrids);
        plotGrids.values().removeIf(java.util.Objects::isNull);
        menuNavigation = stored.menuNavigation == null ? new MenuNavigationState() : stored.menuNavigation;
        menuNavigation.normalize();
        playerVisibility = stored.playerVisibility == null
                ? PlayerVisibility.EVERYWHERE : stored.playerVisibility;
        var migratedPlayers = PlayerRenderPolicy.migrate(playerVisibility);
        playerGroup = stored.playerGroup == null ? migratedPlayers.group() : stored.playerGroup;
        playerScope = stored.playerScope == null ? migratedPlayers.scope() : stored.playerScope;
        interactionMode = stored.interactionMode == null
                ? InteractionMode.INSIDE : stored.interactionMode;
        boundaryMode = stored.boundaryMode == null ? BoundaryMode.NORMAL : stored.boundaryMode;
        boundaryColor = stored.boundaryColor == null ? 0 : stored.boundaryColor & 0xFFFFFF;
        boundaryColorLighting = stored.boundaryColorLighting;
        boundaryNormal = stored.boundaryNormal == null || stored.boundaryNormal;
        boundaryRainbow = stored.boundaryRainbow;
        boundaryOpacity = stored.boundaryOpacity == null ? 100 : BoundaryStyle.opacity(stored.boundaryOpacity);
        boundaryTexture = stored.boundaryTexture == null ? "minecraft:stone" : stored.boundaryTexture;
        if (boundaryMode == BoundaryMode.CULLED) { boundaryMode = BoundaryMode.COLOR; boundaryOpacity = 0; boundaryNormal=false; }
        BoundaryColorTexture.invalidate();
        cullFluidBoundaryFaces = stored.cullFluidBoundaryFaces;
        worldEditSelection = stored.worldEditSelection == null || stored.worldEditSelection;
        debugBoxes = stored.debugBoxes;
        filterInteractionsWhenInactive = stored.filterInteractionsWhenInactive;
        blockFiltersEnabled = stored.blockFiltersEnabled == null || stored.blockFiltersEnabled;
        interactWithHiddenRegions = stored.interactWithHiddenRegions;
        virtualLightMode = commonVirtualLightMode(stored.virtualLightMode, stored.hiddenVirtualLightMode);
        fullReloadThreshold = clampReloadThreshold(stored.fullReloadThreshold == 0
                ? DEFAULT_FULL_RELOAD_THRESHOLD : stored.fullReloadThreshold);
        boolean migrateLegacyPlotMinimum = stored.formatVersion < 4
                && stored.defaultPlotMinY != null && stored.defaultPlotMinY == -100;
        defaultPlotMaxY = stored.defaultPlotMaxY == null ? 319 : stored.defaultPlotMaxY;
        defaultPlotMinY = stored.defaultPlotMinY == null || migrateLegacyPlotMinimum
                ? DEFAULT_PLOT_MIN_Y : stored.defaultPlotMinY;
        if (recovery.recoveredFromBackup() || stored.formatVersion < SETTINGS_FORMAT_VERSION) save(false);
    }

    public static PlayerRenderPolicy.Group playerGroup() { return playerGroup; }
    public static PlayerRenderPolicy.Scope playerScope() { return playerScope; }
    public static void setPlayerGroup(PlayerRenderPolicy.Group value) { playerGroup = value; save(); SelectiveRenderState.refreshOptionalVisuals(); }
    public static void setPlayerScope(PlayerRenderPolicy.Scope value) { playerScope = value; save(); SelectiveRenderState.refreshOptionalVisuals(); }
    public static PlayerVisibility playerVisibility() {
        if(playerGroup==PlayerRenderPolicy.Group.NOBODY || playerScope==PlayerRenderPolicy.Scope.NOWHERE) return PlayerVisibility.NONE;
        if(playerScope==PlayerRenderPolicy.Scope.INSIDE) return PlayerVisibility.INSIDE;
        if(playerScope==PlayerRenderPolicy.Scope.OUTSIDE) return PlayerVisibility.OUTSIDE;
        return switch(playerGroup) { case SELF->PlayerVisibility.OWN_ONLY; case OTHERS->PlayerVisibility.EXCEPT_OWN; default->PlayerVisibility.EVERYWHERE; };
    }
    public static InteractionMode interactionMode() { return interactionMode; }
    public static BoundaryMode boundaryMode() { return boundaryMode == BoundaryMode.NORMAL && !boundaryNormal ? BoundaryMode.CULLED : boundaryMode; }
    public static int boundaryColor() { return boundaryColor; }
    public static boolean boundaryRainbow() { return boundaryRainbow; }
    public static void setBoundaryRainbow(boolean value) {
        if (boundaryRainbow == value) return;
        boundaryRainbow = value; BoundaryColorTexture.invalidate(); BoundaryColorTexture.prepare(); save();
        if (SelectiveRenderState.filteringActive()) SelectiveRenderState.refreshVisibilityRegions(SelectiveRenderState.traversalRegions());
    }
    public static boolean boundaryNormal() { return boundaryNormal; }
    public static void setBoundaryNormal(boolean value) {
        if(boundaryNormal==value) return;
        boundaryNormal=value; save();
        if(SelectiveRenderState.filteringActive()) SelectiveRenderState.refreshVisibilityRegions(SelectiveRenderState.traversalRegions());
    }
    public static boolean boundaryColorLighting() { return boundaryColorLighting; }
    public static void setBoundaryColorLighting(boolean value) {
        if (boundaryColorLighting == value) return;
        boundaryColorLighting = value;
        save();
        if (SelectiveRenderState.filteringActive()) SelectiveRenderState.refreshVisibilityRegions(SelectiveRenderState.traversalRegions());
    }
    public static int boundaryOpacity() { return boundaryOpacity; }
    public static String boundaryTexture() { return boundaryTexture; }
    public static void setBoundaryAppearance(int color, String texture, int opacity) {
        if (boundaryColor == (color & 0xFFFFFF) && boundaryTexture.equals(texture)
                && boundaryOpacity == BoundaryStyle.opacity(opacity)) return;
        boundaryColor = color & 0xFFFFFF;
        boundaryTexture = texture;
        boundaryOpacity = BoundaryStyle.opacity(opacity);
        BoundaryColorTexture.invalidate();
        BoundaryColorTexture.prepare();
        save();
        if (SelectiveRenderState.filteringActive()) {
            SelectiveRenderState.refreshVisibilityRegions(SelectiveRenderState.traversalRegions());
        }
    }
    public static boolean worldEditSelection() { return worldEditSelection; }

    public static void setWorldEditSelection(boolean value) {
        if (worldEditSelection == value) return;
        worldEditSelection = value;
        save();
    }

    public static boolean cullFluidBoundaryFaces() { return cullFluidBoundaryFaces; }
    public static boolean debugBoxes() { return debugBoxes; }
    public static boolean filterInteractionsWhenInactive() { return filterInteractionsWhenInactive; }
    public static boolean blockFiltersEnabled() { return blockFiltersEnabled; }
    public static boolean interactWithHiddenRegions() { return interactWithHiddenRegions; }
    public static VirtualLightMode virtualLightMode() { return virtualLightMode; }
    public static VirtualLightMode hiddenVirtualLightMode() { return virtualLightMode; }
    public static int fullReloadThreshold() { return fullReloadThreshold; }
    public static int defaultPlotMaxY() { return defaultPlotMaxY; }
    public static int defaultPlotMinY() { return defaultPlotMinY; }

    public static void setPlayerVisibility(PlayerVisibility value) {
        var migrated = PlayerRenderPolicy.migrate(value);
        if (playerGroup == migrated.group() && playerScope == migrated.scope()) return;
        playerVisibility = value; playerGroup = migrated.group(); playerScope = migrated.scope();
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
        BoundaryColorTexture.prepare();
        save();
        if (SelectiveRenderState.filteringActive()) {
            SelectiveRenderState.refreshVisibilityRegions(SelectiveRenderState.traversalRegions());
        }
    }

    public static void setCullFluidBoundaryFaces(boolean value) {
        if (cullFluidBoundaryFaces == value) return;
        cullFluidBoundaryFaces = value;
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

    static VirtualLightMode commonVirtualLightMode(VirtualLightMode render, VirtualLightMode hidden) {
        return render != null ? render : hidden != null ? hidden : VirtualLightMode.BOTH;
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

    /** Compatibility entry point: all region types now share the same lighting policy. */
    public static void setHiddenVirtualLightMode(VirtualLightMode value) { setVirtualLightMode(value); }

    public static void setFullReloadThreshold(int value) {
        int next = clampReloadThreshold(value);
        if (fullReloadThreshold == next) return;
        fullReloadThreshold = next;
        save();
    }

    public static void setDefaultPlotMaxY(int value) {
        if (defaultPlotMaxY == value) return;
        defaultPlotMaxY = value;
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
            stored.menuNavigation = menuNavigation;
            stored.plotGrids = plotGrids;
            stored.playerVisibility = playerVisibility(); // Legacy readers cannot represent both dimensions simultaneously.
            stored.playerGroup = playerGroup; stored.playerScope = playerScope;
            stored.interactionMode = interactionMode;
            stored.boundaryMode = boundaryMode;
            stored.boundaryColor = boundaryColor;
            stored.boundaryOpacity = boundaryOpacity;
            stored.boundaryColorLighting = boundaryColorLighting;
            stored.boundaryNormal = boundaryNormal;
            stored.boundaryRainbow = boundaryRainbow;
            stored.boundaryTexture = boundaryTexture;
            stored.cullFluidBoundaryFaces = cullFluidBoundaryFaces;
            stored.worldEditSelection = worldEditSelection;
            stored.debugBoxes = debugBoxes;
            stored.filterInteractionsWhenInactive = filterInteractionsWhenInactive;
            stored.blockFiltersEnabled = blockFiltersEnabled;
            stored.interactWithHiddenRegions = interactWithHiddenRegions;
            stored.virtualLightMode = virtualLightMode;
            stored.hiddenVirtualLightMode = virtualLightMode; // Retain downgrade compatibility.
            stored.fullReloadThreshold = fullReloadThreshold;
            stored.defaultPlotMaxY = defaultPlotMaxY;
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
            SelectiveRenderClient.LOGGER.warn("Could not read selective render settings {}", path, exception);
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
        @SerializedName(value = "COLOR", alternate = {"BLACK", "COLORED"})
        COLOR("Color"),
        TEXTURE("Texture"),
        COLOR_TEXTURE("Color + texture"),
        CULLED("Culled"); // Legacy saved data only; no longer offered by the menu.

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
        java.util.Map<String, PlotGrid> plotGrids;
        MenuNavigationState menuNavigation;
        PlayerVisibility playerVisibility;
        PlayerRenderPolicy.Group playerGroup;
        PlayerRenderPolicy.Scope playerScope;
        InteractionMode interactionMode;
        BoundaryMode boundaryMode;
        Integer boundaryColor;
        Integer boundaryOpacity;
        boolean boundaryColorLighting;
        Boolean boundaryNormal;
        boolean boundaryRainbow;
        String boundaryTexture;
        @SerializedName(value = "cullFluidBoundaryFaces", alternate = {"cullWaterBoundaryFaces"})
        boolean cullFluidBoundaryFaces;
        Boolean worldEditSelection;
        boolean debugBoxes;
        boolean filterInteractionsWhenInactive;
        Boolean blockFiltersEnabled;
        boolean interactWithHiddenRegions;
        VirtualLightMode virtualLightMode;
        VirtualLightMode hiddenVirtualLightMode;
        int fullReloadThreshold;
        Integer defaultPlotMinY;
        Integer defaultPlotMaxY;
    }
}
