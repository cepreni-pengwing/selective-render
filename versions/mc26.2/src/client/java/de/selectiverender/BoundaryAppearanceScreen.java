package de.selectiverender;

import java.util.List;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;

/** Small staged appearance editor: dragging never triggers repeated chunk rebuilds. */
final class BoundaryAppearanceScreen extends Screen {
    private final Screen parent;
    private EditBox texture;
    private List<String> blocks, matches = List.of();
    private String query = "", completionBase, error = "";
    private int completionIndex = -1, opacity, left, wheelX, wheelY;
    private float hue, saturation, brightness;
    private boolean draggingWheel, draggingBrightness;
    BoundaryAppearanceScreen(Screen parent) { super(Component.literal("Boundary appearance")); this.parent = parent; }
    @Override protected void init() {
        left = (width - Math.min(300, width - 24)) / 2;
        wheelX = left + 46; wheelY = 139;
        float[] hsv = BoundaryStyle.hsv(SelectiveRenderSettings.boundaryColor());
        hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]; opacity = SelectiveRenderSettings.boundaryOpacity();
        blocks = BuiltInRegistries.BLOCK.keySet().stream().map(Object::toString).sorted().toList();
        texture = new EditBox(font, left + 78, 36, Math.min(222, width - left - 90), 20, Component.literal("Block texture"));
        texture.setMaxLength(256); texture.setValue(SelectiveRenderSettings.boundaryTexture());
        texture.setResponder(value -> { query = value; completionBase = null; completionIndex = -1; updateMatches(); });
        query = texture.getValue(); updateMatches(); addRenderableWidget(texture);
        addRenderableWidget(Button.builder(Component.literal(opacity + "%"), button -> {
            opacity = (opacity + 10) % 110; button.setMessage(Component.literal(opacity + "%"));
        }).bounds(left + 78, 62, 62, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds((width - 100) / 2, height - 26, 100, 20).build());
    }
    private void updateMatches() {
        String prefix = completionBase == null ? query : completionBase;
        matches = blocks.stream().filter(value -> value.startsWith(prefix) || (!prefix.contains(":") && value.substring(value.indexOf(':') + 1).startsWith(prefix))).limit(100).toList();
    }
    private boolean complete(int direction) {
        if (!texture.isFocused()) return false;
        if (completionBase == null) { completionBase = texture.getValue(); updateMatches(); }
        if (matches.isEmpty()) return true;
        completionIndex = Math.floorMod(completionIndex + direction, matches.size());
        String base = completionBase; int index = completionIndex;
        texture.setValue(matches.get(index)); completionBase = base; completionIndex = index;
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
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x(), y = event.y(); int button = event.button();
        if (button == 0 && texture.isFocused() && !matches.isEmpty() && x >= texture.getX() && x < texture.getX() + texture.getWidth()
                && y >= 58 && y < 58 + Math.min(6, matches.size()) * 12) {
            texture.setValue(matches.get((completionIndex < 0 ? 0 : Math.max(0, completionIndex - 5)) + (int)(y - 58) / 12)); texture.setFocused(false); return true;
        }
        if (button == 0 && pointer(x, y)) { texture.setFocused(false); return true; }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        double x = event.x(), y = event.y(); int button = event.button();
        if (button == 0 && (draggingWheel || draggingBrightness)) return pointer(x, y);
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        draggingWheel = false; draggingBrightness = false; return super.mouseReleased(event);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.key(), modifiers = event.modifiers();
        if (key == 258 && complete((modifiers & 1) == 0 ? 1 : -1)) return true;
        if (key == 257) { onClose(); return true; }
        return super.keyPressed(event);
    }
    @Override public void onClose() {
        Identifier id = Identifier.tryParse(texture.getValue().trim());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) { error = "Choose an available block ID"; return; }
        SelectiveRenderSettings.setBoundaryAppearance(BoundaryStyle.hsv(hue, saturation, brightness), id.toString(), opacity);
        minecraft.gui.setScreen(parent);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor draw, int mouseX, int mouseY, float delta) {
        draw.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        super.extractRenderState(draw, mouseX, mouseY, delta);
        draw.text(font, Component.literal("Texture"), left, 42, 0xFFFFFFFF);
        draw.text(font, Component.literal("Opacity"), left, 68, 0xFFFFFFFF);
        draw.text(font, Component.literal("Color"), left, 91, 0xFFFFFFFF);
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
        draw.text(font, Component.literal(String.format("#%06X", color)), left + 130, 158, 0xFFFFFFFF);
        draw.text(font, Component.literal("Brightness"), left + 96, 183, 0xFFFFFFFF);
        if (!error.isEmpty()) draw.text(font, Component.literal(error), left, height - 42, UiStyle.ERROR);
        if (texture.isFocused() && !matches.isEmpty()) {
            int start = completionIndex < 0 ? 0 : Math.max(0, completionIndex - 5);
            int count = Math.min(6, matches.size() - start);
            draw.fill(texture.getX(), 58, texture.getX() + texture.getWidth(), 58 + count * 12, 0xFF202020);
            for (int i = 0; i < count; i++) {
                if (start + i == completionIndex) draw.fill(texture.getX(), 58 + i * 12, texture.getX() + texture.getWidth(), 70 + i * 12, 0xFF355870);
                draw.text(font, Component.literal(matches.get(start + i)), texture.getX() + 2, 60 + i * 12, 0xFFFFFFFF);
            }
        }
    }
}
