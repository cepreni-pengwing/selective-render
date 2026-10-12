package de.selectiverender;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
/** Inline boundary controls. Sliders commit once on release, not per drag pixel. */
final class BoundaryAppearanceScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget texture;
    private List<String> blocks, matches = List.of();
    private String draftTexture, completionBase;
    private int completionIndex = -1, opacity, right, rightWidth, scroll;
    private float hue, saturation, brightness;
    private boolean initialized, draftChanged, fieldFocused;
    BoundaryAppearanceScreen(Screen parent) { super(Text.literal("Selective Render")); this.parent = parent; }
    @Override protected void init() {
        if (!initialized) {
            float[] hsv = BoundaryStyle.hsv(SelectiveRenderSettings.boundaryColor());
            hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]; opacity = SelectiveRenderSettings.boundaryOpacity();
            draftTexture = SelectiveRenderSettings.boundaryTexture(); scroll = SelectiveRenderSettings.menuNavigation().scroll("BOUNDARIES"); initialized = true;
        } else if (texture != null) draftTexture = texture.getText();
        blocks = Registries.BLOCK.getIds().stream().map(Object::toString).sorted().toList();
        var layout = SettingsViewport.of(width, height, 0, 0);
        int middle = layout.paneLeft(), size = Math.min(112, Math.max(68, layout.paneWidth() / 3));
        right = middle + size + 12; rightWidth = Math.max(52, layout.paneWidth() - size - 12);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, 264 - (height - 70))));
        for (var tab : SelectiveRenderSettingsScreen.Tab.values()) {
            var b = addDrawableChild(ButtonWidget.builder(Text.literal(tab.label), button -> { commit(); client.setScreen(SelectiveRenderSettingsScreen.page(parent, tab)); })
                    .dimensions(layout.left(), 36 + tab.ordinal() * 26, layout.navigationWidth(), 20).build());
            b.active = tab != SelectiveRenderSettingsScreen.Tab.BOUNDARIES;
        }
        boolean colorOn = BoundaryStyle.color(SelectiveRenderSettings.boundaryMode());
        boolean textureOn = BoundaryStyle.texture(SelectiveRenderSettings.boundaryMode());
        for (int i = 0; i < 2; i++) {
            boolean colorButton = i == 0;
            String label = (colorButton ? "Color: " : "Texture: ") + ((colorButton ? colorOn : textureOn) ? "On" : "Off");
            var b = addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> {
                commit(); SelectiveRenderSettings.setBoundaryMode(BoundaryStyle.layers(colorButton ? !colorOn : colorOn,
                        colorButton ? textureOn : !textureOn)); rebuild();
            }).dimensions(middle, 36 + i * 26 - scroll, size, 20).build()); clip(b);
        }
        var fluids = addDrawableChild(ButtonWidget.builder(Text.literal("Cull fluids: " + (SelectiveRenderSettings.cullFluidBoundaryFaces() ? "On" : "Off")), button -> {
            commit(); SelectiveRenderSettings.setCullFluidBoundaryFaces(!SelectiveRenderSettings.cullFluidBoundaryFaces()); rebuild();
        }).dimensions(middle, 104 - scroll, size, 20).build()); clip(fluids);
        texture = new TextFieldWidget(textRenderer, right, 50 - scroll, rightWidth, 20, Text.literal("Block texture"));
        texture.setMaxLength(256); texture.setText(draftTexture);
        texture.setChangedListener(value -> { draftTexture = value; completionBase = null; completionIndex = -1; updateMatches(); draftChanged = true; });
        addDrawableChild(texture); clip(texture); updateMatches();
        for (int i = 0; i < 4; i++) { var bar = addDrawableChild(new Bar(i, right, 88 + i * 38 - scroll, rightWidth)); clip(bar); }
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close()).dimensions((width - 100) / 2, height - 26, 100, 20).build());
    }
    private void clip(net.minecraft.client.gui.widget.ClickableWidget w) { w.visible = w.getY() >= 36 && w.getY() + w.getHeight() <= height - 34; }
    private void rebuild() { clearChildren(); init(); }
    private void updateMatches() {
        String prefix = completionBase == null ? draftTexture : completionBase;
        matches = blocks.stream().filter(v -> v.startsWith(prefix) || (!prefix.contains(":") && v.substring(v.indexOf(':') + 1).startsWith(prefix))).limit(100).toList();
    }
    private boolean complete(int direction) {
        if (!texture.isFocused()) return false;
        if (completionBase == null) { completionBase = texture.getText(); updateMatches(); }
        if (matches.isEmpty()) return true;
        int index = Math.floorMod(completionIndex + direction, matches.size()); String base = completionBase;
        texture.setText(matches.get(index)); completionBase = base; completionIndex = index; updateMatches(); setFocused(texture); texture.setFocused(true); return true;
    }
    private boolean commit() {
        Identifier id = Identifier.tryParse(texture.getText().trim());
        if (id == null || !Registries.BLOCK.containsId(id)) { texture.setEditableColor(0xFFFF6666); return false; }
        if (draftChanged || !id.toString().equals(SelectiveRenderSettings.boundaryTexture())) SelectiveRenderSettings.setBoundaryAppearance(BoundaryStyle.hsv(hue, saturation, brightness), id.toString(), opacity);
        draftChanged = false; texture.setEditableColor(0xFFFFFFFF); return true;
    }
    @Override public void tick() { super.tick(); if (fieldFocused && !texture.isFocused()) commit(); fieldFocused = texture.isFocused(); }
    private int popupY() { int count = Math.min(6, matches.size()); return texture.getY() + 22 + count * 12 < height - 28 ? texture.getY() + 22 : Math.max(36, texture.getY() - count * 12 - 2); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && texture.visible && texture.isFocused() && !matches.isEmpty() && x >= right && x < right + rightWidth && y >= popupY() && y < popupY() + Math.min(6, matches.size()) * 12) {
            int start = completionIndex < 0 ? 0 : Math.max(0, completionIndex - 5);
            texture.setText(matches.get(start + (int)(y - popupY()) / 12)); texture.setFocused(false); commit(); return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseReleased(double x, double y, int button) { boolean handled = super.mouseReleased(x, y, button); if (draftChanged && !texture.isFocused()) commit(); return handled; }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (amount == 0 || x < SettingsViewport.of(width, height, 0, 0).paneLeft()) return false;
        scroll = Math.max(0, Math.min(Math.max(0, 264 - (height - 70)), scroll + (amount > 0 ? -24 : 24))); rebuild(); return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (texture.isFocused() && (key == 264 || key == 265)) { complete(key == 265 ? -1 : 1); setFocused(texture); texture.setFocused(true); return true; }
        if (key == 258 && complete((modifiers & 1) == 0 ? 1 : -1)) return true;
        if (key == 257 && texture.isFocused()) { commit(); texture.setFocused(false); return true; }
        boolean handled = super.keyPressed(key, scan, modifiers); if (draftChanged && !texture.isFocused()) commit(); return handled;
    }
    @Override public void close() { commit(); client.setScreen(parent); }
    @Override public void removed() { var s = SelectiveRenderSettings.menuNavigation(); s.tab = "BOUNDARIES"; s.scrolls.put("BOUNDARIES", scroll); SelectiveRenderSettings.saveMenuNavigation(); super.removed(); }
    private static void gradient(DrawContext draw, int x, int y, int width, int height, int start, int end) {
        draw.getMatrices().push();
        draw.getMatrices().translate(x + width, y, 0);
        draw.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(90));
        draw.fillGradient(0, 0, height, width, end, start);
        draw.getMatrices().pop();
    }
    private final class Bar extends SliderWidget {
        private final int kind;
        Bar(int kind, int x, int y, int size) { super(x, y, size, 20, Text.empty(), kind == 0 ? hue : kind == 1 ? saturation : kind == 2 ? brightness : opacity / 100d); this.kind = kind; updateMessage(); }
        @Override protected void updateMessage() { setMessage(Text.literal(Math.round(value * (kind == 0 ? 360 : 100)) + (kind == 0 ? "°" : "%"))); }
        @Override protected void applyValue() { if (kind == 0) hue = (float)value; else if (kind == 1) saturation = (float)value; else if (kind == 2) brightness = (float)value; else opacity = (int)Math.round(value * 100); draftChanged = true; }
        @Override public void renderButton(DrawContext draw, int mouseX, int mouseY, float delta) {
            if (kind == 3) for (int x = 0; x < getWidth(); x += 4) for (int y = 0; y < 20; y += 4)
                draw.fill(getX() + x, getY() + y, getX() + Math.min(x + 4, getWidth()), getY() + y + 4,
                        ((x / 4 + y / 4) & 1) == 0 ? 0xFF555555 : 0xFF999999);
            if (kind == 0) {
                for (int i = 0; i < 6; i++) {
                    int left = getWidth() * i / 6, right = getWidth() * (i + 1) / 6;
                    gradient(draw, getX() + left, getY(), right - left, 20,
                            0xFF000000 | BoundaryStyle.hsv(i / 6f, 1, 1),
                            0xFF000000 | BoundaryStyle.hsv((i + 1) / 6f, 1, 1));
                }
            } else {
                int rgb = BoundaryStyle.hsv(hue, saturation, brightness);
                int start = kind == 1 ? 0xFF000000 | BoundaryStyle.hsv(hue, 0, brightness)
                        : kind == 2 ? 0xFF000000 : rgb;
                int end = kind == 1 ? 0xFF000000 | BoundaryStyle.hsv(hue, 1, brightness)
                        : kind == 2 ? 0xFF000000 | BoundaryStyle.hsv(hue, saturation, 1) : 0xFF000000 | rgb;
                gradient(draw, getX(), getY(), getWidth(), 20, start, end);
            }
            int handle = getX() + (int)Math.round(value * (getWidth() - 4)); draw.fill(handle, getY(), handle + 4, getY() + 20, 0xFFEEEEEE);
            draw.drawCenteredTextWithShadow(textRenderer, getMessage(), getX() + getWidth() / 2, getY() + 6, 0xFFFFFFFF);
        }
    }
    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        renderBackground(draw); draw.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
        draw.enableScissor(right, 36, right + rightWidth, height - 34); draw.drawTextWithShadow(textRenderer, Text.literal("Block texture"), right, 36 - scroll, 0xFFFFFFFF);
        String[] labels = {"Hue", "Saturation", "Brightness", "Opacity"};
        for (int i = 0; i < 4; i++) draw.drawTextWithShadow(textRenderer, Text.literal(labels[i]), right, 74 + i * 38 - scroll, 0xFFFFFFFF);
        draw.fill(right, 242 - scroll, right + rightWidth, 258 - scroll, BoundaryStyle.alpha(opacity) << 24 | BoundaryStyle.hsv(hue, saturation, brightness)); draw.disableScissor();
        super.render(draw, mouseX, mouseY, delta);
        if (texture.visible && texture.isFocused() && !matches.isEmpty()) {
            int start = completionIndex < 0 ? 0 : Math.max(0, completionIndex - 5), count = Math.min(6, matches.size() - start), top = popupY(); draw.fill(right, top, right + rightWidth, top + count * 12, 0xFF202020);
            for (int i = 0; i < count; i++) { if (start + i == completionIndex) draw.fill(right, top + i * 12, right + rightWidth, top + (i + 1) * 12, 0xFF355870); draw.drawTextWithShadow(textRenderer, Text.literal(matches.get(start + i)), right + 2, top + 2 + i * 12, 0xFFFFFFFF); }
        }
    }
}
