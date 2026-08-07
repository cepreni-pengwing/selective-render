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

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(() -> SelectiveRenderConfig.load(client)));
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
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
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
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .executes(context -> toggle(context.getSource(), StringArgumentType.getString(context, "name"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> deleteCommand(String name) {
        return ClientCommandManager.literal(name)
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .executes(context -> delete(context.getSource(), StringArgumentType.getString(context, "name"))));
    }

    private static int setPosition(FabricClientCommandSource source, boolean isFirst) {
        BlockPos position = source.getPlayer().getBlockPos();
        if (isFirst) SelectiveRenderState.setFirst(position); else SelectiveRenderState.setSecond(position);
        feedback(source, (isFirst ? "Pos1" : "Pos2") + " = block "
                + position.getX() + ", " + position.getY() + ", " + position.getZ(), Formatting.AQUA);
        return Command.SINGLE_SUCCESS;
    }

    private static int save(FabricClientCommandSource source, String name) {
        if (!SelectiveRenderConfig.saveSelection(MinecraftClient.getInstance(), name)) {
            feedback(source, "Set pos1 and pos2 first.", Formatting.RED);
            return 0;
        }
        BlockRegion region = SelectiveRenderState.selection();
        feedback(source, "Preset '" + name.toLowerCase(Locale.ROOT) + "' saved with "
                + region.blockCount() + " blocks.", Formatting.GREEN);
        return Command.SINGLE_SUCCESS;
    }

    private static int toggle(FabricClientCommandSource source, String name) {
        boolean toggled = name == null
                ? SelectiveRenderConfig.toggleCurrent(MinecraftClient.getInstance())
                : SelectiveRenderConfig.togglePreset(MinecraftClient.getInstance(), name);
        if (!toggled) {
            feedback(source, name == null ? "No preset has been saved." : "Preset '" + name + "' does not exist.",
                    Formatting.RED);
            return 0;
        }
        if (name == null) {
            int count = SelectiveRenderConfig.activePresetNames().size();
            feedback(source, "Render group " + (SelectiveRenderConfig.groupEnabled() ? "enabled" : "disabled")
                            + " (" + count + (count == 1 ? " region)." : " regions)."),
                    SelectiveRenderConfig.groupEnabled() ? Formatting.GREEN : Formatting.YELLOW);
        } else {
            boolean active = SelectiveRenderConfig.isPresetActive(name);
            String suffix = SelectiveRenderConfig.groupEnabled() ? "" : " The render group is currently disabled.";
            feedback(source, "Preset '" + name.toLowerCase(Locale.ROOT) + "' "
                            + (active ? "added to" : "removed from") + " the render group." + suffix,
                    active ? Formatting.GREEN : Formatting.YELLOW);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int delete(FabricClientCommandSource source, String name) {
        if (!SelectiveRenderConfig.deletePreset(MinecraftClient.getInstance(), name)) {
            feedback(source, "Preset '" + name + "' does not exist.", Formatting.RED);
            return 0;
        }
        feedback(source, "Preset '" + name.toLowerCase(Locale.ROOT) + "' deleted.", Formatting.YELLOW);
        return Command.SINGLE_SUCCESS;
    }

    private static int toggleHide(FabricClientCommandSource source, String name) {
        boolean toggled = name == null
                ? SelectiveRenderConfig.toggleHiddenGroup(MinecraftClient.getInstance())
                : SelectiveRenderConfig.toggleHiddenPreset(MinecraftClient.getInstance(), name);
        if (!toggled) {
            feedback(source, name == null ? "No presets are in the hide group."
                    : "Preset '" + name + "' does not exist.", Formatting.RED);
            return 0;
        }
        if (name == null) {
            int count = SelectiveRenderConfig.hiddenPresetNames().size();
            feedback(source, "Hide group " + (SelectiveRenderConfig.hideGroupEnabled() ? "enabled" : "disabled")
                            + " (" + count + (count == 1 ? " region)." : " regions)."),
                    SelectiveRenderConfig.hideGroupEnabled() ? Formatting.GREEN : Formatting.YELLOW);
        } else {
            boolean hidden = SelectiveRenderConfig.isPresetHidden(name);
            String suffix = SelectiveRenderConfig.hideGroupEnabled() ? "" : " The hide group is currently disabled.";
            feedback(source, "Preset '" + name.toLowerCase(Locale.ROOT) + "' "
                            + (hidden ? "added to" : "removed from") + " the hide group." + suffix,
                    hidden ? Formatting.GREEN : Formatting.YELLOW);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int list(FabricClientCommandSource source, boolean hiddenOnly) {
        List<String> names = SelectiveRenderConfig.presetNames().stream()
                .filter(name -> SelectiveRenderConfig.isPresetHidden(name) == hiddenOnly)
                .toList();
        if (names.isEmpty()) {
            feedback(source, hiddenOnly ? "No presets are in the hide group."
                    : "No regular presets have been saved.", Formatting.YELLOW);
            return Command.SINGLE_SUCCESS;
        }
        boolean groupEnabled = hiddenOnly
                ? SelectiveRenderConfig.hideGroupEnabled() : SelectiveRenderConfig.groupEnabled();
        feedback(source, (hiddenOnly ? "Hidden regions" : "Render regions") + " — group "
                + (groupEnabled ? "enabled:" : "disabled:"), Formatting.AQUA);
        int width = names.stream().mapToInt(String::length).max().orElse(0);
        for (String name : names) {
            BlockRegion region = SelectiveRenderConfig.presetRegion(name);
            boolean member = hiddenOnly || SelectiveRenderConfig.isPresetActive(name);
            String paddedName = name + " ".repeat(width - name.length());
            feedback(source, paddedName + "  " + (member ? "[active]  " : "[inactive]")
                    + "  " + region.minX() + ", " + region.minY() + ", " + region.minZ(),
                    member ? Formatting.GREEN : Formatting.GRAY);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void toggleFromKey(MinecraftClient client) {
        if (client.player == null) return;
        if (!SelectiveRenderConfig.toggleCurrent(client)) {
            client.player.sendMessage(message("No preset has been saved.", Formatting.RED), false);
            return;
        }
        int count = SelectiveRenderConfig.activePresetNames().size();
        client.player.sendMessage(message("Render group "
                        + (SelectiveRenderConfig.groupEnabled() ? "enabled" : "disabled") + " (" + count
                        + (count == 1 ? " region)." : " regions)."),
                SelectiveRenderConfig.groupEnabled() ? Formatting.GREEN : Formatting.YELLOW), false);
    }

    private static void toggleHideFromKey(MinecraftClient client) {
        if (client.player == null) return;
        if (!SelectiveRenderConfig.toggleHiddenGroup(client)) {
            client.player.sendMessage(message("No presets are in the hide group.", Formatting.RED), false);
            return;
        }
        int count = SelectiveRenderConfig.hiddenPresetNames().size();
        client.player.sendMessage(message("Hide group "
                        + (SelectiveRenderConfig.hideGroupEnabled() ? "enabled" : "disabled") + " (" + count
                        + (count == 1 ? " region)." : " regions)."),
                SelectiveRenderConfig.hideGroupEnabled() ? Formatting.GREEN : Formatting.YELLOW), false);
    }

    private static void feedback(FabricClientCommandSource source, String message, Formatting color) {
        source.sendFeedback(message(message, color));
    }

    private static Text message(String message, Formatting color) {
        return Text.literal("[SelectiveRender] ").formatted(Formatting.GRAY)
                .append(Text.literal(message).formatted(color));
    }
}
