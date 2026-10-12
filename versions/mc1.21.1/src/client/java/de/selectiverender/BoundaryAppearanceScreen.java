package de.selectiverender;

import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;

/** Small staged appearance editor: dragging never triggers repeated chunk rebuilds. */
final class BoundaryAppearanceScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget texture;
    private List<String> blocks, matches = List.of();
    private String query = "", completionBase, error = "";
    private int completionIndex = -1, opacity, left, wheelX, wheelY;
    private float hue, saturation, brightness;
    private boolean draggingWheel, draggingBrightness;
    BoundaryAppearanceScreen(Screen parent) { super(Text.literal("Boundary appearance")); this.parent = parent; }
    @Override protected void init() {
        left = (width - Math.min(300, width - 24)) / 2;
        wheelX = left + 46; wheelY = 139;
        float[] hsv = BoundaryStyle.hsv(SelectiveRenderSettings.boundaryColor());
        hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]; opacity = SelectiveRenderSettings.boundaryOpacity();
        blocks = Registries.BLOCK.getIds().stream().map(Object::toString).sorted().toList();
        texture = new TextFieldWidget(textRenderer, left + 78, 36, Math.min(222, width - left - 90), 20, Text.literal("Block texture"));
        texture.setMaxLength(256); texture.setText(SelectiveRenderSettings.boundaryTexture());
        texture.setChangedListener(value -> { query = value; completionBase = null; completionIndex = -1; updateMatches(); });
        query = texture.getText(); updateMatches(); addDrawableChild(texture);
        addDrawableChild(ButtonWidget.builder(Text.literal(opacity + "%"), button -> {
            opacity = (opacity + 10) % 110; button.setMessage(Text.literal(opacity + "%"));
        }).dimensions(left + 78, 62, 62, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
                .dimensions((width - 100) / 2, height - 26, 100, 20).build());
    }
    private void updateMatches() {
        String prefix = completionBase == null ? query : completionBase;
        matches = blocks.stream().filter(value -> value.startsWith(prefix) || (!prefix.contains(":") && value.substring(value.indexOf(':') + 1).startsWith(prefix))).limit(100).toList();
    }
    private boolean complete(int direction) {
        if (!texture.isFocused()) return false;
        if (completionBase == null) { completionBase = texture.getText(); updateMatches(); }
        if (matches.isEmpty()) return true;
        completionIndex = Math.floorMod(completionIndex + direction, matches.size());
        String base = completionBase; int index = completionIndex;
        texture.setText(matches.get(index)); completionBase = base; completionIndex = index;
        updateMatches(); return true;
    }
    private boolean pointer(double x, double y) {
        double dx = x - wheelX, dy = y - wheelY;
        if (draggingWheel || (!draggingBrightness && dx * dx + dy * dy <= 36 * 36)) {
            hue = (float)(Math.atan2(dy, dx) / (Math.PI * 2)); saturation = Math.min(1, (float)Math.hypot(dx, dy) / 36);
            if (brightness == 0) brightness = 1; draggingWheel = true; return true;
        }
        if (draggingBrightness || (x >= left + 96 && x < left + 112 && y >= 103 && y <= 175)) {
            brightness = Math.max(0, Math.min(1, 1 - (float)(y - 103) / 72)); draggingBrightness = true; return true;
        }
        return false;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && texture.isFocused() && !matches.isEmpty() && x >= texture.getX() && x < texture.getX() + texture.getWidth()
                && y >= 58 && y < 58 + Math.min(6, matches.size()) * 12) {
            texture.setText(matches.get((completionIndex < 0 ? 0 : Math.max(0, completionIndex - 5)) + (int)(y - 58) / 12)); texture.setFocused(false); return true;
        }
        if (button == 0 && pointer(x, y)) { texture.setFocused(false); return true; }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && (draggingWheel || draggingBrightness)) return pointer(x, y);
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        draggingWheel = false; draggingBrightness = false; return super.mouseReleased(x, y, button);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 258 && complete((modifiers & 1) == 0 ? 1 : -1)) return true;
        if (key == 257) { close(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void close() {
        Identifier id = Identifier.tryParse(texture.getText().trim());
        // Invalid draft input discards all pending appearance changes, but never traps the player.
        if (id != null && Registries.BLOCK.containsId(id)) {
            SelectiveRenderSettings.setBoundaryAppearance(BoundaryStyle.hsv(hue, saturation, brightness), id.toString(), opacity);
        }
        client.setScreen(parent);
    }
    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        renderBackground(draw, mouseX, mouseY, delta);
        draw.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        super.render(draw, mouseX, mouseY, delta);
        draw.drawTextWithShadow(textRenderer, Text.literal("Texture"), left, 42, 0xFFFFFF);
        draw.drawTextWithShadow(textRenderer, Text.literal("Opacity"), left, 68, 0xFFFFFF);
        draw.drawTextWithShadow(textRenderer, Text.literal("Color"), left, 91, 0xFFFFFF);
        for (int y = -36; y < 36; y += 2) for (int x = -36; x < 36; x += 2) {
            float distance = (float)Math.hypot(x + 1, y + 1) / 36;
            if (distance <= 1) draw.fill(wheelX + x, wheelY + y, wheelX + x + 2, wheelY + y + 2,
                    0xFF000000 | BoundaryStyle.hsv((float)(Math.atan2(y + 1, x + 1) / (Math.PI * 2)), distance, 1));
        }
        for (int y = 0; y < 72; y++) draw.fill(left + 96, 103 + y, left + 112, 104 + y,
                0xFF000000 | BoundaryStyle.hsv(hue, saturation, 1 - y / 71f));
        int markerX = wheelX + Math.round((float)Math.cos(hue * Math.PI * 2) * saturation * 35);
        int markerY = wheelY + Math.round((float)Math.sin(hue * Math.PI * 2) * saturation * 35);
        draw.fill(markerX - 2, markerY - 2, markerX + 3, markerY + 3, 0xFF000000);
        draw.fill(markerX - 1, markerY - 1, markerX + 2, markerY + 2, 0xFFFFFFFF);
        int valueY = 103 + Math.round((1 - brightness) * 71);
        draw.fill(left + 94, valueY, left + 114, valueY + 1, 0xFFFFFFFF);
        int color = BoundaryStyle.hsv(hue, saturation, brightness);
        draw.fill(left + 130, 106, left + 194, 150, 0xFF000000 | color);
        draw.drawTextWithShadow(textRenderer, Text.literal(String.format("#%06X", color)), left + 130, 158, 0xFFFFFF);
        draw.drawTextWithShadow(textRenderer, Text.literal("Brightness"), left + 96, 183, 0xFFFFFF);
        if (!error.isEmpty()) draw.drawTextWithShadow(textRenderer, Text.literal(error), left, height - 42, UiStyle.ERROR);
        if (texture.isFocused() && !matches.isEmpty()) {
            int start = completionIndex < 0 ? 0 : Math.max(0, completionIndex - 5);
            int count = Math.min(6, matches.size() - start);
            draw.fill(texture.getX(), 58, texture.getX() + texture.getWidth(), 58 + count * 12, 0xFF202020);
            for (int i = 0; i < count; i++) {
                if (start + i == completionIndex) draw.fill(texture.getX(), 58 + i * 12, texture.getX() + texture.getWidth(), 70 + i * 12, 0xFF355870);
                draw.drawTextWithShadow(textRenderer, Text.literal(matches.get(start + i)), texture.getX() + 2, 60 + i * 12, 0xFFFFFF);
            }
        }
    }
}
