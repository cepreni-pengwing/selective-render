package de.selectiverender;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;

/** Fixed navigation column and viewport-sized, scrollable settings content. */
public final class SelectiveRenderSettingsScreen extends Screen {
    private enum Tab {
        GENERAL("General"), REGIONS("Regions"), BOUNDARIES("Boundaries"), PLOTS("Plots");
        final String label;
        Tab(String label) { this.label = label; }
    }

    private record Choice(String label, boolean selected, Runnable action) { }
    private record Row(String label, List<Choice> choices, int numeric, int labelWidth) {
        Row(String label, List<Choice> choices, int numeric) { this(label, choices, numeric, 0); }
    }
    private record Caption(String text, int y, int width) { }
    private final List<Caption> captions = new ArrayList<>();
    private final Screen parent;
    private final Tab tab;
    private final String regionName;
    private int scroll;
    private int visibleRows;
    private int paneLeft;
    private int paneWidth;
    private final List<Row> rows = new ArrayList<>();
    private TextFieldWidget reloadField;
    private TextFieldWidget plotHeightField;

    public SelectiveRenderSettingsScreen(Screen parent) {
        this(parent, Tab.GENERAL, null, 0);
    }

    private SelectiveRenderSettingsScreen(Screen parent, Tab tab, String regionName, int scroll) {
        super(Text.literal("Selective Render"));
        this.parent = parent;
        this.tab = tab;
        this.regionName = regionName;
        this.scroll = scroll;
    }

    @Override
    protected void init() {
        reloadField = null;
        plotHeightField = null;
        rows.clear();
        captions.clear();
        paneWidth = SettingsViewport.of(width, height, 0, 0).paneWidth();
        buildRows();
        SettingsViewport viewport = SettingsViewport.of(width, height, rows.size(), scroll);
        int left = viewport.left();
        int navigationWidth = viewport.navigationWidth();
        paneLeft = viewport.paneLeft();
        paneWidth = viewport.paneWidth();
        visibleRows = viewport.visibleRows();
        scroll = viewport.scroll();
        for (Tab value : Tab.values()) {
            var button = addDrawableChild(ButtonWidget.builder(Text.literal(value.label),
                    b -> navigate(value, null, 0))
                    .dimensions(left, 36 + value.ordinal() * 26, navigationWidth, 20).build());
            button.active = value != tab;
        }
        for (int i = scroll; i < Math.min(rows.size(), scroll + visibleRows); i++) {
            Row row = rows.get(i);
            int y = 36 + (i - scroll) * 24;
            if (row.numeric() != 0) {
                int valueWidth = Math.min(84, paneWidth / 3);
                captions.add(new Caption(row.label(), y, paneWidth - valueWidth - 12));
                TextFieldWidget field = new TextFieldWidget(textRenderer,
                        paneLeft + paneWidth - valueWidth, y, valueWidth, 20, Text.literal(row.label()));
                field.setMaxLength(11);
                boolean signed = row.numeric() == 2;
                field.setTextPredicate(value -> value.isEmpty() || (signed && value.equals("-"))
                        || value.matches(signed ? "-?[0-9]+" : "[0-9]+"));
                field.setText(Integer.toString(signed ? SelectiveRenderSettings.defaultPlotMinY()
                        : SelectiveRenderSettings.fullReloadThreshold()));
                addDrawableChild(field);
                if (signed) plotHeightField = field;
                else reloadField = field;
            } else if (!row.choices().isEmpty()) {
                int count = row.choices().size();
                int available = paneWidth - row.labelWidth();
                int x = paneLeft + row.labelWidth();
                if (!row.label().isEmpty()) captions.add(new Caption(row.label(), y, row.labelWidth() - 12));
                for (int j = 0; j < count; j++) {
                    Choice choice = row.choices().get(j);
                    int buttonWidth = row.labelWidth() == 0 ? available
                            : Math.min(available, Math.max(36, textRenderer.getWidth(choice.label()) + 16));
                    var button = addDrawableChild(ButtonWidget.builder(Text.literal(choice.label()), b -> {
                        applyNumericSettings();
                        choice.action().run();
                    }).dimensions(x, y, buttonWidth, 20).build());
                    x += buttonWidth + 6;
                    button.active = !choice.selected();
                    button.setTooltip(Tooltip.of(Text.literal(choice.label())));
                }
            } else {
                // Read-only labels use normal widgets for clipping, narration and full-text tooltips.
                var label = addDrawableChild(ButtonWidget.builder(UiStyle.label(row.label()), b -> { })
                        .dimensions(paneLeft, y, paneWidth, 20).build());
                label.active = false;
                label.setTooltip(Tooltip.of(Text.literal(row.label())));
            }
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(paneLeft, height - 26, paneWidth, 20).build());
    }

