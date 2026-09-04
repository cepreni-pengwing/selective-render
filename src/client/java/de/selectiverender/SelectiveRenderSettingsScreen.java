package de.selectiverender;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class SelectiveRenderSettingsScreen extends Screen {
    private final Screen parent;
    private ButtonWidget playerButton;
    private ButtonWidget interactionButton;
    private ButtonWidget boundaryButton;
    private ButtonWidget debugButton;
    private ButtonWidget inactiveInteractionsButton;
    private TextFieldWidget reloadThresholdField;
    private TextFieldWidget plotMinYField;

    public SelectiveRenderSettingsScreen(Screen parent) {
        super(Text.literal("Selective Render"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = width / 2 - 100;
        int y = Math.max(36, height / 2 - 105);
        playerButton = addDrawableChild(ButtonWidget.builder(playerText(), button -> {
            SelectiveRenderSettings.setPlayerVisibility(
                    SelectiveRenderSettings.playerVisibility().next());
            button.setMessage(playerText());
        }).dimensions(left, y, 200, 20).build());
        interactionButton = addDrawableChild(ButtonWidget.builder(interactionText(), button -> {
            SelectiveRenderSettings.setInteractionMode(
                    SelectiveRenderSettings.interactionMode().next());
            button.setMessage(interactionText());
        }).dimensions(left, y + 22, 200, 20).build());
        boundaryButton = addDrawableChild(ButtonWidget.builder(boundaryText(), button -> {
            SelectiveRenderSettings.setBoundaryMode(SelectiveRenderSettings.boundaryMode().next());
            button.setMessage(boundaryText());
        }).dimensions(left, y + 44, 200, 20).build());
        debugButton = addDrawableChild(ButtonWidget.builder(debugText(), button -> {
            SelectiveRenderSettings.setDebugBoxes(!SelectiveRenderSettings.debugBoxes());
            button.setMessage(debugText());
        }).dimensions(left, y + 66, 200, 20).build());
        inactiveInteractionsButton = addDrawableChild(ButtonWidget.builder(inactiveInteractionsText(), button -> {
            SelectiveRenderSettings.setFilterInteractionsWhenInactive(
                    !SelectiveRenderSettings.filterInteractionsWhenInactive());
            button.setMessage(inactiveInteractionsText());
        }).dimensions(left, y + 88, 200, 20).build());

        reloadThresholdField = integerField(left, y + 121,
                Integer.toString(SelectiveRenderSettings.fullReloadThreshold()), false);
        plotMinYField = integerField(left, y + 157,
                Integer.toString(SelectiveRenderSettings.defaultPlotMinY()), true);
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
                .dimensions(left, y + 183, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 24, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("Full reload after affected sections"),
                width / 2 - 100, reloadThresholdField.getY() - 11, 0xA0A0A0);
        context.drawTextWithShadow(textRenderer, Text.literal("Default /sr p minimum Y"),
                width / 2 - 100, plotMinYField.getY() - 11, 0xA0A0A0);
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
        if (reloadThresholdField == null || plotMinYField == null) return;
        try {
            SelectiveRenderSettings.setFullReloadThreshold(Integer.parseInt(reloadThresholdField.getText()));
        } catch (NumberFormatException ignored) { }
        try {
            SelectiveRenderSettings.setDefaultPlotMinY(Integer.parseInt(plotMinYField.getText()));
        } catch (NumberFormatException ignored) { }
    }

    private Text playerText() {
        return Text.literal("Players: " + SelectiveRenderSettings.playerVisibility().label());
    }

    private Text boundaryText() {
        return Text.literal("Boundary faces: " + SelectiveRenderSettings.boundaryMode().label());
    }

    private Text interactionText() {
        return Text.literal("Interactions: " + SelectiveRenderSettings.interactionMode().label());
    }

    private Text inactiveInteractionsText() {
        return Text.literal("Interactions while rendering is off: "
                + (SelectiveRenderSettings.filterInteractionsWhenInactive() ? "Filtered" : "Vanilla"));
    }

    private Text debugText() {
        return Text.literal("Debug boxes: " + (SelectiveRenderSettings.debugBoxes() ? "On" : "Off"));
    }
}
