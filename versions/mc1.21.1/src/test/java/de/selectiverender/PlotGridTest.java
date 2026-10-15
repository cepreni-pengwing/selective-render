package de.selectiverender;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlotGridTest {
    @Test void inclusiveCornersAndAllFourNeighborDirectionsIdentifyTheSameRoad() {
        var first=new PlotGrid(15,23,0,0,null,true);
        for(var neighbor:new PlotGrid[]{new PlotGrid(20,0,35,23,null,true),new PlotGrid(-20,0,-5,23,null,true),
                new PlotGrid(0,28,15,51,null,true),new PlotGrid(0,-28,15,-5,null,true)}) {
            var grid=first.identifyNeighbor(neighbor);
            assertEquals(4,grid.roadWidth()); assertEquals(first.minX(),grid.x1()); assertEquals(first.maxZ(),grid.z2());
        }
    }
    @Test void plotsAndRoadsRepeatCorrectlyAcrossZeroAndNegativeCoordinates() {
        var grid=new PlotGrid(3,7,18,30,4,true);
        for(int x=-100;x<=100;x++) for(int z=-100;z<=100;z++) {
            int dx=Math.floorMod(x-3,20),dz=Math.floorMod(z-7,28);
            var result=grid.plotAt(x,z);
            if(dx>=16 || dz>=24) assertNull(result);
            else assertArrayEquals(new int[]{x-dx,x-dx+15,z-dz,z-dz+23},result);
        }
    }
    @Test void normalizedGridKeepsEveryPlotAndRoadAtTheSameWorldCoordinates() {
        var original=new PlotGrid(-157,847,-142,870,4,true);
        var normalized=original.normalized();
        assertEquals(3,normalized.x1()); assertEquals(7,normalized.z1());
        assertEquals(18,normalized.x2()); assertEquals(30,normalized.z2());
        assertEquals(normalized,normalized.normalized());
        for(int x=-250;x<=250;x++) for(int z=-50;z<=50;z++)
            assertArrayEquals(original.plotAt(x,z),normalized.plotAt(x,z));
    }
    @Test void missingRoadIsDifferentFromZeroWidthRoad() {
        var grid=new PlotGrid(0,0,15,15,null,true);
        assertNull(grid.plotAt(0,0));
        assertArrayEquals(new int[]{16,31,0,15},grid.withRoad(0).plotAt(16,0));
        assertEquals(0,grid.identifyNeighbor(new PlotGrid(16,0,31,15,null,true)).roadWidth());
    }
    @Test void overlappingDiagonalSameAndDifferentlySizedPlotsAreRejected() {
        var grid=new PlotGrid(0,0,15,15,null,true);
        for(var bad:new PlotGrid[]{grid,new PlotGrid(1,0,16,15,null,true),new PlotGrid(20,20,35,35,null,true),new PlotGrid(20,0,36,15,null,true)})
            assertThrows(IllegalArgumentException.class,()->grid.identifyNeighbor(bad));
        assertNull(grid.roadWidth());
    }
    @Test void settingsRoundTripPreservesPartialCompleteAndDisabledProfiles() {
        var gson=new Gson();
        for(var grid:new PlotGrid[]{new PlotGrid(-32,-8,-1,7,null,true),new PlotGrid(-32,-8,-1,7,7,true),new PlotGrid(-32,-8,-1,7,7,false)})
            assertEquals(grid,gson.fromJson(gson.toJson(grid),PlotGrid.class));
    }
    @Test void worldLimitsAndRoadWidthDoNotOverflow() {
        assertThrows(IllegalArgumentException.class,()->new PlotGrid(Integer.MIN_VALUE,0,0,0,0,true));
        assertThrows(IllegalArgumentException.class,()->new PlotGrid(0,0,1,1,-1,true));
        var grid=new PlotGrid(-30000000,-30000000,30000000,30000000,30000000,true);
        assertArrayEquals(new int[]{-30000000,30000000,-30000000,30000000},grid.plotAt(0,0));
        assertNull(new PlotGrid(29999995,0,30000000,5,4,true).plotAt(30000001,0));
    }
}
