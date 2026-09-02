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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class PlotSquaredClient {
    static final int DEFAULT_MIN_Y = -100;
    static final int DEFAULT_MAX_Y = 400;
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
    private static Integer pendingMinY;
    private static Integer pendingMaxY;
    private static int pendingXzMargin;
    private static final Map<String, PlotSession> SESSIONS = new LinkedHashMap<>();
    private static String activeContext;

    private PlotSquaredClient() { }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(RESPONSE_CHANNEL,
                (client, handler, buffer, responseSender) -> receive(client, buffer));
    }

    public static void leaveWorld() {
        clearPending();
        PlotSession session = activeContext == null ? null : SESSIONS.get(activeContext);
        if (session != null && SelectiveRenderState.plotModeActive()) {
            session.renderingEnabled = SelectiveRenderState.plotRenderingEnabled();
        }
        activeContext = null;
        SelectiveRenderState.resetPlotMode();
    }

    public static void enterWorld(MinecraftClient client, ClientWorld world) {
        activeContext = SelectiveRenderConfig.contextIdentity(client, world);
        PlotSession session = SESSIONS.get(activeContext);
        if (session == null || session.plots.isEmpty()) return;
        if (session.renderingEnabled) SelectiveRenderConfig.enableHiddenGroupForIsolation(client);
        SelectiveRenderState.activatePlotMode(session.regions(), session.renderingEnabled);
    }

    public static int toggle() {
        return toggle(DEFAULT_MIN_Y, DEFAULT_MAX_Y, 0);
    }

    public static int toggle(int minY, int maxY, int xzMargin) {
        if (minY > maxY) {
            send(red("minY must not be greater than maxY"));
            return 0;
        }
        return toggleWithOptions(minY, maxY, xzMargin);
    }

    private static int toggleWithOptions(Integer minY, Integer maxY, int xzMargin) {
        return request(ACTION_TOGGLE, "", minY == null ? 0 : minY,
                maxY == null ? 0 : maxY, minY, maxY, xzMargin);
    }

    public static int clear() {
        PlotSession session = currentSession(false);
        if (session == null || session.plots.isEmpty()) {
            overlay(white("No temporary plots to clear"));
            return 0;
        }
        session.plots.clear();
        if (activeContext != null) SESSIONS.remove(activeContext);
        SelectiveRenderState.disablePlotMode();
        overlay(white("Temporary plots "), red("cleared"));
        return Command.SINGLE_SUCCESS;
    }

    public static int save(String name, int minY, int maxY, int xzMargin) {
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
        return request(ACTION_SAVE, name, minY, maxY, minY, maxY, xzMargin);
    }

    public static void tick() {
        if (pendingRequest == 0L || Util.getMeasuringTimeMs() - pendingSince < 5000L) return;
        clearPending();
        send(red("Selective Render Plots did not respond"));
    }

    private static int request(int action, String name, int minY, int maxY,
                               Integer requestedMinY, Integer requestedMaxY, int xzMargin) {
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
        pendingMinY = requestedMinY;
        pendingMaxY = requestedMaxY;
        pendingXzMargin = xzMargin;

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
        Integer requestedMinY = pendingMinY;
        Integer requestedMaxY = pendingMaxY;
        int requestedMargin = pendingXzMargin;
        clearPending();
        List<BlockRegion> adjustedRegions = (status == STATUS_TOGGLE || status == STATUS_SAVE)
                ? PlotRegionTransform.apply(regions, requestedMinY, requestedMaxY, requestedMargin)
                : List.of();
        if ((status == STATUS_TOGGLE || status == STATUS_SAVE)
                && !regions.isEmpty() && adjustedRegions.isEmpty()) {
            send(red("The margin removes the entire plot"));
        } else if (status == STATUS_TOGGLE && !adjustedRegions.isEmpty()) {
            togglePlot(client, responseName, regions, adjustedRegions);
        } else if (status == STATUS_SAVE && !adjustedRegions.isEmpty()) {
            if (SelectiveRenderConfig.presetExists(responseName)) {
                send(white("Preset "), aqua(responseName), red(" already exists"),
                        white(" · delete or rename it first"));
                return;
            }
            if (SelectiveRenderConfig.saveRegions(client, responseName, adjustedRegions)) {
                send(white("Preset "), aqua(responseName), green(" saved"),
                        gray(" · " + adjustedRegions.size() + " part(s)"));
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

    private static void togglePlot(MinecraftClient client, String responseName,
                                   List<BlockRegion> rawRegions, List<BlockRegion> adjustedRegions) {
        PlotSession session = currentSession(true);
        PlotIdentity identity = new PlotIdentity(canonical(rawRegions));
        PlotEntry removed = session.plots.remove(identity);
        if (removed != null) {
            if (session.plots.isEmpty()) {
                if (activeContext != null) SESSIONS.remove(activeContext);
                SelectiveRenderState.disablePlotMode();
                overlay(white("Plot "), aqua(responseName), red(" removed"), gray(" · 0 active"));
                return;
            }
            boolean rendering = SelectiveRenderState.plotModeActive()
                    ? SelectiveRenderState.plotRenderingEnabled() : session.renderingEnabled;
            session.renderingEnabled = rendering;
            SelectiveRenderState.updatePlotMode(session.regions(), rendering, removed.regions);
            overlay(white("Plot "), aqua(responseName), red(" removed"),
                    gray(" · " + session.plots.size() + " active"));
            return;
        }

        PlotEntry added = new PlotEntry(responseName, List.copyOf(adjustedRegions));
        boolean rendering = SelectiveRenderState.plotModeActive()
                ? SelectiveRenderState.plotRenderingEnabled() : session.renderingEnabled;
        rendering = PlotSelectionPolicy.renderingAfterAdd(session.plots.isEmpty(), rendering);
        session.plots.put(identity, added);
        session.renderingEnabled = rendering;
        if (rendering) SelectiveRenderConfig.enableHiddenGroupForIsolation(client);
        SelectiveRenderState.updatePlotMode(session.regions(), rendering, added.regions);
        overlay(white("Plot "), aqua(responseName), green(" added"),
                gray(" · " + session.plots.size() + " active"));
    }

    private static PlotSession currentSession(boolean create) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (activeContext == null && client.world != null) {
            activeContext = SelectiveRenderConfig.contextIdentity(client, client.world);
        }
        if (activeContext == null) return null;
        return create ? SESSIONS.computeIfAbsent(activeContext, ignored -> new PlotSession())
                : SESSIONS.get(activeContext);
    }

    private static List<BlockRegion> canonical(List<BlockRegion> regions) {
        return regions.stream().sorted(Comparator.comparingInt(BlockRegion::minX)
                .thenComparingInt(BlockRegion::maxX).thenComparingInt(BlockRegion::minY)
                .thenComparingInt(BlockRegion::maxY).thenComparingInt(BlockRegion::minZ)
                .thenComparingInt(BlockRegion::maxZ)).toList();
    }

    private static void clearPending() {
        pendingRequest = 0L;
        pendingSince = 0L;
        pendingWorld = null;
        pendingMinY = null;
        pendingMaxY = null;
        pendingXzMargin = 0;
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

    private static void overlay(Text... parts) {
        MutableText message = Text.literal("SR: ").formatted(Formatting.GRAY);
        for (Text part : parts) message.append(part);
        SelectiveRenderClient.overlay(message);
    }

    private static MutableText white(String text) { return Text.literal(text).formatted(Formatting.WHITE); }
    private static MutableText gray(String text) { return Text.literal(text).formatted(Formatting.GRAY); }
    private static MutableText aqua(String text) { return Text.literal(text).formatted(Formatting.AQUA); }
    private static MutableText green(String text) { return Text.literal(text).formatted(Formatting.GREEN); }
    private static MutableText red(String text) { return Text.literal(text).formatted(Formatting.RED); }

    private record PlotIdentity(List<BlockRegion> rawRegions) { }
    private record PlotEntry(String name, List<BlockRegion> regions) { }

    private static final class PlotSession {
        private final LinkedHashMap<PlotIdentity, PlotEntry> plots = new LinkedHashMap<>();
        private boolean renderingEnabled = true;

        private List<BlockRegion> regions() {
            return plots.values().stream().flatMap(plot -> plot.regions.stream()).toList();
        }
    }
}