    private void buildRows() {
        switch (tab) {
            case GENERAL -> {
                options("Selection source", new String[]{"SR positions", "WorldEdit"},
                        SelectiveRenderSettings.worldEditSelection() ? 1 : 0,
                        i -> SelectiveRenderSettings.setWorldEditSelection(i == 1));
                var players = SelectiveRenderSettings.PlayerVisibility.values();
                options("Players", new String[]{"None", "Inside regions", "Outside regions", "Everyone", "Own player", "Everyone else"},
                        SelectiveRenderSettings.playerVisibility().ordinal(),
                        i -> SelectiveRenderSettings.setPlayerVisibility(players[i]));
                var interactions = SelectiveRenderSettings.InteractionMode.values();
                options("Interactions", java.util.Arrays.stream(interactions).map(value -> value.label()).toArray(String[]::new),
                        SelectiveRenderSettings.interactionMode().ordinal(),
                        i -> SelectiveRenderSettings.setInteractionMode(interactions[i]));
                options("Inactive interactions", new String[]{"Vanilla", "Filtered"},
                        SelectiveRenderSettings.filterInteractionsWhenInactive() ? 1 : 0,
                        i -> SelectiveRenderSettings.setFilterInteractionsWhenInactive(i == 1));
                options("Hidden interactions", new String[]{"Off", "On"},
                        SelectiveRenderSettings.interactWithHiddenRegions() ? 1 : 0,
                        i -> SelectiveRenderSettings.setInteractWithHiddenRegions(i == 1));
                var lighting = SelectiveRenderSettings.VirtualLightMode.values();
                String[] lights = java.util.Arrays.stream(lighting).map(value -> value.label()).toArray(String[]::new);
                options("Render skylight", lights, SelectiveRenderSettings.virtualLightMode().ordinal(),
                        i -> SelectiveRenderSettings.setVirtualLightMode(lighting[i]));
                options("Hidden skylight", lights, SelectiveRenderSettings.hiddenVirtualLightMode().ordinal(),
                        i -> SelectiveRenderSettings.setHiddenVirtualLightMode(lighting[i]));
                rows.add(new Row("Full reload (sections)", List.of(), 1));
                options("Debug boxes", new String[]{"Off", "On"}, SelectiveRenderSettings.debugBoxes() ? 1 : 0,
                        i -> SelectiveRenderSettings.setDebugBoxes(i == 1));
            }
            case REGIONS -> {
                if (regionName != null) {
                    rows.add(new Row("", List.of(new Choice("All regions", false,
                            () -> navigate(Tab.REGIONS, null, 0))), 0));
                    for (String detail : RegionDetails.rows(regionName)) label(detail);
                } else {
                    var names = SelectiveRenderConfig.regionNames();
                    if (names.isEmpty()) label("No saved regions");
                    for (String name : names) {
                        rows.add(new Row("", List.of(new Choice(name, false,
                                () -> navigate(Tab.REGIONS, name, 0))), 0));
                    }
                }
            }
            case BOUNDARIES -> {
                var boundaries = SelectiveRenderSettings.BoundaryMode.values();
                options("Boundary faces", java.util.Arrays.stream(boundaries).map(value -> value.label()).toArray(String[]::new),
                        SelectiveRenderSettings.boundaryMode().ordinal(),
                        i -> SelectiveRenderSettings.setBoundaryMode(boundaries[i]));
                options("Cull fluid boundaries", new String[]{"Off", "On"},
                        SelectiveRenderSettings.cullFluidBoundaryFaces() ? 1 : 0,
                        i -> SelectiveRenderSettings.setCullFluidBoundaryFaces(i == 1));
            }
            case PLOTS -> rows.add(new Row("Default plot minimum Y", List.of(), 2));
        }
    }

