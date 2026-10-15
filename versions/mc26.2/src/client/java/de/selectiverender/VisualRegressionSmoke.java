package de.selectiverender;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;

/** Destructive fixtures are strictly opt-in, integrated-server-only, disposable CI test worlds. */
final class VisualRegressionSmoke {
    private static final boolean ENABLED=Boolean.getBoolean("selectiverender.visual.smoke");
    private static final VisualScenarioPlan.Case[] SCENARIOS=java.util.Arrays.stream(VisualScenarioPlan.CASES)
            .filter(value -> !Boolean.getBoolean("selectiverender.visual.uiOnly") || value.ui()).toArray(VisualScenarioPlan.Case[]::new);
    private static final String NAME="__sr_visual_fixture__", LEFT="__sr_visual_left__";
    private static int ticks, index=-1, age, finishing;
    private static BlockPos origin;
    private static BlockRegion box;
    private static java.util.concurrent.CompletableFuture<Void> ready;
    private static net.minecraft.world.entity.Entity originalCamera;
    private static RegionEditorScreen editor;
    private static boolean originalHud;
    private VisualRegressionSmoke() { }
    static void tick(Minecraft client) {
        if (!ENABLED || client.level==null || client.player==null || finishing<0) return;
        ticks++;
        if (ticks<100) return;
        if (ticks==100) { prepareFixture(client); return; }
        if (ticks>2000) throw new IllegalStateException("Visual suite timed out");
        if (!ready.isDone()) return;
        ready.join();
        if (finishing>0) {
            if (--finishing==0) {
                client.setCameraEntity(originalCamera);
                if (client.gui.hud.isHidden()!=originalHud) client.gui.hud.toggle();
                SelectiveRenderConfig.deleteRegion(client,LEFT); SelectiveRenderConfig.deleteRegion(client,NAME);
                PlotGridClient.save(null);
                client.gui.setScreen(null); finishing=-1;
                SelectiveRenderClient.LOGGER.info("[SR visual smoke] PASS: {} screenshots and widget/policy assertions", SCENARIOS.length);
            }
            return;
        }
        if (index<0) {
            if (!fixtureDelivered(client)) return;
            index=0; configure(client,SCENARIOS[index]);
            // Network updates can arrive after the server task completes; rebuild only after all cells match.
            client.levelExtractor.allChanged();
        }
        var scenario=SCENARIOS[index];
        if (++age<(index==0&&!scenario.ui()?120:scenario.ui()?10:40)) return;
        capture(client,"sr-visual-"+scenario.id()+".png");
        SelectiveRenderClient.LOGGER.info("[SR visual smoke] CAPTURE {}",scenario.id());
        age=0;
        if (++index==SCENARIOS.length) { finishing=10; return; }
        configure(client,SCENARIOS[index]);
    }
    private static boolean fixtureDelivered(Minecraft client) {
        for(int x=-4;x<=4;x++) for(int y=0;y<=7;y++) for(int z=0;z<=3;z++) {
            var expected=y>5?Blocks.AIR.defaultBlockState():y==5?Blocks.SHORT_GRASS.defaultBlockState():y==4?Blocks.GRASS_BLOCK.defaultBlockState():x==-4?Blocks.GLASS.defaultBlockState():x==4?Blocks.DIRT.defaultBlockState():Blocks.STONE.defaultBlockState();
            if(!client.level.getBlockState(new BlockPos(origin.getX()+x,origin.getY()+y,origin.getZ()+z)).equals(expected)) return false;
        }
        return true;
    }
    private static void prepareFixture(Minecraft client) {
        var server=client.getSingleplayerServer();
        if (server==null) throw new IllegalStateException("Visual fixtures require a disposable integrated-server world");
        var p=client.player.blockPosition();
        origin=new BlockPos(p.getX(),p.getY()+12,p.getZ());
        box=new BlockRegion(origin.getX()-4,origin.getX()+4,origin.getY(),origin.getY()+5,origin.getZ()+1,origin.getZ()+3);
        originalCamera=client.getCameraEntity();
        originalHud=client.gui.hud.isHidden(); if (!originalHud) client.gui.hud.toggle();
        client.gui.setScreen(new SceneScreen("Preparing fixed boundary fixture"));
        ready=new java.util.concurrent.CompletableFuture<>();
        var dimension=client.level.dimension();
        server.execute(()-> {
            try {
                var world=server.getLevel(dimension);
                if (world==null) throw new IllegalStateException("Fixture dimension disappeared");
                for (int x=-4;x<=4;x++) for (int y=0;y<=7;y++) for (int z=0;z<=3;z++) {
                    var state=y>5?Blocks.AIR.defaultBlockState():y==5?Blocks.SHORT_GRASS.defaultBlockState():y==4?Blocks.GRASS_BLOCK.defaultBlockState():x==-4?Blocks.GLASS.defaultBlockState():x==4?Blocks.DIRT.defaultBlockState():Blocks.STONE.defaultBlockState();
                    world.setBlock(new BlockPos(origin.getX()+x,origin.getY()+y,origin.getZ()+z),state,3);
                }
                ready.complete(null);
            } catch (Throwable error) { ready.completeExceptionally(error); }
        });
        var camera=new net.minecraft.world.entity.decoration.ArmorStand(client.level,origin.getX()+.5,origin.getY()+.75,origin.getZ()-10.5);
        camera.snapTo(origin.getX()+.5,origin.getY()+.75,origin.getZ()-10.5,0,0); camera.setOldPosAndRot();
        client.setCameraEntity(camera);
        client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        if (!SelectiveRenderConfig.saveRegion(client,NAME,box,false)) throw new IllegalStateException("Could not create visual fixture region");
        SelectiveRenderSettings.setVirtualLightMode(SelectiveRenderSettings.VirtualLightMode.NONE);
        SelectiveRenderSettings.setBoundaryColorLighting(false); SelectiveRenderSettings.setBoundaryNormal(true);
    }
    private static void configure(Minecraft client,VisualScenarioPlan.Case scenario) {
        String id=scenario.id();
        if (!id.equals("19-ui-reset-confirm") && !id.equals("20-ui-reset-done")) {
            SelectiveRenderConfig.deleteRegion(client,LEFT);
            SelectiveRenderConfig.clearBlockFilters(client,NAME);
            SelectiveRenderConfig.editRegion(client,client.level.dimension().identifier().toString(),NAME,NAME,java.util.List.of(box),false,true,java.util.List.of(),RegionOptions.DEFAULT);
            SelectiveRenderSettings.setBoundaryRainbow(scenario.rainbow());
            SelectiveRenderSettings.setBoundaryAppearance(0xFF0000,"minecraft:diamond_block",scenario.opacity());
            SelectiveRenderSettings.setBoundaryMode(scenario.mode());
        }
        if (!scenario.ui()) {
            if (id.equals("11-only-stone")) SelectiveRenderConfig.setBlockFilter(client,NAME,new BlockFilterRule(BlockFilterRule.Mode.ONLY,BlockFilterRule.Kind.ID,"minecraft:stone"));
            if (id.equals("12-region-split")) {
                var left=new BlockRegion(origin.getX()+1,box.maxX(),box.minY(),box.maxY(),box.minZ(),box.maxZ());
                SelectiveRenderConfig.saveRegion(client,LEFT,left,false);
                SelectiveRenderConfig.editRegion(client,client.level.dimension().identifier().toString(),LEFT,LEFT,java.util.List.of(left),false,true,java.util.List.of(),RegionOptions.DEFAULT.withColor(new RegionOptions.ColorLayer(true,0x0000FF,100,false,false)));
            }
            if (!SelectiveRenderState.isBoundaryFace(new BlockPos(origin.getX(),origin.getY()+2,origin.getZ()+1),net.minecraft.core.Direction.NORTH)
                    || SelectiveRenderState.isBoundaryFace(new BlockPos(origin.getX(),origin.getY()+2,origin.getZ()+2),net.minecraft.core.Direction.NORTH))
                throw new IllegalStateException("Fixture cut/interior classification is wrong");
            client.gui.setScreen(new SceneScreen(scenario.description()));
            return;
        }
        try {
            if (id.equals("22-ui-grid-partial") || id.equals("23-ui-grid-complete")) {
                var grid=new PlotGrid(-32,-32,-1,-1,id.equals("22-ui-grid-partial")?null:Integer.valueOf(7),true).normalized();
                PlotGridClient.save(grid);
                var page=new SelectiveRenderSettingsScreen(null,SelectiveRenderSettingsScreen.Tab.PLOTS,null,0);
                client.gui.setScreen(page);
                assertWidgets(page,0);
                var fields=(java.util.Map<?,?>)field(page,"gridFields");
                if(fields.size()!=3) throw new IllegalStateException("Plot grid must expose exactly three editable values");
                for(var child:page.children()) if(child instanceof net.minecraft.client.gui.components.AbstractWidget button) {
                    if(button.getMessage().getString().equals("Save grid")) throw new IllegalStateException("Redundant save-grid button remained");
                    if(button.getMessage().getString().equals("Delete grid") && button.getY()!=36) throw new IllegalStateException("Delete grid is not at the top");
                }
                if(id.equals("23-ui-grid-complete")) {
                    var road=(net.minecraft.client.gui.components.EditBox)fields.get(6);
                    var commit=SelectiveRenderSettingsScreen.class.getDeclaredMethod("commitGrid"); commit.setAccessible(true);
                    road.setValue("-1");
                    if((Boolean)commit.invoke(page) || !grid.equals(PlotGridClient.current())) throw new IllegalStateException("Invalid grid edit was accepted");
                    page.setFocused(road); page.tick(); road.setValue("9");
                    page.setFocused(null); page.tick();
                    if(PlotGridClient.current().roadWidth()!=9) throw new IllegalStateException("Grid menu did not save road edit");
                    SelectiveRenderSettings.load();
                    if(PlotGridClient.current().roadWidth()!=9) throw new IllegalStateException("Edited grid did not survive reload");
                    road.setValue("7"); commit.invoke(page);
                }
                return;
            }
            if (id.startsWith("13-") || id.startsWith("14-") || id.startsWith("15-")) {
                var page=new BoundaryAppearanceScreen(null); client.gui.setScreen(page);
                assertWidgets(page,id.startsWith("15-")?0:4);
                return;
            }
            if (id.equals("19-ui-reset-confirm") || id.equals("20-ui-reset-done")) {
                var before=SelectiveRenderConfig.editorDocument(client,client.level.dimension().identifier().toString());
                var reset=RegionEditorScreen.class.getDeclaredMethod("resetRegion"); reset.setAccessible(true); reset.invoke(editor); editor.init();
                var after=SelectiveRenderConfig.editorDocument(client,client.level.dimension().identifier().toString());
                if (id.equals("19-ui-reset-confirm") && (!before.options().equals(after.options()) || !before.filters().equals(after.filters()))) throw new IllegalStateException("First reset click altered values");
                if (id.equals("20-ui-reset-done") && (!after.filters().getOrDefault(NAME,java.util.List.of()).isEmpty() || !after.options().getOrDefault(NAME,RegionOptions.DEFAULT).equals(RegionOptions.DEFAULT))) throw new IllegalStateException("Second reset click did not restore inheritance");
                assertWidgets(editor,-1); return;
            }
            boolean off=id.equals("17-ui-region-off");
            if (id.equals("16-ui-region-color") || off || id.equals("18-ui-reset-before")) {
                var options=RegionOptions.DEFAULT.withColor(new RegionOptions.ColorLayer(!off,0x0000FF,35,false,false));
                var filters=id.equals("18-ui-reset-before")?java.util.List.of(new BlockFilterRule(BlockFilterRule.Mode.HIDE,BlockFilterRule.Kind.ID,"minecraft:grass_block")):java.util.List.<BlockFilterRule>of();
                SelectiveRenderConfig.editRegion(client,client.level.dimension().identifier().toString(),NAME,NAME,java.util.List.of(box),false,true,filters,options);
            }
            editor=new RegionEditorScreen(null); client.gui.setScreen(editor);
            field(editor,"region",NAME); field(editor,"boundariesOpen",true);
            field(editor,"scroll",id.equals("18-ui-reset-before")?0:Integer.MAX_VALUE); editor.init();
            if (off && field(editor,"colorControls")!=null) throw new IllegalStateException("Color Off retained the regional palette");
            if (!off && field(editor,"colorControls")==null) throw new IllegalStateException("Color On lost its regional palette");
            var listed=(java.util.List<RegionDocument>)field(editor,"documents");
            if(listed.stream().anyMatch(value -> !value.dimension().equals(client.level.dimension().identifier().toString()))) throw new IllegalStateException("Other dimension leaked into region menu");
            assertWidgets(editor,-1);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Visual UI scenario failed",error); }
    }
    private static Object field(Object object,String name) throws ReflectiveOperationException { var f=object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
    private static void field(Object object,String name,Object value) throws ReflectiveOperationException { var f=object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object,value); }
    /** Visible interactive widgets must stay on screen and may not overlap one another. */
    private static void assertWidgets(Screen page,int expectedSliders) {
        var widgets=new java.util.ArrayList<net.minecraft.client.gui.components.AbstractWidget>(); int sliders=0;
        for (var child:page.children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.visible) {
            if (widget instanceof net.minecraft.client.gui.components.AbstractSliderButton) sliders++;
            if (widget.getX()<0 || widget.getY()<0 || widget.getX()+widget.getWidth()>page.width || widget.getY()+widget.getHeight()>page.height) throw new IllegalStateException("Widget outside visual viewport");
            for (var other:widgets) if (widget.getX()<other.getX()+other.getWidth() && widget.getX()+widget.getWidth()>other.getX() && widget.getY()<other.getY()+other.getHeight() && widget.getY()+widget.getHeight()>other.getY()) throw new IllegalStateException("Overlapping visual widgets: "+widget.getMessage()+" / "+other.getMessage());
            widgets.add(widget);
        }
        if (expectedSliders>=0 && sliders!=expectedSliders) throw new IllegalStateException("Unexpected visible palette slider count: "+sliders);
    }
    private static void capture(Minecraft client,String name) { net.minecraft.client.Screenshot.grab(client.gameDirectory,name,client.gameRenderer.mainRenderTarget(),1,message-> { }); }
    private static final class SceneScreen extends Screen {
        SceneScreen(String label) { super(Component.literal(label)); }
        @Override public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor draw,int x,int y,float delta) { }
        @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor draw,int x,int y,float delta) { draw.centeredText(font,title,width/2,8,0xFFFFFFFF); }
        @Override public boolean isPauseScreen() { return false; }
    }
}
