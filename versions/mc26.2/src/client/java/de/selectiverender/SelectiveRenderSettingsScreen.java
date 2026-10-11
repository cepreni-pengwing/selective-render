package de.selectiverender;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SelectiveRenderSettingsScreen extends Screen {
    private final Screen parent;
    private int scroll;
    private int visibleRows;
    private int contentWidth;
    private Button playerButton;
    private Button interactionButton;
    private Button boundaryButton;
    private Button waterBoundaryButton;
    private Button debugButton;
    private Button inactiveInteractionsButton;
    private Button hiddenInteractionsButton;
    private Button virtualLightButton;
    private Button hiddenVirtualLightButton;
    private EditBox reloadThresholdField;
    private EditBox plotMinYField;

    public SelectiveRenderSettingsScreen(Screen parent) {
        super(Component.literal("Selective Render"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        reloadThresholdField = null;
        plotMinYField = null;
        contentWidth = Math.min(360, width - 24);
        int left = (width - contentWidth) / 2;
        visibleRows = Math.max(1, (height - 90) / 34);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, 13 - visibleRows)));
        int y = 48;
        if (rowVisible(0)) {
        addRenderableWidget(Button.builder(UiStyle.label("Selection source: "
                + (SelectiveRenderSettings.worldEditSelection() ? "WorldEdit" : "SR positions")), button -> {
            SelectiveRenderSettings.setWorldEditSelection(!SelectiveRenderSettings.worldEditSelection());
            button.setMessage(UiStyle.label("Selection source: "
                    + (SelectiveRenderSettings.worldEditSelection() ? "WorldEdit" : "SR positions")));
        }).bounds(left, y + (0 - scroll) * 34, contentWidth, 20).build());
        }
        if (rowVisible(1)) {
        playerButton = addRenderableWidget(Button.builder(playerText(), button -> {
            SelectiveRenderSettings.setPlayerVisibility(
                    SelectiveRenderSettings.playerVisibility().next());
            button.setMessage(playerText());
        }).bounds(left, y + (1 - scroll) * 34, contentWidth, 20).build());
        }
        if (rowVisible(2)) {
        interactionButton = addRenderableWidget(Button.builder(interactionText(), button -> {
            SelectiveRenderSettings.setInteractionMode(
                    SelectiveRenderSettings.interactionMode().next());
            button.setMessage(interactionText());
        }).bounds(left, y + (2 - scroll) * 34, contentWidth, 20).build());
        }
        if (rowVisible(3)) {
        boundaryButton = addRenderableWidget(Button.builder(boundaryText(), button -> {
            SelectiveRenderSettings.setBoundaryMode(SelectiveRenderSettings.boundaryMode().next());
            button.setMessage(boundaryText());
        }).bounds(left, y + (3 - scroll) * 34, contentWidth, 20).build());
        }
        if (rowVisible(4)) {
        waterBoundaryButton = addRenderableWidget(Button.builder(waterBoundaryText(), button -> {
            SelectiveRenderSettings.setCullWaterBoundaryFaces(
                    !SelectiveRenderSettings.cullWaterBoundaryFaces());
            button.setMessage(waterBoundaryText());
        }).bounds(left, y + (4 - scroll) * 34, contentWidth, 20).build());
        }
        if (rowVisible(5)) {
        debugButton = addRenderableWidget(Button.builder(debugText(), button -> {
            SelectiveRenderSettings.setDebugBoxes(!SelectiveRenderSettings.debugBoxes());
            button.setMessage(debugText());
        }).bounds(left, y + (5 - scroll) * 34, contentWidth, 20).build());
        }
        if (rowVisible(6)) {
        inactiveInteractionsButton = addRenderableWidget(Button.builder(inactiveInteractionsText(), button -> {
            SelectiveRenderSettings.setFilterInteractionsWhenInactive(
                    !SelectiveRenderSettings.filterInteractionsWhenInactive());
            button.setMessage(inactiveInteractionsText());
        }).bounds(left, y + (6 - scroll) * 34, contentWidth, 20).build());
        }
        if (rowVisible(7)) {
        hiddenInteractionsButton = addRenderableWidget(Button.builder(hiddenInteractionsText(), button -> {
            SelectiveRenderSettings.setInteractWithHiddenRegions(
                    !SelectiveRenderSettings.interactWithHiddenRegions());
            button.setMessage(hiddenInteractionsText());
        }).bounds(left, y + (7 - scroll) * 34, contentWidth, 20).build());
        }
        if (rowVisible(8)) {
        virtualLightButton = addRenderableWidget(Button.builder(virtualLightText(), button -> {
            SelectiveRenderSettings.setVirtualLightMode(
                    SelectiveRenderSettings.virtualLightMode().next());
            button.setMessage(virtualLightText());
        }).bounds(left, y + (8 - scroll) * 34, contentWidth, 20).build());
        }
        if (rowVisible(9)) {
        hiddenVirtualLightButton = addRenderableWidget(Button.builder(hiddenVirtualLightText(), button -> {
            SelectiveRenderSettings.setHiddenVirtualLightMode(
                    SelectiveRenderSettings.hiddenVirtualLightMode().next());
            button.setMessage(hiddenVirtualLightText());
        }).bounds(left, y + (9 - scroll) * 34, contentWidth, 20).build());
        }
        if (rowVisible(10)) reloadThresholdField = integerField(left, y + (10 - scroll) * 34 + 11,
                Integer.toString(SelectiveRenderSettings.fullReloadThreshold()), false);
        if (rowVisible(11)) plotMinYField = integerField(left, y + (11 - scroll) * 34 + 11,
                Integer.toString(SelectiveRenderSettings.defaultPlotMinY()), true);
        if (rowVisible(12)) addRenderableWidget(Button.builder(UiStyle.label("Preset list"), button -> {
            applyNumericSettings();
            minecraft.gui.setScreen(new PresetListScreen(this));
        }).bounds(left, y + (12 - scroll) * 34, contentWidth, 20).build());
        var up = addRenderableWidget(Button.builder(Component.literal("Up"), button -> scrollBy(-1))
                .bounds(left, height - 26, 36, 20).build());
        up.active = scroll > 0;
        var down = addRenderableWidget(Button.builder(Component.literal("Down"), button -> scrollBy(1))
                .bounds(left + contentWidth - 36, height - 26, 36, 20).build());
        down.active = scroll + visibleRows < 13;
        addRenderableWidget(Button.builder(UiStyle.label("Done"), button -> onClose())
                .bounds(left + 40, height - 26, contentWidth - 80, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.centeredText(font, title, width / 2, 24, UiStyle.TEXT);
        if (reloadThresholdField != null) context.text(font, Component.literal("Full reload after affected sections"),
                (width - contentWidth) / 2, reloadThresholdField.getY() - 11, UiStyle.MUTED);
        if (plotMinYField != null) context.text(font, Component.literal("Default /sr p minimum Y"),
                (width - contentWidth) / 2, plotMinYField.getY() - 11, UiStyle.MUTED);
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

        if (reloadThresholdField != null) try {
            SelectiveRenderSettings.setFullReloadThreshold(Integer.parseInt(reloadThresholdField.getValue()));
        } catch (NumberFormatException ignored) { }
        if (plotMinYField != null) try {
            SelectiveRenderSettings.setDefaultPlotMinY(Integer.parseInt(plotMinYField.getValue()));
        } catch (NumberFormatException ignored) { }
    }


    private boolean rowVisible(int row) {
        return row >= scroll && row < scroll + visibleRows;
    }

    private void scrollBy(int change) {
        applyNumericSettings();
        SelectiveRenderSettingsScreen next = new SelectiveRenderSettingsScreen(parent);
        next.scroll = scroll + change;
        minecraft.gui.setScreen(next);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double amount) {
        if (amount == 0) return false;
        scrollBy(amount > 0 ? -1 : 1);
        return true;
    }

    private Component playerText() {
        return UiStyle.label("Players: " + SelectiveRenderSettings.playerVisibility().label());
    }

    private Component boundaryText() {
        return UiStyle.label("Boundary faces: " + SelectiveRenderSettings.boundaryMode().label());
    }

    private Component waterBoundaryText() {
        return UiStyle.label("Cull water boundary faces: "
                + (SelectiveRenderSettings.cullWaterBoundaryFaces() ? "On" : "Off"));
    }

    private Component interactionText() {
        return UiStyle.label("Interactions: " + SelectiveRenderSettings.interactionMode().label());
    }

    private Component inactiveInteractionsText() {
        return UiStyle.label("Interactions while rendering is off: "
                + (SelectiveRenderSettings.filterInteractionsWhenInactive() ? "Filtered" : "Vanilla"));
    }

    private Component hiddenInteractionsText() {
        return UiStyle.label("Interactions with hidden regions: "
                + (SelectiveRenderSettings.interactWithHiddenRegions() ? "On" : "Off"));
    }

    private Component virtualLightText() {
        return UiStyle.label("Virtual skylight (render): " + SelectiveRenderSettings.virtualLightMode().label());
    }

    private Component hiddenVirtualLightText() {
        return UiStyle.label("Virtual skylight (hidden): "
                + SelectiveRenderSettings.hiddenVirtualLightMode().label());
    }

    private Component debugText() {
        return UiStyle.label("Debug boxes: " + (SelectiveRenderSettings.debugBoxes() ? "On" : "Off"));
    }
}
