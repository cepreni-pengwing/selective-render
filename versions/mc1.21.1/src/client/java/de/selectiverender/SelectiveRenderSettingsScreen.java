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
    enum Tab {
        GENERAL("General"), REGIONS("Regions"), INTERACTIONS("Interactions"), BOUNDARIES("Boundaries"), PLOTS("Plots");
        final String label;
        Tab(String label) { this.label = label; }
    }

    private record Choice(String label, boolean selected, Runnable action, String help) { }
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
    private final java.util.Map<Integer, TextFieldWidget> gridFields = new java.util.HashMap<>();
    private String gridError = "";
    private boolean deletingGrid;
    private TextFieldWidget reloadField;
    private TextFieldWidget plotHeightField;
    private TextFieldWidget plotMaximumField;
    private boolean maximumWasFocused;
    private boolean reloadWasFocused, heightWasFocused;

    public static Screen open(Screen parent) {
        var state = SelectiveRenderSettings.menuNavigation();
        Tab tab;
        try { tab = Tab.valueOf(state.tab); } catch (IllegalArgumentException error) { tab = Tab.GENERAL; }
        return page(parent, tab);
    }

    static Screen page(Screen parent, Tab tab) {
        return tab == Tab.REGIONS ? new RegionEditorScreen(parent) : tab == Tab.BOUNDARIES ? new BoundaryAppearanceScreen(parent)
                : new SelectiveRenderSettingsScreen(parent, tab, null, SelectiveRenderSettings.menuNavigation().scroll(tab.name()));
    }

    @Override
    public void removed() {
        var state = SelectiveRenderSettings.menuNavigation();
        state.tab = tab.name();
        state.scrolls.put(tab.name(), scroll);
        SelectiveRenderSettings.saveMenuNavigation();
        super.removed();
    }

    public SelectiveRenderSettingsScreen(Screen parent) {
        this(parent, Tab.GENERAL, null, 0);
    }

    SelectiveRenderSettingsScreen(Screen parent, Tab tab, String regionName, int scroll) {
        super(Text.literal("Selective Render"));
        this.parent = parent;
        this.tab = tab;
        this.regionName = regionName;
        this.scroll = scroll;
    }

    @Override
    protected void init() {
        reloadWasFocused = false; heightWasFocused = false;
        reloadField = null;
        plotHeightField = null; plotMaximumField = null; maximumWasFocused = false;
        gridFields.clear();
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
                    b -> navigate(value, null, SelectiveRenderSettings.menuNavigation().scroll(value.name())))
                    .dimensions(left, 36 + value.ordinal() * 26, navigationWidth, 20).build());
            button.active = value != tab;
        }
        for (int i = scroll; i < Math.min(rows.size(), scroll + visibleRows); i++) {
            Row row = rows.get(i);
            int y = 36 + (i - scroll) * 24;
            if (row.numeric() >= 4) {
                int labelWidth = optionLabelWidth();
                captions.add(new Caption(row.label(),y,labelWidth-12));
                var field = new TextFieldWidget(textRenderer,paneLeft+labelWidth,y,Math.max(32,paneWidth-labelWidth),20,Text.literal(row.label()));
                field.setMaxLength(32);
                var grid = PlotGridClient.current();
                String value = grid == null ? "" : row.numeric()==4 ? grid.x1()+" "+grid.z1()
                        : row.numeric()==5 ? grid.x2()+" "+grid.z2() : grid.roadWidth()==null ? "" : grid.roadWidth().toString();
                field.setText(value);
                gridFields.put(row.numeric(),field);
                addDrawableChild(field);
            } else if (row.numeric() != 0) {
                int labelWidth = optionLabelWidth();
                int valueWidth = Math.min(84, paneWidth - labelWidth);
                captions.add(new Caption(row.label(), y, labelWidth - 12));
                TextFieldWidget field = new TextFieldWidget(textRenderer,
                        paneLeft + labelWidth, y, valueWidth, 20, Text.literal(row.label()));
                field.setMaxLength(11);
                boolean signed = row.numeric() >= 2;
                field.setTextPredicate(value -> value.isEmpty() || (signed && value.equals("-"))
                        || value.matches(signed ? "-?[0-9]+" : "[0-9]+"));
                field.setText(Integer.toString(row.numeric() == 3 ? SelectiveRenderSettings.defaultPlotMaxY() : signed ? SelectiveRenderSettings.defaultPlotMinY()
                        : SelectiveRenderSettings.fullReloadThreshold()));
                addDrawableChild(field);
                if (row.numeric() == 3) plotMaximumField = field;
                else if (signed) plotHeightField = field;
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
                        if (choice.label().equals("Delete grid") || applyNumericSettings()) choice.action().run();
                    }).dimensions(x, y, buttonWidth, 20).build());
                    x += buttonWidth + 6;
                    button.active = !choice.selected();
                    String help = choice.help();
                    if (!help.isEmpty()) button.setTooltip(Tooltip.of(Text.literal(help)));
                }
            } else {
                // Read-only labels use normal widgets for clipping, narration and full-text tooltips.
                var label = addDrawableChild(ButtonWidget.builder(UiStyle.label(row.label()), b -> { })
                        .dimensions(paneLeft, y, paneWidth, 20).build());
                label.active = false;
            }
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions((width - Math.min(100, width - 24)) / 2, height - 26, Math.min(100, width - 24), 20).build());
    }

    private void buildRows() {
        switch (tab) {
            case GENERAL -> {
                rows.add(new Row("Selection source", List.of(new Choice(SelectiveRenderSettings.worldEditSelection() ? "WorldEdit" : "SR positions", false, () -> {
                    SelectiveRenderSettings.setWorldEditSelection(!SelectiveRenderSettings.worldEditSelection());
                    navigate(tab, regionName, scroll);
                }, "Choose WorldEdit or SR positions for region creation and redefining")), 0, optionLabelWidth()));
                options("Player visibility", new String[]{"Everyone","Nobody","Only you","Other players"},
                        SelectiveRenderSettings.playerGroup().ordinal(),
                        i -> SelectiveRenderSettings.setPlayerGroup(PlayerRenderPolicy.Group.values()[i]));
                options("Region scope", new String[]{"Everywhere","Inside regions","Outside regions","Nowhere"},
                        SelectiveRenderSettings.playerScope().ordinal(),
                        i -> SelectiveRenderSettings.setPlayerScope(PlayerRenderPolicy.Scope.values()[i]));
                var lighting = SelectiveRenderSettings.VirtualLightMode.values();
                String[] lights = java.util.Arrays.stream(lighting).map(value -> value.label()).toArray(String[]::new);
                options("Virtual skylight", lights, SelectiveRenderSettings.virtualLightMode().ordinal(),
                        i -> SelectiveRenderSettings.setVirtualLightMode(lighting[i]));
                rows.add(new Row("Full reload (sections)", List.of(), 1));
                options("Debug boxes", new String[]{"Off", "On"}, SelectiveRenderSettings.debugBoxes() ? 1 : 0,
                        i -> SelectiveRenderSettings.setDebugBoxes(i == 1));
            }
            case INTERACTIONS -> {
                var interactions = SelectiveRenderSettings.InteractionMode.values();
                options("Interactions", java.util.Arrays.stream(interactions).map(value -> value.label()).toArray(String[]::new),
                        SelectiveRenderSettings.interactionMode().ordinal(),
                        i -> SelectiveRenderSettings.setInteractionMode(interactions[i]));
                options("Interactions while rendering is off", new String[]{"Unrestricted", "Follow region rules"},
                        SelectiveRenderSettings.filterInteractionsWhenInactive() ? 1 : 0,
                        i -> SelectiveRenderSettings.setFilterInteractionsWhenInactive(i == 1));
                options("Hidden interactions", new String[]{"Off", "On"},
                        SelectiveRenderSettings.interactWithHiddenRegions() ? 1 : 0,
                        i -> SelectiveRenderSettings.setInteractWithHiddenRegions(i == 1));
            }
            case REGIONS -> label("Open the region editor to browse saved dimensions");
            case BOUNDARIES -> { } // Dedicated inline boundary page.
            case PLOTS -> {
                var grid = PlotGridClient.current();
                if (PlotGridClient.context() == null) label("Join a world to edit its plot grid");
                else if (grid == null) label("Set up with /sr plot grid");
                else {
                    options("Plot source", new String[]{"SRP", "Grid"}, grid.enabled()?1:0,
                            i -> PlotGridClient.save(PlotGridClient.current().withEnabled(i==1)));
                    rows.add(new Row("Corner 1 (X Z)",List.of(),4));
                    rows.add(new Row("Corner 2 (X Z)",List.of(),5));
                    rows.add(new Row("Road width",List.of(),6));
                    if (grid.roadWidth()==null) label("Road width not set");
                    rows.add(new Row("",List.of(new Choice("Save grid",false,() -> {
                        if (commitGrid()) navigate(tab,regionName,scroll);
                    },"Save both plot corners and the uniform road width")),0));
                    rows.add(new Row("",List.of(new Choice("Delete grid",false,() -> {
                        deletingGrid=true; PlotGridClient.save(null); gridFields.clear(); navigate(tab,regionName,scroll);
                    },"Remove the grid for this server and dimension")),0));
                    if (!gridError.isEmpty()) label(gridError);
                }
                rows.add(new Row("Default plot minimum Y", List.of(), 2));
                rows.add(new Row("Default plot maximum Y", List.of(), 3));
            }
        }
    }

    private static boolean signedNumeric(Row row) { return row.numeric() >= 2; }

    private void label(String text) {
        rows.add(new Row(text, List.of(), 0));
    }

    private void options(String label, String[] labels, int selected, IntConsumer setter) {
        int labelWidth = optionLabelWidth();
        if (labels.length == 2 && labels[0].equals("Off") && labels[1].equals("On")) {
            rows.add(new Row(label, List.of(new Choice(labels[selected], false, () -> {
                setter.accept(1 - selected);
                navigate(tab, regionName, scroll);
            }, optionHelp(label))), 0, labelWidth));
            return;
        }
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
                }, optionHelp(label)));
            }
            rows.add(new Row(start == 0 ? label : "", List.copyOf(choices), 0, labelWidth));
            start = end;
        }
    }

    private int optionLabelWidth() { return Math.min(176, Math.max(88, paneWidth / 3)); }

    private static String optionHelp(String label) {
        return switch (label) {
            case "Interactions while rendering is off" -> "Choose whether region interaction rules still apply when rendering is disabled. Active block filters keep their own interaction rules.";
            case "Region scope" -> "Applies the player selection to the currently rendered area, not to inactive saved regions.";
            case "Hidden interactions" -> "Allow targeting and interacting with hidden regions and blocks removed by filters.";
            case "Virtual skylight" -> "Let skylight enter through removed blocks from above, from the sides, both, or neither. Applies to render regions, hidden regions and block filters.";
            case "Full reload (sections)" -> "Changes affecting more sections than this threshold rebuild the whole renderer; smaller changes rebuild affected sections.";
            default -> "";
        };
    }

    private void navigate(Tab nextTab, String nextRegion, int nextScroll) {
        if (!applyNumericSettings()) return;
        client.setScreen(nextTab == Tab.BOUNDARIES ? new BoundaryAppearanceScreen(parent) : nextTab == Tab.REGIONS ? new RegionEditorScreen(parent)
                : new SelectiveRenderSettingsScreen(parent, nextTab, nextRegion, nextScroll));
    }

    private boolean commitGrid() {
        if (deletingGrid || gridFields.isEmpty()) return true;
        var grid=PlotGridClient.current();
        if(grid==null) return true;
        String a=gridFields.containsKey(4)?gridFields.get(4).getText():grid.x1()+" "+grid.z1();
        String b=gridFields.containsKey(5)?gridFields.get(5).getText():grid.x2()+" "+grid.z2();
        String road=gridFields.containsKey(6)?gridFields.get(6).getText():grid.roadWidth()==null?"":grid.roadWidth().toString();
        try {
            String[] first=a.trim().split("[,\\s]+"); String[] second=b.trim().split("[,\\s]+");
            if(first.length!=2 || second.length!=2) throw new IllegalArgumentException();
            var next=new PlotGrid(Integer.parseInt(first[0]),Integer.parseInt(first[1]),Integer.parseInt(second[0]),Integer.parseInt(second[1]),
                    road.isBlank()?null:Integer.valueOf(road.trim()),grid.enabled());
            if(!next.equals(grid)) PlotGridClient.save(next);
            gridError="";
            for(var field:gridFields.values()) field.setEditableColor(0xFFFFFF);
            return true;
        } catch(IllegalArgumentException error) {
            gridError="Enter two X/Z coordinates and a nonnegative road width.";
            for(var field:gridFields.values()) field.setEditableColor(UiStyle.ERROR);
            return false;
        }
    }

    private boolean applyNumericSettings() {
        return commitGrid() && commitNumber(reloadField, false) && commitNumber(plotHeightField, true) && commitNumber(plotMaximumField, true);
    }

    private boolean commitNumber(TextFieldWidget field, boolean plot) {
        if (field == null) return true;
        try {
            int value = Integer.parseInt(field.getText());
            if (field == plotMaximumField) SelectiveRenderSettings.setDefaultPlotMaxY(value);
            else if (plot) SelectiveRenderSettings.setDefaultPlotMinY(value);
            else SelectiveRenderSettings.setFullReloadThreshold(value);
            field.setText(Integer.toString(field == plotMaximumField ? SelectiveRenderSettings.defaultPlotMaxY() : plot ? SelectiveRenderSettings.defaultPlotMinY() : SelectiveRenderSettings.fullReloadThreshold()));
            field.setEditableColor(0xFFFFFF);
            return true;
        } catch (NumberFormatException error) {
            field.setEditableColor(UiStyle.ERROR);
            return false;
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (!commitOutside(x, y)) return true;
        return super.mouseClicked(x, y, button);
    }

    private boolean commitOutside(double x, double y) {
        if (reloadField != null && reloadField.isFocused() && !reloadField.isMouseOver(x, y)) {
            if (!commitNumber(reloadField, false)) return false;
            setFocused(null);
        }
        if (plotHeightField != null && plotHeightField.isFocused() && !plotHeightField.isMouseOver(x, y)) {
            if (!commitNumber(plotHeightField, true)) return false;
            setFocused(null);
        }
        if (plotMaximumField != null && plotMaximumField.isFocused() && !plotMaximumField.isMouseOver(x, y)) {
            if (!commitNumber(plotMaximumField, true)) return false;
            setFocused(null);
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (reloadField != null) {
            if (reloadWasFocused && !reloadField.isFocused() && !commitNumber(reloadField, false)) setFocused(reloadField);
            reloadWasFocused = reloadField.isFocused();
        }
        if (plotHeightField != null) {
            if (heightWasFocused && !plotHeightField.isFocused() && !commitNumber(plotHeightField, true)) setFocused(plotHeightField);
            heightWasFocused = plotHeightField.isFocused();
        }
        if (plotMaximumField != null) {
            if (maximumWasFocused && !plotMaximumField.isFocused() && !commitNumber(plotMaximumField, true)) setFocused(plotMaximumField);
            maximumWasFocused = plotMaximumField.isFocused();
        }
    }

    private boolean scrollKey(int key) {
        if (key != 266 && key != 267 && key != 268 && key != 269) return false;
        if ((reloadField != null && reloadField.isFocused())
                || (plotHeightField != null && plotHeightField.isFocused())
                || (plotMaximumField != null && plotMaximumField.isFocused())) return false;
        int maximum = Math.max(0, rows.size() - visibleRows);
        int next = key == 268 ? 0 : key == 269 ? maximum
                : Math.max(0, Math.min(maximum, scroll + (key == 266 ? -visibleRows : visibleRows)));
        if (next != scroll) navigate(tab, regionName, next);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (reloadField != null && reloadField.isFocused()) {
                reloadField.setText(Integer.toString(SelectiveRenderSettings.fullReloadThreshold()));
                setFocused(null); return true;
            }
            if (plotHeightField != null && plotHeightField.isFocused()) {
                plotHeightField.setText(Integer.toString(SelectiveRenderSettings.defaultPlotMinY()));
                setFocused(null); return true;
            }
            if (plotMaximumField != null && plotMaximumField.isFocused()) {
                plotMaximumField.setText(Integer.toString(SelectiveRenderSettings.defaultPlotMaxY()));
                setFocused(null); return true;
            }
        }
        if (keyCode == 257 || keyCode == 335) {
            if (reloadField != null && reloadField.isFocused()) { commitNumber(reloadField, false); return true; }
            if (plotHeightField != null && plotHeightField.isFocused()) { commitNumber(plotHeightField, true); return true; }
            if (plotMaximumField != null && plotMaximumField.isFocused()) { commitNumber(plotMaximumField, true); return true; }
        }
        return scrollKey(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
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
    public void close() {
        if (!applyNumericSettings()) return;
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, UiStyle.TEXT);
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
                context.drawTextWithShadow(textRenderer, Text.literal(line), paneLeft, caption.y() + offset + (textRenderer.getWidth(caption.text()) <= caption.width() ? 5 : 0), 0xFFFFFFFF);
            }
        }
    }
}
