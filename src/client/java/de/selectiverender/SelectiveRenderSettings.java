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

public final class SelectiveRenderSettings {
    private static final class SettingsFile {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private static final Path PATH = FabricLoader.getInstance().getConfigDir()
                .resolve("selectiverender").resolve("settings.json");
    }
    private static volatile PlayerVisibility playerVisibility = PlayerVisibility.EVERYWHERE;
    private static volatile InteractionMode interactionMode = InteractionMode.EVERYWHERE;
    private static volatile BoundaryMode boundaryMode = BoundaryMode.NORMAL;
    private static volatile boolean debugBoxes;

    private SelectiveRenderSettings() { }

    public static void load() {
        Path path = SettingsFile.PATH;
        if (!Files.isRegularFile(path)) return;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            StoredSettings stored = SettingsFile.GSON.fromJson(reader, StoredSettings.class);
            if (stored == null) return;
            playerVisibility = stored.playerVisibility == null
                    ? PlayerVisibility.EVERYWHERE : stored.playerVisibility;
            interactionMode = stored.interactionMode == null
                    ? InteractionMode.EVERYWHERE : stored.interactionMode;
            boundaryMode = stored.boundaryMode == null ? BoundaryMode.NORMAL : stored.boundaryMode;
            debugBoxes = stored.debugBoxes;
        } catch (IOException | RuntimeException exception) {
            SelectiveRenderClient.LOGGER.error("Could not load selective render settings {}", path, exception);
        }
    }

    public static PlayerVisibility playerVisibility() { return playerVisibility; }
    public static InteractionMode interactionMode() { return interactionMode; }
    public static BoundaryMode boundaryMode() { return boundaryMode; }
    public static boolean debugBoxes() { return debugBoxes; }

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

    private static void save() {
        Path path = SettingsFile.PATH;
        try {
            Files.createDirectories(path.getParent());
            StoredSettings stored = new StoredSettings();
            stored.playerVisibility = playerVisibility;
            stored.interactionMode = interactionMode;
            stored.boundaryMode = boundaryMode;
            stored.debugBoxes = debugBoxes;
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                SettingsFile.GSON.toJson(stored, writer);
            }
        } catch (IOException exception) {
            SelectiveRenderClient.LOGGER.error("Could not save selective render settings {}", path, exception);
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
    }
}
