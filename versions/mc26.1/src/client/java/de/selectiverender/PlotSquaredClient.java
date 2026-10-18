package de.selectiverender;

import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class PlotSquaredClient {
    private static final Identifier REQUEST_CHANNEL = Identifier.fromNamespaceAndPath("selectiverender", "plot_request");
    private static final Identifier RESPONSE_CHANNEL = Identifier.fromNamespaceAndPath("selectiverender", "plot_response");
    private static final CustomPacketPayload.Type<PlotPayload> REQUEST_TYPE = new CustomPacketPayload.Type<>(REQUEST_CHANNEL);
    private static final CustomPacketPayload.Type<PlotPayload> RESPONSE_TYPE = new CustomPacketPayload.Type<>(RESPONSE_CHANNEL);

    private record PlotPayload(CustomPacketPayload.Type<PlotPayload> type, byte[] bytes) implements CustomPacketPayload { }

    private static StreamCodec<RegistryFriendlyByteBuf, PlotPayload> payloadCodec(CustomPacketPayload.Type<PlotPayload> type) {
        return new StreamCodec<>() {
            @Override public PlotPayload decode(RegistryFriendlyByteBuf buffer) {
                if (buffer.readableBytes() > 16384) throw new IllegalArgumentException("Plot payload too large");
                byte[] bytes = new byte[buffer.readableBytes()];
                buffer.readBytes(bytes);
                return new PlotPayload(type, bytes);
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, PlotPayload payload) {
                buffer.writeBytes(payload.bytes());
            }
        };
    }
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
    private static ClientLevel pendingWorld;
    private static Integer pendingMinY;
    private static Integer pendingMaxY;
    private static int pendingXzMargin;
    private static final Map<String, PlotSelection> SESSIONS = new LinkedHashMap<>();
    private static String activeContext;

    private PlotSquaredClient() { }

    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(REQUEST_TYPE, payloadCodec(REQUEST_TYPE));
        PayloadTypeRegistry.clientboundPlay().register(RESPONSE_TYPE, payloadCodec(RESPONSE_TYPE));
        ClientPlayNetworking.registerGlobalReceiver(RESPONSE_TYPE, (payload, context) -> {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.bytes()));
            try { receive(context.client(), buffer); }
            finally { buffer.release(); }
        });
    }

    public static void leaveWorld() {
        clearPending();
        PlotSelection session = activeContext == null ? null : SESSIONS.get(activeContext);
        if (session != null && SelectiveRenderState.plotModeActive()) {
            session.renderingEnabled = SelectiveRenderState.plotRenderingEnabled();
        }
        activeContext = null;
        SelectiveRenderState.resetPlotMode();
    }

    public static void enterWorld(Minecraft client, ClientLevel world) {
        activeContext = SelectiveRenderConfig.contextIdentity(client, world);
        PlotSelection session = SESSIONS.get(activeContext);
        if (session == null || session.isEmpty()) return;
        if (session.renderingEnabled) SelectiveRenderConfig.enableHiddenGroupForIsolation(client);
        SelectiveRenderState.activatePlotMode(session.regions(), session.renderingEnabled);
    }

    public static int toggle() {
        return toggle(SelectiveRenderSettings.defaultPlotMinY(), SelectiveRenderSettings.defaultPlotMaxY(), 0);
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

    static void clearGridPlots() {
        if (activeContext == null) return;
        PlotSelection session = SESSIONS.remove(activeContext);
        if (session != null && SelectiveRenderState.plotModeActive()) SelectiveRenderState.disablePlotMode();
        clearPending();
    }

    public static int clear() {
        clearPending();
        PlotSelection session = currentSession(false);
        if (session == null || session.isEmpty()) {
            overlay(white("No temporary plots to clear"));
            return 0;
        }
        session.clear();
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
        if (SelectiveRenderConfig.regionExists(name)) {
            send(white("Region "), aqua(name), red(" already exists"),
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
        if (pendingRequest == 0L || Util.getMillis() - pendingSince < 5000L) return;
        clearPending();
        send(red("Selective Render Plots did not respond"));
    }

    private static int request(int action, String name, int minY, int maxY,
                               Integer requestedMinY, Integer requestedMaxY, int xzMargin) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.getConnection() == null) return 0;
        PlotGrid grid = PlotGridClient.current();
        if (grid != null && grid.enabled()) {
            if (grid.roadWidth() == null) { send(red("Set the road width or mark the neighboring plot first.")); return 0; }
            BlockRegion raw = PlotGridClient.plotAtPlayer(minY,maxY);
            if (raw == null) { send(red("You are not standing on a plot.")); return 0; }
            List<BlockRegion> adjusted = PlotRegionTransform.apply(List.of(raw),requestedMinY,requestedMaxY,xzMargin);
            if (adjusted.isEmpty()) { send(red("The margin removes the entire plot.")); return 0; }
            if (action == ACTION_TOGGLE) togglePlot(client,"grid",List.of(new BlockRegion(raw.minX(),raw.maxX(),0,0,raw.minZ(),raw.maxZ())),List.of(raw),requestedMinY,requestedMaxY,xzMargin,adjusted);
            else if (!SelectiveRenderConfig.saveRegions(client,name,adjusted)) { send(red("The plot could not be saved.")); return 0; }
            else send(green("Plot saved."));
            return Command.SINGLE_SUCCESS;
        }
        if (!ClientPlayNetworking.canSend(REQUEST_CHANNEL)) {
            send(red("Selective Render Plots is unavailable on this server"));
            return 0;
        }
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        if (nameBytes.length > 256) {
            send(red("Region name is too long"));
            return 0;
        }

        long requestId = ThreadLocalRandom.current().nextLong();
        if (requestId == 0L) requestId = 1L;
        pendingRequest = requestId;
        pendingSince = Util.getMillis();
        pendingWorld = client.level;
        pendingMinY = requestedMinY;
        pendingMaxY = requestedMaxY;
        pendingXzMargin = xzMargin;

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeInt(MAGIC);
        buffer.writeInt(VERSION);
        buffer.writeLong(requestId);
        buffer.writeByte(action);
        buffer.writeInt(nameBytes.length);
        buffer.writeBytes(nameBytes);
        buffer.writeInt(minY);
        buffer.writeInt(maxY);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        buffer.release();
        ClientPlayNetworking.send(new PlotPayload(REQUEST_TYPE, bytes));
        return Command.SINGLE_SUCCESS;
    }

    private static void receive(Minecraft client, FriendlyByteBuf buffer) {
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
            ClientLevel responseWorld = pendingWorld;
            client.execute(() -> applyResponse(client, responseWorld, requestId,
                    responseStatus, responseName, List.copyOf(regions)));
        } catch (RuntimeException exception) {
            client.execute(() -> {
                clearPending();
                send(red("Invalid response from Selective Render Plots"));
            });
        }
    }

    private static void applyResponse(Minecraft client, ClientLevel responseWorld, long requestId,
                                      int status, String responseName, List<BlockRegion> regions) {
        if (requestId != pendingRequest || responseWorld == null || client.level != responseWorld) return;
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
            togglePlot(client, responseName, regions, regions, requestedMinY, requestedMaxY, requestedMargin, adjustedRegions);
        } else if (status == STATUS_SAVE && !adjustedRegions.isEmpty()) {
            if (SelectiveRenderConfig.regionExists(responseName)) {
                send(white("Region "), aqua(responseName), red(" already exists"),
                        white(" · delete or rename it first"));
                return;
            }
            if (SelectiveRenderConfig.saveRegions(client, responseName, adjustedRegions)) {
                send(white("Region "), aqua(responseName), green(" saved"),
                        gray(" · " + adjustedRegions.size() + " part(s)"));
            } else {
                send(red("The plot region could not be saved"));
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

    private static void togglePlot(Minecraft client, String responseName, List<BlockRegion> rawRegions,
                                   List<BlockRegion> sourceRegions, Integer minY, Integer maxY, int margin,
                                   List<BlockRegion> adjustedRegions) {
        PlotSelection session = currentSession(true);
        boolean defaults = margin == 0 && minY != null && maxY != null
                && minY == SelectiveRenderSettings.defaultPlotMinY() && maxY == SelectiveRenderSettings.defaultPlotMaxY();
        boolean wasEmpty = session.isEmpty();
        boolean rendering = SelectiveRenderState.plotModeActive()
                ? SelectiveRenderState.plotRenderingEnabled() : session.renderingEnabled;
        PlotSelection.Change change = session.toggle(rawRegions, responseName, sourceRegions, minY, maxY, margin,
                defaults, adjustedRegions);
        if (change == PlotSelection.Change.REMOVED) {
            if (session.isEmpty()) {
                if (activeContext != null) SESSIONS.remove(activeContext);
                SelectiveRenderState.disablePlotMode();
                overlay(white("Plot "), aqua(responseName), red(" removed"), gray(" · 0 active"));
                return;
            }
            session.renderingEnabled = rendering;
            SelectiveRenderState.updatePlotMode(session.regions(), rendering, session.lastChanged());
            overlay(white("Plot "), aqua(responseName), red(" removed"),
                    gray(" · " + session.size() + " active"));
            return;
        }
        if (change == PlotSelection.Change.ADDED) rendering = PlotSelectionPolicy.renderingAfterAdd(wasEmpty, rendering);
        session.renderingEnabled = rendering;
        if (rendering) SelectiveRenderConfig.enableHiddenGroupForIsolation(client);
        SelectiveRenderState.updatePlotMode(session.regions(), rendering, session.lastChanged());
        overlay(white("Plot "), aqua(responseName), green(change == PlotSelection.Change.ADDED ? " added" : " updated"),
                gray(" · " + session.size() + " active"));
    }

    public static void defaultHeightsChanged() {
        PlotSelection session = activeContext == null ? null : SESSIONS.get(activeContext);
        if (session == null || session.isEmpty()) return;
        if (!session.applyDefaultHeights(SelectiveRenderSettings.defaultPlotMinY(), SelectiveRenderSettings.defaultPlotMaxY())) return;
        if (SelectiveRenderState.plotModeActive())
            SelectiveRenderState.updatePlotMode(session.regions(), SelectiveRenderState.plotRenderingEnabled(), session.lastChanged());
    }

    private static PlotSelection currentSession(boolean create) {
        Minecraft client = Minecraft.getInstance();
        if (activeContext == null && client.level != null) {
            activeContext = SelectiveRenderConfig.contextIdentity(client, client.level);
        }
        if (activeContext == null) return null;
        return create ? SESSIONS.computeIfAbsent(activeContext, ignored -> new PlotSelection())
                : SESSIONS.get(activeContext);
    }

    private static void clearPending() {
        pendingRequest = 0L;
        pendingSince = 0L;
        pendingWorld = null;
        pendingMinY = null;
        pendingMaxY = null;
        pendingXzMargin = 0;
    }

    private static String readString(FriendlyByteBuf buffer) {
        int length = buffer.readInt();
        if (length < 0 || length > 256 || length > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid string length");
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void send(Component... parts) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        MutableComponent message = Component.empty();
        for (Component part : parts) message.append(part);
        client.getChatListener().handleSystemMessage(message, false);
    }

    private static void overlay(Component... parts) {
        MutableComponent message = Component.empty();
        for (Component part : parts) message.append(part);
        SelectiveRenderClient.overlay(message);
    }

    private static MutableComponent white(String text) { return Component.literal(text).withStyle(style -> style.withColor(UiStyle.TEXT)); }
    private static MutableComponent gray(String text) { return Component.literal(text).withStyle(style -> style.withColor(UiStyle.MUTED)); }
    private static MutableComponent aqua(String text) { return Component.literal(text).withStyle(style -> style.withColor(UiStyle.ACCENT)); }
    private static MutableComponent green(String text) { return Component.literal(text).withStyle(style -> style.withColor(UiStyle.SUCCESS)); }
    private static MutableComponent red(String text) { return Component.literal(text).withStyle(style -> style.withColor(UiStyle.ERROR)); }
}