    private static boolean signedNumeric(Row row) { return row.numeric() == 2; }

    private void label(String text) {
        rows.add(new Row(text, List.of(), 0));
    }

    private void options(String label, String[] labels, int selected, IntConsumer setter) {
        int labelWidth = Math.min(176, Math.max(88, paneWidth / 3));
        int available = paneWidth - labelWidth;
        int[] widths = java.util.Arrays.stream(labels).mapToInt(value -> textRenderer.getWidth(value)).toArray();
        for (int start = 0; start < labels.length;) {
            int end = OptionRowLayout.end(available, widths, start);
            List<Choice> choices = new ArrayList<>();
            for (int i = start; i < end; i++) {
                int index = i;
                choices.add(new Choice(labels[i], i == selected, () -> {
                    setter.accept(index);
                    navigate(tab, regionName, scroll);
                }));
            }
            rows.add(new Row(start == 0 ? label : "", List.copyOf(choices), 0, labelWidth));
            start = end;
        }
    }

    private void navigate(Tab nextTab, String nextRegion, int nextScroll) {
        applyNumericSettings();
        client.setScreen(new SelectiveRenderSettingsScreen(parent, nextTab, nextRegion, nextScroll));
    }

    private void applyNumericSettings() {
        if (reloadField != null) try {
            SelectiveRenderSettings.setFullReloadThreshold(Integer.parseInt(reloadField.getText()));
        } catch (NumberFormatException ignored) { }
        if (plotHeightField != null) try {
            SelectiveRenderSettings.setDefaultPlotMinY(Integer.parseInt(plotHeightField.getText()));
        } catch (NumberFormatException ignored) { }
    }

    private boolean scrollKey(int key) {
        if (key != 266 && key != 267 && key != 268 && key != 269) return false;
        if ((reloadField != null && reloadField.isFocused())
                || (plotHeightField != null && plotHeightField.isFocused())) return false;
        int maximum = Math.max(0, rows.size() - visibleRows);
        int next = key == 268 ? 0 : key == 269 ? maximum
                : Math.max(0, Math.min(maximum, scroll + (key == 266 ? -visibleRows : visibleRows)));
        if (next != scroll) navigate(tab, regionName, next);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return scrollKey(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        if (amount == 0 || x < paneLeft) return false;
        int next = Math.max(0, Math.min(scroll + (amount > 0 ? -1 : 1),
                Math.max(0, rows.size() - visibleRows)));
        if (next != scroll) navigate(tab, regionName, next);
        return true;
    }

    @Override
    public void close() {
        applyNumericSettings();
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, UiStyle.TEXT);
        super.render(context, mouseX, mouseY, delta);
        for (Caption caption : captions) {
            String remaining = caption.text();
            for (int offset = 1; offset <= 11 && !remaining.isEmpty(); offset += 10) {
                int end = remaining.length();
                while (end > 0 && textRenderer.getWidth(remaining.substring(0, end)) > caption.width()) end--;
                if (end < remaining.length() && offset == 1) {
                    int space = remaining.lastIndexOf(' ', end);
                    if (space > 0) end = space;
                }
                String line = remaining.substring(0, end);
                remaining = remaining.substring(end).stripLeading();
                if (offset == 11 && !remaining.isEmpty()) {
                    while (!line.isEmpty() && textRenderer.getWidth(line + "...") > caption.width()) line = line.substring(0, line.length() - 1);
                    line += "...";
                }
                context.drawTextWithShadow(textRenderer, Text.literal(line), paneLeft, caption.y() + offset, UiStyle.ACCENT);
            }
        }
    }
}
