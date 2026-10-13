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
    private boolean deletionArmed;
    private boolean navigationRestored;
    private String restoredDimension;
    private boolean boxesOpen, filtersOpen, boundariesOpen, interactionsOpen, refreshRequested, documentsLoaded;
    private BlockFilterRule.Mode newFilterMode = BlockFilterRule.Mode.HIDE;
    private Input newFilterInput;
    private boolean addingFilter;

    RegionEditorScreen(Screen parent) {
        super(Text.literal("Selective Render"));
        this.parent = parent;
    }

    @Override
    public void removed() {
        var state = SelectiveRenderSettings.menuNavigation();
        state.tab = "REGIONS";
        var view = state.region(context == null ? "menu" : context);
        view.dimension = document == null ? null : document.dimension();
        view.selected = region;
        view.collapsed = new HashSet<>(collapsed);
        view.scroll = scroll; view.navigationScroll = navScroll;
        view.filtersOpen = filtersOpen; view.boxesOpen = boxesOpen; view.boundariesOpen=boundariesOpen; view.interactionsOpen=interactionsOpen;
        SelectiveRenderSettings.saveMenuNavigation();
        super.removed();
    }

    @Override
    protected void init() {
        inputs.clear(); captions.clear(); rows.clear(); newFilterInput = null; filterEdits.clear();
        if (!documentsLoaded) {
            documents = SelectiveRenderConfig.editorDocuments(client);
            documentsLoaded = true;
        }
        if (client.world != null && context == null) context = SelectiveRenderConfig.contextIdentity(client, client.world);
        if (!navigationRestored) {
            var view = SelectiveRenderSettings.menuNavigation().region(context == null ? "menu" : context);
            collapsed.addAll(view.collapsed);
            region = view.selected;
            restoredDimension = view.dimension;
            scroll = Math.max(0, view.scroll); navScroll = Math.max(0, view.navigationScroll);
            filtersOpen = view.filtersOpen; boxesOpen = view.boxesOpen; boundariesOpen=view.boundariesOpen; interactionsOpen=view.interactionsOpen;
            navigationRestored = true;
        }
        String dimension = document == null ? restoredDimension : document.dimension();
        document = documents.stream().filter(value -> value.dimension().equals(dimension)).findFirst()
                .orElse(documents.isEmpty() ? null : documents.get(0));
        if (document != null && !document.regions().containsKey(region)) region = document.regions().keySet().iterator().next();
        SettingsViewport viewport = SettingsViewport.of(width, height, 0, 0);
        navLeft = viewport.paneLeft();
        navWidth = Math.min(120, Math.max(72, viewport.paneWidth() / 4));
        paneLeft = navLeft + navWidth + 12;
        paneWidth = Math.max(80, viewport.paneWidth() - navWidth - 12);
        visibleRows = Math.max(1, (height - (error.isEmpty() ? 58 : 82)) / 24);
        for (var tab : SelectiveRenderSettingsScreen.Tab.values()) {
            var button = addDrawableChild(ButtonWidget.builder(Text.literal(tab.label), b -> {
                if (commitInputs()) client.setScreen(SelectiveRenderSettingsScreen.page(parent, tab));
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
                    region = entry.name(); deletionArmed = false; scroll = 0; boxesOpen = false; filtersOpen = false; boundariesOpen=false; interactionsOpen=false; addingFilter = false;
                }
            })).dimensions(navLeft, 36 + (i - navScroll) * 24, entry.name() == null ? navWidth : Math.min(Math.max(24, navWidth - 48), Math.max(24, textRenderer.getWidth(label) + 16)), 20).build());
            button.active = entry.name() == null || document == null
                    || !entry.dimension().equals(document.dimension()) || !entry.name().equals(region);
            if (entry.name() != null) {
                var listedDocument = documents.stream().filter(value -> value.dimension().equals(entry.dimension())).findFirst().orElseThrow();
                boolean listedSelected = listedDocument.isSelected(entry.name());
                addDrawableChild(ButtonWidget.builder(Text.literal(listedSelected ? "On" : "Off"), b -> runAction(() -> {
                    SelectiveRenderConfig.editRegion(client, entry.dimension(), entry.name(), entry.name(),
                            listedDocument.regions().get(entry.name()), listedDocument.hidden().contains(entry.name()), !listedSelected,
                            listedDocument.filters().getOrDefault(entry.name(), List.of()),
                            listedDocument.options().getOrDefault(entry.name(), RegionOptions.DEFAULT));
                    documents = SelectiveRenderConfig.editorDocuments(client);
                })).dimensions(navLeft + navWidth - 44, 36 + (i - navScroll) * 24, 44, 20).build());
            }

        }
        if (document == null) info("No saved regions in this world or server");
        else buildRows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows)));
        for (int i = scroll; i < Math.min(rows.size(), scroll + visibleRows); i++) {
            Row row = rows.get(i);
            int y = 36 + (i - scroll) * 24;
            if (row.field() != null) {
                if (!row.label().isEmpty()) captions.add(new Caption(row.label(), paneLeft, y, row.labelWidth() - 12));
                int controlsWidth = row.choices().stream().mapToInt(choice -> Math.max(20, textRenderer.getWidth(choice.label()) + 12) + 4).sum();
                int inputWidth = Math.max(20, paneWidth - row.labelWidth() - controlsWidth);
                Input input = new Input(row.field(), paneLeft + row.labelWidth(), y, inputWidth);
                int controlX = paneLeft + row.labelWidth() + inputWidth + 4;
                for (Choice choice : row.choices()) {
                    int size = Math.max(20, textRenderer.getWidth(choice.label()) + 12);
                    var button = addDrawableChild(ButtonWidget.builder(Text.literal(choice.label()), b -> {
                        if (choice.label().equals("×")) input.widget.setText(input.committed);
                        runAction(choice.action());
                    })
                            .dimensions(controlX, y, size, 20).build());
                    if (choice.label().equals("×")) {
                        input.removeX = controlX; input.removeWidth = size; input.removeAction = choice.action();
                    }
                    if(row.label().equals("Interactions while rendering is off") || choice.label().equals("Unrestricted") || choice.label().equals("Follow region rules"))
                        button.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(net.minecraft.text.Text.literal("Choose whether region interaction rules still apply when rendering is disabled. Active block filters keep their own interaction rules.")));
                    button.active = !choice.selected(); controlX += size + 4;
                }
                inputs.add(input); addDrawableChild(input.widget);
                if (row.field().key().equals("new-filter")) newFilterInput = input;
            } else if (!row.choices().isEmpty()) {
                int x = paneLeft + row.labelWidth();
                if (!row.label().isEmpty()) captions.add(new Caption(row.label(), paneLeft, y, row.labelWidth() - 12));
                for (Choice choice : row.choices()) {
                    int size = row.labelWidth() == 0 ? (choice.label().equals("+") ? 24 : choice.label().equals("Delete region") || choice.label().equals("Confirm deletion") ? Math.min(paneWidth, textRenderer.getWidth("Confirm deletion") + 16) : paneWidth)
                            : Math.min(paneWidth - row.labelWidth(), Math.max(36, textRenderer.getWidth(choice.label()) + 16));
                    var button = addDrawableChild(ButtonWidget.builder(Text.literal(choice.label()), b -> runAction(choice.action()))
                            .dimensions(x, y, size, 20).build());
                    if(row.label().equals("Interactions while rendering is off") || choice.label().equals("Unrestricted") || choice.label().equals("Follow region rules"))
                        button.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(net.minecraft.text.Text.literal("Choose whether region interaction rules still apply when rendering is disabled. Active block filters keep their own interaction rules.")));
                    button.active = !choice.selected(); x += size + 6;
                }
            } else captions.add(new Caption(row.label(), paneLeft, y, paneWidth));
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions((width - Math.min(100, width - 24)) / 2, height - 26, Math.min(100, width - 24), 20).build());
    }

    private void buildRows() {
        rows.add(new Row("", List.of(new Choice(deletionArmed ? "Confirm deletion" : "Delete region", false, () -> {
            if (!deletionArmed) { deletionArmed = true; return; }
            if (client.world == null || !SelectiveRenderConfig.contextIdentity(client, client.world).equals(context)) {
                throw new IllegalArgumentException("The world changed; reopen the region editor");
            }
            SelectiveRenderConfig.deleteEditorRegion(client, document.dimension(), region);
            documents = SelectiveRenderConfig.editorDocuments(client);
            deletionArmed = false; scroll = 0; navScroll = 0; filtersOpen = false; boxesOpen = false;
        })), null, 0));
        field("Name", "name", region, false, value -> save(value, boxes(), hidden(), selected(), rules()));
        options("Type", List.of(new Choice("Render", !hidden(), () -> save(region, boxes(), false, selected(), rules())),
                new Choice("Hidden", hidden(), () -> save(region, boxes(), true, selected(), rules()))));
        accordion("Filters", rules().size(), filtersOpen, () -> filtersOpen = !filtersOpen);
        if (filtersOpen) {
            RegionOptions currentOptions = document.options().getOrDefault(region, RegionOptions.DEFAULT);
            options("Enabled", List.of(new Choice("Off", !currentOptions.filtersActive(), () -> setOption(0, false)),
                    new Choice("On", currentOptions.filtersActive(), () -> setOption(0, true))));
            List<BlockFilterRule> existing = rules();
            for (int i = 0; i < existing.size(); i++) {
                BlockFilterRule rule = existing.get(i);
                filterRow("filter-" + i, rule.selector(), value -> {
                    List<BlockFilterRule> parsed = BlockFilterRule.parse(rule.mode(), value);
                    if (parsed.size() != 1) throw new IllegalArgumentException("Enter one block ID or tag per row");
                    replaceRule(rule, parsed.get(0));
                }, List.of(
                        new Choice("Hide", rule.mode() == BlockFilterRule.Mode.HIDE, () -> setRuleMode(rule, BlockFilterRule.Mode.HIDE)),
                        new Choice("Only", rule.mode() == BlockFilterRule.Mode.ONLY, () -> setRuleMode(rule, BlockFilterRule.Mode.ONLY)),
                        new Choice("×", false, () -> replaceRule(rule, null))));
            }
            if (addingFilter) filterRow("new-filter", "", value -> {
                if (value.isBlank()) return;
                List<BlockFilterRule> next = new ArrayList<>(rules());
                next.addAll(BlockFilterRule.parse(newFilterMode, value));
                save(region, boxes(), hidden(), selected(), next);
                addingFilter = false;
                if (newFilterInput != null) newFilterInput.widget.setText("");
            }, List.of(
                    new Choice("Hide", newFilterMode == BlockFilterRule.Mode.HIDE, () -> newFilterMode = BlockFilterRule.Mode.HIDE),
                    new Choice("Only", newFilterMode == BlockFilterRule.Mode.ONLY, () -> newFilterMode = BlockFilterRule.Mode.ONLY),
                    new Choice("×", false, () -> addingFilter = false)));
            rows.add(new Row("", List.of(new Choice("+", addingFilter, () -> addingFilter = true)), null, 0));
        }
        RegionOptions overrides = document.options().getOrDefault(region, RegionOptions.DEFAULT);
        accordion("Boundaries", -1, boundariesOpen, () -> boundariesOpen=!boundariesOpen);
        if (boundariesOpen) {
            var target=boundaryTarget();
            boolean colorOn=BoundaryStyle.color(target.mode()), textureOn=BoundaryStyle.texture(target.mode());
            options("Texture", List.of(new Choice("Use global",target.textureGlobal(),target::inheritTexture),
                    new Choice(textureOn?"On":"Off",false,()->target.setTextureEnabled(!textureOn))));
            field("Block texture","boundary-texture",target.texture(),true,value-> {
                var id=net.minecraft.util.Identifier.tryParse(value.trim());
                if(id==null || !net.minecraft.registry.Registries.BLOCK.containsId(id))
                    throw new IllegalArgumentException("Unknown block ID");
                target.commit(target.color(),id.toString(),target.opacity(),target.rainbow(),false,true);
            });
            options("Color",List.of(new Choice("Use global",target.colorGlobal(),target::inheritColor),
                    new Choice(colorOn?"On":"Off",false,()->target.setColorEnabled(!colorOn))));
            options("Appearance",List.of(new Choice("Edit texture and color",false,()->client.setScreen(new BoundaryAppearanceScreen(this,target)))));
            override("Cull fluid faces",2,overrides.cullFluids(),new Object[]{false,true},new String[]{"Off","On"});
        }
        accordion("Interactions",-1,interactionsOpen,()->interactionsOpen=!interactionsOpen);
        if(interactionsOpen) {
            override("Interactions", 3, overrides.interactions(), new Object[]{SelectiveRenderSettings.InteractionMode.NONE, SelectiveRenderSettings.InteractionMode.EVERYWHERE}, new String[]{"Off", "On"});
            override("Interactions while rendering is off", 4, overrides.inactiveInteractions(), new Object[]{false, true}, new String[]{"Unrestricted", "Follow region rules"});
            override("Hidden interactions", 5, overrides.hiddenInteractions(), new Object[]{false, true}, new String[]{"Off", "On"});
        }
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
    }

    private BoundaryEditorTarget boundaryTarget() {
        return new BoundaryEditorTarget(()->document.options().getOrDefault(region,RegionOptions.DEFAULT),this::setBoundaryOptions);
    }
    private void setBoundaryOptions(RegionOptions value) {
        if (client.world==null || !SelectiveRenderConfig.contextIdentity(client,client.world).equals(context))
            throw new IllegalArgumentException("The world changed; reopen the region editor");
        SelectiveRenderConfig.editRegion(client,document.dimension(),region,region,boxes(),hidden(),selected(),rules(),value);
        document=SelectiveRenderConfig.editorDocument(client,document.dimension());
        documents=documents.stream().map(d->d.dimension().equals(document.dimension())?document:d).toList();
        refreshRequested=true;
    }
    private void setOption(int field, Object value) {
        RegionOptions next = document.options().getOrDefault(region, RegionOptions.DEFAULT).with(field, value);
        SelectiveRenderConfig.editRegion(client, document.dimension(), region, region, boxes(), hidden(), selected(), rules(), next);
        document = SelectiveRenderConfig.editorDocument(client, document.dimension());
        documents = documents.stream().map(valueDoc -> valueDoc.dimension().equals(document.dimension()) ? document : valueDoc).toList();
    }
    private void override(String label, int field, Object current, Object[] values, String[] labels) {
        List<Choice> choices = new ArrayList<>();
        choices.add(new Choice("Use global", current == null, () -> setOption(field, null)));
        if (labels.length == 2 && labels[0].equals("Off") && labels[1].equals("On")) {
            Object effective = current != null ? current : switch (field) {
                case 2 -> SelectiveRenderSettings.cullFluidBoundaryFaces();
                case 3 -> SelectiveRenderSettings.interactionMode();
                case 5 -> SelectiveRenderSettings.interactWithHiddenRegions();
                default -> false;
            };
            boolean on = values[1].equals(effective);
            choices.add(new Choice(on ? "On" : "Off", false, () -> setOption(field, values[on ? 0 : 1])));
            options(label, choices);
            return;
        }
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            choices.add(new Choice(labels[i], value.equals(current), () -> setOption(field, value)));
        }
        options(label, choices);
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
        SelectiveRenderConfig.editRegion(client, document.dimension(), region, name, boxes, hidden, selected, filters, document.options().getOrDefault(region, RegionOptions.DEFAULT));
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
    private void filterRow(String key, String value, Consumer<String> change, List<Choice> choices) {
        rows.add(new Row("", choices, new Field(key, value, true, change), 0));
    }
    private void info(String value) { rows.add(new Row(value, List.of(), null, 0)); }
    private void accordion(String title, int count, boolean open, Runnable action) {
        rows.add(new Row("", List.of(new Choice((open ? "- " : "+ ") + title + (count < 0 ? "" : " (" + count + ")"), false, action)), null, 0));
    }
    private void options(String label, List<Choice> values) {
        // Binary values remain clickable: one button shows the current value and flips it.
        if (values.size() == 2 && values.get(0).label().equals("Off") && values.get(1).label().equals("On")) {
            int selected = values.get(1).selected() ? 1 : 0;
            Choice current = values.get(selected), next = values.get(1 - selected);
            values = List.of(new Choice(current.label(), false, next.action()));
        }
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
        boolean wasFocused, completing, popupClosed;
        final int x, y, size;
        String matchQuery;
        List<String> matchValues = List.of();
        int popupOffset;
        int removeX = -1, removeWidth;
        Runnable removeAction;
        boolean removeClick(double mouseX, double mouseY) {
            if (removeAction == null || mouseX < removeX || mouseX >= removeX + removeWidth || mouseY < y || mouseY >= y + 20) return false;
            widget.setText(committed);
            runAction(removeAction); return true;
        }
        int popupWidth() { return Math.min(width - x - 12, Math.max(size, 260)); }
        int popupRows() { return Math.max(1, Math.min(6, (height - 70) / 12)); }
        int popupY() {
            int rows = Math.min(popupRows(), matches().size());
            return y + 22 + rows * 12 < height - 28 ? y + 22 : Math.max(30, y - rows * 12 - 2);
        }
        List<String> matches() {
            String query = completionBase == null ? widget.getText() : completionBase;
            if (!query.equals(matchQuery)) {
                if (selectors == null) selectors = SelectiveRenderClient.blockFilterSuggestions("");
                matchValues = FilterCompletion.matches(query, selectors);
                if(definition.key().startsWith("boundary-")) matchValues=matchValues.stream().filter(v->!v.startsWith("#")).toList(); matchQuery = query;
                popupOffset = 0;
            }
            return matchValues;
        }
        boolean popup() { return definition.completion() && widget.isFocused() && !popupClosed && !matches().isEmpty(); }
        boolean click(double mouseX, double mouseY) {
            if (!popup() || mouseX < x || mouseX >= x + popupWidth()) return false;
            int row = (int) ((mouseY - popupY()) / 12);
            if (mouseY < popupY() || row >= Math.min(popupRows(), matches().size() - popupOffset)) return false;
            choose(popupOffset + row); return true;
        }
        void choose(int index) {
            if (completionBase == null) completionBase = widget.getText();
            completionIndex = index;
            completing = true; widget.setText(matches().get(index)); completing = false;
            popupClosed = false;
            popupOffset = Math.max(0, Math.min(popupOffset, completionIndex));
            if (completionIndex >= popupOffset + popupRows()) popupOffset = completionIndex - popupRows() + 1;
        }
        Input(Field definition, int x, int y, int size) {
            this.definition = definition; this.x = x; this.y = y; this.size = size;
            widget = new TextFieldWidget(textRenderer, x, y, size, 20, Text.literal(definition.key()));
            widget.setMaxLength(4096); widget.setText(definition.value()); committed = definition.value();
            widget.setChangedListener(value -> {
                widget.setEditableColor(0xFFFFFF);
                if (!completing) { completionBase = null; completionIndex = -1; popupClosed = false; matchQuery = null; }
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
            popupClosed = false;
            var values = matches();
            if (values.isEmpty()) return true;
            int index = completionIndex < 0 ? (backwards ? values.size() - 1 : 0)
                    : Math.floorMod(completionIndex + (backwards ? -1 : 1), values.size());
            choose(index);
            setFocused(widget); widget.setFocused(true);
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
        for (Input input : inputs) if (input.removeClick(x, y)) return true;
        for (Input input : inputs) if (input.click(x, y)) return true;
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
            if ((keyCode == 264 || keyCode == 265) && input.definition.completion()) return input.complete(keyCode == 265);
            if (keyCode == 256 && input.popup()) { input.popupClosed = true; return true; }
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
    public boolean mouseScrolled(double x, double y, double horizontal, double amount) {
        for (Input input : inputs) if (input.popup() && x >= input.x && x < input.x + input.popupWidth()
                && y >= input.popupY() && y < input.popupY() + input.popupRows() * 12) {
            input.popupOffset = Math.max(0, Math.min(Math.max(0, input.matches().size() - input.popupRows()),
                    input.popupOffset + (amount > 0 ? -1 : 1)));
            return true;
        }
        if (amount == 0 || x < navLeft || !commitInputs()) return false;
        if (x < paneLeft) navScroll = Math.max(0, navScroll + (amount > 0 ? -1 : 1));
        else scroll = Math.max(0, Math.min(Math.max(0, rows.size() - visibleRows), scroll + (amount > 0 ? -1 : 1)));
        setFocused(null); rebuild(); return true;
    }

    @Override
    public void close() { if (commitInputs()) client.setScreen(parent); }

    @Override
    public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        renderBackground(draw, mouseX, mouseY, delta);
        draw.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        super.render(draw, mouseX, mouseY, delta);
        for (Caption caption : captions) drawCaption(draw, caption, 0xFFFFFF);
        if (!error.isEmpty()) drawCaption(draw, new Caption(error, paneLeft, height - 45, paneWidth), UiStyle.ERROR);
        for (Input input : inputs) if (input.popup()) {
            var matches = input.matches();
            int count = Math.min(input.popupRows(), matches.size() - input.popupOffset);
            int top = input.popupY();
            draw.fill(input.x, top, input.x + input.popupWidth(), top + count * 12, 0xF0202020);
            draw.enableScissor(input.x, top, input.x + input.popupWidth(), top + count * 12);
            for (int i = 0; i < count; i++) {
                int index = input.popupOffset + i;
                int rowY = top + i * 12;
                if (index == input.completionIndex) draw.fill(input.x, rowY, input.x + input.popupWidth(), rowY + 12, 0xFF355870);
                String suggestion = matches.get(index);
                suggestion = suggestion.substring(suggestion.lastIndexOf(',') + 1).stripLeading();
                draw.drawTextWithShadow(textRenderer, Text.literal(suggestion), input.x + 3, rowY + 2, 0xFFFFFF);
            }
            draw.disableScissor();
        }
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
            draw.drawTextWithShadow(textRenderer, Text.literal(line), caption.x(), caption.y() + offset + (textRenderer.getWidth(caption.text()) <= caption.width() ? 5 : 0), color);
        }
    }
}
