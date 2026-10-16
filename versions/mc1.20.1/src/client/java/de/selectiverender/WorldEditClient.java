package de.selectiverender;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

/** Passive client-only CUI mirror. No commands, permissions or server world state are changed. */
public final class WorldEditClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("selectiverender/worldedit");
    private static final WorldEditSelection SELECTION = new WorldEditSelection();
    private static final boolean HAS_CUI = FabricLoader.getInstance().isModLoaded("worldeditcui");
    private static boolean ready;
    private static volatile long generation;
    private static Object currentWorld;
    private static int helloDelay;

    private WorldEditClient() { }

    public static void initialize() {
        // All common/client entrypoints must finish before checking for an existing channel codec.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            if (HAS_CUI) return; // The optional dispatcher mixin observes its existing receiver.
            try {
                register();
            } catch (RuntimeException | LinkageError error) {
                LOGGER.warn("WorldEdit selection synchronization unavailable", error);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (helloDelay > 0 && client.world != null && client.getNetworkHandler() != null && --helloDelay == 0 && ready) {
                try {
                    hello();
                } catch (RuntimeException error) {
                    LOGGER.warn("Could not initialize WorldEdit selection synchronization", error);
                }
            }
        });
    }

    public static void worldChanged(Object world) {
        if (world == currentWorld) return;
        currentWorld = world;
        generation++;
        SELECTION.clear();
        helloDelay = world != null && !HAS_CUI ? 10 : 0;
    }

    public static void receive(String text) {
        long receivedGeneration = generation;
        MinecraftClient.getInstance().execute(() -> {
            if (receivedGeneration == generation && currentWorld != null) SELECTION.accept(text);
        });
    }

    public static BlockRegion selection() { return SELECTION.region(); }
    public static String problem() { return SELECTION.problem(); }

    private static final Identifier CHANNEL = new Identifier("worldedit", "cui");

    private static void register() {
        ready = ClientPlayNetworking.registerGlobalReceiver(CHANNEL, (client, handler, buffer, sender) -> {
            String text = buffer.readableBytes() <= 4096 ? buffer.toString(StandardCharsets.UTF_8) : null;
            receive(text);
        });
    }

    private static void hello() {
        // Older Bukkit servers do not advertise their incoming CUI channel.
        var buffer = PacketByteBufs.create();
        buffer.writeBytes("v|3".getBytes(StandardCharsets.UTF_8));
        ClientPlayNetworking.send(CHANNEL, buffer);
    }
}
