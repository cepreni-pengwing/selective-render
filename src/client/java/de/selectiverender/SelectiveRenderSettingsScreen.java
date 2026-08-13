package de.selectiverender;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public final class SelectiveRenderSettingsScreen extends Screen {
    private final Screen parent;
    private ButtonWidget playerButton;
    private ButtonWidget boundaryButton;
    private ButtonWidget debugButton;
    private ColorPickerWidget colorPicker;

    public SelectiveRenderSettingsScreen(Screen parent) {
        super(Text.literal("Selective Render"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = width / 2 - 100;
        int y = Math.max(32, height / 2 - 110);
        playerButton = addDrawableChild(ButtonWidget.builder(playerText(), button -> {
            SelectiveRenderSettings.setPlayerVisibility(
                    SelectiveRenderSettings.playerVisibility().next());
            button.setMessage(playerText());
        }).dimensions(left, y, 200, 20).build());
        boundaryButton = addDrawableChild(ButtonWidget.builder(boundaryText(), button -> {
            SelectiveRenderSettings.setBoundaryMode(SelectiveRenderSettings.boundaryMode().next());
            button.setMessage(boundaryText());
        }).dimensions(left, y + 24, 200, 20).build());
        debugButton = addDrawableChild(ButtonWidget.builder(debugText(), button -> {
            SelectiveRenderSettings.setDebugBoxes(!SelectiveRenderSettings.debugBoxes());
            button.setMessage(debugText());
        }).dimensions(left, y + 48, 200, 20).build());
        colorPicker = addDrawableChild(new ColorPickerWidget(left, y + 78,
                SelectiveRenderSettings.boundaryRed(),
                SelectiveRenderSettings.boundaryGreen(),
                SelectiveRenderSettings.boundaryBlue()));
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
                .dimensions(left, Math.min(height - 28, y + 174), 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (colorPicker != null) {
            SelectiveRenderSettings.setBoundaryColor(
                    colorPicker.red(), colorPicker.green(), colorPicker.blue());
        }
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

    private static final class ColorPickerWidget extends ClickableWidget {
        private static final int WHEEL_SIZE = 72;
        private static final int WHEEL_RADIUS = WHEEL_SIZE / 2;
        private static final int VALUE_X = 82;
        private static final int VALUE_WIDTH = 14;
        private float hue;
        private float saturation;
        private float value;
        private DragTarget dragTarget = DragTarget.NONE;

        private ColorPickerWidget(int x, int y, int red, int green, int blue) {
            super(x, y, 200, WHEEL_SIZE, Text.literal("Boundary color"));
            float[] hsv = rgbToHsv(red, green, blue);
            hue = hsv[0];
            saturation = hsv[1];
            value = hsv[2];
        }

        private int red() { return color() >> 16 & 0xFF; }
        private int green() { return color() >> 8 & 0xFF; }
        private int blue() { return color() & 0xFF; }

        @Override
        protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
            int centerX = getX() + WHEEL_RADIUS;
            int centerY = getY() + WHEEL_RADIUS;
            for (int offsetY = -WHEEL_RADIUS; offsetY < WHEEL_RADIUS; offsetY++) {
                for (int offsetX = -WHEEL_RADIUS; offsetX < WHEEL_RADIUS; offsetX++) {
                    double distance = Math.sqrt(offsetX * offsetX + offsetY * offsetY);
                    if (distance > WHEEL_RADIUS) continue;
                    float pixelHue = (float) (Math.atan2(offsetY, offsetX) / (Math.PI * 2.0));
                    if (pixelHue < 0.0f) pixelHue += 1.0f;
                    float pixelSaturation = (float) (distance / WHEEL_RADIUS);
                    int pixelColor = 0xFF000000
                            | MathHelper.hsvToRgb(pixelHue, pixelSaturation, 1.0f);
                    context.fill(centerX + offsetX, centerY + offsetY,
                            centerX + offsetX + 1, centerY + offsetY + 1, pixelColor);
                }
            }

            int barX = getX() + VALUE_X;
            for (int row = 0; row < WHEEL_SIZE; row++) {
                float rowValue = 1.0f - row / (float) (WHEEL_SIZE - 1);
                int rowColor = 0xFF000000 | MathHelper.hsvToRgb(hue, saturation, rowValue);
                context.fill(barX, getY() + row, barX + VALUE_WIDTH, getY() + row + 1, rowColor);
            }
            context.drawBorder(barX - 1, getY() - 1, VALUE_WIDTH + 2, WHEEL_SIZE + 2, 0xFF808080);

            int selectedX = centerX + Math.round((float) Math.cos(hue * Math.PI * 2.0)
                    * saturation * WHEEL_RADIUS);
            int selectedY = centerY + Math.round((float) Math.sin(hue * Math.PI * 2.0)
                    * saturation * WHEEL_RADIUS);
            drawMarker(context, selectedX, selectedY);
            int valueY = getY() + Math.round((1.0f - value) * (WHEEL_SIZE - 1));
            context.fill(barX - 3, valueY - 1, barX + VALUE_WIDTH + 3, valueY + 2, 0xFFFFFFFF);
            context.fill(barX - 2, valueY, barX + VALUE_WIDTH + 2, valueY + 1, 0xFF000000);

            int previewX = getX() + 110;
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, "Boundary color",
                    previewX, getY() + 6, 0xFFFFFF);
            context.fill(previewX, getY() + 22, getX() + width, getY() + 48,
                    0xFF000000 | color());
            context.drawBorder(previewX, getY() + 22, width - 110, 26, 0xFF808080);
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    String.format("#%06X", color()), previewX, getY() + 55, 0xFFFFFF);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            dragTarget = target(mouseX, mouseY);
            update(mouseX, mouseY);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
            update(mouseX, mouseY);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            dragTarget = DragTarget.NONE;
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }

        private void update(double mouseX, double mouseY) {
            if (dragTarget == DragTarget.WHEEL) {
                double dx = mouseX - (getX() + WHEEL_RADIUS);
                double dy = mouseY - (getY() + WHEEL_RADIUS);
                double distance = Math.sqrt(dx * dx + dy * dy);
                saturation = MathHelper.clamp((float) (distance / WHEEL_RADIUS), 0.0f, 1.0f);
                if (distance > 0.0) {
                    hue = (float) (Math.atan2(dy, dx) / (Math.PI * 2.0));
                    if (hue < 0.0f) hue += 1.0f;
                }
            } else if (dragTarget == DragTarget.VALUE) {
                value = MathHelper.clamp(1.0f - (float) ((mouseY - getY()) / (WHEEL_SIZE - 1)),
                        0.0f, 1.0f);
            }
        }

        private DragTarget target(double mouseX, double mouseY) {
            double dx = mouseX - (getX() + WHEEL_RADIUS);
            double dy = mouseY - (getY() + WHEEL_RADIUS);
            if (dx * dx + dy * dy <= WHEEL_RADIUS * WHEEL_RADIUS) return DragTarget.WHEEL;
            int barX = getX() + VALUE_X;
            if (mouseX >= barX && mouseX < barX + VALUE_WIDTH
                    && mouseY >= getY() && mouseY < getY() + WHEEL_SIZE) {
                return DragTarget.VALUE;
            }
            return DragTarget.NONE;
        }

        private int color() {
            return MathHelper.hsvToRgb(hue, saturation, value) & 0xFFFFFF;
        }

        private static void drawMarker(DrawContext context, int x, int y) {
            context.fill(x - 3, y - 3, x + 4, y - 2, 0xFF000000);
            context.fill(x - 3, y + 3, x + 4, y + 4, 0xFF000000);
            context.fill(x - 3, y - 2, x - 2, y + 3, 0xFF000000);
            context.fill(x + 3, y - 2, x + 4, y + 3, 0xFF000000);
            context.fill(x - 2, y - 2, x + 3, y - 1, 0xFFFFFFFF);
            context.fill(x - 2, y + 2, x + 3, y + 3, 0xFFFFFFFF);
            context.fill(x - 2, y - 1, x - 1, y + 2, 0xFFFFFFFF);
            context.fill(x + 2, y - 1, x + 3, y + 2, 0xFFFFFFFF);
        }

        private static float[] rgbToHsv(int red, int green, int blue) {
            float r = red / 255.0f;
            float g = green / 255.0f;
            float b = blue / 255.0f;
            float max = Math.max(r, Math.max(g, b));
            float min = Math.min(r, Math.min(g, b));
            float delta = max - min;
            float hue = 0.0f;
            if (delta != 0.0f) {
                if (max == r) hue = ((g - b) / delta) % 6.0f;
                else if (max == g) hue = (b - r) / delta + 2.0f;
                else hue = (r - g) / delta + 4.0f;
                hue /= 6.0f;
                if (hue < 0.0f) hue += 1.0f;
            }
            return new float[]{hue, max == 0.0f ? 0.0f : delta / max, max};
        }

        private enum DragTarget { NONE, WHEEL, VALUE }
    }

}
