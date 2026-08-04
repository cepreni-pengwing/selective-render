package de.selectiverender;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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

public final class SelectiveRenderClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("selectiverender");

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(command("selectiverender"));
            dispatcher.register(command("sr"));
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(() -> SelectiveRenderConfig.load(client)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SelectiveRenderState.resetForDisconnect());
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command(String name) {
        return ClientCommandManager.literal(name)
                .then(ClientCommandManager.literal("pos1").executes(context -> setPosition(context.getSource(), true)))
                .then(ClientCommandManager.literal("pos2").executes(context -> setPosition(context.getSource(), false)))
                .then(ClientCommandManager.literal("save").executes(context -> save(context.getSource())))
                .then(ClientCommandManager.literal("toggle").executes(context -> toggle(context.getSource())));
    }

    private static int setPosition(FabricClientCommandSource source, boolean isFirst) {
        ChunkPos position = source.getPlayer().getChunkPos();
        if (isFirst) SelectiveRenderState.setFirst(position); else SelectiveRenderState.setSecond(position);
        feedback(source, (isFirst ? "Pos1" : "Pos2") + " = Chunk " + position.x + ", " + position.z, Formatting.AQUA);
        return Command.SINGLE_SUCCESS;
    }

    private static int save(FabricClientCommandSource source) {
        if (!SelectiveRenderState.saveSelection()) {
            feedback(source, "Set pos1 and pos2 first.", Formatting.RED);
            return 0;
        }
        SelectiveRenderConfig.save(MinecraftClient.getInstance());
        SelectiveRenderState.refreshRenderer();
        ChunkRegion region = SelectiveRenderState.region();
        feedback(source, "Region saved: " + region.chunkCount() + " chunks.", Formatting.GREEN);
        return Command.SINGLE_SUCCESS;
    }

    private static int toggle(FabricClientCommandSource source) {
        if (!SelectiveRenderState.toggle()) {
            feedback(source, "No region has been saved.", Formatting.RED);
            return 0;
        }
        SelectiveRenderConfig.save(MinecraftClient.getInstance());
        feedback(source, "Selective rendering " + (SelectiveRenderState.enabled() ? "enabled" : "disabled") + ".",
                SelectiveRenderState.enabled() ? Formatting.GREEN : Formatting.YELLOW);
        return Command.SINGLE_SUCCESS;
    }

    private static void feedback(FabricClientCommandSource source, String message, Formatting color) {
        source.sendFeedback(Text.literal("[SelectiveRender] ").formatted(Formatting.GRAY)
                .append(Text.literal(message).formatted(color)));
    }
}
