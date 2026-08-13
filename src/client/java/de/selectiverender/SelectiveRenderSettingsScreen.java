package de.selectiverender;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public final class SelectiveRenderSettingsScreen extends Screen {
    private final Screen parent;
    private ButtonWidget playerButton;
    private ButtonWidget boundaryButton;
    private ButtonWidget debugButton;
    private int red;
    private int green;
    private int blue;

    public SelectiveRenderSettingsScreen(Screen parent) {
        super(Text.literal("Selective Render"));
        this.parent = parent;
        red = SelectiveRenderSettings.boundaryRed();
        green = SelectiveRenderSettings.boundaryGreen();
        blue = SelectiveRenderSettings.boundaryBlue();
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
        addDrawableChild(new ColorSlider(left, y + 84, "Red", 0, red));
        addDrawableChild(new ColorSlider(left, y + 108, "Green", 1, green));
        addDrawableChild(new ColorSlider(left, y + 132, "Blue", 2, blue));
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
                .dimensions(left, y + 180, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
        int previewColor = 0xFF000000 | red << 16 | green << 8 | blue;
        context.fill(width / 2 - 100, height / 4 + 160, width / 2 - 36,
                height / 4 + 172, previewColor);
        context.drawTextWithShadow(textRenderer,
                String.format("#%02X%02X%02X", red, green, blue),
                width / 2 - 28, height / 4 + 162, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        SelectiveRenderSettings.setBoundaryColor(red, green, blue);
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

    private final class ColorSlider extends SliderWidget {
        private final String label;
        private final int channel;

        private ColorSlider(int x, int y, String label, int channel, int initialValue) {
            super(x, y, 200, 20, Text.empty(), initialValue / 255.0);
            this.label = label;
            this.channel = channel;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + ": " + (int) Math.round(value * 255.0)));
        }

        @Override
        protected void applyValue() {
            int next = (int) Math.round(value * 255.0);
            if (channel == 0) red = next;
            else if (channel == 1) green = next;
            else blue = next;
        }
    }
}
