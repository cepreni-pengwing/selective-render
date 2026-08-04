package de.plotrender;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlotRenderClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("plotrender");

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("plotrender")
                        .then(ClientCommandManager.literal("pos1").executes(context -> setPosition(context.getSource(), true)))
                        .then(ClientCommandManager.literal("pos2").executes(context -> setPosition(context.getSource(), false)))
                        .then(ClientCommandManager.literal("save").executes(context -> save(context.getSource())))
                        .then(ClientCommandManager.literal("toggle").executes(context -> toggle(context.getSource())))));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(() -> PlotRenderConfig.load(client)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PlotRenderState.resetForDisconnect());
    }

    private static int setPosition(FabricClientCommandSource source, boolean isFirst) {
        ChunkPos position = source.getPlayer().getChunkPos();
        if (isFirst) PlotRenderState.setFirst(position); else PlotRenderState.setSecond(position);
        feedback(source, (isFirst ? "Pos1" : "Pos2") + " = Chunk " + position.x + ", " + position.z, Formatting.AQUA);
        return Command.SINGLE_SUCCESS;
    }

    private static int save(FabricClientCommandSource source) {
        if (!PlotRenderState.saveSelection()) {
            feedback(source, "Zuerst /plotrender pos1 und /plotrender pos2 setzen.", Formatting.RED);
            return 0;
        }
        PlotRenderConfig.save(MinecraftClient.getInstance());
        PlotRenderState.refreshRenderer();
        ChunkRegion region = PlotRenderState.region();
        feedback(source, "Region gespeichert: " + region.chunkCount() + " Chunks.", Formatting.GREEN);
        return Command.SINGLE_SUCCESS;
    }

    private static int toggle(FabricClientCommandSource source) {
        if (!PlotRenderState.toggle()) {
            feedback(source, "Keine Region gespeichert.", Formatting.RED);
            return 0;
        }
        PlotRenderConfig.save(MinecraftClient.getInstance());
        feedback(source, "Plot-Rendering " + (PlotRenderState.enabled() ? "aktiviert" : "deaktiviert") + ".",
                PlotRenderState.enabled() ? Formatting.GREEN : Formatting.YELLOW);
        return Command.SINGLE_SUCCESS;
    }

    private static void feedback(FabricClientCommandSource source, String message, Formatting color) {
        source.sendFeedback(Text.literal("[PlotRender] ").formatted(Formatting.GRAY)
                .append(Text.literal(message).formatted(color)));
    }
}
