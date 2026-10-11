package de.selectiverender;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Dimension-grouped region navigation and an immediately persisted, validated editor. */
final class RegionEditorScreen extends Screen {
    private record Choice(String label, boolean selected, Runnable action) { }
    private record Field(String key, String value, boolean completion, Consumer<String> change) { }
    private record Row(String label, List<Choice> choices, Field field, int labelWidth) { }
    private record Caption(String text, int x, int y, int width) { }
    private record Navigation(String dimension, String name) { }
    private final Screen parent;
    private final Set<String> collapsed = new HashSet<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<Caption> captions = new ArrayList<>();
    private final List<Input> inputs = new ArrayList<>();
    private final FilterEditSession filterEdits = new FilterEditSession();
    private List<RegionDocument> documents = List.of();
    private List<String> selectors;
    private RegionDocument document;
    private String region;
    private String context;
    private String error = "";
    private int paneLeft, paneWidth, navLeft, navWidth, navScroll, scroll, visibleRows;
    private boolean boxesOpen, filtersOpen, refreshRequested, documentsLoaded;
    private BlockFilterRule.Mode newFilterMode = BlockFilterRule.Mode.HIDE;
    private Input newFilterInput;

    RegionEditorScreen(Screen parent) {
        super(Text.literal("Selective Render"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        inputs.clear(); captions.clear(); rows.clear(); newFilterInput = null; filterEdits.clear();
        if (!documentsLoaded) {
            documents = SelectiveRenderConfig.editorDocuments(client);
            documentsLoaded = true;
        }
        if (client.world != null && context == null) context = SelectiveRenderConfig.contextIdentity(client, client.world);
        String dimension = document == null ? null : document.dimension();
        document = documents.stream().filter(value -> value.dimension().equals(dimension)).findFirst()
                .orElse(documents.isEmpty() ? null : documents.get(0));
        if (document != null && !document.regions().containsKey(region)) region = document.regions().keySet().iterator().next();
        SettingsViewport viewport = SettingsViewport.of(width, height, 0, 0);
        navLeft = viewport.paneLeft();
        navWidth = Math.min(160, Math.max(84, viewport.paneWidth() / 3));
        paneLeft = navLeft + navWidth + 12;
        paneWidth = Math.max(80, viewport.paneWidth() - navWidth - 12);
        visibleRows = Math.max(1, (height - 100) / 24);
        for (var tab : SelectiveRenderSettingsScreen.Tab.values()) {
            var button = addDrawableChild(ButtonWidget.builder(Text.literal(tab.label), b -> {
                if (commitInputs()) client.setScreen(new SelectiveRenderSettingsScreen(parent, tab, null, 0));
            }).dimensions(viewport.left(), 36 + tab.ordinal() * 26, viewport.navigationWidth(), 20).build());
            button.active = tab != SelectiveRenderSettingsScreen.Tab.REGIONS;
        }
        List<Navigation> navigation = new ArrayList<>();
        for (RegionDocument value : documents) {
            navigation.add(new Navigation(value.dimension(), null));
            if (!collapsed.contains(value.dimension())) for (String name : value.regions().keySet()) {
                navigation.add(new Navigation(value.dimension(), name));
            }
        }
        navScroll = Math.max(0, Math.min(navScroll, Math.max(0, navigation.size() - visibleRows)));
        for (int i = navScroll; i < Math.min(navigation.size(), navScroll + visibleRows); i++) {
            Navigation entry = navigation.get(i);
            String label = entry.name() == null ? (collapsed.contains(entry.dimension()) ? "+ " : "- ")
                    + entry.dimension().replace("minecraft:", "") : entry.name();
            var button = addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> runAction(() -> {
                if (entry.name() == null) {
                    if (!collapsed.add(entry.dimension())) collapsed.remove(entry.dimension());
                } else {
                    document = documents.stream().filter(value -> value.dimension().equals(entry.dimension())).findFirst().orElseThrow();
                    region = entry.name(); scroll = 0; boxesOpen = false; filtersOpen = false;
                }
            })).dimensions(navLeft, 36 + (i - navScroll) * 24, navWidth, 20).build());
            button.active = entry.name() == null || document == null
                    || !entry.dimension().equals(document.dimension()) || !entry.name().equals(region);
        }
        if (document == null) info("No saved regions in this world or server");
        else buildRows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows)));
        for (int i = scroll; i < Math.min(rows.size(), scroll + visibleRows); i++) {
            Row row = rows.get(i);
            int y = 36 + (i - scroll) * 24;
            if (row.field() != null) {
                captions.add(new Caption(row.label(), paneLeft, y, row.labelWidth() - 12));
                Input input = new Input(row.field(), paneLeft + row.labelWidth(), y, paneWidth - row.labelWidth());
                inputs.add(input); addDrawableChild(input.widget);
                if (row.field().key().equals("new-filter")) newFilterInput = input;
            } else if (!row.choices().isEmpty()) {
                int x = paneLeft + row.labelWidth();
                if (!row.label().isEmpty()) captions.add(new Caption(row.label(), paneLeft, y, row.labelWidth() - 12));
                for (Choice choice : row.choices()) {
                    int size = row.labelWidth() == 0 ? paneWidth
                            : Math.min(paneWidth - row.labelWidth(), Math.max(36, textRenderer.getWidth(choice.label()) + 16));
                    var button = addDrawableChild(ButtonWidget.builder(Text.literal(choice.label()), b -> runAction(choice.action()))
                            .dimensions(x, y, size, 20).build());
                    button.active = !choice.selected(); x += size + 6;
                }
            } else captions.add(new Caption(row.label(), paneLeft, y, paneWidth));
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(navLeft, height - 26, viewport.paneWidth(), 20).build());
    }

    private void buildRows() {
        field("Name", "name", region, false, value -> save(value, boxes(), hidden(), selected(), rules()));
        options("Type", List.of(new Choice("Render", !hidden(), () -> save(region, boxes(), false, selected(), rules())),
                new Choice("Hidden", hidden(), () -> save(region, boxes(), true, selected(), rules()))));
        options("Selected", List.of(new Choice("Off", !selected(), () -> save(region, boxes(), hidden(), false, rules())),
                new Choice("On", selected(), () -> save(region, boxes(), hidden(), true, rules()))));
        info("Effect: " + (selected() && (hidden() ? document.hiding() : document.rendering()) ? "On" : "Off"));
        accordion("Bounding boxes", boxes().size(), boxesOpen, () -> boxesOpen = !boxesOpen);
        if (boxesOpen) {
            if (boxes().size() == 1) {
                BlockRegion box = boxes().get(0);
                field("Minimum", "minimum", cornerText(box, true), false,
                        value -> save(region, List.of(RegionDocument.corner(boxes().get(0), value, true)), hidden(), selected(), rules()));
                field("Maximum", "maximum", cornerText(box, false), false,
                        value -> save(region, List.of(RegionDocument.corner(boxes().get(0), value, false)), hidden(), selected(), rules()));
                info("Blocks: " + box.blockCount());
            } else {
                info("Multiple boxes: read-only");
                for (int i = 0; i < boxes().size(); i++) {
                    BlockRegion box = boxes().get(i);
                    info("Box " + (i + 1) + " min: " + cornerText(box, true));
                    info("Box " + (i + 1) + " max: " + cornerText(box, false));
                    info("Blocks: " + box.blockCount());
                }
            }
        }
        accordion("Filters", rules().size(), filtersOpen, () -> filtersOpen = !filtersOpen);
        if (filtersOpen) {
            info("IDs or #tags; Tab completes, Enter adds");
            options("New filter", List.of(new Choice("Hide", newFilterMode == BlockFilterRule.Mode.HIDE,
                    () -> newFilterMode = BlockFilterRule.Mode.HIDE),
                    new Choice("Only", newFilterMode == BlockFilterRule.Mode.ONLY, () -> newFilterMode = BlockFilterRule.Mode.ONLY)));
            field("Add filter", "new-filter", "", true, value -> {
                if (value.isBlank()) return;
                List<BlockFilterRule> next = new ArrayList<>(rules());
                next.addAll(BlockFilterRule.parse(newFilterMode, value));
                save(region, boxes(), hidden(), selected(), next);
                if (newFilterInput != null) newFilterInput.widget.setText("");
            });
            List<BlockFilterRule> existing = rules();
            for (int i = 0; i < existing.size(); i++) {
                BlockFilterRule rule = existing.get(i);
                options("Filter " + (i + 1), List.of(
                        new Choice("Hide", rule.mode() == BlockFilterRule.Mode.HIDE, () -> setRuleMode(rule, BlockFilterRule.Mode.HIDE)),
                        new Choice("Only", rule.mode() == BlockFilterRule.Mode.ONLY, () -> setRuleMode(rule, BlockFilterRule.Mode.ONLY)),
                        new Choice("Remove", false, () -> replaceRule(rule, null))));
                field("Block / tag", "filter-" + i, rule.selector(), true, value -> {
                    List<BlockFilterRule> parsed = BlockFilterRule.parse(rule.mode(), value);
                    if (parsed.size() != 1) throw new IllegalArgumentException("Edit one filter here; add batches in Add filter");
                    replaceRule(rule, parsed.get(0));
                });
            }
        }
    }

    private List<BlockRegion> boxes() { return document.regions().get(region); }
    private List<BlockFilterRule> rules() { return document.filters().getOrDefault(region, List.of()); }
    private boolean hidden() { return document.hidden().contains(region); }
    private boolean selected() { return document.isSelected(region); }
    private static String cornerText(BlockRegion box, boolean minimum) {
        return minimum ? box.minX() + " " + box.minY() + " " + box.minZ()
                : box.maxX() + " " + box.maxY() + " " + box.maxZ();
    }

    private void save(String name, List<BlockRegion> boxes, boolean hidden, boolean selected, List<BlockFilterRule> filters) {
        if (client.world == null || !SelectiveRenderConfig.contextIdentity(client, client.world).equals(context)) {
            throw new IllegalArgumentException("The world changed; reopen the region editor");
        }
        SelectiveRenderConfig.editRegion(client, document.dimension(), region, name, boxes, hidden, selected, filters);
        region = name.trim().toLowerCase(java.util.Locale.ROOT);
        document = SelectiveRenderConfig.editorDocument(client, document.dimension());
        documents = documents.stream().map(value -> value.dimension().equals(document.dimension()) ? document : value).toList();
        refreshRequested = true;
    }

    private void setRuleMode(BlockFilterRule original, BlockFilterRule.Mode mode) {
        BlockFilterRule current = filterEdits.current(original);
        replaceRule(original, new BlockFilterRule(mode, current.kind(), current.value()));
    }

    private void replaceRule(BlockFilterRule old, BlockFilterRule replacement) {
        BlockFilterRule current = filterEdits.current(old);
        List<BlockFilterRule> next = new ArrayList<>(rules());
        int index = next.indexOf(current);
        if (index < 0) throw new IllegalArgumentException("This filter changed; reopen its entry");
        if (replacement == null) next.remove(index); else next.set(index, replacement);
        save(region, boxes(), hidden(), selected(), next);
        filterEdits.replaced(current, replacement);
    }

    private int labelWidth() { return Math.min(176, Math.max(64, paneWidth / 3)); }
    private void field(String label, String key, String value, boolean completion, Consumer<String> change) {
        rows.add(new Row(label, List.of(), new Field(key, value, completion, change), labelWidth()));
    }
    private void info(String value) { rows.add(new Row(value, List.of(), null, 0)); }
    private void accordion(String title, int count, boolean open, Runnable action) {
        rows.add(new Row("", List.of(new Choice((open ? "- " : "+ ") + title + " (" + count + ")", false, action)), null, 0));
    }
    private void options(String label, List<Choice> values) {
        int[] widths = values.stream().mapToInt(value -> textRenderer.getWidth(value.label())).toArray();
        for (int start = 0; start < values.size();) {
            int end = OptionRowLayout.end(paneWidth - labelWidth(), widths, start);
            rows.add(new Row(start == 0 ? label : "", List.copyOf(values.subList(start, end)), null, labelWidth()));
            start = end;
        }
    }

    private final class Input {
        final Field definition;
        final TextFieldWidget widget;
        String committed, completionBase;
        int completionIndex = -1;
        boolean wasFocused, completing;
        Input(Field definition, int x, int y, int size) {
            this.definition = definition;
            widget = new TextFieldWidget(textRenderer, x, y, size, 20, Text.literal(definition.key()));
            widget.setMaxLength(4096); widget.setText(definition.value()); committed = definition.value();
            widget.setChangedListener(value -> {
                widget.setEditableColor(0xFFFFFF);
                if (!completing) { completionBase = null; completionIndex = -1; }
                if (definition.completion()) {
                    if (selectors == null) selectors = SelectiveRenderClient.blockFilterSuggestions("");
                    var matches = value.isBlank() ? List.<String>of() : FilterCompletion.matches(value, selectors);
                    String first = matches.isEmpty() ? "" : matches.get(0);
                    widget.setSuggestion(first.startsWith(value) ? first.substring(value.length()) : null);
                }
            });
        }
        boolean commit() {
            if (widget.getText().equals(committed)) return true;
            try {
                definition.change().accept(widget.getText());
                committed = widget.getText(); error = ""; widget.setEditableColor(0xFFFFFF);
                return true;
            } catch (IllegalArgumentException exception) {
                error = exception.getMessage(); widget.setEditableColor(UiStyle.ERROR); return false;
            }
        }
        boolean complete(boolean backwards) {
            if (!definition.completion()) return false;
            if (selectors == null) selectors = SelectiveRenderClient.blockFilterSuggestions("");
            if (completionBase == null) completionBase = widget.getText();
            var matches = FilterCompletion.matches(completionBase, selectors);
            if (matches.isEmpty()) return true;
            completionIndex = completionIndex < 0 ? (backwards ? matches.size() - 1 : 0)
                    : Math.floorMod(completionIndex + (backwards ? -1 : 1), matches.size());
            completing = true; widget.setText(matches.get(completionIndex)); completing = false;
            return true;
        }
    }

    private boolean commitInputs() {
        for (Input input : inputs) if (!input.commit()) { setFocused(input.widget); return false; }
        return true;
    }
    private void runAction(Runnable action) {
        if (!commitInputs()) return;
        try { action.run(); error = ""; rebuild(); }
        catch (IllegalArgumentException exception) { error = exception.getMessage(); }
    }
    private void rebuild() { refreshRequested = false; clearChildren(); init(); }

    @Override
    public void tick() {
        super.tick();
        for (Input input : inputs) {
            if (input.wasFocused && !input.widget.isFocused() && !input.commit()) setFocused(input.widget);
            input.wasFocused = input.widget.isFocused();
        }
        if (refreshRequested && inputs.stream().noneMatch(input -> input.widget.isFocused())) rebuild();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (!commitOutside(x, y)) return true;
        return super.mouseClicked(x, y, button);
    }
    private boolean commitOutside(double x, double y) {
        for (Input input : inputs) if (input.widget.isFocused() && !input.widget.isMouseOver(x, y)) {
            if (!input.commit()) return false;
            setFocused(null);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (Input input : inputs) if (input.widget.isFocused()) {
            if (keyCode == 256) {
                input.widget.setText(input.committed);
                setFocused(null); error = ""; return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                if (input.commit()) { setFocused(null); if (refreshRequested) rebuild(); }
                return true;
            }
            if (keyCode == 258 && input.complete((modifiers & 1) != 0)) return true;
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode >= 266 && keyCode <= 269) {
            int maximum = Math.max(0, rows.size() - visibleRows);
            scroll = keyCode == 268 ? 0 : keyCode == 269 ? maximum
                    : Math.max(0, Math.min(maximum, scroll + (keyCode == 266 ? -visibleRows : visibleRows)));
            runAction(() -> { }); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        if (amount == 0 || x < navLeft || !commitInputs()) return false;
        if (x < paneLeft) navScroll = Math.max(0, navScroll + (amount > 0 ? -1 : 1));
        else scroll = Math.max(0, Math.min(Math.max(0, rows.size() - visibleRows), scroll + (amount > 0 ? -1 : 1)));
        setFocused(null); rebuild(); return true;
    }

    @Override
    public void close() { if (commitInputs()) client.setScreen(parent); }

    @Override
    public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        renderBackground(draw);
        draw.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        super.render(draw, mouseX, mouseY, delta);
        for (Caption caption : captions) drawCaption(draw, caption, 0xFFFFFF);
        if (!error.isEmpty()) drawCaption(draw, new Caption(error, paneLeft, height - 45, paneWidth), UiStyle.ERROR);
    }
    private void drawCaption(DrawContext draw, Caption caption, int color) {
        String remaining = caption.text();
        for (int offset = 1; offset <= 11 && !remaining.isEmpty(); offset += 10) {
            int end = remaining.length();
            while (end > 0 && textRenderer.getWidth(remaining.substring(0, end)) > caption.width()) end--;
            if (end < remaining.length() && offset == 1) {
                int space = remaining.lastIndexOf(' ', end); if (space > 0) end = space;
            }
            String line = remaining.substring(0, end); remaining = remaining.substring(end).stripLeading();
            if (offset == 11 && !remaining.isEmpty()) {
                while (!line.isEmpty() && textRenderer.getWidth(line + "...") > caption.width()) line = line.substring(0, line.length() - 1);
                line += "...";
            }
            draw.drawTextWithShadow(textRenderer, Text.literal(line), caption.x(), caption.y() + offset, color);
        }
    }
}
