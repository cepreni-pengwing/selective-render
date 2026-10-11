package de.selectiverender;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Read-only preset overview with viewport-sized pages and mouse-wheel navigation. */
public final class PresetListScreen extends Screen {
    private final Screen parent;
    private final String preset;
    private int scroll;
    private int visibleRows;
    private List<String> rows = List.of();

    public PresetListScreen(Screen parent) {
        this(parent, null, 0);
    }

    private PresetListScreen(Screen parent, String preset, int scroll) {
        super(Text.literal(preset == null ? "Preset list" : "Preset: " + preset));
        this.parent = parent;
        this.preset = preset;
        this.scroll = scroll;
    }

    @Override
    protected void init() {
        rows = preset == null ? SelectiveRenderConfig.presetNames() : details();
        if (rows.isEmpty()) rows = List.of("No saved presets");
        visibleRows = Math.max(1, (height - 90) / 24);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows)));
        int contentWidth = Math.min(420, width - 24);
        int left = (width - contentWidth) / 2;
        for (int i = scroll; i < Math.min(rows.size(), scroll + visibleRows); i++) {
            String value = rows.get(i);
            var button = addDrawableChild(ButtonWidget.builder(UiStyle.label(value), b ->
                    client.setScreen(new PresetListScreen(this, value, 0)))
                    .dimensions(left, 48 + (i - scroll) * 24, contentWidth, 20).build());
            button.active = preset == null && SelectiveRenderConfig.presetExists(value);
        }
        var up = addDrawableChild(ButtonWidget.builder(Text.literal("Up"), b -> scrollBy(-1))
                .dimensions(left, height - 26, 36, 20).build());
        up.active = scroll > 0;
        var down = addDrawableChild(ButtonWidget.builder(Text.literal("Down"), b -> scrollBy(1))
                .dimensions(left + contentWidth - 36, height - 26, 36, 20).build());
        down.active = scroll + visibleRows < rows.size();
        addDrawableChild(ButtonWidget.builder(UiStyle.label("Back"), b -> close())
                .dimensions(left + 40, height - 26, contentWidth - 80, 20).build());
    }

    private List<String> details() {
        List<String> result = new ArrayList<>();
        boolean hidden = SelectiveRenderConfig.isPresetHidden(preset);
        boolean selected = hidden ? SelectiveRenderConfig.isHiddenPresetActive(preset)
                : SelectiveRenderConfig.isPresetActive(preset);
        boolean enabled = selected && (hidden ? SelectiveRenderConfig.hideGroupEnabled()
                : SelectiveRenderConfig.groupEnabled());
        result.add("Name: " + preset);
        result.add("Type: " + (hidden ? "Hidden" : "Render"));
        result.add("Selected in group: " + (selected ? "Yes" : "No"));
        result.add("Group enabled: " + ((hidden ? SelectiveRenderConfig.hideGroupEnabled()
                : SelectiveRenderConfig.groupEnabled()) ? "Yes" : "No"));
        result.add("Region effect active: " + (enabled ? "Yes" : "No"));
        result.add("Block filtering: " + (SelectiveRenderSettings.blockFiltersEnabled() ? "On" : "Off"));
        List<BlockRegion> regions = SelectiveRenderConfig.presetRegions(preset);
        result.add("Bounding boxes: " + regions.size());
        for (int i = 0; i < regions.size(); i++) {
            BlockRegion region = regions.get(i);
            result.add("Box " + (i + 1) + " minimum: " + region.minX() + ", " + region.minY() + ", " + region.minZ());
            result.add("Box " + (i + 1) + " maximum: " + region.maxX() + ", " + region.maxY() + ", " + region.maxZ());
            result.add("Box " + (i + 1) + " blocks: " + region.blockCount());
        }
        var filters = SelectiveRenderConfig.blockFilters(preset);
        result.add("Filters: " + filters.size());
        for (BlockFilterRule rule : filters) {
            result.add(rule.mode().name().toLowerCase(java.util.Locale.ROOT) + ": " + rule.selector());
        }
        return result;
    }

    private void scrollBy(int change) {
        client.setScreen(new PresetListScreen(parent, preset, scroll + change));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        if (amount == 0) return false;
        scrollBy(amount > 0 ? -1 : 1);
        return true;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 24, UiStyle.TEXT);
        super.render(context, mouseX, mouseY, delta);
    }
}
