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
        GENERAL("General"), PRESETS("Presets"), BOUNDARIES("Boundaries"), PLOTS("Plots");
        final String label;
        Tab(String label) { this.label = label; }
    }

    private record Choice(String label, boolean selected, Runnable action) { }
    private record Row(String label, List<Choice> choices, int numeric) { }
    private final Screen parent;
    private final Tab tab;
    private final String preset;
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

    private SelectiveRenderSettingsScreen(Screen parent, Tab tab, String preset, int scroll) {
        super(Component.literal("Selective Render"));
        this.parent = parent;
        this.tab = tab;
        this.preset = preset;
        this.scroll = scroll;
    }

    @Override
    protected void init() {
        reloadField = null;
        plotHeightField = null;
        rows.clear();
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
                String displayLabel = font.width(row.label()) <= paneWidth - valueWidth - 14
                        ? row.label() : (signedNumeric(row) ? "Plot minimum Y" : "Reload sections");
                var label = addRenderableWidget(Button.builder(Component.literal(displayLabel), b -> { })
                        .bounds(paneLeft, y, paneWidth - valueWidth - 6, 20).build());
                label.active = false;
                label.setTooltip(Tooltip.create(Component.literal(row.label())));
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
                int buttonWidth = (paneWidth - (count - 1) * 6) / count;
                for (int j = 0; j < count; j++) {
                    Choice choice = row.choices().get(j);
                    var button = addRenderableWidget(Button.builder(Component.literal(choice.label()), b -> {
                        applyNumericSettings();
                        choice.action().run();
                    }).bounds(paneLeft + j * (buttonWidth + 6), y, buttonWidth, 20).build());
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
        var up = addRenderableWidget(Button.builder(Component.literal("Up"), b -> navigate(tab, preset, scroll - 1))
                .bounds(left, height - 26, (navigationWidth - 4) / 2, 20).build());
        up.active = scroll > 0;
        var down = addRenderableWidget(Button.builder(Component.literal("Down"), b -> navigate(tab, preset, scroll + 1))
                .bounds(left + (navigationWidth + 4) / 2, height - 26, (navigationWidth - 4) / 2, 20).build());
        down.active = scroll + visibleRows < rows.size();
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
                options("Interactions while rendering is off", new String[]{"Vanilla", "Filtered"},
                        SelectiveRenderSettings.filterInteractionsWhenInactive() ? 1 : 0,
                        i -> SelectiveRenderSettings.setFilterInteractionsWhenInactive(i == 1));
                options("Interactions with hidden content", new String[]{"Off", "On"},
                        SelectiveRenderSettings.interactWithHiddenRegions() ? 1 : 0,
                        i -> SelectiveRenderSettings.setInteractWithHiddenRegions(i == 1));
                var lighting = SelectiveRenderSettings.VirtualLightMode.values();
                String[] lights = java.util.Arrays.stream(lighting).map(value -> value.label()).toArray(String[]::new);
                options("Virtual skylight: render regions", lights, SelectiveRenderSettings.virtualLightMode().ordinal(),
                        i -> SelectiveRenderSettings.setVirtualLightMode(lighting[i]));
                options("Virtual skylight: hidden regions", lights, SelectiveRenderSettings.hiddenVirtualLightMode().ordinal(),
                        i -> SelectiveRenderSettings.setHiddenVirtualLightMode(lighting[i]));
                rows.add(new Row("Full reload after affected sections", List.of(), 1));
                options("Debug boxes", new String[]{"Off", "On"}, SelectiveRenderSettings.debugBoxes() ? 1 : 0,
                        i -> SelectiveRenderSettings.setDebugBoxes(i == 1));
            }
            case PRESETS -> {
                if (preset != null) {
                    rows.add(new Row("", List.of(new Choice("All presets", false,
                            () -> navigate(Tab.PRESETS, null, 0))), 0));
                    for (String detail : PresetDetails.rows(preset)) label(detail);
                } else {
                    var names = SelectiveRenderConfig.presetNames();
                    if (names.isEmpty()) label("No saved presets");
                    for (String name : names) {
                        rows.add(new Row("", List.of(new Choice(name, false,
                                () -> navigate(Tab.PRESETS, name, 0))), 0));
                    }
                }
            }
            case BOUNDARIES -> {
                var boundaries = SelectiveRenderSettings.BoundaryMode.values();
                options("Boundary faces", java.util.Arrays.stream(boundaries).map(value -> value.label()).toArray(String[]::new),
                        SelectiveRenderSettings.boundaryMode().ordinal(),
                        i -> SelectiveRenderSettings.setBoundaryMode(boundaries[i]));
                options("Cull fluid boundary faces", new String[]{"Off", "On"},
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
        label(label);
        int columns = labels.length == 3 ? 3 : 2;
        for (int start = 0; start < labels.length; start += columns) {
            List<Choice> choices = new ArrayList<>();
            for (int i = start; i < Math.min(labels.length, start + columns); i++) {
                int index = i;
                choices.add(new Choice(labels[i], i == selected, () -> {
                    setter.accept(index);
                    navigate(tab, preset, scroll);
                }));
            }
            rows.add(new Row("", List.copyOf(choices), 0));
        }
    }

    private void navigate(Tab nextTab, String nextPreset, int nextScroll) {
        applyNumericSettings();
        minecraft.gui.setScreen(new SelectiveRenderSettingsScreen(parent, nextTab, nextPreset, nextScroll));
    }

    private void applyNumericSettings() {
        if (reloadField != null) try {
            SelectiveRenderSettings.setFullReloadThreshold(Integer.parseInt(reloadField.getValue()));
        } catch (NumberFormatException ignored) { }
        if (plotHeightField != null) try {
            SelectiveRenderSettings.setDefaultPlotMinY(Integer.parseInt(plotHeightField.getValue()));
        } catch (NumberFormatException ignored) { }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double amount) {
        if (amount == 0 || x < paneLeft) return false;
        int next = Math.max(0, Math.min(scroll + (amount > 0 ? -1 : 1),
                Math.max(0, rows.size() - visibleRows)));
        if (next != scroll) navigate(tab, preset, next);
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
    }
}
