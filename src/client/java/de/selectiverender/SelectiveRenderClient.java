package de.selectiverender;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.CommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

public final class SelectiveRenderClient implements ClientModInitializer {
    private static final WorldSessionLifecycle<ClientWorld> WORLD_SESSION =
            new WorldSessionLifecycle<>();
    public static final Logger LOGGER = LoggerFactory.getLogger("selectiverender");
    private static final KeyBinding TOGGLE_KEY = new KeyBinding(
            "key.selectiverender.toggle",
            GLFW.GLFW_KEY_F8,
            "category.selectiverender");
    private static final KeyBinding HIDE_TOGGLE_KEY = new KeyBinding(
            "key.selectiverender.toggle_hide",
            GLFW.GLFW_KEY_F9,
            "category.selectiverender");
    private static final KeyBinding POS1_KEY = new KeyBinding(
            "key.selectiverender.pos1",
            GLFW.GLFW_KEY_UNKNOWN,
            "category.selectiverender");
    private static final KeyBinding POS2_KEY = new KeyBinding(
            "key.selectiverender.pos2",
            GLFW.GLFW_KEY_UNKNOWN,
            "category.selectiverender");
    private static final KeyBinding PLOT_TOGGLE_KEY = new KeyBinding(
            "key.selectiverender.toggle_plot",
            GLFW.GLFW_KEY_UNKNOWN,
            "category.selectiverender");
    private static final KeyBinding SETTINGS_KEY = new KeyBinding(
            "key.selectiverender.settings",
            GLFW.GLFW_KEY_UNKNOWN,
            "category.selectiverender");
    private static final KeyBinding PLAYER_VISIBILITY_KEY = new KeyBinding(
            "key.selectiverender.toggle_players",
            GLFW.GLFW_KEY_K,
            "category.selectiverender");
    private static final KeyBinding INTERACTION_KEY = new KeyBinding(
            "key.selectiverender.cycle_interactions",
            GLFW.GLFW_KEY_UNKNOWN,
            "category.selectiverender");
    private static final KeyBinding BOUNDARY_KEY = new KeyBinding(
            "key.selectiverender.cycle_boundary",
            GLFW.GLFW_KEY_UNKNOWN,
            "category.selectiverender");
    private static final KeyBinding CLEAR_PLOTS_KEY = new KeyBinding(
            "key.selectiverender.clear_plots",
            GLFW.GLFW_KEY_UNKNOWN,
            "category.selectiverender");

    @Override
    public void onInitializeClient() {
        SelectiveRenderSettings.load();
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return new Identifier("selectiverender", "boundary-color");
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        BoundaryColorTexture.invalidate();
                    }

