package de.selectiverender;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public final class SelectiveRenderSettingsScreen extends Screen {
    private final Screen parent;
    private ButtonWidget playerButton;
    private ButtonWidget borderButton;

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
        borderButton = addDrawableChild(ButtonWidget.builder(borderText(), button -> {
            SelectiveRenderSettings.setBorderMode(SelectiveRenderSettings.borderMode().next());
            button.setMessage(borderText());
        }).dimensions(left, y + 26, 200, 20).build());
        addDrawableChild(new ColorSlider(left, y + 58, "Red", 0));
        addDrawableChild(new ColorSlider(left, y + 82, "Green", 1));
        addDrawableChild(new ColorSlider(left, y + 106, "Blue", 2));
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
                .dimensions(left, y + 142, 200, 20).build());
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

    private Text borderText() {
        return Text.literal("Region borders: " + SelectiveRenderSettings.borderMode().label());
    }

    private static final class ColorSlider extends SliderWidget {
        private final String label;
        private final int channel;

        private ColorSlider(int x, int y, String label, int channel) {
            super(x, y, 200, 20, Text.empty(), channelValue(channel) / 255.0);
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
            int red = channel == 0 ? next : SelectiveRenderSettings.borderRed();
            int green = channel == 1 ? next : SelectiveRenderSettings.borderGreen();
            int blue = channel == 2 ? next : SelectiveRenderSettings.borderBlue();
            SelectiveRenderSettings.setBorderColor(red, green, blue);
        }

        private static int channelValue(int channel) {
            return switch (channel) {
                case 0 -> SelectiveRenderSettings.borderRed();
                case 1 -> SelectiveRenderSettings.borderGreen();
                default -> SelectiveRenderSettings.borderBlue();
            };
        }
    }
}
