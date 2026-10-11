package de.selectiverender;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

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
    private EditBox reloadField;
    private EditBox plotHeightField;

    public SelectiveRenderSettingsScreen(Screen parent) {
        this(parent, Tab.GENERAL, null, 0);
    }

    private SelectiveRenderSettingsScreen(Screen parent, Tab tab, String regionName, int scroll) {
        super(Component.literal("Selective Render"));
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
            var button = addRenderableWidget(Button.builder(Component.literal(value.label),
                    b -> navigate(value, null, 0))
                    .bounds(left, 36 + value.ordinal() * 26, navigationWidth, 20).build());
            button.active = value != tab;
        }
        for (int i = scroll; i < Math.min(rows.size(), scroll + visibleRows); i++) {
            Row row = rows.get(i);
            int y = 36 + (i - scroll) * 24;
            if (row.numeric() != 0) {
                int valueWidth = Math.min(84, paneWidth / 3);
                captions.add(new Caption(row.label(), y, paneWidth - valueWidth - 12));
                EditBox field = new EditBox(font,
                        paneLeft + paneWidth - valueWidth, y, valueWidth, 20, Component.literal(row.label()));
                field.setMaxLength(11);
                boolean signed = row.numeric() == 2;
                field.setValue(Integer.toString(signed ? SelectiveRenderSettings.defaultPlotMinY()
                        : SelectiveRenderSettings.fullReloadThreshold()));
                String[] lastValid = {field.getValue()};
                field.setResponder(value -> {
                    if (value.isEmpty() || (signed && value.equals("-"))
                            || value.matches(signed ? "-?[0-9]+" : "[0-9]+")) lastValid[0] = value;
                    else field.setValue(lastValid[0]);
                });
                addRenderableWidget(field);
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
                            : Math.min(available, Math.max(36, font.width(choice.label()) + 16));
                    var button = addRenderableWidget(Button.builder(Component.literal(choice.label()), b -> {
                        applyNumericSettings();
                        choice.action().run();
                    }).bounds(x, y, buttonWidth, 20).build());
                    x += buttonWidth + 6;
                    button.active = !choice.selected();
                    button.setTooltip(Tooltip.create(Component.literal(choice.label())));
                }
            } else {
                // Read-only labels use normal widgets for clipping, narration and full-text tooltips.
                var label = addRenderableWidget(Button.builder(UiStyle.label(row.label()), b -> { })
                        .bounds(paneLeft, y, paneWidth, 20).build());
                label.active = false;
                label.setTooltip(Tooltip.create(Component.literal(row.label())));
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(paneLeft, height - 26, paneWidth, 20).build());
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
        int[] widths = java.util.Arrays.stream(labels).mapToInt(value -> font.width(value)).toArray();
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
        minecraft.gui.setScreen(new SelectiveRenderSettingsScreen(parent, nextTab, nextRegion, nextScroll));
    }

    private void applyNumericSettings() {
        if (reloadField != null) try {
            SelectiveRenderSettings.setFullReloadThreshold(Integer.parseInt(reloadField.getValue()));
        } catch (NumberFormatException ignored) { }
        if (plotHeightField != null) try {
            SelectiveRenderSettings.setDefaultPlotMinY(Integer.parseInt(plotHeightField.getValue()));
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
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return scrollKey(event.key()) || super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double amount) {
        if (amount == 0 || x < paneLeft) return false;
        int next = Math.max(0, Math.min(scroll + (amount > 0 ? -1 : 1),
                Math.max(0, rows.size() - visibleRows)));
        if (next != scroll) navigate(tab, regionName, next);
        return true;
    }

    @Override
    public void onClose() {
        applyNumericSettings();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.centeredText(font, title, width / 2, 12, UiStyle.TEXT);
        super.extractRenderState(context, mouseX, mouseY, delta);
        for (Caption caption : captions) {
            String remaining = caption.text();
            for (int offset = 1; offset <= 11 && !remaining.isEmpty(); offset += 10) {
                int end = remaining.length();
                while (end > 0 && font.width(remaining.substring(0, end)) > caption.width()) end--;
                if (end < remaining.length() && offset == 1) {
                    int space = remaining.lastIndexOf(' ', end);
                    if (space > 0) end = space;
                }
                String line = remaining.substring(0, end);
                remaining = remaining.substring(end).stripLeading();
                if (offset == 11 && !remaining.isEmpty()) {
                    while (!line.isEmpty() && font.width(line + "...") > caption.width()) line = line.substring(0, line.length() - 1);
                    line += "...";
                }
                context.text(font, line, paneLeft, caption.y() + offset, 0xFF000000 | UiStyle.ACCENT);
            }
        }
    }
}
