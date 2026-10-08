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
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

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
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
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

    private static final Identifier CHANNEL = Identifier.of("worldedit", "cui");
    private static final CustomPayload.Id<CustomPayload> TYPE = new CustomPayload.Id<>(CHANNEL);
    private static CustomPayload handshake;

    private record TextPayload(String text) implements CustomPayload {
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return TYPE; }
    }

    private static final PacketCodec<RegistryByteBuf, CustomPayload> CODEC = new PacketCodec<>() {
        @Override public CustomPayload decode(RegistryByteBuf buffer) {
            String text = buffer.readableBytes() <= 4096 ? buffer.toString(StandardCharsets.UTF_8) : null;
            buffer.skipBytes(buffer.readableBytes());
            return new TextPayload(text);
        }
        @Override public void encode(RegistryByteBuf buffer, CustomPayload packet) {
            buffer.writeBytes(((TextPayload) packet).text().getBytes(StandardCharsets.UTF_8));
        }
    };

    private static void register() throws ReflectiveOperationException {
        Object existing = WorldEditCuiBridge.hello();
        handshake = existing == null ? new TextPayload("v|3") : (CustomPayload) existing;
        if (WorldEditCuiBridge.subscribe(WorldEditClient::receive)) {
            ready = true;
            return;
        }
        // WorldEdit may already own these codecs (even without WorldEditCUI installed).
        if (existing == null) {
            PayloadTypeRegistry.playC2S().register(TYPE, CODEC);
            PayloadTypeRegistry.playS2C().register(TYPE, CODEC);
        }
        ready = ClientPlayNetworking.registerGlobalReceiver(TYPE, (packet, context) -> {
            try {
                receive(packet instanceof TextPayload text ? text.text() : WorldEditCuiBridge.packetText(packet));
            } catch (ReflectiveOperationException | RuntimeException error) {
                receive(null);
            }
        });
    }

    private static void hello() {
        ClientPlayNetworking.send(handshake);
    }
}
