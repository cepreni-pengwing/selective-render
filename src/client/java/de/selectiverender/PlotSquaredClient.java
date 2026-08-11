package de.selectiverender;

import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class PlotSquaredClient {
    private static final Identifier REQUEST_CHANNEL = new Identifier("selectiverender", "plot_request");
    private static final Identifier RESPONSE_CHANNEL = new Identifier("selectiverender", "plot_response");
    private static final int MAGIC = 0x53525031;
    private static final int VERSION = 2;
    private static final int MAX_REGIONS = 256;
    private static final int ACTION_TOGGLE = 0;
    private static final int ACTION_SAVE = 1;
    private static final int STATUS_NO_PLOT = 1;
    private static final int STATUS_NO_PERMISSION = 2;
    private static final int STATUS_ERROR = 3;
    private static final int STATUS_TOGGLE = 6;
    private static final int STATUS_SAVE = 7;

    private static long pendingRequest;
    private static long pendingSince;
    private static ClientWorld pendingWorld;

    private PlotSquaredClient() { }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(RESPONSE_CHANNEL,
                (client, handler, buffer, responseSender) -> receive(client, buffer));
    }

    public static void reset() {
        clearPending();
        SelectiveRenderState.resetPlotMode();
    }

    public static int toggle() {
        if (SelectiveRenderState.plotModeActive()) {
            if (SelectiveRenderState.plotRenderingEnabled()) {
                SelectiveRenderState.disablePlotMode();
                send(white("Plot mode "), red("disabled"));
            } else {
                SelectiveRenderState.activatePlotMode(SelectiveRenderState.plotRegions());
                send(white("Plot mode "), green("enabled"));
            }
            return Command.SINGLE_SUCCESS;
        }
        return request(ACTION_TOGGLE, "", 0, 0);
    }

    public static int save(String name, int minY, int maxY) {
        if (SelectiveRenderConfig.isReservedName(name)) {
            send(aqua(name), red(" is reserved"));
            return 0;
        }
        if (SelectiveRenderConfig.presetExists(name)) {
            send(white("Preset "), aqua(name), red(" already exists"),
                    white(" · delete or rename it first"));
            return 0;
        }
        if (minY > maxY) {
            send(red("minY must not be greater than maxY"));
            return 0;
        }
        return request(ACTION_SAVE, name, minY, maxY);
    }

    public static void tick() {
        if (pendingRequest == 0L || Util.getMeasuringTimeMs() - pendingSince < 5000L) return;
        clearPending();
        send(red("Selective Render Plots did not respond"));
    }

    private static int request(int action, String name, int minY, int maxY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || client.getNetworkHandler() == null) return 0;
        if (!ClientPlayNetworking.canSend(REQUEST_CHANNEL)) {
            send(red("Selective Render Plots is unavailable on this server"));
            return 0;
        }
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        if (nameBytes.length > 256) {
            send(red("Preset name is too long"));
            return 0;
        }

        long requestId = ThreadLocalRandom.current().nextLong();
        if (requestId == 0L) requestId = 1L;
        pendingRequest = requestId;
        pendingSince = Util.getMeasuringTimeMs();
        pendingWorld = client.world;

        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeInt(MAGIC);
        buffer.writeInt(VERSION);
        buffer.writeLong(requestId);
        buffer.writeByte(action);
        buffer.writeInt(nameBytes.length);
        buffer.writeBytes(nameBytes);
        buffer.writeInt(minY);
        buffer.writeInt(maxY);
        ClientPlayNetworking.send(REQUEST_CHANNEL, buffer);
        return Command.SINGLE_SUCCESS;
    }

    private static void receive(MinecraftClient client, PacketByteBuf buffer) {
        try {
            if (buffer.readInt() != MAGIC || buffer.readInt() != VERSION) return;
            long requestId = buffer.readLong();
            int responseStatus = buffer.readUnsignedByte();
            String responseName = readString(buffer);
            int count = buffer.readInt();
            if (count < 0 || count > MAX_REGIONS) throw new IllegalArgumentException("Invalid region count");

            List<BlockRegion> regions = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                regions.add(new BlockRegion(buffer.readInt(), buffer.readInt(), buffer.readInt(),
                        buffer.readInt(), buffer.readInt(), buffer.readInt()));
            }
            if (buffer.isReadable()) throw new IllegalArgumentException("Trailing response data");
            ClientWorld responseWorld = pendingWorld;
            client.execute(() -> applyResponse(client, responseWorld, requestId,
                    responseStatus, responseName, List.copyOf(regions)));
        } catch (RuntimeException exception) {
            client.execute(() -> {
                clearPending();
                send(red("Invalid response from Selective Render Plots"));
            });
        }
    }

    private static void applyResponse(MinecraftClient client, ClientWorld responseWorld, long requestId,
                                      int status, String responseName, List<BlockRegion> regions) {
        if (requestId != pendingRequest || responseWorld == null || client.world != responseWorld) return;
        clearPending();
        if (status == STATUS_TOGGLE && !regions.isEmpty()) {
            SelectiveRenderState.activatePlotMode(regions);
            send(white("Plot "), aqua(responseName), green(" isolated"));
        } else if (status == STATUS_SAVE && !regions.isEmpty()) {
            if (SelectiveRenderConfig.presetExists(responseName)) {
                send(white("Preset "), aqua(responseName), red(" already exists"),
                        white(" · delete or rename it first"));
                return;
            }
            SelectiveRenderState.resetPlotMode();
            if (SelectiveRenderConfig.saveRegions(client, responseName, regions)) {
                send(white("Preset "), aqua(responseName), green(" saved"),
                        gray(" · " + regions.size() + " part(s)"));
            } else {
                send(red("The plot preset could not be saved"));
            }
        } else if (status == STATUS_NO_PLOT) {
            send(red("You are not standing on a plot"));
        } else if (status == STATUS_NO_PERMISSION) {
            send(red("The server denied plot access"));
        } else if (status == STATUS_ERROR) {
            send(red("The server could not resolve this plot"));
        } else {
            send(red("Invalid response from Selective Render Plots"));
        }
    }

    private static void clearPending() {
        pendingRequest = 0L;
        pendingSince = 0L;
        pendingWorld = null;
    }

    private static String readString(PacketByteBuf buffer) {
        int length = buffer.readInt();
        if (length < 0 || length > 256 || length > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid string length");
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void send(Text... parts) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        MutableText message = Text.literal("SR: ").formatted(Formatting.GRAY);
        for (Text part : parts) message.append(part);
        client.player.sendMessage(message, false);
    }

    private static MutableText white(String text) { return Text.literal(text).formatted(Formatting.WHITE); }
    private static MutableText gray(String text) { return Text.literal(text).formatted(Formatting.GRAY); }
    private static MutableText aqua(String text) { return Text.literal(text).formatted(Formatting.AQUA); }
    private static MutableText green(String text) { return Text.literal(text).formatted(Formatting.GREEN); }
    private static MutableText red(String text) { return Text.literal(text).formatted(Formatting.RED); }
}
