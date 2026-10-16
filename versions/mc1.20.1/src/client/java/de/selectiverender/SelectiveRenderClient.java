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
            GLFW.GLFW_KEY_F9,
            "category.selectiverender");
    private static final KeyBinding HIDE_TOGGLE_KEY = new KeyBinding(
            "key.selectiverender.toggle_hide",
            GLFW.GLFW_KEY_F10,
            "category.selectiverender");
    private static final KeyBinding PLOT_TOGGLE_KEY = new KeyBinding(
            "key.selectiverender.toggle_plot",
            GLFW.GLFW_KEY_UNKNOWN,
            "category.selectiverender");
    private static final KeyBinding PLOT_CLEAR_KEY = new KeyBinding(
            "key.selectiverender.clear_plots",
            GLFW.GLFW_KEY_UNKNOWN,
            "category.selectiverender");
    private static final KeyBinding SETTINGS_KEY = new KeyBinding(
            "key.selectiverender.settings",
            GLFW.GLFW_KEY_APOSTROPHE,
            "category.selectiverender");
    private static final KeyBinding BLOCK_FILTERS_KEY = new KeyBinding(
            "key.selectiverender.toggle_block_filters",
            GLFW.GLFW_KEY_UNKNOWN,
            "category.selectiverender");

    @Override
    public void onInitializeClient() {
        SelectiveRenderSettings.load();
        net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            if (!client) return;
            invalidateFilterMetadata();
            if (SelectiveRenderState.filteredRegionCount() > 0) SelectiveRenderState.refreshRenderer();
        });
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return new Identifier("selectiverender", "boundary-color");
                    }

                    @Override
                    public void reload(ResourceManager manager) {
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
        KeyBindingHelper.registerKeyBinding(TOGGLE_KEY);
        KeyBindingHelper.registerKeyBinding(HIDE_TOGGLE_KEY);
        KeyBindingHelper.registerKeyBinding(PLOT_TOGGLE_KEY);
        KeyBindingHelper.registerKeyBinding(PLOT_CLEAR_KEY);
        KeyBindingHelper.registerKeyBinding(SETTINGS_KEY);
        KeyBindingHelper.registerKeyBinding(BLOCK_FILTERS_KEY);
        ClientTickEvents.START_CLIENT_TICK.register(client -> PerformanceDiagnostics.tickStart());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            VirtualSkyLightSampler.tick(client.world);
            PlotSquaredClient.tick();
            if (BoundaryColorTexture.prepare()) SelectiveRenderState.refreshVisibilityRegions(SelectiveRenderState.traversalRegions());
            BoundaryRuntimeSmoke.tick(client);
            VisualRegressionSmoke.tick(client);
            if (PerformanceDiagnostics.tickEnd()) {
                logPerformanceContext();
                if (!PerformanceDiagnostics.enabled() && client.player != null) {
                    client.inGameHud.getChatHud().addMessage(message(green("Diagnostics finished; send logs/latest.log.")));
                }
            }
            while (TOGGLE_KEY.wasPressed()) toggleFromKey(client);
            while (HIDE_TOGGLE_KEY.wasPressed()) toggleHideFromKey(client);
            while (PLOT_TOGGLE_KEY.wasPressed()) PlotSquaredClient.toggle();
            while (PLOT_CLEAR_KEY.wasPressed()) PlotSquaredClient.clear();
            while (BLOCK_FILTERS_KEY.wasPressed()) toggleBlockFilters();
            while (SETTINGS_KEY.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(SelectiveRenderSettingsScreen.open(null));
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
        return ClientCommandManager.literal(name)
                .then(ClientCommandManager.literal("pos1").requires(source -> !SelectiveRenderSettings.worldEditSelection()).executes(context -> setPosition(context.getSource(), true)))
                .then(ClientCommandManager.literal("pos2").requires(source -> !SelectiveRenderSettings.worldEditSelection()).executes(context -> setPosition(context.getSource(), false)))
                .then(ClientCommandManager.literal("1").requires(source -> !SelectiveRenderSettings.worldEditSelection()).executes(context -> setPosition(context.getSource(), true)))
                .then(ClientCommandManager.literal("2").requires(source -> !SelectiveRenderSettings.worldEditSelection()).executes(context -> setPosition(context.getSource(), false)))
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
        return ClientCommandManager.literal(name)
                .executes(context -> {
                    feedback(context.getSource(), message(white("Performance diagnostics: "),
                            aqua(PerformanceDiagnostics.enabled() ? "ON" : "OFF"),
                            white(" · /sr diagnose start [seconds] | mark LABEL | stop")));
                    return Command.SINGLE_SUCCESS;
                })
                .then(ClientCommandManager.literal("start")
                        .executes(context -> startPerformanceDiagnostics(context.getSource(), 120))
                        .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(10, 600))
                                .executes(context -> startPerformanceDiagnostics(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "seconds")))))
                .then(ClientCommandManager.literal("stop").executes(context -> {
                    PerformanceDiagnostics.stop();
                    feedback(context.getSource(), message(green("Diagnostics stopped"),
                            white(" · send logs/latest.log")));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommandManager.literal("mark")
                        .then(ClientCommandManager.argument("label", StringArgumentType.greedyString())
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
        return ClientCommandManager.literal(name)
                .then(ClientCommandManager.argument("region", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                SelectiveRenderConfig.regionNames(), builder))
                        .executes(context -> listBlockFilters(context.getSource(), StringArgumentType.getString(context, "region")))
                        .then(ClientCommandManager.literal("clear")
                                .executes(context -> clearBlockFilters(context.getSource(),
                                        StringArgumentType.getString(context, "region"))))
                        .then(ClientCommandManager.literal("hide")
                                .then(ClientCommandManager.argument("selector", StringArgumentType.greedyString())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                                blockFilterSuggestions(builder.getRemaining()), builder))
                                        .executes(context -> setBlockFilter(context.getSource(),
                                                StringArgumentType.getString(context, "region"),
                                                BlockFilterRule.Mode.HIDE,
                                                StringArgumentType.getString(context, "selector")))))
                        .then(ClientCommandManager.literal("only")
                                .then(ClientCommandManager.argument("selector", StringArgumentType.greedyString())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                                blockFilterSuggestions(builder.getRemaining()), builder))
                                        .executes(context -> setBlockFilter(context.getSource(),
                                                StringArgumentType.getString(context, "region"),
                                                BlockFilterRule.Mode.ONLY,
                                                StringArgumentType.getString(context, "selector"))))));
    }

    private static volatile List<String> filterSelectors;

    static List<String> filterSelectors() {
        List<String> cached = filterSelectors;
        if (cached != null) return cached;
        List<String> suggestions = new java.util.ArrayList<>();
        suggestions.add("#" + BuiltInBlockTags.BEAMS);
        net.minecraft.registry.Registries.BLOCK.getIds().forEach(id -> suggestions.add(id.toString()));
        net.minecraft.registry.Registries.BLOCK.forEach(block -> block.getRegistryEntry().streamTags()
                .forEach(tag -> suggestions.add("#" + tag.id())));
        cached = suggestions.stream().distinct().toList();
        filterSelectors = cached;
        return cached;
    }

    static void invalidateFilterMetadata() {
        BlockFilterMetadata.clear();
        filterSelectors = null;
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
            if (!SelectiveRenderConfig.setBlockFilters(MinecraftClient.getInstance(), region, rules)) {
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
        if (!SelectiveRenderConfig.clearBlockFilters(MinecraftClient.getInstance(), region)) {
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
        return ClientCommandManager.literal(name)
                .executes(context -> PlotSquaredClient.toggle())
                .then(ClientCommandManager.literal("grid").executes(context -> {
                    feedback(context.getSource(), message(white(PlotGridClient.setup(commandSelection()))));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommandManager.literal("clear").executes(context -> PlotSquaredClient.clear()))
                .then(ClientCommandManager.literal("c").executes(context -> PlotSquaredClient.clear()))
                .then(ClientCommandManager.argument("minY", IntegerArgumentType.integer())
                        .executes(context -> PlotSquaredClient.toggle(
                                IntegerArgumentType.getInteger(context, "minY"),
                                SelectiveRenderSettings.defaultPlotMaxY(), 0))
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
                                SelectiveRenderSettings.defaultPlotMinY(), SelectiveRenderSettings.defaultPlotMaxY(), 0))
                        .then(ClientCommandManager.argument("minY", IntegerArgumentType.integer())
                                .executes(context -> PlotSquaredClient.save(
                                        StringArgumentType.getString(context, "name"),
                                        IntegerArgumentType.getInteger(context, "minY"),
                                        SelectiveRenderSettings.defaultPlotMaxY(), 0))
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
                                SelectiveRenderConfig.regionNames(), builder))
                        .executes(context -> toggleHide(context.getSource(),
                                StringArgumentType.getString(context, "name"))));
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
                                SelectiveRenderConfig.regionNames(), builder))
                        .executes(context -> toggle(context.getSource(), StringArgumentType.getString(context, "name"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> deleteCommand(String name) {
        return ClientCommandManager.literal(name)
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                SelectiveRenderConfig.regionNames(), builder))
                        .executes(context -> delete(context.getSource(), StringArgumentType.getString(context, "name"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> renameCommand(String name) {
        return ClientCommandManager.literal(name)
                .then(ClientCommandManager.argument("oldName", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                SelectiveRenderConfig.regionNames(), builder))
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

    private static void applyPosition(BlockPos position, boolean isFirst) {
        if (isFirst) SelectiveRenderState.setFirst(position); else SelectiveRenderState.setSecond(position);
    }

    private static MutableText positionMessage(BlockPos position, boolean isFirst) {
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
        SelectiveRenderConfig.saveRegion(MinecraftClient.getInstance(), name, region, hidden);
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
        if (!SelectiveRenderConfig.redefineRegion(MinecraftClient.getInstance(), name, region)) {
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
                    ? togglePlotRenderingWithHidden(MinecraftClient.getInstance())
                    : SelectiveRenderConfig.toggleCurrent(MinecraftClient.getInstance()))
                : SelectiveRenderConfig.toggleRegion(MinecraftClient.getInstance(), name);
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
            MutableText content = message(white("Region "), aqua(name.toLowerCase(Locale.ROOT)),
                    white(" · "), active ? green("added") : red("removed"), white(active ? " to render group" : " from render group"));
            if (!SelectiveRenderConfig.groupEnabled()) {
                content.append(white(" · group ")).append(red("disabled"));
            }
            feedback(source, content);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int delete(FabricClientCommandSource source, String name) {
        if (!SelectiveRenderConfig.deleteRegion(MinecraftClient.getInstance(), name)) {
            feedback(source, missingRegion(name));
            return 0;
        }
        feedback(source, message(white("Region "), aqua(name.toLowerCase(Locale.ROOT)), red(" deleted")));
        return Command.SINGLE_SUCCESS;
    }

    private static int toggleHide(FabricClientCommandSource source, String name) {
        boolean toggled = name == null
                ? SelectiveRenderConfig.toggleHiddenGroup(MinecraftClient.getInstance())
                : SelectiveRenderConfig.toggleHiddenRegion(MinecraftClient.getInstance(), name);
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
            MutableText content = message(white("Region "), aqua(name.toLowerCase(Locale.ROOT)),
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
                MinecraftClient.getInstance(), oldName, newName);
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
        feedback(source, message(aqua(oldName.toLowerCase(Locale.ROOT)), white(" → "),
                aqua(newName.toLowerCase(Locale.ROOT)), green(" renamed")));
        return Command.SINGLE_SUCCESS;
    }

    private static int toggleAll(FabricClientCommandSource source, boolean hidden) {
        boolean toggled = hidden
                ? SelectiveRenderConfig.toggleAllHiddenRegions(MinecraftClient.getInstance())
                : SelectiveRenderConfig.toggleAllRegions(MinecraftClient.getInstance());
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

    private static void toggleFromKey(MinecraftClient client) {
        if (client.player == null) return;
        boolean toggled = SelectiveRenderState.plotModeActive()
                ? togglePlotRenderingWithHidden(client)
                : SelectiveRenderConfig.toggleCurrent(client);
        if (!toggled) {
            client.player.sendMessage(message(red("No regions in the render group.")), false);
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
            client.player.sendMessage(message(red("No regions in the hide group.")), false);
            return;
        }
        overlay(message(white("Hide group "), SelectiveRenderConfig.hideGroupEnabled()
                ? green("enabled") : red("disabled")));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> createCommand(String name) {
        return ClientCommandManager.literal(name)
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .executes(context -> createFromSelection(context.getSource(),
                                StringArgumentType.getString(context, "name"), false))
                        .then(ClientCommandManager.literal("render")
                                .executes(context -> createFromSelection(context.getSource(),
                                        StringArgumentType.getString(context, "name"), false)))
                        .then(ClientCommandManager.literal("hidden")
                                .executes(context -> createFromSelection(context.getSource(),
                                        StringArgumentType.getString(context, "name"), true))))
                .then(ClientCommandManager.argument("x1", IntegerArgumentType.integer())
                .then(ClientCommandManager.argument("y1", IntegerArgumentType.integer())
                .then(ClientCommandManager.argument("z1", IntegerArgumentType.integer())
                .then(ClientCommandManager.argument("x2", IntegerArgumentType.integer())
                .then(ClientCommandManager.argument("y2", IntegerArgumentType.integer())
                .then(ClientCommandManager.argument("z2", IntegerArgumentType.integer())
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .executes(context -> create(context.getSource(), context, false))
                        .then(ClientCommandManager.literal("render")
                                .executes(context -> create(context.getSource(), context, false)))
                        .then(ClientCommandManager.literal("hidden")
                                .executes(context -> create(context.getSource(), context, true))))))))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> redefineCommand(String name) {
        return ClientCommandManager.literal(name)
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                SelectiveRenderConfig.regionNames(), builder))
                        .executes(context -> redefine(context.getSource(),
                                StringArgumentType.getString(context, "name"))));
    }

    private static boolean togglePlotRenderingWithHidden(MinecraftClient client) {
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
        MutableText message = Text.empty();
        for (Text part : parts) message.append(part);
        return message;
    }

    private static MutableText missingRegion(String name) {
        return message(white("Region "), aqua(name.toLowerCase(Locale.ROOT)), red(" does not exist"));
    }

    private static MutableText regionExists(String name) {
        return message(white("Region "), aqua(name.toLowerCase(Locale.ROOT)), red(" already exists"),
                white(" · delete or rename it first"));
    }

    private static MutableText white(String text) {
        return Text.literal(text).styled(style -> style.withColor(UiStyle.TEXT));
    }

    private static MutableText gray(String text) {
        return Text.literal(text).styled(style -> style.withColor(UiStyle.MUTED));
    }

    private static MutableText aqua(String text) {
        return Text.literal(text).styled(style -> style.withColor(UiStyle.ACCENT));
    }

    private static MutableText green(String text) {
        return Text.literal(text).styled(style -> style.withColor(UiStyle.SUCCESS));
    }

    private static MutableText red(String text) {
        return Text.literal(text).styled(style -> style.withColor(UiStyle.ERROR));
    }
}
