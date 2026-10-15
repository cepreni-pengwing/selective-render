package de.selectiverender;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
            if (helloDelay > 0 && client.level != null && client.getConnection() != null && --helloDelay == 0 && ready) {
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
        Minecraft.getInstance().execute(() -> {
            if (receivedGeneration == generation && currentWorld != null) SELECTION.accept(text);
        });
    }

    public static BlockRegion selection() { return SELECTION.region(); }
    public static String problem() { return SELECTION.problem(); }

    private static final Identifier CHANNEL = Identifier.fromNamespaceAndPath("worldedit", "cui");
    private static final CustomPacketPayload.Type<CustomPacketPayload> TYPE = new CustomPacketPayload.Type<>(CHANNEL);
    private static CustomPacketPayload handshake;

    private record TextPayload(String text) implements CustomPacketPayload {
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload> CODEC = new StreamCodec<>() {
        @Override public CustomPacketPayload decode(RegistryFriendlyByteBuf buffer) {
            String text = buffer.readableBytes() <= 4096 ? buffer.toString(StandardCharsets.UTF_8) : null;
            buffer.skipBytes(buffer.readableBytes());
            return new TextPayload(text);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, CustomPacketPayload packet) {
            buffer.writeBytes(((TextPayload) packet).text().getBytes(StandardCharsets.UTF_8));
        }
    };

    private static void register() throws ReflectiveOperationException {
        Object existing = WorldEditCuiBridge.hello();
        handshake = existing == null ? new TextPayload("v|3") : (CustomPacketPayload) existing;
        if (WorldEditCuiBridge.subscribe(WorldEditClient::receive)) {
            ready = true;
            return;
        }
        // WorldEdit may already own these codecs (even without WorldEditCUI installed).
        if (existing == null) {
            PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
            PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
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
