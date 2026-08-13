package de.selectiverender;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class SelectiveRenderSettingsScreen extends Screen {
    private final Screen parent;
    private ButtonWidget playerButton;
    private ButtonWidget boundaryButton;
    private ButtonWidget debugButton;

    public SelectiveRenderSettingsScreen(Screen parent) {
        super(Text.literal("Selective Render"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = width / 2 - 100;
        int y = height / 4;
        playerButton = addDrawableChild(ButtonWidget.builder(playerText(), button -> {
            SelectiveRenderSettings.setPlayerVisibility(
                    SelectiveRenderSettings.playerVisibility().next());
            button.setMessage(playerText());
        }).dimensions(left, y, 200, 20).build());
        boundaryButton = addDrawableChild(ButtonWidget.builder(boundaryText(), button -> {
            SelectiveRenderSettings.setBoundaryMode(SelectiveRenderSettings.boundaryMode().next());
            button.setMessage(boundaryText());
        }).dimensions(left, y + 26, 200, 20).build());
        debugButton = addDrawableChild(ButtonWidget.builder(debugText(), button -> {
            SelectiveRenderSettings.setDebugBoxes(!SelectiveRenderSettings.debugBoxes());
            button.setMessage(debugText());
        }).dimensions(left, y + 52, 200, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
                .dimensions(left, y + 88, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    private Text playerText() {
        return Text.literal("Players: " + SelectiveRenderSettings.playerVisibility().label());
    }

    private Text boundaryText() {
        return Text.literal("Boundary faces: " + SelectiveRenderSettings.boundaryMode().label());
    }

    private Text debugText() {
        return Text.literal("Debug boxes: " + (SelectiveRenderSettings.debugBoxes() ? "On" : "Off"));
    }
}
