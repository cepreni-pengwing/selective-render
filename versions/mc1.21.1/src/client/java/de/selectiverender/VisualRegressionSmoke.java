package de.selectiverender;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.block.Blocks;

/** Destructive fixtures are strictly opt-in, integrated-server-only, disposable CI test worlds. */
final class VisualRegressionSmoke {
    private static final boolean ENABLED=Boolean.getBoolean("selectiverender.visual.smoke");
    private static final String NAME="__sr_visual_fixture__", LEFT="__sr_visual_left__";
    private static int ticks, index=-1, age, finishing;
    private static BlockPos origin;
    private static BlockRegion box;
    private static java.util.concurrent.CompletableFuture<Void> ready;
    private static net.minecraft.entity.Entity originalCamera;
    private static RegionEditorScreen editor;
    private static boolean originalHud;
    private VisualRegressionSmoke() { }
    static void tick(MinecraftClient client) {
        if (!ENABLED || client.world==null || client.player==null || finishing<0) return;
        ticks++;
        if (ticks<100) return;
        if (ticks==100) { prepareFixture(client); return; }
        if (ticks>2000) throw new IllegalStateException("Visual suite timed out");
        if (!ready.isDone()) return;
        ready.join();
        if (finishing>0) {
            if (--finishing==0) {
                client.setCameraEntity(originalCamera);
                client.options.hudHidden=originalHud;
                SelectiveRenderConfig.deleteRegion(client,LEFT); SelectiveRenderConfig.deleteRegion(client,NAME);
                PlotGridClient.save(null);
                client.setScreen(null); finishing=-1;
                SelectiveRenderClient.LOGGER.info("[SR visual smoke] PASS: {} screenshots and widget/policy assertions", VisualScenarioPlan.CASES.length);
            }
            return;
        }
        if (index<0) { index=0; configure(client,VisualScenarioPlan.CASES[index]); }
        var scenario=VisualScenarioPlan.CASES[index];
        if (++age<(scenario.ui()?10:40)) return;
        capture(client,"sr-visual-"+scenario.id()+".png");
        SelectiveRenderClient.LOGGER.info("[SR visual smoke] CAPTURE {}",scenario.id());
        age=0;
        if (++index==VisualScenarioPlan.CASES.length) { finishing=10; return; }
        configure(client,VisualScenarioPlan.CASES[index]);
    }
    private static void prepareFixture(MinecraftClient client) {
        var server=client.getServer();
        if (server==null) throw new IllegalStateException("Visual fixtures require a disposable integrated-server world");
        var p=client.player.getBlockPos();
        origin=new BlockPos(p.getX(),p.getY()+12,p.getZ());
        box=new BlockRegion(origin.getX()-4,origin.getX()+4,origin.getY(),origin.getY()+5,origin.getZ()+1,origin.getZ()+3);
        originalCamera=client.getCameraEntity();
        originalHud=client.options.hudHidden; client.options.hudHidden=true;
        client.setScreen(new SceneScreen("Preparing fixed boundary fixture"));
        ready=new java.util.concurrent.CompletableFuture<>();
        var dimension=client.world.getRegistryKey();
        server.execute(()-> {
            try {
                var world=server.getWorld(dimension);
                if (world==null) throw new IllegalStateException("Fixture dimension disappeared");
                for (int x=-4;x<=4;x++) for (int y=0;y<=7;y++) for (int z=0;z<=3;z++) {
                    var state=y>5?Blocks.AIR.getDefaultState():y==5?Blocks.SHORT_GRASS.getDefaultState():y==4?Blocks.GRASS_BLOCK.getDefaultState():x==-4?Blocks.GLASS.getDefaultState():x==4?Blocks.DIRT.getDefaultState():Blocks.STONE.getDefaultState();
                    world.setBlockState(new BlockPos(origin.getX()+x,origin.getY()+y,origin.getZ()+z),state,3);
                }
                ready.complete(null);
            } catch (Throwable error) { ready.completeExceptionally(error); }
        });
        var camera=new net.minecraft.entity.decoration.ArmorStandEntity(client.world,origin.getX()+.5,origin.getY()+.75,origin.getZ()-10.5);
        camera.refreshPositionAndAngles(origin.getX()+.5,origin.getY()+.75,origin.getZ()-10.5,0,0); camera.resetPosition();
        client.setCameraEntity(camera);
        client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
        if (!SelectiveRenderConfig.saveRegion(client,NAME,box,false)) throw new IllegalStateException("Could not create visual fixture region");
        SelectiveRenderSettings.setVirtualLightMode(SelectiveRenderSettings.VirtualLightMode.NONE);
        SelectiveRenderSettings.setBoundaryColorLighting(false); SelectiveRenderSettings.setBoundaryNormal(true);
    }
    private static void configure(MinecraftClient client,VisualScenarioPlan.Case scenario) {
        String id=scenario.id();
        if (!id.equals("19-ui-reset-confirm") && !id.equals("20-ui-reset-done")) {
            SelectiveRenderConfig.deleteRegion(client,LEFT);
            SelectiveRenderConfig.clearBlockFilters(client,NAME);
            SelectiveRenderConfig.editRegion(client,client.world.getRegistryKey().getValue().toString(),NAME,NAME,java.util.List.of(box),false,true,java.util.List.of(),RegionOptions.DEFAULT);
            SelectiveRenderSettings.setBoundaryRainbow(scenario.rainbow());
            SelectiveRenderSettings.setBoundaryAppearance(0xFF0000,"minecraft:diamond_block",scenario.opacity());
            SelectiveRenderSettings.setBoundaryMode(scenario.mode());
        }
        if (!scenario.ui()) {
            if (id.equals("11-only-stone")) SelectiveRenderConfig.setBlockFilter(client,NAME,new BlockFilterRule(BlockFilterRule.Mode.ONLY,BlockFilterRule.Kind.ID,"minecraft:stone"));
            if (id.equals("12-region-split")) {
                var left=new BlockRegion(origin.getX()+1,box.maxX(),box.minY(),box.maxY(),box.minZ(),box.maxZ());
                SelectiveRenderConfig.saveRegion(client,LEFT,left,false);
                SelectiveRenderConfig.editRegion(client,client.world.getRegistryKey().getValue().toString(),LEFT,LEFT,java.util.List.of(left),false,true,java.util.List.of(),RegionOptions.DEFAULT.withColor(new RegionOptions.ColorLayer(true,0x0000FF,100,false,false)));
            }
            if (!SelectiveRenderState.isBoundaryFace(new BlockPos(origin.getX(),origin.getY()+2,origin.getZ()+1),net.minecraft.util.math.Direction.NORTH)
                    || SelectiveRenderState.isBoundaryFace(new BlockPos(origin.getX(),origin.getY()+2,origin.getZ()+2),net.minecraft.util.math.Direction.NORTH))
                throw new IllegalStateException("Fixture cut/interior classification is wrong");
            client.setScreen(new SceneScreen(scenario.description()));
            return;
        }
        try {
            if (id.equals("22-ui-grid-partial") || id.equals("23-ui-grid-complete")) {
                var grid=new PlotGrid(-32,-32,-1,-1,id.equals("22-ui-grid-partial")?null:Integer.valueOf(7),true);
                PlotGridClient.save(grid);
                var page=new SelectiveRenderSettingsScreen(null,SelectiveRenderSettingsScreen.Tab.PLOTS,null,0);
                client.setScreen(page);
                assertWidgets(page,0);
                var fields=(java.util.Map<?,?>)field(page,"gridFields");
                if(fields.size()!=3) throw new IllegalStateException("Plot grid must expose exactly three editable values");
                return;
            }
            if (id.startsWith("13-") || id.startsWith("14-") || id.startsWith("15-")) {
                var page=new BoundaryAppearanceScreen(null); client.setScreen(page);
                assertWidgets(page,id.startsWith("15-")?0:4);
                return;
            }
            if (id.equals("19-ui-reset-confirm") || id.equals("20-ui-reset-done")) {
                var before=SelectiveRenderConfig.editorDocument(client,client.world.getRegistryKey().getValue().toString());
                var reset=RegionEditorScreen.class.getDeclaredMethod("resetRegion"); reset.setAccessible(true); reset.invoke(editor); editor.init();
                var after=SelectiveRenderConfig.editorDocument(client,client.world.getRegistryKey().getValue().toString());
                if (id.equals("19-ui-reset-confirm") && (!before.options().equals(after.options()) || !before.filters().equals(after.filters()))) throw new IllegalStateException("First reset click altered values");
                if (id.equals("20-ui-reset-done") && (!after.filters().getOrDefault(NAME,java.util.List.of()).isEmpty() || !after.options().getOrDefault(NAME,RegionOptions.DEFAULT).equals(RegionOptions.DEFAULT))) throw new IllegalStateException("Second reset click did not restore inheritance");
                assertWidgets(editor,-1); return;
            }
            boolean off=id.equals("17-ui-region-off");
            if (id.equals("16-ui-region-color") || off || id.equals("18-ui-reset-before")) {
                var options=RegionOptions.DEFAULT.withColor(new RegionOptions.ColorLayer(!off,0x0000FF,35,false,false));
                var filters=id.equals("18-ui-reset-before")?java.util.List.of(new BlockFilterRule(BlockFilterRule.Mode.HIDE,BlockFilterRule.Kind.ID,"minecraft:grass_block")):java.util.List.<BlockFilterRule>of();
                SelectiveRenderConfig.editRegion(client,client.world.getRegistryKey().getValue().toString(),NAME,NAME,java.util.List.of(box),false,true,filters,options);
            }
            editor=new RegionEditorScreen(null); client.setScreen(editor);
            field(editor,"region",NAME); field(editor,"boundariesOpen",true);
            field(editor,"scroll",id.equals("18-ui-reset-before")?0:Integer.MAX_VALUE); editor.init();
            if (off && field(editor,"colorControls")!=null) throw new IllegalStateException("Color Off retained the regional palette");
            if (!off && field(editor,"colorControls")==null) throw new IllegalStateException("Color On lost its regional palette");
            assertWidgets(editor,-1);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Visual UI scenario failed",error); }
    }
    private static Object field(Object object,String name) throws ReflectiveOperationException { var f=object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
    private static void field(Object object,String name,Object value) throws ReflectiveOperationException { var f=object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object,value); }
    /** Visible interactive widgets must stay on screen and may not overlap one another. */
    private static void assertWidgets(Screen page,int expectedSliders) {
        var widgets=new java.util.ArrayList<net.minecraft.client.gui.widget.ClickableWidget>(); int sliders=0;
        for (var child:page.children()) if (child instanceof net.minecraft.client.gui.widget.ClickableWidget widget && widget.visible) {
            if (widget instanceof net.minecraft.client.gui.widget.SliderWidget) sliders++;
            if (widget.getX()<0 || widget.getY()<0 || widget.getX()+widget.getWidth()>page.width || widget.getY()+widget.getHeight()>page.height) throw new IllegalStateException("Widget outside visual viewport");
            for (var other:widgets) if (widget.getX()<other.getX()+other.getWidth() && widget.getX()+widget.getWidth()>other.getX() && widget.getY()<other.getY()+other.getHeight() && widget.getY()+widget.getHeight()>other.getY()) throw new IllegalStateException("Overlapping visual widgets: "+widget.getMessage()+" / "+other.getMessage());
            widgets.add(widget);
        }
        if (expectedSliders>=0 && sliders!=expectedSliders) throw new IllegalStateException("Unexpected visible palette slider count: "+sliders);
    }
    private static void capture(MinecraftClient client,String name) { net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),message-> { }); }
    private static final class SceneScreen extends Screen {
        SceneScreen(String label) { super(Text.literal(label)); }
        @Override public void render(net.minecraft.client.gui.DrawContext draw,int x,int y,float delta) { draw.drawCenteredTextWithShadow(textRenderer,title,width/2,8,0xFFFFFFFF); }
        @Override public boolean shouldPause() { return false; }
    }
}
