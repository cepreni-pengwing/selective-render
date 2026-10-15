package de.selectiverender;

import net.minecraft.client.MinecraftClient;
import java.util.HashMap;
import java.util.Map;

/** Explicit three-invocation setup; partial calibration is saved after the second invocation. */
final class PlotGridClient {
    private static final Map<String, Integer> STEPS = new HashMap<>();
    static String context() {
        var client = MinecraftClient.getInstance();
        return client.world == null ? null : SelectiveRenderConfig.contextIdentity(client,client.world);
    }
    static PlotGrid current() { var key=context(); return key==null?null:SelectiveRenderSettings.plotGrid(key); }
    static void save(PlotGrid grid) {
        var key=context();
        if(key!=null) { SelectiveRenderSettings.setPlotGrid(key,grid); STEPS.remove(key); PlotSquaredClient.clearGridPlots(); }
    }
    static String setup(BlockRegion selection) {
        var key=context();
        if(key==null) return "Join a world first.";
        int step=STEPS.getOrDefault(key,0);
        String source=SelectiveRenderSettings.worldEditSelection()?"WorldEdit":"SR positions";
        if(step==0) {
            STEPS.put(key,1);
            return "Mark opposite plot corners with "+source+" and repeat /sr plot grid.";
        }
        if(selection==null) return "Mark both plot corners with "+source+" first.";
        PlotGrid selected;
        try { selected=new PlotGrid(selection.minX(),selection.minZ(),selection.maxX(),selection.maxZ(),null,true); }
        catch(IllegalArgumentException error) { return error.getMessage(); }
        if(step==1) {
            SelectiveRenderSettings.setPlotGrid(key,selected);
            PlotSquaredClient.clearGridPlots();
            STEPS.put(key,2);
            return "Mark opposite corners of a directly neighboring plot and repeat /sr plot grid.";
        }
        var first=current();
        if(first==null) { STEPS.remove(key); return "Repeat /sr plot grid to start setup."; }
        try {
            save(first.identifyNeighbor(selected));
            return "Plot grid saved.";
        } catch(IllegalArgumentException error) { return error.getMessage(); }
    }
    static BlockRegion plotAtPlayer(int minY,int maxY) {
        var client=MinecraftClient.getInstance(); var grid=current();
        if(client.player==null || grid==null) return null;
        var pos=client.player.getBlockPos();
        var bounds=grid.plotAt(pos.getX(),pos.getZ());
        return bounds==null?null:new BlockRegion(bounds[0],bounds[1],minY,maxY,bounds[2],bounds[3]);
    }
}
