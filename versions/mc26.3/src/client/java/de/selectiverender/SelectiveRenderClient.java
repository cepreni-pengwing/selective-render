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
import com.mojang.blaze3d.platform.InputConstants;

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
            InputConstants.KEY_F9,
            KEY_CATEGORY);
    private static final KeyMapping HIDE_TOGGLE_KEY = new KeyMapping(
            "key.selectiverender.toggle_hide",
            InputConstants.KEY_F10,
            KEY_CATEGORY);
    private static final KeyMapping PLOT_TOGGLE_KEY = new KeyMapping(
            "key.selectiverender.toggle_plot",
            InputConstants.UNKNOWN.getValue(),
            KEY_CATEGORY);
    private static final KeyMapping PLOT_CLEAR_KEY = new KeyMapping(
            "key.selectiverender.clear_plots",
            InputConstants.UNKNOWN.getValue(),
            KEY_CATEGORY);
    private static final KeyMapping SETTINGS_KEY = new KeyMapping(
            "key.selectiverender.settings",
            InputConstants.KEY_APOSTROPHE,
            KEY_CATEGORY);
    private static final KeyMapping BLOCK_FILTERS_KEY = new KeyMapping(
            "key.selectiverender.toggle_block_filters",
            InputConstants.UNKNOWN.getValue(),
            KEY_CATEGORY);

    @Override
    public void onInitializeClient() {
        SelectiveRenderSettings.load();
        net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            if (!client) return;
            invalidateFilterMetadata();
            if (SelectiveRenderState.filteredRegionCount() > 0) SelectiveRenderState.refreshRenderer();
        });
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.fromNamespaceAndPath("selectiverender", "boundary-color");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        BoundaryColorTexture.invalidate();
                        invalidateFilterMetadata();
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
        KeyMappingHelper.registerKeyMapping(PLOT_TOGGLE_KEY);
        KeyMappingHelper.registerKeyMapping(PLOT_CLEAR_KEY);
        KeyMappingHelper.registerKeyMapping(SETTINGS_KEY);
        KeyMappingHelper.registerKeyMapping(BLOCK_FILTERS_KEY);
        ClientTickEvents.START_CLIENT_TICK.register(client -> PerformanceDiagnostics.tickStart());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            VirtualSkyLightSampler.tick(client.level);
            PlotSquaredClient.tick();
            if (BoundaryColorTexture.prepare()) SelectiveRenderState.refreshVisibilityRegions(SelectiveRenderState.traversalRegions());
            BoundaryRuntimeSmoke.tick(client);
            VisualRegressionSmoke.tick(client);
            if (PerformanceDiagnostics.tickEnd()) {
                logPerformanceContext();
                if (!PerformanceDiagnostics.enabled() && client.player != null) {
                    client.gui.chatListener().handleSystemMessage(message(green("Diagnostics finished; send logs/latest.log.")), false);
                }
            }
            while (TOGGLE_KEY.consumeClick()) toggleFromKey(client);
            while (HIDE_TOGGLE_KEY.consumeClick()) toggleHideFromKey(client);
            while (PLOT_TOGGLE_KEY.consumeClick()) PlotSquaredClient.toggle();
            while (PLOT_CLEAR_KEY.consumeClick()) PlotSquaredClient.clear();
            while (BLOCK_FILTERS_KEY.consumeClick()) toggleBlockFilters();
            while (SETTINGS_KEY.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(SelectiveRenderSettingsScreen.open(null));
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
        invalidateFilterMetadata();
        WorldEditClient.worldChanged(world);
        WORLD_SESSION.switchTo(world, () -> {
            PlotSquaredClient.leaveWorld();
            SelectiveRenderConfig.endSession();
            SelectiveRenderState.resetForDisconnect();
        }, next -> {
            SelectiveRenderConfig.beginSession(client, next);
            PlotSquaredClient.enterWorld(client, next);
        });
        // Opt-in smoke-test recording; ordinary launches never enable diagnostics.
        if (world != null && Boolean.getBoolean("selectiverender.performance.auto")
                && !PerformanceDiagnostics.enabled()) {
            PerformanceDiagnostics.start(120, LOGGER::info);
            logPerformanceContext();
        }
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command(String name) {
        return ClientCommands.literal(name)
                .then(ClientCommands.literal("pos1").requires(source -> !SelectiveRenderSettings.worldEditSelection()).executes(context -> setPosition(context.getSource(), true)))
                .then(ClientCommands.literal("pos2").requires(source -> !SelectiveRenderSettings.worldEditSelection()).executes(context -> setPosition(context.getSource(), false)))
                .then(ClientCommands.literal("1").requires(source -> !SelectiveRenderSettings.worldEditSelection()).executes(context -> setPosition(context.getSource(), true)))
                .then(ClientCommands.literal("2").requires(source -> !SelectiveRenderSettings.worldEditSelection()).executes(context -> setPosition(context.getSource(), false)))
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
                .then(renameCommand("name"))
                .then(renameCommand("n"))
                .then(plotCommand("plot"))
                .then(plotCommand("p"))
                .then(filterCommand("filter"))
                .then(filterCommand("f"))
                .then(listCommand("list"))
                .then(listCommand("l"))
                .then(diagnosticsCommand("diagnose"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> diagnosticsCommand(String name) {
        return ClientCommands.literal(name)
                .executes(context -> {
                    feedback(context.getSource(), message(white("Performance diagnostics: "),
                            aqua(PerformanceDiagnostics.enabled() ? "ON" : "OFF"),
                            white(" · /sr diagnose start [seconds] | mark LABEL | stop")));
                    return Command.SINGLE_SUCCESS;
                })
                .then(ClientCommands.literal("start")
                        .executes(context -> startPerformanceDiagnostics(context.getSource(), 120))
                        .then(ClientCommands.argument("seconds", IntegerArgumentType.integer(10, 600))
                                .executes(context -> startPerformanceDiagnostics(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "seconds")))))
                .then(ClientCommands.literal("stop").executes(context -> {
                    PerformanceDiagnostics.stop();
                    feedback(context.getSource(), message(green("Diagnostics stopped"),
                            white(" · send logs/latest.log")));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommands.literal("mark")
                        .then(ClientCommands.argument("label", StringArgumentType.greedyString())
                                .executes(context -> {
                                    PerformanceDiagnostics.mark(StringArgumentType.getString(context, "label"));
                                    logPerformanceContext();
                                    feedback(context.getSource(), message(green("Diagnostic marker saved")));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    private static int startPerformanceDiagnostics(FabricClientCommandSource source, int seconds) {
        PerformanceDiagnostics.start(seconds, LOGGER::info);
        logPerformanceContext();
        feedback(source, message(green("Diagnostics recording"), white(" · " + seconds
                + " seconds · /sr diagnose stop · output: logs/latest.log")));
        return Command.SINGLE_SUCCESS;
    }

    private static void logPerformanceContext() {
        var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        LOGGER.info("[SR performance] CONTEXT sr={} sodium={} iris={} filtering={} render={} hidden={} render_regions={} hidden_regions={} generation={} skylight={} hidden_skylight={} boundary={} debug_boxes={}",
                loader.getModContainer("selectiverender").map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("unknown"),
                loader.getModContainer("sodium").map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("absent"),
                loader.getModContainer("iris").map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("absent"),
                SelectiveRenderState.filteringActive(), SelectiveRenderState.enabled(),
                SelectiveRenderState.hideEnabled(), SelectiveRenderState.activeRegions().size(),
                SelectiveRenderState.hiddenRegions().size(), SelectiveRenderState.visibilityGeneration(),
                SelectiveRenderSettings.virtualLightMode(), SelectiveRenderSettings.hiddenVirtualLightMode(),
                SelectiveRenderSettings.boundaryMode(), SelectiveRenderSettings.debugBoxes());
        LOGGER.info("[SR performance] CACHE terrain={} sampler={} filtered_regions={}",
                TerrainSkyLightCache.INSTANCE.diagnosticStatus(),
                VirtualSkyLightSampler.diagnosticStatus(), SelectiveRenderState.filteredRegionCount());
        LOGGER.info("[SR performance] REGION_SAMPLE render={} hidden={}",
                SelectiveRenderState.activeRegions().stream().limit(4).toList(),
                SelectiveRenderState.hiddenRegions().stream().limit(4).toList());
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> filterCommand(String name) {
        return ClientCommands.literal(name)
                .then(ClientCommands.argument("region", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(SelectiveRenderConfig.regionNames(), builder))
                        .executes(context -> listBlockFilters(context.getSource(), StringArgumentType.getString(context, "region")))
                        .then(ClientCommands.literal("clear").executes(context -> clearBlockFilters(context.getSource(), StringArgumentType.getString(context, "region"))))
                        .then(ClientCommands.literal("hide").then(ClientCommands.argument("selector", StringArgumentType.greedyString())
                                .suggests((context, builder) -> suggestFilters(builder))
                                .executes(context -> setBlockFilter(context.getSource(), StringArgumentType.getString(context, "region"), BlockFilterRule.Mode.HIDE, StringArgumentType.getString(context, "selector")))))
                        .then(ClientCommands.literal("only").then(ClientCommands.argument("selector", StringArgumentType.greedyString())
                                .suggests((context, builder) -> suggestFilters(builder))
                                .executes(context -> setBlockFilter(context.getSource(), StringArgumentType.getString(context, "region"), BlockFilterRule.Mode.ONLY, StringArgumentType.getString(context, "selector"))))));
    }

    private static volatile List<String> filterSelectors;

    static List<String> filterSelectors() {
        List<String> cached = filterSelectors;
        if (cached != null) return cached;
        List<String> suggestions = new java.util.ArrayList<>();
        suggestions.add("#" + BuiltInBlockTags.BEAMS);
        BuiltInRegistries.BLOCK.keySet().forEach(id -> suggestions.add(id.toString()));
        BuiltInRegistries.BLOCK.forEach(block -> BuiltInRegistries.BLOCK.wrapAsHolder(block).tags()
                .forEach(tag -> suggestions.add("#" + tag.location())));
        cached = suggestions.stream().distinct().toList();
        filterSelectors = cached;
        return cached;
    }

    static void invalidateFilterMetadata() {
        BlockFilterMetadata.clear();
        filterSelectors = null;
    }

    static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestFilters(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        FilterCompletion.matches(builder.getRemaining(), filterSelectors()).forEach(builder::suggest);
        return builder.buildFuture();
    }

    static List<String> blockFilterSuggestions(String remaining) {
        List<String> suggestions = filterSelectors();
        int comma = remaining.lastIndexOf(',');
        String prefix = comma < 0 ? "" : remaining.substring(0, comma + 1);
        String tail = remaining.substring(comma + 1);
        String spacing = tail.substring(0, tail.length() - tail.stripLeading().length());
        String typed = tail.stripLeading();
        return suggestions.stream().distinct().map(value -> {
            if (typed.startsWith("tag:")) return value.startsWith("#")
                    ? "tag:" + value.substring(1) : null;
            if (typed.startsWith("id:")) return value.startsWith("#") ? null : "id:" + value;
            return value;
        }).filter(java.util.Objects::nonNull).map(value -> prefix + spacing + value).toList();
    }

    private static int setBlockFilter(FabricClientCommandSource source, String region,
                                      BlockFilterRule.Mode mode, String selector) {
        try {
            List<BlockFilterRule> rules = BlockFilterRule.parse(mode, selector);
            if (!SelectiveRenderConfig.setBlockFilters(Minecraft.getInstance(), region, rules)) {
                feedback(source, message(red("Cannot add filters: "), white("unknown region "), aqua(region)));
                return 0;
            }
            feedback(source, message(green("Added "), aqua(Integer.toString(rules.size())),
                    white(" " + mode.name().toLowerCase(Locale.ROOT) + " filter(s) to "), aqua(region)));
            return Command.SINGLE_SUCCESS;
        } catch (IllegalArgumentException exception) {
            feedback(source, message(red("Invalid filter: "), white("use a block ID or #tag; separate entries with commas")));
            return 0;
        }
    }

    private static int clearBlockFilters(FabricClientCommandSource source, String region) {
        if (!SelectiveRenderConfig.clearBlockFilters(Minecraft.getInstance(), region)) {
            feedback(source, message(red("Cannot clear filters: "), white("unknown region or no filters for "), aqua(region)));
            return 0;
        }
        feedback(source, message(green("Cleared "), white("block filters for "), aqua(region)));
        return Command.SINGLE_SUCCESS;
    }

    private static int listBlockFilters(FabricClientCommandSource source, String region) {
        if (!SelectiveRenderConfig.regionExists(region)) {
            feedback(source, missingRegion(region));
            return 0;
        }
        List<BlockFilterRule> rules = SelectiveRenderConfig.blockFilters(region);
        feedback(source, message(white("Filters for "), aqua(region), gray(" (" + rules.size() + ")")));
        for (BlockFilterRule rule : rules) {
            feedback(source, message(aqua(rule.mode().name().toLowerCase(Locale.ROOT)),
                    white(" " + rule.selector())));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> plotCommand(String name) {
        return ClientCommands.literal(name)
                .executes(context -> PlotSquaredClient.toggle())
                .then(ClientCommands.literal("grid").executes(context -> {
                    feedback(context.getSource(), message(white(PlotGridClient.setup(commandSelection()))));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommands.literal("clear").executes(context -> PlotSquaredClient.clear()))
                .then(ClientCommands.literal("c").executes(context -> PlotSquaredClient.clear()))
                .then(ClientCommands.argument("minY", IntegerArgumentType.integer())
                        .executes(context -> PlotSquaredClient.toggle(
                                IntegerArgumentType.getInteger(context, "minY"),
                                SelectiveRenderSettings.defaultPlotMaxY(), 0))
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
                                SelectiveRenderSettings.defaultPlotMinY(), SelectiveRenderSettings.defaultPlotMaxY(), 0))
                        .then(ClientCommands.argument("minY", IntegerArgumentType.integer())
                                .executes(context -> PlotSquaredClient.save(
                                        StringArgumentType.getString(context, "name"),
                                        IntegerArgumentType.getInteger(context, "minY"),
                                        SelectiveRenderSettings.defaultPlotMaxY(), 0))
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
                                SelectiveRenderConfig.regionNames(), builder))
                        .executes(context -> toggleHide(context.getSource(),
                                StringArgumentType.getString(context, "name"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> createCommand(String name) {
        return ClientCommands.literal(name)
                .then(ClientCommands.argument("name", StringArgumentType.word())
                        .executes(context -> createFromSelection(context.getSource(),
                                StringArgumentType.getString(context, "name"), false))
                        .then(ClientCommands.literal("render")
                                .executes(context -> createFromSelection(context.getSource(),
                                        StringArgumentType.getString(context, "name"), false)))
                        .then(ClientCommands.literal("hidden")
                                .executes(context -> createFromSelection(context.getSource(),
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
                                SelectiveRenderConfig.regionNames(), builder))
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
                                SelectiveRenderConfig.regionNames(), builder))
                        .executes(context -> toggle(context.getSource(), StringArgumentType.getString(context, "name"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> deleteCommand(String name) {
        return ClientCommands.literal(name)
                .then(ClientCommands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                SelectiveRenderConfig.regionNames(), builder))
                        .executes(context -> delete(context.getSource(), StringArgumentType.getString(context, "name"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> renameCommand(String name) {
        return ClientCommands.literal(name)
                .then(ClientCommands.argument("oldName", StringArgumentType.word())
                        .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                SelectiveRenderConfig.regionNames(), builder))
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

    private static void applyPosition(BlockPos position, boolean isFirst) {
        if (isFirst) SelectiveRenderState.setFirst(position); else SelectiveRenderState.setSecond(position);
    }

    private static MutableComponent positionMessage(BlockPos position, boolean isFirst) {
        return message(aqua(isFirst ? "Pos1" : "Pos2"), white(" = "
                + position.getX() + ", " + position.getY() + ", " + position.getZ()));
    }

    private static int create(FabricClientCommandSource source,
                              com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context,
                              boolean hidden) {
        String name = StringArgumentType.getString(context, "name");
        if (SelectiveRenderConfig.isReservedName(name)) {
            feedback(source, message(aqua(name), red(" is reserved")));
            return 0;
        }
        if (SelectiveRenderConfig.regionExists(name)) {
            feedback(source, regionExists(name));
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

    private static BlockRegion commandSelection() {
        if (SelectiveRenderSettings.worldEditSelection()) return WorldEditClient.selection();
        return SelectiveRenderState.saveSelection() ? SelectiveRenderState.selection() : null;
    }

    private static String selectionProblem() {
        return SelectiveRenderSettings.worldEditSelection()
                ? WorldEditClient.problem() : "Set both SR selection positions first.";
    }

    private static int createFromSelection(FabricClientCommandSource source, String name, boolean hidden) {
        if (SelectiveRenderConfig.isReservedName(name)) {
            feedback(source, message(aqua(name), red(" is reserved")));
            return 0;
        }
        if (SelectiveRenderConfig.regionExists(name)) {
            feedback(source, regionExists(name));
            return 0;
        }
        BlockRegion region = commandSelection();
        if (region == null) {
            feedback(source, message(red(selectionProblem())));
            return 0;
        }
        return createRegion(source, name, region, hidden);
    }

    private static int createRegion(FabricClientCommandSource source, String name, BlockRegion region, boolean hidden) {
        SelectiveRenderConfig.saveRegion(Minecraft.getInstance(), name, region, hidden);
        feedback(source, message(white("Region "), aqua(name.toLowerCase(Locale.ROOT)),
                green(" created"), white(" · " + region.blockCount() + " blocks · "),
                hidden ? red("hidden") : green("render")));
        return Command.SINGLE_SUCCESS;
    }

    private static int redefine(FabricClientCommandSource source, String name) {
        if (!SelectiveRenderConfig.regionExists(name)) {
            feedback(source, missingRegion(name));
            return 0;
        }
        BlockRegion region = commandSelection();
        if (region == null) {
            feedback(source, message(red(selectionProblem())));
            return 0;
        }
        if (!SelectiveRenderConfig.redefineRegion(Minecraft.getInstance(), name, region)) {
            feedback(source, message(white("Set "), red("pos1 and pos2"), white(" first.")));
            return 0;
        }
        feedback(source, message(white("Region "), aqua(name.toLowerCase(Locale.ROOT)),
                green(" redefined"), white(" · " + region.blockCount() + " blocks")));
        return Command.SINGLE_SUCCESS;
    }

    private static int toggle(FabricClientCommandSource source, String name) {
        boolean toggled = name == null
                ? (SelectiveRenderState.plotModeActive()
                    ? togglePlotRenderingWithHidden(Minecraft.getInstance())
                    : SelectiveRenderConfig.toggleCurrent(Minecraft.getInstance()))
                : SelectiveRenderConfig.toggleRegion(Minecraft.getInstance(), name);
        if (!toggled) {
            feedback(source, name == null
                    ? message(red("No regions in the render group."))
                    : missingRegion(name));
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
            boolean active = SelectiveRenderConfig.isRegionActive(name);
            MutableComponent content = message(white("Region "), aqua(name.toLowerCase(Locale.ROOT)),
                    white(" · "), active ? green("added") : red("removed"), white(active ? " to render group" : " from render group"));
            if (!SelectiveRenderConfig.groupEnabled()) {
                content.append(white(" · group ")).append(red("disabled"));
            }
            feedback(source, content);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int delete(FabricClientCommandSource source, String name) {
        if (!SelectiveRenderConfig.deleteRegion(Minecraft.getInstance(), name)) {
            feedback(source, missingRegion(name));
            return 0;
        }
        feedback(source, message(white("Region "), aqua(name.toLowerCase(Locale.ROOT)), red(" deleted")));
        return Command.SINGLE_SUCCESS;
    }

    private static int toggleHide(FabricClientCommandSource source, String name) {
        boolean toggled = name == null
                ? SelectiveRenderConfig.toggleHiddenGroup(Minecraft.getInstance())
                : SelectiveRenderConfig.toggleHiddenRegion(Minecraft.getInstance(), name);
        if (!toggled) {
            feedback(source, name == null ? message(red("No regions in the hide group.")) : missingRegion(name));
            return 0;
        }
        if (name == null) {
            overlay(message(white("Hide group "), SelectiveRenderConfig.hideGroupEnabled()
                    ? green("enabled") : red("disabled")));
            return Command.SINGLE_SUCCESS;
        } else {
            boolean hidden = SelectiveRenderConfig.isHiddenRegionActive(name);
            MutableComponent content = message(white("Region "), aqua(name.toLowerCase(Locale.ROOT)),
                    white(" · "), hidden ? green("added") : red("removed"), white(hidden ? " to hide group" : " from hide group"));
            if (!SelectiveRenderConfig.hideGroupEnabled()) {
                content.append(white(" · group ")).append(red("disabled"));
            }
            feedback(source, content);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int rename(FabricClientCommandSource source, String oldName, String newName) {
        SelectiveRenderConfig.RenameResult result = SelectiveRenderConfig.renameRegion(
                Minecraft.getInstance(), oldName, newName);
        if (result == SelectiveRenderConfig.RenameResult.MISSING_SOURCE) {
            feedback(source, missingRegion(oldName));
            return 0;
        }
        if (result == SelectiveRenderConfig.RenameResult.TARGET_EXISTS) {
            feedback(source, message(white("Region "), aqua(newName), red(" already exists")));
            return 0;
        }
        if (result == SelectiveRenderConfig.RenameResult.RESERVED_NAME) {
            feedback(source, message(aqua(newName), red(" is reserved")));
            return 0;
        }
        feedback(source, message(aqua(oldName.toLowerCase(Locale.ROOT)), white(" â†’ "),
                aqua(newName.toLowerCase(Locale.ROOT)), green(" renamed")));
        return Command.SINGLE_SUCCESS;
    }

    private static int toggleAll(FabricClientCommandSource source, boolean hidden) {
        boolean toggled = hidden
                ? SelectiveRenderConfig.toggleAllHiddenRegions(Minecraft.getInstance())
                : SelectiveRenderConfig.toggleAllRegions(Minecraft.getInstance());
        if (!toggled) {
            feedback(source, message(red(hidden ? "No hidden regions saved." : "No render regions saved.")));
            return 0;
        }
        boolean anyActive = hidden
                ? !SelectiveRenderConfig.hiddenRegionNames().isEmpty()
                : !SelectiveRenderConfig.activeRegionNames().isEmpty();
        feedback(source, message(aqua(hidden ? "Hidden regions" : "Render regions"), white(" · all "),
                anyActive ? green("enabled") : red("disabled")));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(FabricClientCommandSource source, boolean hiddenOnly) {
        List<String> names = SelectiveRenderConfig.regionNames().stream()
                .filter(name -> SelectiveRenderConfig.isRegionHidden(name) == hiddenOnly)
                .toList();
        if (names.isEmpty()) {
            feedback(source, message(red(hiddenOnly ? "No regions in the hide group."
                    : "No regular regions saved.")));
            return Command.SINGLE_SUCCESS;
        }
        boolean groupEnabled = hiddenOnly
                ? SelectiveRenderConfig.hideGroupEnabled() : SelectiveRenderConfig.groupEnabled();
        feedback(source, message(aqua(hiddenOnly ? "Hidden regions" : "Render regions"),
                white(" · group "), groupEnabled ? green("enabled") : red("disabled")));
        int width = names.stream().mapToInt(String::length).max().orElse(0);
        for (String name : names) {
            BlockRegion region = SelectiveRenderConfig.regionRegion(name);
            boolean member = hiddenOnly
                    ? SelectiveRenderConfig.isHiddenRegionActive(name)
                    : SelectiveRenderConfig.isRegionActive(name);
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
            client.gui.chatListener().handleSystemMessage(message(red("No regions in the render group.")), false);
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
            client.gui.chatListener().handleSystemMessage(message(red("No regions in the hide group.")), false);
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

    private static void toggleBlockFilters() {
        boolean enabled = !SelectiveRenderSettings.blockFiltersEnabled();
        SelectiveRenderSettings.setBlockFiltersEnabled(enabled);
        overlay(message(white("Block filters "), enabled ? green("enabled") : red("disabled")));
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
        MutableComponent message = Component.empty();
        for (Component part : parts) message.append(part);
        return message;
    }

    private static MutableComponent missingRegion(String name) {
        return message(white("Region "), aqua(name.toLowerCase(Locale.ROOT)), red(" does not exist"));
    }

    private static MutableComponent regionExists(String name) {
        return message(white("Region "), aqua(name.toLowerCase(Locale.ROOT)), red(" already exists"),
                white(" · delete or rename it first"));
    }

    private static MutableComponent white(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(UiStyle.TEXT));
    }

    private static MutableComponent gray(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(UiStyle.MUTED));
    }

    private static MutableComponent aqua(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(UiStyle.ACCENT));
    }

    private static MutableComponent green(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(UiStyle.SUCCESS));
    }

    private static MutableComponent red(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(UiStyle.ERROR));
    }
}
