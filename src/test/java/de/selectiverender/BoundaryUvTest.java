package de.selectiverender;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundaryUvTest {
    private static final float[] S = {0,0,1,1}, T = {0,1,1,0};
    @Test void preservesAllFourCornersIncludingRotationAndMirroring() {
        for (int rotation = 0; rotation < 4; rotation++) for (int mirror = 0; mirror < 2; mirror++) {
            float[] u = new float[4], v = new float[4];
            for (int i=0;i<4;i++) {
                int j=(i+rotation)%4; u[i]=.25f+.125f*(mirror==0?S[j]:1-S[j]); v[i]=.5f+.0625f*T[j];
            }
            var mapping=BoundaryUv.of(S,T,u,v);
            for (int i=0;i<4;i++) { assertEquals(u[i],mapping.u(S[i],T[i]),.000001); assertEquals(v[i],mapping.v(S[i],T[i]),.000001); }
            assertEquals(.3125f,mapping.u(.5f,.5f),.000001); assertEquals(.53125f,mapping.v(.5f,.5f),.000001);
        }
    }
    @Test void preservesCroppedTileScaleInsteadOfStretchingThePiece() {
        var mapping=BoundaryUv.of(new float[]{0,0,.5f,.5f},T,new float[]{.25f,.25f,.3125f,.3125f},new float[]{.625f,.5f,.5f,.625f});
        assertEquals(.28125f,mapping.u(.25f,.5f),.000001); assertEquals(.5625f,mapping.v(.25f,.5f),.000001);
    }
    @Test void degenerateFaceCannotProduceNaN() {
        var mapping=BoundaryUv.of(new float[4],new float[4],new float[]{.25f,0,0,0},new float[]{.5f,0,0,0});
        assertEquals(.25f,mapping.u(1,1)); assertEquals(.5f,mapping.v(1,1));
    }
}
