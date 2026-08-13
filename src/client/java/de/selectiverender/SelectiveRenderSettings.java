package de.selectiverender;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SelectiveRenderSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("selectiverender").resolve("settings.json");
    private static volatile PlayerVisibility playerVisibility = PlayerVisibility.EVERYWHERE;
    private static volatile BorderMode borderMode = BorderMode.OFF;
    private static volatile int borderRed = 0;
    private static volatile int borderGreen = 255;
    private static volatile int borderBlue = 255;

    private SelectiveRenderSettings() { }

    public static void load() {
        if (!Files.isRegularFile(PATH)) return;
        try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
            StoredSettings stored = GSON.fromJson(reader, StoredSettings.class);
            if (stored == null) return;
            playerVisibility = stored.playerVisibility == null
                    ? PlayerVisibility.EVERYWHERE : stored.playerVisibility;
            borderMode = stored.borderMode == null ? BorderMode.OFF : stored.borderMode;
            borderRed = clamp(stored.borderRed);
            borderGreen = clamp(stored.borderGreen);
            borderBlue = clamp(stored.borderBlue);
        } catch (IOException | RuntimeException exception) {
            SelectiveRenderClient.LOGGER.error("Could not load selective render settings {}", PATH, exception);
        }
    }

    public static PlayerVisibility playerVisibility() { return playerVisibility; }
    public static BorderMode borderMode() { return borderMode; }
    public static int borderRed() { return borderRed; }
    public static int borderGreen() { return borderGreen; }
    public static int borderBlue() { return borderBlue; }

    public static void setPlayerVisibility(PlayerVisibility value) {
        playerVisibility = value;
        save();
    }

    public static void setBorderMode(BorderMode value) {
        borderMode = value;
        save();
    }

    public static void setBorderColor(int red, int green, int blue) {
        borderRed = clamp(red);
        borderGreen = clamp(green);
        borderBlue = clamp(blue);
        save();
    }

    private static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            StoredSettings stored = new StoredSettings();
            stored.playerVisibility = playerVisibility;
            stored.borderMode = borderMode;
            stored.borderRed = borderRed;
            stored.borderGreen = borderGreen;
            stored.borderBlue = borderBlue;
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(stored, writer);
            }
        } catch (IOException exception) {
            SelectiveRenderClient.LOGGER.error("Could not save selective render settings {}", PATH, exception);
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
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

    public enum BorderMode {
        OFF("Off"),
        NORMAL("Normal"),
        SEE_THROUGH("See-through");

        private final String label;

        BorderMode(String label) { this.label = label; }
        public String label() { return label; }
        public BorderMode next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private static final class StoredSettings {
        PlayerVisibility playerVisibility;
        BorderMode borderMode;
        int borderRed;
        int borderGreen = 255;
        int borderBlue = 255;
    }
}
