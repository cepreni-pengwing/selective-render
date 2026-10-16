package de.selectiverender;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualLightPropagationTest {
    @Test void frontierBatchesMatchTheCompleteFrontierForEveryBatchSize() {
        byte[] light=new byte[7*5*9]; var random=new java.util.Random(42);
        for(int i=0;i<light.length;i++) light[i]=(byte)random.nextInt(16);
        byte[] wholeQueued=new byte[light.length]; int[] whole=new int[light.length];
        int expected=VirtualLightPropagation.seedFrontier(light,wholeQueued,whole,7,5,9);
        for(int batch:new int[]{1,2,7,64,128,1000}) {
            byte[] queued=new byte[light.length]; int[] queue=new int[light.length]; int count=0;
            for(int from=0;from<light.length;from+=batch) count+=VirtualLightPropagation.seedFrontierRange(
                    light,queued,queue,7,5,9,from,Math.min(light.length,from+batch),count);
            org.junit.jupiter.api.Assertions.assertEquals(expected,count);
            org.junit.jupiter.api.Assertions.assertArrayEquals(wholeQueued,queued);
            org.junit.jupiter.api.Assertions.assertArrayEquals(whole,queue);
        }
    }

    @Test
    void skipsNeighborsThatCannotBecomeBrighter() {
        assertFalse(VirtualLightPropagation.canImprove(15, 15));
        assertFalse(VirtualLightPropagation.canImprove(15, 14));
        assertFalse(VirtualLightPropagation.canImprove(1, 0));
    }

    @Test
    void retainsNeighborsThatMayBecomeBrighter() {
        assertTrue(VirtualLightPropagation.canImprove(15, 13));
        assertTrue(VirtualLightPropagation.canImprove(2, 0));
    }
}