                    @Override
                    public java.util.Collection<Identifier> getFabricDependencies() {
                        return java.util.List.of(net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys.MODELS);
                    }
                });
        RegionBorderRenderer.initialize();
        PlotSquaredClient.initialize();
        KeyBindingHelper.registerKeyBinding(TOGGLE_KEY);
        KeyBindingHelper.registerKeyBinding(HIDE_TOGGLE_KEY);
        KeyBindingHelper.registerKeyBinding(POS1_KEY);
        KeyBindingHelper.registerKeyBinding(POS2_KEY);
        KeyBindingHelper.registerKeyBinding(PLOT_TOGGLE_KEY);
        KeyBindingHelper.registerKeyBinding(SETTINGS_KEY);
        KeyBindingHelper.registerKeyBinding(PLAYER_VISIBILITY_KEY);
        KeyBindingHelper.registerKeyBinding(INTERACTION_KEY);
        KeyBindingHelper.registerKeyBinding(BOUNDARY_KEY);
        KeyBindingHelper.registerKeyBinding(CLEAR_PLOTS_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PlotSquaredClient.tick();
            while (TOGGLE_KEY.wasPressed()) toggleFromKey(client);
            while (HIDE_TOGGLE_KEY.wasPressed()) toggleHideFromKey(client);
            while (POS1_KEY.wasPressed()) setPositionFromKey(client, true);
            while (POS2_KEY.wasPressed()) setPositionFromKey(client, false);
            while (PLOT_TOGGLE_KEY.wasPressed()) PlotSquaredClient.toggle();
            while (PLAYER_VISIBILITY_KEY.wasPressed()) cyclePlayerVisibility();
            while (INTERACTION_KEY.wasPressed()) cycleInteractions();
            while (BOUNDARY_KEY.wasPressed()) cycleBoundaryFaces();
            while (CLEAR_PLOTS_KEY.wasPressed()) {
                if (client.world != null) PlotSquaredClient.clear();
            }
            while (SETTINGS_KEY.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new SelectiveRenderSettingsScreen(null));
                }
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(command("selectiverender"));
            dispatcher.register(command("sr"));
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(() -> worldChanged(client, client.world)));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> worldChanged(client, null));
    }

    public static void worldChanged(MinecraftClient client, ClientWorld world) {
        WORLD_SESSION.switchTo(world, () -> {
            PlotSquaredClient.leaveWorld();
            SelectiveRenderConfig.endSession();
            SelectiveRenderState.resetForDisconnect();
        }, next -> {
            SelectiveRenderConfig.beginSession(client, next);
            PlotSquaredClient.enterWorld(client, next);
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command(String name) {
        return ClientCommandManager.literal(name)
                .then(ClientCommandManager.literal("pos1").executes(context -> setPosition(context.getSource(), true)))
                .then(ClientCommandManager.literal("pos2").executes(context -> setPosition(context.getSource(), false)))
                .then(ClientCommandManager.literal("1").executes(context -> setPosition(context.getSource(), true)))
                .then(ClientCommandManager.literal("2").executes(context -> setPosition(context.getSource(), false)))
                .then(saveCommand("save"))
                .then(saveCommand("s"))
                .then(toggleCommand("toggle"))
                .then(toggleCommand("t"))
                .then(hideCommand("hide"))
                .then(hideCommand("h"))
                .then(deleteCommand("delete"))
                .then(deleteCommand("d"))
                .then(renameCommand("rename"))
                .then(renameCommand("r"))
                .then(plotCommand("plot"))
                .then(plotCommand("p"))
                .then(listCommand("list"))
                .then(listCommand("l"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> plotCommand(String name) {
        return ClientCommandManager.literal(name)
                .executes(context -> PlotSquaredClient.toggle())
                .then(ClientCommandManager.literal("clear").executes(context -> PlotSquaredClient.clear()))
                .then(ClientCommandManager.argument("minY", IntegerArgumentType.integer())
                        .executes(context -> PlotSquaredClient.toggle(
                                IntegerArgumentType.getInteger(context, "minY"),
                                PlotSquaredClient.DEFAULT_MAX_Y, 0))
                        .then(ClientCommandManager.argument("maxY", IntegerArgumentType.integer())
                                .executes(context -> PlotSquaredClient.toggle(
                                        IntegerArgumentType.getInteger(context, "minY"),
                                        IntegerArgumentType.getInteger(context, "maxY"), 0))
                                .then(ClientCommandManager.argument("xzMargin", IntegerArgumentType.integer())
                                        .executes(context -> PlotSquaredClient.toggle(
                                                IntegerArgumentType.getInteger(context, "minY"),
                                                IntegerArgumentType.getInteger(context, "maxY"),
                                                IntegerArgumentType.getInteger(context, "xzMargin"))))))
                .then(plotSaveCommand("save"))
                .then(plotSaveCommand("s"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> plotSaveCommand(String name) {
        return ClientCommandManager.literal(name)
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .executes(context -> PlotSquaredClient.save(
                                StringArgumentType.getString(context, "name"),
                                SelectiveRenderSettings.defaultPlotMinY(), PlotSquaredClient.DEFAULT_MAX_Y, 0))
                        .then(ClientCommandManager.argument("minY", IntegerArgumentType.integer())
                                .executes(context -> PlotSquaredClient.save(
                                        StringArgumentType.getString(context, "name"),
                                        IntegerArgumentType.getInteger(context, "minY"),
                                        PlotSquaredClient.DEFAULT_MAX_Y, 0))
                                .then(ClientCommandManager.argument("maxY", IntegerArgumentType.integer())
                                        .executes(context -> PlotSquaredClient.save(
                                                StringArgumentType.getString(context, "name"),
                                                IntegerArgumentType.getInteger(context, "minY"),
                                                IntegerArgumentType.getInteger(context, "maxY"), 0))
                                        .then(ClientCommandManager.argument("xzMargin", IntegerArgumentType.integer())
                                                .executes(context -> PlotSquaredClient.save(
                                                        StringArgumentType.getString(context, "name"),
                                                        IntegerArgumentType.getInteger(context, "minY"),
                                                        IntegerArgumentType.getInteger(context, "maxY"),
                                                        IntegerArgumentType.getInteger(context, "xzMargin")))))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> listCommand(String name) {
        return ClientCommandManager.literal(name)
                .executes(context -> list(context.getSource(), false))
                .then(ClientCommandManager.literal("hidden")
                        .executes(context -> list(context.getSource(), true)))
                .then(ClientCommandManager.literal("h")
                        .executes(context -> list(context.getSource(), true)));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> hideCommand(String name) {
        return ClientCommandManager.literal(name)
                .executes(context -> toggleHide(context.getSource(), null))
                .then(ClientCommandManager.literal("all")
                        .executes(context -> toggleAll(context.getSource(), true)))
                .then(ClientCommandManager.literal("a")
                        .executes(context -> toggleAll(context.getSource(), true)))
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                SelectiveRenderConfig.presetNames(), builder))
                        .executes(context -> toggleHide(context.getSource(),
                                StringArgumentType.getString(context, "name"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> saveCommand(String name) {
        return ClientCommandManager.literal(name)
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .executes(context -> save(context.getSource(), StringArgumentType.getString(context, "name"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> toggleCommand(String name) {
        return ClientCommandManager.literal(name)
                .executes(context -> toggle(context.getSource(), null))
                .then(ClientCommandManager.literal("all")
                        .executes(context -> toggleAll(context.getSource(), false)))
                .then(ClientCommandManager.literal("a")
                        .executes(context -> toggleAll(context.getSource(), false)))
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                SelectiveRenderConfig.presetNames(), builder))
                        .executes(context -> toggle(context.getSource(), StringArgumentType.getString(context, "name"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> deleteCommand(String name) {
        return ClientCommandManager.literal(name)
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                SelectiveRenderConfig.presetNames(), builder))
                        .executes(context -> delete(context.getSource(), StringArgumentType.getString(context, "name"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> renameCommand(String name) {
        return ClientCommandManager.literal(name)
                .then(ClientCommandManager.argument("oldName", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                SelectiveRenderConfig.presetNames(), builder))
                        .then(ClientCommandManager.argument("newName", StringArgumentType.word())
                                .executes(context -> rename(context.getSource(),
                                        StringArgumentType.getString(context, "oldName"),
                                        StringArgumentType.getString(context, "newName")))));
    }

    private static int setPosition(FabricClientCommandSource source, boolean isFirst) {
        BlockPos position = source.getPlayer().getBlockPos();
        applyPosition(position, isFirst);
        feedback(source, positionMessage(position, isFirst));
        return Command.SINGLE_SUCCESS;
    }

    private static void setPositionFromKey(MinecraftClient client, boolean isFirst) {
        if (client.player == null) return;
        BlockPos position = client.player.getBlockPos();
        applyPosition(position, isFirst);
        client.player.sendMessage(positionMessage(position, isFirst), false);
    }

    private static void applyPosition(BlockPos position, boolean isFirst) {
        if (isFirst) SelectiveRenderState.setFirst(position); else SelectiveRenderState.setSecond(position);
    }

    private static MutableText positionMessage(BlockPos position, boolean isFirst) {
        return message(aqua(isFirst ? "Pos1" : "Pos2"), white(" = "
                + position.getX() + ", " + position.getY() + ", " + position.getZ()));
    }

    private static int save(FabricClientCommandSource source, String name) {
        if (SelectiveRenderConfig.isReservedName(name)) {
            feedback(source, message(aqua(name), red(" is reserved")));
            return 0;
        }
        if (SelectiveRenderConfig.presetExists(name)) {
            feedback(source, presetExists(name));
            return 0;
        }
        if (!SelectiveRenderConfig.saveSelection(MinecraftClient.getInstance(), name)) {
            feedback(source, message(white("Set "), red("pos1 and pos2"), white(" first.")));
            return 0;
        }
        BlockRegion region = SelectiveRenderState.selection();
        feedback(source, message(white("Preset "), aqua(name.toLowerCase(Locale.ROOT)),
                green(" saved"), white(" · " + region.blockCount() + " blocks")));
        return Command.SINGLE_SUCCESS;
    }

    private static int toggle(FabricClientCommandSource source, String name) {
        boolean toggled = name == null
                ? (SelectiveRenderState.plotModeActive()
                    ? togglePlotRenderingWithHidden(MinecraftClient.getInstance())
                    : SelectiveRenderConfig.toggleCurrent(MinecraftClient.getInstance()))
                : SelectiveRenderConfig.togglePreset(MinecraftClient.getInstance(), name);
        if (!toggled) {
            feedback(source, name == null
                    ? message(red("No presets in the render group."))
                    : missingPreset(name));
            return 0;
        }
        if (name == null) {
            boolean enabled = SelectiveRenderState.plotModeActive()
                    ? SelectiveRenderState.plotRenderingEnabled()
                    : SelectiveRenderConfig.groupEnabled();
            overlay(message(white(SelectiveRenderState.plotModeActive() ? "Plot rendering " : "Render group "),
                    enabled ? green("enabled") : red("disabled")));
            return Command.SINGLE_SUCCESS;
        } else {
            boolean active = SelectiveRenderConfig.isPresetActive(name);
            MutableText content = message(white("Preset "), aqua(name.toLowerCase(Locale.ROOT)),
                    white(" · "), active ? green("added") : red("removed"), white(" from render group"));
            if (!SelectiveRenderConfig.groupEnabled()) {
                content.append(white(" · group ")).append(red("disabled"));
            }
            feedback(source, content);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int delete(FabricClientCommandSource source, String name) {
        if (!SelectiveRenderConfig.deletePreset(MinecraftClient.getInstance(), name)) {
            feedback(source, missingPreset(name));
            return 0;
        }
        feedback(source, message(white("Preset "), aqua(name.toLowerCase(Locale.ROOT)), red(" deleted")));
        return Command.SINGLE_SUCCESS;
    }

    private static int toggleHide(FabricClientCommandSource source, String name) {
        boolean toggled = name == null
                ? SelectiveRenderConfig.toggleHiddenGroup(MinecraftClient.getInstance())
                : SelectiveRenderConfig.toggleHiddenPreset(MinecraftClient.getInstance(), name);
        if (!toggled) {
            feedback(source, name == null ? message(red("No presets in the hide group.")) : missingPreset(name));
            return 0;
        }
        if (name == null) {
            overlay(message(white("Hide group "), SelectiveRenderConfig.hideGroupEnabled()
                    ? green("enabled") : red("disabled")));
            return Command.SINGLE_SUCCESS;
        } else {
            boolean hidden = SelectiveRenderConfig.isHiddenPresetActive(name);
            MutableText content = message(white("Preset "), aqua(name.toLowerCase(Locale.ROOT)),
                    white(" · "), hidden ? green("added") : red("removed"), white(" from hide group"));
            if (!SelectiveRenderConfig.hideGroupEnabled()) {
                content.append(white(" · group ")).append(red("disabled"));
            }
            feedback(source, content);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int rename(FabricClientCommandSource source, String oldName, String newName) {
        SelectiveRenderConfig.RenameResult result = SelectiveRenderConfig.renamePreset(
                MinecraftClient.getInstance(), oldName, newName);
        if (result == SelectiveRenderConfig.RenameResult.MISSING_SOURCE) {
            feedback(source, missingPreset(oldName));
            return 0;
        }
        if (result == SelectiveRenderConfig.RenameResult.TARGET_EXISTS) {
            feedback(source, message(white("Preset "), aqua(newName), red(" already exists")));
            return 0;
        }
        if (result == SelectiveRenderConfig.RenameResult.RESERVED_NAME) {
            feedback(source, message(aqua(newName), red(" is reserved")));
            return 0;
        }
        feedback(source, message(aqua(oldName.toLowerCase(Locale.ROOT)), white(" → "),
                aqua(newName.toLowerCase(Locale.ROOT)), green(" renamed")));
        return Command.SINGLE_SUCCESS;
    }

    private static int toggleAll(FabricClientCommandSource source, boolean hidden) {
        boolean toggled = hidden
                ? SelectiveRenderConfig.toggleAllHiddenPresets(MinecraftClient.getInstance())
                : SelectiveRenderConfig.toggleAllPresets(MinecraftClient.getInstance());
        if (!toggled) {
            feedback(source, message(red(hidden ? "No hidden regions saved." : "No render regions saved.")));
            return 0;
        }
        boolean anyActive = hidden
                ? !SelectiveRenderConfig.hiddenPresetNames().isEmpty()
                : !SelectiveRenderConfig.activePresetNames().isEmpty();
        feedback(source, message(aqua(hidden ? "Hidden regions" : "Render regions"), white(" · all "),
                anyActive ? green("enabled") : red("disabled")));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(FabricClientCommandSource source, boolean hiddenOnly) {
        List<String> names = SelectiveRenderConfig.presetNames().stream()
                .filter(name -> SelectiveRenderConfig.isPresetHidden(name) == hiddenOnly)
                .toList();
        if (names.isEmpty()) {
            feedback(source, message(red(hiddenOnly ? "No presets in the hide group."
                    : "No regular presets saved.")));
            return Command.SINGLE_SUCCESS;
        }
        boolean groupEnabled = hiddenOnly
                ? SelectiveRenderConfig.hideGroupEnabled() : SelectiveRenderConfig.groupEnabled();
        feedback(source, message(aqua(hiddenOnly ? "Hidden regions" : "Render regions"),
                white(" · group "), groupEnabled ? green("enabled") : red("disabled")));
        int width = names.stream().mapToInt(String::length).max().orElse(0);
        for (String name : names) {
            BlockRegion region = SelectiveRenderConfig.presetRegion(name);
            boolean member = hiddenOnly
                    ? SelectiveRenderConfig.isHiddenPresetActive(name)
                    : SelectiveRenderConfig.isPresetActive(name);
            String paddedName = name + " ".repeat(width - name.length());
            listLine(source, gray("  "), white(paddedName + "  "),
                    member ? green("active  ") : red("inactive"),
                    gray("  " + region.minX() + ", " + region.minY() + ", " + region.minZ()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void toggleFromKey(MinecraftClient client) {
        if (client.player == null) return;
        boolean toggled = SelectiveRenderState.plotModeActive()
                ? togglePlotRenderingWithHidden(client)
                : SelectiveRenderConfig.toggleCurrent(client);
        if (!toggled) {
            client.player.sendMessage(message(red("No presets in the render group.")), false);
            return;
        }
        boolean enabled = SelectiveRenderState.plotModeActive()
                ? SelectiveRenderState.plotRenderingEnabled()
                : SelectiveRenderConfig.groupEnabled();
        overlay(message(white(SelectiveRenderState.plotModeActive() ? "Plot rendering " : "Render group "),
                enabled ? green("enabled") : red("disabled")));
    }

    private static void toggleHideFromKey(MinecraftClient client) {
        if (client.player == null) return;
        if (!SelectiveRenderConfig.toggleHiddenGroup(client)) {
            client.player.sendMessage(message(red("No presets in the hide group.")), false);
            return;
        }
        overlay(message(white("Hide group "), SelectiveRenderConfig.hideGroupEnabled()
                ? green("enabled") : red("disabled")));
    }

    private static boolean togglePlotRenderingWithHidden(MinecraftClient client) {
        if (!SelectiveRenderState.plotRenderingEnabled()) {
            SelectiveRenderConfig.enableHiddenGroupForIsolation(client);
        }
        return SelectiveRenderState.togglePlotRendering();
    }

    private static void cyclePlayerVisibility() {
        SelectiveRenderSettings.PlayerVisibility next =
                SelectiveRenderSettings.playerVisibility().next();
        SelectiveRenderSettings.setPlayerVisibility(next);
        overlay(message(white("Players: "), aqua(next.label())));
    }

    private static void cycleInteractions() {
        SelectiveRenderSettings.InteractionMode next = SelectiveRenderSettings.interactionMode().next();
        SelectiveRenderSettings.setInteractionMode(next);
        overlay(message(white("Interactions: "), aqua(next.label())));
    }

    private static void cycleBoundaryFaces() {
        SelectiveRenderSettings.BoundaryMode next = SelectiveRenderSettings.boundaryMode().next();
        SelectiveRenderSettings.setBoundaryMode(next);
        overlay(message(white("Boundary faces: "), aqua(next.label())));
    }

    public static void overlay(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud != null) client.inGameHud.setOverlayMessage(message, false);
    }

    private static void feedback(FabricClientCommandSource source, Text message) {
        source.sendFeedback(message);
    }

    private static void listLine(FabricClientCommandSource source, Text... parts) {
        MutableText line = Text.empty();
        for (Text part : parts) line.append(part);
        source.sendFeedback(line);
    }

    private static MutableText message(Text... parts) {
        MutableText message = Text.literal("SR: ").formatted(Formatting.GRAY);
        for (Text part : parts) message.append(part);
        return message;
    }

    private static MutableText missingPreset(String name) {
        return message(white("Preset "), aqua(name.toLowerCase(Locale.ROOT)), red(" does not exist"));
    }

    private static MutableText presetExists(String name) {
        return message(white("Preset "), aqua(name.toLowerCase(Locale.ROOT)), red(" already exists"),
                white(" · delete or rename it first"));
    }

    private static MutableText white(String text) {
        return Text.literal(text).formatted(Formatting.WHITE);
    }

    private static MutableText gray(String text) {
        return Text.literal(text).formatted(Formatting.GRAY);
    }

    private static MutableText aqua(String text) {
        return Text.literal(text).formatted(Formatting.AQUA);
    }

    private static MutableText green(String text) {
        return Text.literal(text).formatted(Formatting.GREEN);
    }

    private static MutableText red(String text) {
        return Text.literal(text).formatted(Formatting.RED);
    }
}
