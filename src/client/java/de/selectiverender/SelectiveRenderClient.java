package de.selectiverender;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.command.CommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

public final class SelectiveRenderClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("selectiverender");
    private static final KeyBinding TOGGLE_KEY = new KeyBinding(
            "key.selectiverender.toggle",
            GLFW.GLFW_KEY_MINUS,
            "category.selectiverender");
    private static final KeyBinding HIDE_TOGGLE_KEY = new KeyBinding(
            "key.selectiverender.toggle_hide",
            GLFW.GLFW_KEY_EQUAL,
            "category.selectiverender");

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(TOGGLE_KEY);
        KeyBindingHelper.registerKeyBinding(HIDE_TOGGLE_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE_KEY.wasPressed()) toggleFromKey(client);
            while (HIDE_TOGGLE_KEY.wasPressed()) toggleHideFromKey(client);
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(command("selectiverender"));
            dispatcher.register(command("sr"));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            SelectiveRenderConfig.reset();
            SelectiveRenderState.resetForDisconnect();
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command(String name) {
        return ClientCommandManager.literal(name)
                .then(ClientCommandManager.literal("pos1").executes(context -> setPosition(context.getSource(), true)))
                .then(ClientCommandManager.literal("pos2").executes(context -> setPosition(context.getSource(), false)))
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
                .then(listCommand("list"))
                .then(listCommand("l"));
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
        if (isFirst) SelectiveRenderState.setFirst(position); else SelectiveRenderState.setSecond(position);
        feedback(source, message(aqua(isFirst ? "Pos1" : "Pos2"), white(" = "
                + position.getX() + ", " + position.getY() + ", " + position.getZ())));
        return Command.SINGLE_SUCCESS;
    }

    private static int save(FabricClientCommandSource source, String name) {
        if (SelectiveRenderConfig.isReservedName(name)) {
            feedback(source, message(aqua(name), red(" is reserved")));
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
                ? SelectiveRenderConfig.toggleCurrent(MinecraftClient.getInstance())
                : SelectiveRenderConfig.togglePreset(MinecraftClient.getInstance(), name);
        if (!toggled) {
            feedback(source, name == null
                    ? message(red("No presets in the render group."))
                    : missingPreset(name));
            return 0;
        }
        if (name == null) {
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
        if (!SelectiveRenderConfig.toggleCurrent(client)) {
            client.player.sendMessage(message(red("No presets in the render group.")), false);
            return;
        }
    }

    private static void toggleHideFromKey(MinecraftClient client) {
        if (client.player == null) return;
        if (!SelectiveRenderConfig.toggleHiddenGroup(client)) {
            client.player.sendMessage(message(red("No presets in the hide group.")), false);
            return;
        }
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
