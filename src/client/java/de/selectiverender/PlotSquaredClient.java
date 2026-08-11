package de.selectiverender;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class PlotSquaredClient {
    private static final Identifier RESPONSE_CHANNEL = new Identifier("selectiverender", "plot_response");
    private static final int MAGIC = 0x53525031;
    private static final int VERSION = 1;
    private static final int MAX_REGIONS = 256;
    private static final int STATUS_OK = 0;
    private static final int STATUS_NO_PLOT = 1;
    private static final int STATUS_NO_PERMISSION = 2;
    private static final int STATUS_ERROR = 3;
    private static final int STATUS_OFF = 4;
    private static final int STATUS_INFO = 5;
    private static final int STATUS_TOGGLE = 6;
    private static final int STATUS_SAVE = 7;

    private PlotSquaredClient() { }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(RESPONSE_CHANNEL,
                (client, handler, buffer, responseSender) -> receive(client, buffer));
    }

    public static void reset() {
        SelectiveRenderState.resetPlotMode();
    }

    private static void receive(MinecraftClient client, PacketByteBuf buffer) {
        try {
            if (buffer.readInt() != MAGIC || buffer.readInt() != VERSION) return;
            int responseStatus = buffer.readUnsignedByte();
            String responsePlotId = readString(buffer);
            int count = buffer.readInt();
            if (count < 0 || count > MAX_REGIONS) throw new IllegalArgumentException("Invalid region count");

            List<BlockRegion> regions = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                regions.add(new BlockRegion(buffer.readInt(), buffer.readInt(), buffer.readInt(),
                        buffer.readInt(), buffer.readInt(), buffer.readInt()));
            }
            if (buffer.isReadable()) throw new IllegalArgumentException("Trailing response data");
            client.execute(() -> applyResponse(responseStatus, responsePlotId, List.copyOf(regions)));
        } catch (RuntimeException exception) {
            client.execute(() -> send(red("Invalid response from PlotSquared bridge")));
        }
    }

    private static void applyResponse(int status, String responsePlotId, List<BlockRegion> regions) {
        if (status == STATUS_TOGGLE) {
            if (SelectiveRenderState.plotModeActive()) {
                SelectiveRenderState.disablePlotMode();
                send(white("Plot mode "), red("disabled"));
            } else if (regions.isEmpty()) {
                send(red("You are not standing on a plot"));
            } else {
                SelectiveRenderState.activatePlotMode(regions);
                send(white("Plot "), aqua(responsePlotId), green(" isolated"));
            }
        } else if (status == STATUS_SAVE) {
            if (SelectiveRenderConfig.presetExists(responsePlotId)) {
                send(white("Preset "), aqua(responsePlotId), red(" already exists"),
                        white(" · delete or rename it first"));
                return;
            }
            if (regions.isEmpty() || SelectiveRenderConfig.isReservedName(responsePlotId)) {
                send(red("The plot preset could not be saved"));
                return;
            }
            SelectiveRenderState.resetPlotMode();
            if (SelectiveRenderConfig.saveRegions(MinecraftClient.getInstance(), responsePlotId, regions)) {
                send(white("Preset "), aqua(responsePlotId), green(" saved"),
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
        } else if (status == STATUS_OFF) {
            if (SelectiveRenderState.disablePlotMode()) send(white("Plot mode "), red("disabled"));
        } else if (status == STATUS_INFO) {
            boolean active = SelectiveRenderState.plotModeActive();
            send(white("Plot "), aqua(responsePlotId.isEmpty() ? "unknown" : responsePlotId),
                    white(" · " + regions.size() + " region(s) · mode "),
                    active ? (SelectiveRenderState.plotRenderingEnabled() ? green("enabled") : red("disabled"))
                            : red("inactive"));
        } else if (status != STATUS_OK || regions.isEmpty()) {
            send(red("Invalid response from PlotSquared bridge"));
        } else {
            SelectiveRenderState.activatePlotMode(regions);
            send(white("Plot "), aqua(responsePlotId), green(" isolated"),
                    gray(" · " + regions.size() + " region(s)"));
        }
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
        MutableText message = Text.literal("SRP: ").formatted(Formatting.GRAY);
        for (Text part : parts) message.append(part);
        client.player.sendMessage(message, false);
    }

    private static MutableText white(String text) { return Text.literal(text).formatted(Formatting.WHITE); }
    private static MutableText gray(String text) { return Text.literal(text).formatted(Formatting.GRAY); }
    private static MutableText aqua(String text) { return Text.literal(text).formatted(Formatting.AQUA); }
    private static MutableText green(String text) { return Text.literal(text).formatted(Formatting.GREEN); }
    private static MutableText red(String text) { return Text.literal(text).formatted(Formatting.RED); }
}
