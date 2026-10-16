package de.selectiverender;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
/** Inline boundary controls. Sliders commit once on release, not per drag pixel. */
final class BoundaryAppearanceScreen extends Screen {
    private final Screen parent;
    private final BoundaryEditorTarget target;
    private BoundarySliderTexture sliderTexture;
    private Button rainbowPreset;
    private int colorTop=88, contentHeight=264;
    private boolean colorVisible, textureVisible;
    private EditBox texture;
    private List<String> blocks, matches = List.of();
    private String draftTexture, completionBase;
    private int completionIndex = -1, opacity, right, rightWidth, sliderWidth, presetLeft, presetWidth, scroll;
    private float hue, saturation, brightness;
    private boolean rainbow, initialized, draftChanged, colorChanged, textureChanged, fieldFocused;
    BoundaryAppearanceScreen(Screen parent) { this(parent,new BoundaryEditorTarget()); }
    BoundaryAppearanceScreen(Screen parent,BoundaryEditorTarget target) { super(Component.literal(target.regional()?"Region boundaries":"Selective Render")); this.parent=parent; this.target=target; }
    @Override protected void init() {
        if (!initialized) {
            float[] hsv = BoundaryStyle.hsv(target.color());
            hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]; opacity = target.opacity();
            rainbow = target.rainbow();
            draftTexture = target.texture(); scroll = target.regional()?0:SelectiveRenderSettings.menuNavigation().scroll("BOUNDARIES"); initialized = true;
        } else if (texture != null) draftTexture = texture.getValue();
        blocks = BuiltInRegistries.BLOCK.keySet().stream().map(Object::toString).sorted().toList();
        var layout = SettingsViewport.of(width, height, 0, 0);
        int middle = layout.paneLeft(), size = Math.min(112, Math.max(68, layout.paneWidth() / 3));
        right = middle + size + 12; rightWidth = Math.max(52, layout.paneWidth() - size - 12);
        presetWidth = Math.min(80, Math.max(44, rightWidth / 4));
        sliderWidth = Math.max(32, rightWidth - presetWidth - 12); presetLeft = right + sliderWidth + 12;
        colorVisible=target.colorEnabled(); textureVisible=target.textureEnabled();
        colorTop=textureVisible?88:50;
        contentHeight=Math.max(target.regional()?252:192,colorVisible?colorTop+174:textureVisible?70:36);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - (height - 34))));
        for (var tab : SelectiveRenderSettingsScreen.Tab.values()) {
            var b = addRenderableWidget(Button.builder(Component.literal(tab.label), button -> { commit(); minecraft.setScreen(SelectiveRenderSettingsScreen.page(parent, tab)); })
                    .bounds(layout.left(), 36 + tab.ordinal() * 26, layout.navigationWidth(), 20).build());
            b.active = tab != SelectiveRenderSettingsScreen.Tab.BOUNDARIES;
        }
        boolean colorOn = target.colorEnabled();
        boolean textureOn = target.textureEnabled();
        var normal = addRenderableWidget(Button.builder(Component.literal("Normal: " + (target.normal()?"On":"Off")), b -> {
            if(!commit()) return; target.setNormal(!target.normal()); rebuild();
        }).bounds(middle,36-scroll,size,20).build()); clip(normal);
        for (int i = 0; i < 2; i++) {
            boolean colorButton = i == 1;
            String label = (colorButton ? "Color: " : "Texture: ") + ((colorButton ? colorOn : textureOn) ? "On" : "Off");
            var b = addRenderableWidget(Button.builder(Component.literal(label), button -> {
                if(!commit()) return;
                if(colorButton) target.setColorEnabled(!colorOn); else target.setTextureEnabled(!textureOn);
                rebuild();
            }).bounds(middle, 62 + i * (target.regional()?60:26) - scroll, size, 20).build()); clip(b);
        }
        if(target.regional()) {
            for(int i=0;i<2;i++) {
                final boolean color=i==1;
                var inherit=addRenderableWidget(Button.builder(Component.literal("Use global"), b-> {
                    if(color) target.inheritColor(); else target.inheritTexture();
                    reloadDraft(); rebuild();
                }).bounds(middle,88+i*60-scroll,size,20).build());
                inherit.active=color? !target.colorGlobal():!target.textureGlobal(); clip(inherit);
            }
        }
        var lighting = addRenderableWidget(Button.builder(Component.literal("Shade overlay: " + (target.lighting() ? "On" : "Off")), button -> {
            if(!commit()) return; target.setLighting(!target.lighting()); rebuild();
        }).bounds(middle, (target.regional()?186:130) - scroll, size, 20).build()); clip(lighting); lighting.visible &= colorVisible;
        var fluids = addRenderableWidget(Button.builder(Component.literal("Cull fluids: " + (target.cullFluids() ? "On" : "Off")), button -> {
            if(!commit()) return; target.setCullFluids(!target.cullFluids()); rebuild();
        }).bounds(middle, (target.regional()?232:172) - scroll, size, 20).build()); clip(fluids);
        texture = new EditBox(font, right, 50 - scroll, rightWidth, 20, Component.literal("Block texture"));
        texture.setMaxLength(256); texture.setValue(draftTexture);
        texture.setResponder(value -> { draftTexture = value; completionBase = null; completionIndex = -1; updateMatches(); draftChanged = true; textureChanged=true; });
        addRenderableWidget(texture); clip(texture); texture.visible &= textureVisible; if(!textureVisible) texture.setFocused(false); updateMatches();
        if(colorVisible) for (int i = 0; i < 4; i++) { var bar = addRenderableWidget(new Bar(i, right, colorTop + i * 38 - scroll, sliderWidth)); clip(bar); }
        String[] names = {"Rainbow", "White", "Black"};
        int[] colors = {0, 0xFFFFFF, 0};
        if(colorVisible) for (int i = 0; i < names.length; i++) {
            final int preset = i;
            var button = addRenderableWidget(Button.builder(Component.literal(names[i]), b -> {
                if (preset == 0) { rainbow = true; saturation = brightness = 1; }
                else { rainbow = false; var hsv = BoundaryStyle.hsv(colors[preset]); hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]; }
                draftChanged = true; colorChanged=true;
                if (commit()) target.setColorEnabled(true);
                rebuild();
            }).bounds(presetLeft, colorTop + i * 26 - scroll, presetWidth, 20).build()); clip(button);
            if (i == 0) rainbowPreset = button;
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose()).bounds((width - 100) / 2, height - 26, 100, 20).build());
    }
    private void clip(net.minecraft.client.gui.components.AbstractWidget w) { w.visible = w.getY() >= 36 && w.getY() + w.getHeight() <= height - 34; }
    private void rebuild() { clearWidgets(); init(); }
    private void updateMatches() {
        String prefix = completionBase == null ? draftTexture : completionBase;
        matches = blocks.stream().filter(v -> v.startsWith(prefix) || (!prefix.contains(":") && v.substring(v.indexOf(':') + 1).startsWith(prefix))).limit(100).toList();
    }
    private boolean complete(int direction) {
        if (!texture.isFocused()) return false;
        if (completionBase == null) { completionBase = texture.getValue(); updateMatches(); }
        if (matches.isEmpty()) return true;
        int index = Math.floorMod(completionIndex + direction, matches.size()); String base = completionBase;
        texture.setValue(matches.get(index)); completionBase = base; completionIndex = index; updateMatches(); setFocused(texture); texture.setFocused(true); return true;
    }
    private boolean commit() {
        Identifier id = Identifier.tryParse(texture.getValue().trim());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            // Reject the draft, not navigation: keep the previously valid texture.
            texture.setValue(target.texture()); draftTexture=target.texture();
            textureChanged=false; id=Identifier.tryParse(target.texture());
            texture.setTextColor(0xFFFF6666);
            if(id==null) return false;
        }
        try { target.commit(BoundaryStyle.hsv(hue,saturation,brightness),id.toString(),opacity,rainbow,colorChanged,textureChanged);
            draftChanged=false; colorChanged=false; textureChanged=false; texture.setTextColor(0xFFFFFFFF); return true;
        } catch(IllegalArgumentException error) { texture.setTextColor(0xFFFF6666); return false; }
    }
    private void reloadDraft() {
        var hsv=BoundaryStyle.hsv(target.color()); hue=hsv[0]; saturation=hsv[1]; brightness=hsv[2];
        opacity=target.opacity(); rainbow=target.rainbow(); draftTexture=target.texture();
        if(texture!=null) texture.setValue(draftTexture);
        draftChanged=false; colorChanged=false; textureChanged=false;
    }

    @Override public void tick() { super.tick(); if (fieldFocused && !texture.isFocused()) commit(); fieldFocused = texture.isFocused(); }
    private int popupY() { int count = Math.min(6, matches.size()); return texture.getY() + 22 + count * 12 < height - 28 ? texture.getY() + 22 : Math.max(36, texture.getY() - count * 12 - 2); }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double x = event.x(), y = event.y(); int button = event.button();
        if (button == 0 && texture.visible && texture.isFocused() && !matches.isEmpty() && x >= right && x < right + rightWidth && y >= popupY() && y < popupY() + Math.min(6, matches.size()) * 12) {
            int start = completionIndex < 0 ? 0 : Math.max(0, completionIndex - 5);
            texture.setValue(matches.get(start + (int)(y - popupY()) / 12)); texture.setFocused(false); commit(); return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) { boolean handled = super.mouseReleased(event); if (draftChanged && !texture.isFocused()) commit(); return handled; }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double amount) {
        if (amount == 0 || x < SettingsViewport.of(width, height, 0, 0).paneLeft()) return false;
        scroll = Math.max(0, Math.min(Math.max(0, contentHeight - (height - 34)), scroll + (amount > 0 ? -24 : 24))); rebuild(); return true;
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        if (texture.isFocused() && (key == com.mojang.blaze3d.platform.InputConstants.KEY_DOWN || key == com.mojang.blaze3d.platform.InputConstants.KEY_UP)) { complete(key == com.mojang.blaze3d.platform.InputConstants.KEY_UP ? -1 : 1); setFocused(texture); texture.setFocused(true); return true; }
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_TAB && complete(!event.hasShiftDown() ? 1 : -1)) return true;
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN && texture.isFocused()) { commit(); texture.setFocused(false); return true; }
        boolean handled = super.keyPressed(event); if (draftChanged && !texture.isFocused()) commit(); return handled;
    }
    @Override public void onClose() { commit(); minecraft.setScreen(parent); }
    @Override public void removed() { if (sliderTexture != null) { sliderTexture.close(); sliderTexture = null; } if(!target.regional()) { var s = SelectiveRenderSettings.menuNavigation(); s.tab = "BOUNDARIES"; s.scrolls.put("BOUNDARIES", scroll); SelectiveRenderSettings.saveMenuNavigation(); } super.removed(); }
    private final class Bar extends AbstractSliderButton {
        private final int kind;
        Bar(int kind, int x, int y, int size) { super(x, y, size, 20, Component.empty(), kind == 0 ? hue : kind == 1 ? saturation : kind == 2 ? brightness : opacity / 100d); this.kind = kind; updateMessage(); }
        @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
            if (kind == 0 && visible && isMouseOver(event.x(), event.y())) { rainbow = false; draftChanged = true; colorChanged=true; }
            return super.mouseClicked(event, doubleClick);
        }
        @Override protected void updateMessage() { setMessage(Component.literal(Math.round(value * (kind == 0 ? 360 : 100)) + (kind == 0 ? "°" : "%"))); }
        @Override protected void applyValue() { if (kind == 0) { hue = (float)value; rainbow = false; } else if (kind == 1) saturation = (float)value; else if (kind == 2) brightness = (float)value; else opacity = (int)Math.round(value * 100); draftChanged = true; colorChanged=true; }
        @Override public void extractWidgetRenderState(GuiGraphicsExtractor draw, int mouseX, int mouseY, float delta) {
            if (sliderTexture == null) sliderTexture = new BoundarySliderTexture();
            sliderTexture.update(hue, saturation, brightness, opacity, rainbow);
            sliderTexture.draw(draw, kind, getX(), getY(), getWidth());
            int handle = getX() + (int)Math.round(value * (getWidth() - 4)); draw.fill(handle, getY(), handle + 4, getY() + 20, 0xFFEEEEEE);
            draw.centeredText(font, getMessage(), getX() + getWidth() / 2, getY() + 6, 0xFFFFFFFF);
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor draw, int mouseX, int mouseY, float delta) {
        draw.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        draw.enableScissor(right, 36, right + rightWidth, height - 34); if(textureVisible) draw.text(font, Component.literal("Block texture"), right, 36 - scroll, 0xFFFFFFFF);
        if(colorVisible) {
        draw.text(font, Component.literal("Presets"), presetLeft, colorTop - 14 - scroll, 0xFFFFFFFF);
        String[] labels = {"Hue", "Saturation", "Brightness", "Opacity"};
        for (int i = 0; i < 4; i++) draw.text(font, Component.literal(labels[i]), right, colorTop - 14 + i * 38 - scroll, 0xFFFFFFFF);
        if (sliderTexture == null) sliderTexture = new BoundarySliderTexture();
        sliderTexture.update(hue, saturation, brightness, opacity, rainbow);
        sliderTexture.draw(draw, 4, right, colorTop + 154 - scroll, sliderWidth);
        }
        draw.disableScissor();
        super.extractRenderState(draw, mouseX, mouseY, delta);
        if (texture.visible && texture.isFocused() && !matches.isEmpty()) {
            int start = completionIndex < 0 ? 0 : Math.max(0, completionIndex - 5), count = Math.min(6, matches.size() - start), top = popupY(); draw.fill(right, top, right + rightWidth, top + count * 12, 0xFF202020);
            for (int i = 0; i < count; i++) { if (start + i == completionIndex) draw.fill(right, top + i * 12, right + rightWidth, top + (i + 1) * 12, 0xFF355870); draw.text(font, Component.literal(matches.get(start + i)), right + 2, top + 2 + i * 12, 0xFFFFFFFF); }
        }
    }
}
