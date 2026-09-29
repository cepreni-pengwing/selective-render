package de.selectiverender;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SelectiveRenderSettingsScreen extends Screen {
    private final Screen parent;
    private Button playerButton;
    private Button interactionButton;
    private Button boundaryButton;
    private Button debugButton;
    private Button inactiveInteractionsButton;
    private Button hiddenInteractionsButton;
    private Button virtualLightButton;
    private EditBox reloadThresholdField;
    private EditBox plotMinYField;

    public SelectiveRenderSettingsScreen(Screen parent) {
        super(Component.literal("Selective Render"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = width / 2 - 100;
        int y = Math.max(30, height / 2 - 132);
        playerButton = addRenderableWidget(Button.builder(playerText(), button -> {
            SelectiveRenderSettings.setPlayerVisibility(
                    SelectiveRenderSettings.playerVisibility().next());
            button.setMessage(playerText());
        }).bounds(left, y, 200, 20).build());
        interactionButton = addRenderableWidget(Button.builder(interactionText(), button -> {
            SelectiveRenderSettings.setInteractionMode(
                    SelectiveRenderSettings.interactionMode().next());
            button.setMessage(interactionText());
        }).bounds(left, y + 22, 200, 20).build());
        boundaryButton = addRenderableWidget(Button.builder(boundaryText(), button -> {
            SelectiveRenderSettings.setBoundaryMode(SelectiveRenderSettings.boundaryMode().next());
            button.setMessage(boundaryText());
        }).bounds(left, y + 44, 200, 20).build());
        debugButton = addRenderableWidget(Button.builder(debugText(), button -> {
            SelectiveRenderSettings.setDebugBoxes(!SelectiveRenderSettings.debugBoxes());
            button.setMessage(debugText());
        }).bounds(left, y + 66, 200, 20).build());
        inactiveInteractionsButton = addRenderableWidget(Button.builder(inactiveInteractionsText(), button -> {
            SelectiveRenderSettings.setFilterInteractionsWhenInactive(
                    !SelectiveRenderSettings.filterInteractionsWhenInactive());
            button.setMessage(inactiveInteractionsText());
        }).bounds(left, y + 88, 200, 20).build());
        hiddenInteractionsButton = addRenderableWidget(Button.builder(hiddenInteractionsText(), button -> {
            SelectiveRenderSettings.setInteractWithHiddenRegions(
                    !SelectiveRenderSettings.interactWithHiddenRegions());
            button.setMessage(hiddenInteractionsText());
        }).bounds(left, y + 110, 200, 20).build());
        virtualLightButton = addRenderableWidget(Button.builder(virtualLightText(), button -> {
            SelectiveRenderSettings.setVirtualLightMode(
                    SelectiveRenderSettings.virtualLightMode().next());
            button.setMessage(virtualLightText());
        }).bounds(left, y + 132, 200, 20).build());

        reloadThresholdField = integerField(left, y + 165,
                Integer.toString(SelectiveRenderSettings.fullReloadThreshold()), false);
        plotMinYField = integerField(left, y + 201,
                Integer.toString(SelectiveRenderSettings.defaultPlotMinY()), true);
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left, y + 227, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractBackground(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, 24, 0xFFFFFF);
        context.text(font, Component.literal("Full reload after affected sections"),
                width / 2 - 100, reloadThresholdField.getY() - 11, 0xA0A0A0);
        context.text(font, Component.literal("Default /sr p minimum Y"),
                width / 2 - 100, plotMinYField.getY() - 11, 0xA0A0A0);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        applyNumericSettings();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private EditBox integerField(int x, int y, String initial, boolean signed) {
        EditBox field = new EditBox(font, x, y, 200, 20, Component.empty());
        field.setMaxLength(11);
        field.setValue(initial);
        return addRenderableWidget(field);
    }

    private void applyNumericSettings() {
        if (reloadThresholdField == null || plotMinYField == null) return;
        try {
            SelectiveRenderSettings.setFullReloadThreshold(Integer.parseInt(reloadThresholdField.getValue()));
        } catch (NumberFormatException ignored) { }
        try {
            SelectiveRenderSettings.setDefaultPlotMinY(Integer.parseInt(plotMinYField.getValue()));
        } catch (NumberFormatException ignored) { }
    }

    private Component playerText() {
        return Component.literal("Players: " + SelectiveRenderSettings.playerVisibility().label());
    }

    private Component boundaryText() {
        return Component.literal("Boundary faces: " + SelectiveRenderSettings.boundaryMode().label());
    }

    private Component interactionText() {
        return Component.literal("Interactions: " + SelectiveRenderSettings.interactionMode().label());
    }

    private Component inactiveInteractionsText() {
        return Component.literal("Interactions while rendering is off: "
                + (SelectiveRenderSettings.filterInteractionsWhenInactive() ? "Filtered" : "Vanilla"));
    }

    private Component hiddenInteractionsText() {
        return Component.literal("Interactions with hidden regions: "
                + (SelectiveRenderSettings.interactWithHiddenRegions() ? "On" : "Off"));
    }

    private Component virtualLightText() {
        return Component.literal("Virtual skylight: " + SelectiveRenderSettings.virtualLightMode().label());
    }

    private Component debugText() {
        return Component.literal("Debug boxes: " + (SelectiveRenderSettings.debugBoxes() ? "On" : "Off"));
    }
}
