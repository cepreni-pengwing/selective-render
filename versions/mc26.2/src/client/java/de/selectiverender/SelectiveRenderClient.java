package de.selectiverender;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

public final class SelectiveRenderClient implements ClientModInitializer {
    private static final KeyMapping.Category KEY_CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("selectiverender", "controls"));
    private static final WorldSessionLifecycle<ClientLevel> WORLD_SESSION =
            new WorldSessionLifecycle<>();
    public static final Logger LOGGER = LoggerFactory.getLogger("selectiverender");
    private static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.selectiverender.toggle",
            GLFW.GLFW_KEY_F9,
            KEY_CATEGORY);
    private static final KeyMapping HIDE_TOGGLE_KEY = new KeyMapping(
            "key.selectiverender.toggle_hide",
            GLFW.GLFW_KEY_F10,
            KEY_CATEGORY);
    private static final KeyMapping POS1_KEY = new KeyMapping(
            "key.selectiverender.pos1",
            GLFW.GLFW_KEY_UNKNOWN,
            KEY_CATEGORY);
    private static final KeyMapping POS2_KEY = new KeyMapping(
            "key.selectiverender.pos2",
            GLFW.GLFW_KEY_UNKNOWN,
            KEY_CATEGORY);
    private static final KeyMapping PLOT_TOGGLE_KEY = new KeyMapping(
            "key.selectiverender.toggle_plot",
            GLFW.GLFW_KEY_BACKSPACE,
            KEY_CATEGORY);
    private static final KeyMapping SETTINGS_KEY = new KeyMapping(
            "key.selectiverender.settings",
            GLFW.GLFW_KEY_APOSTROPHE,
            KEY_CATEGORY);
    private static final KeyMapping PLAYER_VISIBILITY_KEY = new KeyMapping(
            "key.selectiverender.toggle_players",
            GLFW.GLFW_KEY_UNKNOWN,
            KEY_CATEGORY);
    private static final KeyMapping INTERACTION_KEY = new KeyMapping(
            "key.selectiverender.cycle_interactions",
            GLFW.GLFW_KEY_UNKNOWN,
            KEY_CATEGORY);
    private static final KeyMapping BOUNDARY_KEY = new KeyMapping(
            "key.selectiverender.cycle_boundary",
            GLFW.GLFW_KEY_UNKNOWN,
            KEY_CATEGORY);
    private static final KeyMapping CLEAR_PLOTS_KEY = new KeyMapping(
            "key.selectiverender.clear_plots",
            GLFW.GLFW_KEY_UNKNOWN,
            KEY_CATEGORY);

    @Override
    public void onInitializeClient() {
        SelectiveRenderSettings.load();
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.fromNamespaceAndPath("selectiverender", "boundary-color");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        BoundaryColorTexture.invalidate();
                    }

                    @Override
                    public java.util.Collection<Identifier> getFabricDependencies() {
                        return java.util.List.of(net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys.MODELS);
                    }
                });
        RegionBorderRenderer.initialize();
        PlotSquaredClient.initialize();
        WorldEditClient.initialize();
        KeyMappingHelper.registerKeyMapping(TOGGLE_KEY);
        KeyMappingHelper.registerKeyMapping(HIDE_TOGGLE_KEY);
        KeyMappingHelper.registerKeyMapping(POS1_KEY);
        KeyMappingHelper.registerKeyMapping(POS2_KEY);
        KeyMappingHelper.registerKeyMapping(PLOT_TOGGLE_KEY);
        KeyMappingHelper.registerKeyMapping(SETTINGS_KEY);
        KeyMappingHelper.registerKeyMapping(PLAYER_VISIBILITY_KEY);
        KeyMappingHelper.registerKeyMapping(INTERACTION_KEY);
        KeyMappingHelper.registerKeyMapping(BOUNDARY_KEY);
        KeyMappingHelper.registerKeyMapping(CLEAR_PLOTS_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            VirtualSkyLightSampler.tick(client.level);
            PlotSquaredClient.tick();
            while (TOGGLE_KEY.consumeClick()) toggleFromKey(client);
            while (HIDE_TOGGLE_KEY.consumeClick()) toggleHideFromKey(client);
            while (POS1_KEY.consumeClick()) setPositionFromKey(client, true);
            while (POS2_KEY.consumeClick()) setPositionFromKey(client, false);
            while (PLOT_TOGGLE_KEY.consumeClick()) PlotSquaredClient.toggle();
            while (PLAYER_VISIBILITY_KEY.consumeClick()) cyclePlayerVisibility();
            while (INTERACTION_KEY.consumeClick()) cycleInteractions();
            while (BOUNDARY_KEY.consumeClick()) cycleBoundaryFaces();
            while (CLEAR_PLOTS_KEY.consumeClick()) {
                if (client.level != null) PlotSquaredClient.clear();
            }
            while (SETTINGS_KEY.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new SelectiveRenderSettingsScreen(null));
                }
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(command("selectiverender"));
            dispatcher.register(command("sr"));
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(() -> worldChanged(client, client.level)));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> worldChanged(client, null));
    }

    public static void worldChanged(Minecraft client, ClientLevel world) {
        WorldEditClient.worldChanged(world);
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
        return ClientCommands.literal(name)
                .then(ClientCommands.literal("pos1").executes(context -> setPosition(context.getSource(), true)))
                .then(ClientCommands.literal("pos2").executes(context -> setPosition(context.getSource(), false)))
                .then(ClientCommands.literal("1").executes(context -> setPosition(context.getSource(), true)))
                .then(ClientCommands.literal("2").executes(context -> setPosition(context.getSource(), false)))
                .then(saveCommand("save"))
                .then(saveCommand("s"))
                .then(createCommand("create"))
                .then(createCommand("c"))
                .then(toggleCommand("toggle"))
                .then(toggleCommand("t"))
                .then(hideCommand("hide"))
                .then(hideCommand("h"))
                .then(deleteCommand("delete"))
                .then(deleteCommand("d"))
                .then(redefineCommand("redefine"))
                .then(redefineCommand("r"))
                .then(renameCommand("rename"))
                .then(renameCommand("name"))
                .then(renameCommand("n"))
                .then(plotCommand("plot"))
                .then(plotCommand("p"))
                .then(filterCommand("filter"))
                .then(filterCommand("f"))
                .then(listCommand("list"))
                .then(listCommand("l"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> filterCommand(String name) {
        return ClientCommands.literal(name)
                .then(ClientCommands.argument("region", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(SelectiveRenderConfig.presetNames(), builder))
                        .executes(context -> listBlockFilters(context.getSource(), StringArgumentType.getString(context, "region")))
                        .then(ClientCommands.literal("clear").executes(context -> clearBlockFilters(context.getSource(), StringArgumentType.getString(context, "region"))))
                        .then(ClientCommands.literal("hide").then(ClientCommands.argument("selector", StringArgumentType.greedyString())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(blockFilterSuggestions(builder.getRemaining()), builder))
                                .executes(context -> setBlockFilter(context.getSource(), StringArgumentType.getString(context, "region"), BlockFilterRule.Mode.HIDE, StringArgumentType.getString(context, "selector")))))
                        .then(ClientCommands.literal("only").then(ClientCommands.argument("selector", StringArgumentType.greedyString())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(blockFilterSuggestions(builder.getRemaining()), builder))
                                .executes(context -> setBlockFilter(context.getSource(), StringArgumentType.getString(context, "region"), BlockFilterRule.Mode.ONLY, StringArgumentType.getString(context, "selector"))))));
    }

    private static List<String> blockFilterSuggestions(String remaining) {
        List<String> suggestions = new java.util.ArrayList<>();
        if (!remaining.startsWith("id:")) suggestions.add("tag:" + BuiltInBlockTags.BEAMS);
        if (!remaining.startsWith("tag:")) BuiltInRegistries.BLOCK.keySet().forEach(id -> suggestions.add("id:" + id));
        if (!remaining.startsWith("id:")) BuiltInRegistries.BLOCK.forEach(block -> BuiltInRegistries.BLOCK.wrapAsHolder(block).tags()
                .forEach(tag -> suggestions.add("tag:" + tag.location())));
        return suggestions;
    }

    private static int setBlockFilter(FabricClientCommandSource source, String region, BlockFilterRule.Mode mode, String selector) {
        try {
            int separator = selector.indexOf(':');
            if (separator <= 0 || separator == selector.length() - 1) throw new IllegalArgumentException();
            BlockFilterRule.Kind kind = switch (selector.substring(0, separator).toLowerCase(Locale.ROOT)) {
                case "id" -> BlockFilterRule.Kind.ID; case "tag" -> BlockFilterRule.Kind.TAG; default -> throw new IllegalArgumentException();
            };
            BlockFilterRule rule = new BlockFilterRule(mode, kind, selector.substring(separator + 1).toLowerCase(Locale.ROOT));
            if (!SelectiveRenderConfig.setBlockFilter(Minecraft.getInstance(), region, rule)) { feedback(source, Component.literal("Could not add filter: unknown or hidden region " + region)); return 0; }
            feedback(source, Component.literal("Added " + mode.name().toLowerCase(Locale.ROOT) + " filter " + selector + " to " + region));
            return Command.SINGLE_SUCCESS;
        } catch (IllegalArgumentException exception) { feedback(source, Component.literal("Use id:minecraft:stone or tag:minecraft:slabs")); return 0; }
    }

    private static int clearBlockFilters(FabricClientCommandSource source, String region) {
        if (!SelectiveRenderConfig.clearBlockFilters(Minecraft.getInstance(), region)) { feedback(source, Component.literal("No block filters cleared for " + region)); return 0; }
        feedback(source, Component.literal("Cleared block filters for " + region)); return Command.SINGLE_SUCCESS;
    }

    private static int listBlockFilters(FabricClientCommandSource source, String region) {
        List<BlockFilterRule> rules = SelectiveRenderConfig.blockFilters(region);
        feedback(source, rules.isEmpty() ? Component.literal("No block filters for " + region)
                : Component.literal("Block filters for " + region + ": " + rules.stream()
                        .map(rule -> rule.mode().name().toLowerCase(Locale.ROOT) + " "
                                + rule.kind().name().toLowerCase(Locale.ROOT) + ":" + rule.value())
                        .collect(java.util.stream.Collectors.joining(", "))));
        return rules.isEmpty() ? 0 : Command.SINGLE_SUCCESS;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> plotCommand(String name) {
        return ClientCommands.literal(name)
                .executes(context -> PlotSquaredClient.toggle())
                .then(ClientCommands.literal("clear").executes(context -> PlotSquaredClient.clear()))
                .then(ClientCommands.argument("minY", IntegerArgumentType.integer())
                        .executes(context -> PlotSquaredClient.toggle(
                                IntegerArgumentType.getInteger(context, "minY"),
                                PlotSquaredClient.DEFAULT_MAX_Y, 0))
                        .then(ClientCommands.argument("maxY", IntegerArgumentType.integer())
                                .executes(context -> PlotSquaredClient.toggle(
                                        IntegerArgumentType.getInteger(context, "minY"),
                                        IntegerArgumentType.getInteger(context, "maxY"), 0))
                                .then(ClientCommands.argument("xzMargin", IntegerArgumentType.integer())
                                        .executes(context -> PlotSquaredClient.toggle(
                                                IntegerArgumentType.getInteger(context, "minY"),
                                                IntegerArgumentType.getInteger(context, "maxY"),
                                                IntegerArgumentType.getInteger(context, "xzMargin"))))))
                .then(plotSaveCommand("save"))
                .then(plotSaveCommand("s"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> plotSaveCommand(String name) {
        return ClientCommands.literal(name)
                .then(ClientCommands.argument("name", StringArgumentType.word())
                        .executes(context -> PlotSquaredClient.save(
                                StringArgumentType.getString(context, "name"),
                                SelectiveRenderSettings.defaultPlotMinY(), PlotSquaredClient.DEFAULT_MAX_Y, 0))
                        .then(ClientCommands.argument("minY", IntegerArgumentType.integer())
                                .executes(context -> PlotSquaredClient.save(
                                        StringArgumentType.getString(context, "name"),
                                        IntegerArgumentType.getInteger(context, "minY"),
                                        PlotSquaredClient.DEFAULT_MAX_Y, 0))
                                .then(ClientCommands.argument("maxY", IntegerArgumentType.integer())
                                        .executes(context -> PlotSquaredClient.save(
                                                StringArgumentType.getString(context, "name"),
                                                IntegerArgumentType.getInteger(context, "minY"),
                                                IntegerArgumentType.getInteger(context, "maxY"), 0))
                                        .then(ClientCommands.argument("xzMargin", IntegerArgumentType.integer())
                                                .executes(context -> PlotSquaredClient.save(
                                                        StringArgumentType.getString(context, "name"),
                                                        IntegerArgumentType.getInteger(context, "minY"),
                                                        IntegerArgumentType.getInteger(context, "maxY"),
                                                        IntegerArgumentType.getInteger(context, "xzMargin")))))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> listCommand(String name) {
        return ClientCommands.literal(name)
                .executes(context -> list(context.getSource(), false))
                .then(ClientCommands.literal("hidden")
                        .executes(context -> list(context.getSource(), true)))
                .then(ClientCommands.literal("h")
                        .executes(context -> list(context.getSource(), true)));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> hideCommand(String name) {
        return ClientCommands.literal(name)
                .executes(context -> toggleHide(context.getSource(), null))
                .then(ClientCommands.literal("all")
                        .executes(context -> toggleAll(context.getSource(), true)))
                .then(ClientCommands.literal("a")
                        .executes(context -> toggleAll(context.getSource(), true)))
                .then(ClientCommands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                SelectiveRenderConfig.presetNames(), builder))
                        .executes(context -> toggleHide(context.getSource(),
                                StringArgumentType.getString(context, "name"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> saveCommand(String name) {
        return ClientCommands.literal(name)
                .then(ClientCommands.argument("name", StringArgumentType.word())
                        .executes(context -> save(context.getSource(), StringArgumentType.getString(context, "name"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> createCommand(String name) {
        return ClientCommands.literal(name)
                .then(ClientCommands.argument("name", StringArgumentType.word())
                        .executes(context -> createFromWorldEdit(context.getSource(),
                                StringArgumentType.getString(context, "name"), false))
                        .then(ClientCommands.literal("render")
                                .executes(context -> createFromWorldEdit(context.getSource(),
                                        StringArgumentType.getString(context, "name"), false)))
                        .then(ClientCommands.literal("hidden")
                                .executes(context -> createFromWorldEdit(context.getSource(),
                                        StringArgumentType.getString(context, "name"), true))))
                .then(ClientCommands.argument("x1", IntegerArgumentType.integer())
                .then(ClientCommands.argument("y1", IntegerArgumentType.integer())
                .then(ClientCommands.argument("z1", IntegerArgumentType.integer())
                .then(ClientCommands.argument("x2", IntegerArgumentType.integer())
                .then(ClientCommands.argument("y2", IntegerArgumentType.integer())
                .then(ClientCommands.argument("z2", IntegerArgumentType.integer())
                .then(ClientCommands.argument("name", StringArgumentType.word())
                        .executes(context -> create(context.getSource(), context, false))
                        .then(ClientCommands.literal("render")
                                .executes(context -> create(context.getSource(), context, false)))
                        .then(ClientCommands.literal("hidden")
                                .executes(context -> create(context.getSource(), context, true))))))))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> redefineCommand(String name) {
        return ClientCommands.literal(name)
                .then(ClientCommands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                SelectiveRenderConfig.presetNames(), builder))
                        .executes(context -> redefine(context.getSource(),
                                StringArgumentType.getString(context, "name"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> toggleCommand(String name) {
        return ClientCommands.literal(name)
                .executes(context -> toggle(context.getSource(), null))
                .then(ClientCommands.literal("all")
                        .executes(context -> toggleAll(context.getSource(), false)))
                .then(ClientCommands.literal("a")
                        .executes(context -> toggleAll(context.getSource(), false)))
                .then(ClientCommands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                SelectiveRenderConfig.presetNames(), builder))
                        .executes(context -> toggle(context.getSource(), StringArgumentType.getString(context, "name"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> deleteCommand(String name) {
        return ClientCommands.literal(name)
                .then(ClientCommands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                SelectiveRenderConfig.presetNames(), builder))
                        .executes(context -> delete(context.getSource(), StringArgumentType.getString(context, "name"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> renameCommand(String name) {
        return ClientCommands.literal(name)
                .then(ClientCommands.argument("oldName", StringArgumentType.word())
                        .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                SelectiveRenderConfig.presetNames(), builder))
                        .then(ClientCommands.argument("newName", StringArgumentType.word())
                                .executes(context -> rename(context.getSource(),
                                        StringArgumentType.getString(context, "oldName"),
                                        StringArgumentType.getString(context, "newName")))));
    }

    private static int setPosition(FabricClientCommandSource source, boolean isFirst) {
        BlockPos position = source.getPlayer().blockPosition();
        applyPosition(position, isFirst);
        feedback(source, positionMessage(position, isFirst));
        return Command.SINGLE_SUCCESS;
    }

    private static void setPositionFromKey(Minecraft client, boolean isFirst) {
        if (client.player == null) return;
        BlockPos position = client.player.blockPosition();
        applyPosition(position, isFirst);
        client.gui.chatListener().handleSystemMessage(positionMessage(position, isFirst), false);
    }

    private static void applyPosition(BlockPos position, boolean isFirst) {
        if (isFirst) SelectiveRenderState.setFirst(position); else SelectiveRenderState.setSecond(position);
    }

    private static MutableComponent positionMessage(BlockPos position, boolean isFirst) {
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
        if (!SelectiveRenderConfig.saveSelection(Minecraft.getInstance(), name)) {
            feedback(source, message(white("Set "), red("pos1 and pos2"), white(" first.")));
            return 0;
        }
        BlockRegion region = SelectiveRenderState.selection();
        feedback(source, message(white("Preset "), aqua(name.toLowerCase(Locale.ROOT)),
                green(" saved"), white(" · " + region.blockCount() + " blocks")));
        return Command.SINGLE_SUCCESS;
    }

    private static int create(FabricClientCommandSource source,
                              com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context,
                              boolean hidden) {
        String name = StringArgumentType.getString(context, "name");
        if (SelectiveRenderConfig.isReservedName(name)) {
            feedback(source, message(aqua(name), red(" is reserved")));
            return 0;
        }
        if (SelectiveRenderConfig.presetExists(name)) {
            feedback(source, presetExists(name));
            return 0;
        }
        int x1 = IntegerArgumentType.getInteger(context, "x1");
        int y1 = IntegerArgumentType.getInteger(context, "y1");
        int z1 = IntegerArgumentType.getInteger(context, "z1");
        int x2 = IntegerArgumentType.getInteger(context, "x2");
        int y2 = IntegerArgumentType.getInteger(context, "y2");
        int z2 = IntegerArgumentType.getInteger(context, "z2");
        BlockRegion region = new BlockRegion(Math.min(x1, x2), Math.max(x1, x2),
                Math.min(y1, y2), Math.max(y1, y2),
                Math.min(z1, z2), Math.max(z1, z2));
        return createRegion(source, name, region, hidden);
    }

    private static int createFromWorldEdit(FabricClientCommandSource source, String name, boolean hidden) {
        if (SelectiveRenderConfig.isReservedName(name)) {
            feedback(source, message(aqua(name), red(" is reserved")));
            return 0;
        }
        if (SelectiveRenderConfig.presetExists(name)) {
            feedback(source, presetExists(name));
            return 0;
        }
        BlockRegion region = WorldEditClient.selection();
        if (region == null) {
            feedback(source, message(red(WorldEditClient.problem())));
            return 0;
        }
        return createRegion(source, name, region, hidden);
    }

    private static int createRegion(FabricClientCommandSource source, String name, BlockRegion region, boolean hidden) {
        SelectiveRenderConfig.saveRegion(Minecraft.getInstance(), name, region, hidden);
        feedback(source, message(white("Preset "), aqua(name.toLowerCase(Locale.ROOT)),
                green(" created"), white(" · " + region.blockCount() + " blocks · "),
                hidden ? red("hidden") : green("render")));
        return Command.SINGLE_SUCCESS;
    }

    private static int redefine(FabricClientCommandSource source, String name) {
        if (!SelectiveRenderConfig.presetExists(name)) {
            feedback(source, missingPreset(name));
            return 0;
        }
        if (!SelectiveRenderConfig.redefinePreset(Minecraft.getInstance(), name)) {
            feedback(source, message(white("Set "), red("pos1 and pos2"), white(" first.")));
            return 0;
        }
        BlockRegion region = SelectiveRenderState.selection();
        feedback(source, message(white("Preset "), aqua(name.toLowerCase(Locale.ROOT)),
                green(" redefined"), white(" · " + region.blockCount() + " blocks")));
        return Command.SINGLE_SUCCESS;
    }

    private static int toggle(FabricClientCommandSource source, String name) {
        boolean toggled = name == null
                ? (SelectiveRenderState.plotModeActive()
                    ? togglePlotRenderingWithHidden(Minecraft.getInstance())
                    : SelectiveRenderConfig.toggleCurrent(Minecraft.getInstance()))
                : SelectiveRenderConfig.togglePreset(Minecraft.getInstance(), name);
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
            MutableComponent content = message(white("Preset "), aqua(name.toLowerCase(Locale.ROOT)),
                    white(" · "), active ? green("added") : red("removed"), white(" from render group"));
            if (!SelectiveRenderConfig.groupEnabled()) {
                content.append(white(" · group ")).append(red("disabled"));
            }
            feedback(source, content);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int delete(FabricClientCommandSource source, String name) {
        if (!SelectiveRenderConfig.deletePreset(Minecraft.getInstance(), name)) {
            feedback(source, missingPreset(name));
            return 0;
        }
        feedback(source, message(white("Preset "), aqua(name.toLowerCase(Locale.ROOT)), red(" deleted")));
        return Command.SINGLE_SUCCESS;
    }

    private static int toggleHide(FabricClientCommandSource source, String name) {
        boolean toggled = name == null
                ? SelectiveRenderConfig.toggleHiddenGroup(Minecraft.getInstance())
                : SelectiveRenderConfig.toggleHiddenPreset(Minecraft.getInstance(), name);
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
            MutableComponent content = message(white("Preset "), aqua(name.toLowerCase(Locale.ROOT)),
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
                Minecraft.getInstance(), oldName, newName);
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
                ? SelectiveRenderConfig.toggleAllHiddenPresets(Minecraft.getInstance())
                : SelectiveRenderConfig.toggleAllPresets(Minecraft.getInstance());
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

    private static void toggleFromKey(Minecraft client) {
        if (client.player == null) return;
        boolean toggled = SelectiveRenderState.plotModeActive()
                ? togglePlotRenderingWithHidden(client)
                : SelectiveRenderConfig.toggleCurrent(client);
        if (!toggled) {
            client.gui.chatListener().handleSystemMessage(message(red("No presets in the render group.")), false);
            return;
        }
        boolean enabled = SelectiveRenderState.plotModeActive()
                ? SelectiveRenderState.plotRenderingEnabled()
                : SelectiveRenderConfig.groupEnabled();
        overlay(message(white(SelectiveRenderState.plotModeActive() ? "Plot rendering " : "Render group "),
                enabled ? green("enabled") : red("disabled")));
    }

    private static void toggleHideFromKey(Minecraft client) {
        if (client.player == null) return;
        if (!SelectiveRenderConfig.toggleHiddenGroup(client)) {
            client.gui.chatListener().handleSystemMessage(message(red("No presets in the hide group.")), false);
            return;
        }
        overlay(message(white("Hide group "), SelectiveRenderConfig.hideGroupEnabled()
                ? green("enabled") : red("disabled")));
    }

    private static boolean togglePlotRenderingWithHidden(Minecraft client) {
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

    public static void overlay(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui != null) client.gui.hud.setOverlayMessage(message, false);
    }

    private static void feedback(FabricClientCommandSource source, Component message) {
        source.sendFeedback(message);
    }

    private static void listLine(FabricClientCommandSource source, Component... parts) {
        MutableComponent line = Component.empty();
        for (Component part : parts) line.append(part);
        source.sendFeedback(line);
    }

    private static MutableComponent message(Component... parts) {
        MutableComponent message = Component.literal("SR: ").withStyle(ChatFormatting.GRAY);
        for (Component part : parts) message.append(part);
        return message;
    }

    private static MutableComponent missingPreset(String name) {
        return message(white("Preset "), aqua(name.toLowerCase(Locale.ROOT)), red(" does not exist"));
    }

    private static MutableComponent presetExists(String name) {
        return message(white("Preset "), aqua(name.toLowerCase(Locale.ROOT)), red(" already exists"),
                white(" · delete or rename it first"));
    }

    private static MutableComponent white(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE);
    }

    private static MutableComponent gray(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent aqua(String text) {
        return Component.literal(text).withStyle(ChatFormatting.AQUA);
    }

    private static MutableComponent green(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GREEN);
    }

    private static MutableComponent red(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }
}
