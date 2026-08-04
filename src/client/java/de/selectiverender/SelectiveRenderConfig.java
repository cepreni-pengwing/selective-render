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

public final class SelectiveRenderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("selectiverender");

    private SelectiveRenderConfig() { }

    public static void load(MinecraftClient client) {
        Path path = pathFor(client);
        if (path == null || !Files.isRegularFile(path)) {
            SelectiveRenderState.setSavedState(null, false);
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            StoredRegion stored = GSON.fromJson(reader, StoredRegion.class);
            if (stored == null) {
                SelectiveRenderState.setSavedState(null, false);
            } else {
                SelectiveRenderState.setSavedState(
                        new ChunkRegion(stored.minX, stored.maxX, stored.minZ, stored.maxZ), stored.enabled);
            }
        } catch (RuntimeException | IOException exception) {
            SelectiveRenderClient.LOGGER.error("Could not load selective render config {}", path, exception);
            SelectiveRenderState.setSavedState(null, false);
        }
    }

    public static void save(MinecraftClient client) {
        ChunkRegion region = SelectiveRenderState.region();
        Path path = pathFor(client);
        if (region == null || path == null) return;
        try {
            Files.createDirectories(DIRECTORY);
            StoredRegion stored = new StoredRegion();
            stored.minX = region.minX();
            stored.maxX = region.maxX();
            stored.minZ = region.minZ();
            stored.maxZ = region.maxZ();
            stored.enabled = SelectiveRenderState.enabled();
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
            owner = "server:" + client.getCurrentServerEntry().address.toLowerCase();
        } else if (client.getServer() != null) {
            owner = "singleplayer:" + client.getServer().getSaveProperties().getLevelName();
        } else {
            owner = "local:unknown";
        }
        String dimension = client.world.getRegistryKey().getValue().toString();
        return DIRECTORY.resolve(sha256(owner + "|" + dimension) + ".json");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class StoredRegion {
        int minX;
        int maxX;
        int minZ;
        int maxZ;
        boolean enabled;
    }
}
