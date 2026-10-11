package de.selectiverender;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class SelectiveRenderSettingsScreen extends Screen {
    private final Screen parent;
    private int scroll;
    private int visibleRows;
    private int contentWidth;
    private ButtonWidget playerButton;
    private ButtonWidget interactionButton;
    private ButtonWidget boundaryButton;
    private ButtonWidget waterBoundaryButton;
    private ButtonWidget debugButton;
    private ButtonWidget inactiveInteractionsButton;
    private ButtonWidget hiddenInteractionsButton;
    private ButtonWidget virtualLightButton;
    private ButtonWidget hiddenVirtualLightButton;
    private TextFieldWidget reloadThresholdField;
    private TextFieldWidget plotMinYField;

    public SelectiveRenderSettingsScreen(Screen parent) {
        super(Text.literal("Selective Render"));
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
        addDrawableChild(ButtonWidget.builder(UiStyle.label("Selection source: "
                + (SelectiveRenderSettings.worldEditSelection() ? "WorldEdit" : "SR positions")), button -> {
            SelectiveRenderSettings.setWorldEditSelection(!SelectiveRenderSettings.worldEditSelection());
            button.setMessage(UiStyle.label("Selection source: "
                    + (SelectiveRenderSettings.worldEditSelection() ? "WorldEdit" : "SR positions")));
        }).dimensions(left, y + (0 - scroll) * 34, contentWidth, 20).build());
        }
        if (rowVisible(1)) {
        playerButton = addDrawableChild(ButtonWidget.builder(playerText(), button -> {
            SelectiveRenderSettings.setPlayerVisibility(
                    SelectiveRenderSettings.playerVisibility().next());
            button.setMessage(playerText());
        }).dimensions(left, y + (1 - scroll) * 34, contentWidth, 20).build());
        }
        if (rowVisible(2)) {
        interactionButton = addDrawableChild(ButtonWidget.builder(interactionText(), button -> {
            SelectiveRenderSettings.setInteractionMode(
                    SelectiveRenderSettings.interactionMode().next());
            button.setMessage(interactionText());
        }).dimensions(left, y + (2 - scroll) * 34, contentWidth, 20).build());
        }
        if (rowVisible(3)) {
        boundaryButton = addDrawableChild(ButtonWidget.builder(boundaryText(), button -> {
            SelectiveRenderSettings.setBoundaryMode(SelectiveRenderSettings.boundaryMode().next());
            button.setMessage(boundaryText());
        }).dimensions(left, y + (3 - scroll) * 34, contentWidth, 20).build());
        }
        if (rowVisible(4)) {
        waterBoundaryButton = addDrawableChild(ButtonWidget.builder(waterBoundaryText(), button -> {
            SelectiveRenderSettings.setCullWaterBoundaryFaces(
                    !SelectiveRenderSettings.cullWaterBoundaryFaces());
            button.setMessage(waterBoundaryText());
        }).dimensions(left, y + (4 - scroll) * 34, contentWidth, 20).build());
        }
        if (rowVisible(5)) {
        debugButton = addDrawableChild(ButtonWidget.builder(debugText(), button -> {
            SelectiveRenderSettings.setDebugBoxes(!SelectiveRenderSettings.debugBoxes());
            button.setMessage(debugText());
        }).dimensions(left, y + (5 - scroll) * 34, contentWidth, 20).build());
        }
        if (rowVisible(6)) {
        inactiveInteractionsButton = addDrawableChild(ButtonWidget.builder(inactiveInteractionsText(), button -> {
            SelectiveRenderSettings.setFilterInteractionsWhenInactive(
                    !SelectiveRenderSettings.filterInteractionsWhenInactive());
            button.setMessage(inactiveInteractionsText());
        }).dimensions(left, y + (6 - scroll) * 34, contentWidth, 20).build());
        }
        if (rowVisible(7)) {
        hiddenInteractionsButton = addDrawableChild(ButtonWidget.builder(hiddenInteractionsText(), button -> {
            SelectiveRenderSettings.setInteractWithHiddenRegions(
                    !SelectiveRenderSettings.interactWithHiddenRegions());
            button.setMessage(hiddenInteractionsText());
        }).dimensions(left, y + (7 - scroll) * 34, contentWidth, 20).build());
        }
        if (rowVisible(8)) {
        virtualLightButton = addDrawableChild(ButtonWidget.builder(virtualLightText(), button -> {
            SelectiveRenderSettings.setVirtualLightMode(
                    SelectiveRenderSettings.virtualLightMode().next());
            button.setMessage(virtualLightText());
        }).dimensions(left, y + (8 - scroll) * 34, contentWidth, 20).build());
        }
        if (rowVisible(9)) {
        hiddenVirtualLightButton = addDrawableChild(ButtonWidget.builder(hiddenVirtualLightText(), button -> {
            SelectiveRenderSettings.setHiddenVirtualLightMode(
                    SelectiveRenderSettings.hiddenVirtualLightMode().next());
            button.setMessage(hiddenVirtualLightText());
        }).dimensions(left, y + (9 - scroll) * 34, contentWidth, 20).build());
        }
        if (rowVisible(10)) reloadThresholdField = integerField(left, y + (10 - scroll) * 34 + 11,
                Integer.toString(SelectiveRenderSettings.fullReloadThreshold()), false);
        if (rowVisible(11)) plotMinYField = integerField(left, y + (11 - scroll) * 34 + 11,
                Integer.toString(SelectiveRenderSettings.defaultPlotMinY()), true);
        if (rowVisible(12)) addDrawableChild(ButtonWidget.builder(UiStyle.label("Preset list"), button -> {
            applyNumericSettings();
            client.setScreen(new PresetListScreen(this));
        }).dimensions(left, y + (12 - scroll) * 34, contentWidth, 20).build());
        var up = addDrawableChild(ButtonWidget.builder(Text.literal("Up"), button -> scrollBy(-1))
                .dimensions(left, height - 26, 36, 20).build());
        up.active = scroll > 0;
        var down = addDrawableChild(ButtonWidget.builder(Text.literal("Down"), button -> scrollBy(1))
                .dimensions(left + contentWidth - 36, height - 26, 36, 20).build());
        down.active = scroll + visibleRows < 13;
        addDrawableChild(ButtonWidget.builder(UiStyle.label("Done"), button -> close())
                .dimensions(left + 40, height - 26, contentWidth - 80, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 24, UiStyle.TEXT);
        if (reloadThresholdField != null) context.drawTextWithShadow(textRenderer, Text.literal("Full reload after affected sections"),
                (width - contentWidth) / 2, reloadThresholdField.getY() - 11, UiStyle.MUTED);
        if (plotMinYField != null) context.drawTextWithShadow(textRenderer, Text.literal("Default /sr p minimum Y"),
                (width - contentWidth) / 2, plotMinYField.getY() - 11, UiStyle.MUTED);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        applyNumericSettings();
        if (client != null) client.setScreen(parent);
    }

    private TextFieldWidget integerField(int x, int y, String initial, boolean signed) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, 200, 20, Text.empty());
        field.setMaxLength(11);
        field.setTextPredicate(value -> value.isEmpty()
                || (signed && "-".equals(value))
                || value.chars().allMatch(Character::isDigit)
                || (signed && value.charAt(0) == '-'
                && value.length() > 1 && value.substring(1).chars().allMatch(Character::isDigit)));
        field.setText(initial);
        return addDrawableChild(field);
    }

    private void applyNumericSettings() {

        if (reloadThresholdField != null) try {
            SelectiveRenderSettings.setFullReloadThreshold(Integer.parseInt(reloadThresholdField.getText()));
        } catch (NumberFormatException ignored) { }
        if (plotMinYField != null) try {
            SelectiveRenderSettings.setDefaultPlotMinY(Integer.parseInt(plotMinYField.getText()));
        } catch (NumberFormatException ignored) { }
    }


    private boolean rowVisible(int row) {
        return row >= scroll && row < scroll + visibleRows;
    }

    private void scrollBy(int change) {
        applyNumericSettings();
        SelectiveRenderSettingsScreen next = new SelectiveRenderSettingsScreen(parent);
        next.scroll = scroll + change;
        client.setScreen(next);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double amount) {
        if (amount == 0) return false;
        scrollBy(amount > 0 ? -1 : 1);
        return true;
    }

    private Text playerText() {
        return UiStyle.label("Players: " + SelectiveRenderSettings.playerVisibility().label());
    }

    private Text boundaryText() {
        return UiStyle.label("Boundary faces: " + SelectiveRenderSettings.boundaryMode().label());
    }

    private Text waterBoundaryText() {
        return UiStyle.label("Cull water boundary faces: "
                + (SelectiveRenderSettings.cullWaterBoundaryFaces() ? "On" : "Off"));
    }

    private Text interactionText() {
        return UiStyle.label("Interactions: " + SelectiveRenderSettings.interactionMode().label());
    }

    private Text inactiveInteractionsText() {
        return UiStyle.label("Interactions while rendering is off: "
                + (SelectiveRenderSettings.filterInteractionsWhenInactive() ? "Filtered" : "Vanilla"));
    }

    private Text hiddenInteractionsText() {
        return UiStyle.label("Interactions with hidden regions: "
                + (SelectiveRenderSettings.interactWithHiddenRegions() ? "On" : "Off"));
    }

    private Text virtualLightText() {
        return UiStyle.label("Virtual skylight (render): " + SelectiveRenderSettings.virtualLightMode().label());
    }

    private Text hiddenVirtualLightText() {
        return UiStyle.label("Virtual skylight (hidden): "
                + SelectiveRenderSettings.hiddenVirtualLightMode().label());
    }

    private Text debugText() {
        return UiStyle.label("Debug boxes: " + (SelectiveRenderSettings.debugBoxes() ? "On" : "Off"));
    }
}
