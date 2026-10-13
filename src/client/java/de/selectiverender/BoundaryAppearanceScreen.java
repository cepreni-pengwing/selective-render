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
    private final BoundaryEditorTarget target;
    private BoundarySliderTexture sliderTexture;
    private ButtonWidget rainbowPreset;
    private int colorTop=88, contentHeight=264;
    private boolean colorVisible, textureVisible;
    private TextFieldWidget texture;
    private List<String> blocks, matches = List.of();
    private String draftTexture, completionBase;
    private int completionIndex = -1, opacity, right, rightWidth, sliderWidth, presetLeft, presetWidth, scroll;
    private float hue, saturation, brightness;
    private boolean rainbow, initialized, draftChanged, colorChanged, textureChanged, fieldFocused;
    BoundaryAppearanceScreen(Screen parent) { this(parent,new BoundaryEditorTarget()); }
    BoundaryAppearanceScreen(Screen parent,BoundaryEditorTarget target) { super(Text.literal(target.regional()?"Region boundaries":"Selective Render")); this.parent=parent; this.target=target; }
    @Override protected void init() {
        if (!initialized) {
            float[] hsv = BoundaryStyle.hsv(target.color());
            hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]; opacity = target.opacity();
            rainbow = target.rainbow();
            draftTexture = target.texture(); scroll = target.regional()?0:SelectiveRenderSettings.menuNavigation().scroll("BOUNDARIES"); initialized = true;
        } else if (texture != null) draftTexture = texture.getText();
        blocks = Registries.BLOCK.getIds().stream().map(Object::toString).sorted().toList();
        var layout = SettingsViewport.of(width, height, 0, 0);
        int middle = layout.paneLeft(), size = Math.min(112, Math.max(68, layout.paneWidth() / 3));
        right = middle + size + 12; rightWidth = Math.max(52, layout.paneWidth() - size - 12);
        presetWidth = Math.min(80, Math.max(44, rightWidth / 4));
        sliderWidth = Math.max(32, rightWidth - presetWidth - 12); presetLeft = right + sliderWidth + 12;
        colorVisible=BoundaryStyle.color(target.mode()); textureVisible=BoundaryStyle.texture(target.mode());
        colorTop=textureVisible?88:50;
        contentHeight=Math.max(target.regional()?252:192,colorVisible?colorTop+174:textureVisible?70:36);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - (height - 34))));
        for (var tab : SelectiveRenderSettingsScreen.Tab.values()) {
            var b = addDrawableChild(ButtonWidget.builder(Text.literal(tab.label), button -> { commit(); client.setScreen(SelectiveRenderSettingsScreen.page(parent, tab)); })
                    .dimensions(layout.left(), 36 + tab.ordinal() * 26, layout.navigationWidth(), 20).build());
            b.active = tab != SelectiveRenderSettingsScreen.Tab.BOUNDARIES;
        }
        boolean colorOn = BoundaryStyle.color(target.mode());
        boolean textureOn = BoundaryStyle.texture(target.mode());
        var normal = addDrawableChild(ButtonWidget.builder(Text.literal("Normal: " + (target.normal()?"On":"Off")), b -> {
            if(!commit()) return; target.setNormal(!target.normal()); rebuild();
        }).dimensions(middle,36-scroll,size,20).build()); clip(normal);
        for (int i = 0; i < 2; i++) {
            boolean colorButton = i == 1;
            String label = (colorButton ? "Color: " : "Texture: ") + ((colorButton ? colorOn : textureOn) ? "On" : "Off");
            var b = addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> {
                if(!commit()) return;
                if(colorButton) target.setColorEnabled(!colorOn); else target.setTextureEnabled(!textureOn);
                rebuild();
            }).dimensions(middle, 62 + i * (target.regional()?60:26) - scroll, size, 20).build()); clip(b);
        }
        if(target.regional()) {
            for(int i=0;i<2;i++) {
                final boolean color=i==1;
                var inherit=addDrawableChild(ButtonWidget.builder(Text.literal("Use global"), b-> {
                    if(color) target.inheritColor(); else target.inheritTexture();
                    reloadDraft(); rebuild();
                }).dimensions(middle,88+i*60-scroll,size,20).build());
                inherit.active=color? !target.colorGlobal():!target.textureGlobal(); clip(inherit);
            }
        }
        var lighting = addDrawableChild(ButtonWidget.builder(Text.literal("Color lighting: " + (target.lighting() ? "On" : "Off")), button -> {
            if(!commit()) return; target.setLighting(!target.lighting()); rebuild();
        }).dimensions(middle, (target.regional()?186:130) - scroll, size, 20).build()); clip(lighting); lighting.visible &= colorVisible;
        var fluids = addDrawableChild(ButtonWidget.builder(Text.literal("Cull fluids: " + (target.cullFluids() ? "On" : "Off")), button -> {
            if(!commit()) return; target.setCullFluids(!target.cullFluids()); rebuild();
        }).dimensions(middle, (target.regional()?232:172) - scroll, size, 20).build()); clip(fluids);
        texture = new TextFieldWidget(textRenderer, right, 50 - scroll, rightWidth, 20, Text.literal("Block texture"));
        texture.setMaxLength(256); texture.setText(draftTexture);
        texture.setChangedListener(value -> { draftTexture = value; completionBase = null; completionIndex = -1; updateMatches(); draftChanged = true; textureChanged=true; });
        addDrawableChild(texture); clip(texture); texture.visible &= textureVisible; if(!textureVisible) texture.setFocused(false); updateMatches();
        if(colorVisible) for (int i = 0; i < 4; i++) { var bar = addDrawableChild(new Bar(i, right, colorTop + i * 38 - scroll, sliderWidth)); clip(bar); }
        String[] names = {"Rainbow", "White", "Black"};
        int[] colors = {0, 0xFFFFFF, 0};
        if(colorVisible) for (int i = 0; i < names.length; i++) {
            final int preset = i;
            var button = addDrawableChild(ButtonWidget.builder(Text.literal(names[i]), b -> {
                if (preset == 0) { rainbow = true; saturation = brightness = 1; }
                else { rainbow = false; var hsv = BoundaryStyle.hsv(colors[preset]); hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]; }
                draftChanged = true; colorChanged=true;
                if (commit()) target.setColorEnabled(true);
                rebuild();
            }).dimensions(presetLeft, colorTop + i * 26 - scroll, presetWidth, 20).build()); clip(button);
            if (i == 0) rainbowPreset = button;
        }
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
        if (id == null || !Registries.BLOCK.containsId(id)) {
            // Reject the draft, not navigation: keep the previously valid texture.
            texture.setText(target.texture()); draftTexture=target.texture();
            textureChanged=false; id=Identifier.tryParse(target.texture());
            texture.setEditableColor(0xFFFF6666);
            if(id==null) return false;
        }
        try { target.commit(BoundaryStyle.hsv(hue,saturation,brightness),id.toString(),opacity,rainbow,colorChanged,textureChanged);
            draftChanged=false; colorChanged=false; textureChanged=false; texture.setEditableColor(0xFFFFFFFF); return true;
        } catch(IllegalArgumentException error) { texture.setEditableColor(0xFFFF6666); return false; }
    }
    private void reloadDraft() {
        var hsv=BoundaryStyle.hsv(target.color()); hue=hsv[0]; saturation=hsv[1]; brightness=hsv[2];
        opacity=target.opacity(); rainbow=target.rainbow(); draftTexture=target.texture();
        if(texture!=null) texture.setText(draftTexture);
        draftChanged=false; colorChanged=false; textureChanged=false;
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
        scroll = Math.max(0, Math.min(Math.max(0, contentHeight - (height - 34)), scroll + (amount > 0 ? -24 : 24))); rebuild(); return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (texture.isFocused() && (key == 264 || key == 265)) { complete(key == 265 ? -1 : 1); setFocused(texture); texture.setFocused(true); return true; }
        if (key == 258 && complete((modifiers & 1) == 0 ? 1 : -1)) return true;
        if (key == 257 && texture.isFocused()) { commit(); texture.setFocused(false); return true; }
        boolean handled = super.keyPressed(key, scan, modifiers); if (draftChanged && !texture.isFocused()) commit(); return handled;
    }
    @Override public void close() { commit(); client.setScreen(parent); }
    @Override public void removed() { if (sliderTexture != null) { sliderTexture.close(); sliderTexture = null; } if(!target.regional()) { var s = SelectiveRenderSettings.menuNavigation(); s.tab = "BOUNDARIES"; s.scrolls.put("BOUNDARIES", scroll); SelectiveRenderSettings.saveMenuNavigation(); } super.removed(); }
    private final class Bar extends SliderWidget {
        private final int kind;
        Bar(int kind, int x, int y, int size) { super(x, y, size, 20, Text.empty(), kind == 0 ? hue : kind == 1 ? saturation : kind == 2 ? brightness : opacity / 100d); this.kind = kind; updateMessage(); }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (kind == 0 && visible && button == 0 && isMouseOver(x, y)) { rainbow = false; draftChanged = true; colorChanged=true; }
            return super.mouseClicked(x, y, button);
        }
        @Override protected void updateMessage() { setMessage(Text.literal(Math.round(value * (kind == 0 ? 360 : 100)) + (kind == 0 ? "°" : "%"))); }
        @Override protected void applyValue() { if (kind == 0) { hue = (float)value; rainbow = false; } else if (kind == 1) saturation = (float)value; else if (kind == 2) brightness = (float)value; else opacity = (int)Math.round(value * 100); draftChanged = true; colorChanged=true; }
        @Override public void renderButton(DrawContext draw, int mouseX, int mouseY, float delta) {
            draw.getMatrices().push(); draw.getMatrices().translate(0, 0, 300);
            if (sliderTexture == null) sliderTexture = new BoundarySliderTexture();
            sliderTexture.update(hue, saturation, brightness, opacity, rainbow);
            sliderTexture.draw(draw, kind, getX(), getY(), getWidth());
            int handle = getX() + (int)Math.round(value * (getWidth() - 4)); draw.fill(handle, getY(), handle + 4, getY() + 20, 0xFFEEEEEE);
            draw.drawCenteredTextWithShadow(textRenderer, getMessage(), getX() + getWidth() / 2, getY() + 6, 0xFFFFFFFF);
            draw.getMatrices().pop();
        }
    }
    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        renderBackground(draw); draw.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
        draw.enableScissor(right, 36, right + rightWidth, height - 34); if(textureVisible) draw.drawTextWithShadow(textRenderer, Text.literal("Block texture"), right, 36 - scroll, 0xFFFFFFFF);
        if(colorVisible) {
        draw.drawTextWithShadow(textRenderer, Text.literal("Presets"), presetLeft, colorTop - 14 - scroll, 0xFFFFFFFF);
        String[] labels = {"Hue", "Saturation", "Brightness", "Opacity"};
        for (int i = 0; i < 4; i++) draw.drawTextWithShadow(textRenderer, Text.literal(labels[i]), right, colorTop - 14 + i * 38 - scroll, 0xFFFFFFFF);
        if (sliderTexture == null) sliderTexture = new BoundarySliderTexture();
        sliderTexture.update(hue, saturation, brightness, opacity, rainbow);
        draw.getMatrices().push(); draw.getMatrices().translate(0, 0, 300);
        sliderTexture.draw(draw, 4, right, colorTop + 154 - scroll, sliderWidth);
        draw.getMatrices().pop();
        }
        draw.disableScissor();
        super.render(draw, mouseX, mouseY, delta);
        if (texture.visible && texture.isFocused() && !matches.isEmpty()) {
            draw.getMatrices().push(); draw.getMatrices().translate(0, 0, 400);
            int start = completionIndex < 0 ? 0 : Math.max(0, completionIndex - 5), count = Math.min(6, matches.size() - start), top = popupY(); draw.fill(right, top, right + rightWidth, top + count * 12, 0xFF202020);
            for (int i = 0; i < count; i++) { if (start + i == completionIndex) draw.fill(right, top + i * 12, right + rightWidth, top + (i + 1) * 12, 0xFF355870); draw.drawTextWithShadow(textRenderer, Text.literal(matches.get(start + i)), right + 2, top + 2 + i * 12, 0xFFFFFFFF); }
            draw.getMatrices().pop();
        }
    }
}
