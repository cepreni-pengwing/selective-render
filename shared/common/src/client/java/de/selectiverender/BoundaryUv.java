package de.selectiverender;

/** Affine face-space mapping preserves model UV rotation, mirroring and sub-tile scale. */
public final class BoundaryUv {
    private BoundaryUv() { }
    public record Mapping(float us, float ut, float u0, float vs, float vt, float v0) {
        public float u(float s, float t) { return us * s + ut * t + u0; }
        public float v(float s, float t) { return vs * s + vt * t + v0; }
    }

    public static Mapping of(float[] s, float[] t, float[] u, float[] v) {
        for (int a = 1; a < 4; a++) for (int b = a + 1; b < 4; b++) {
            float sa = s[a]-s[0], ta = t[a]-t[0], sb = s[b]-s[0], tb = t[b]-t[0];
            float determinant = sa*tb-sb*ta;
            if (Math.abs(determinant) < .000001f || !Float.isFinite(determinant)) continue;
            float us = ((u[a]-u[0])*tb-(u[b]-u[0])*ta)/determinant;
            float ut = (sa*(u[b]-u[0])-sb*(u[a]-u[0]))/determinant;
            float vs = ((v[a]-v[0])*tb-(v[b]-v[0])*ta)/determinant;
            float vt = (sa*(v[b]-v[0])-sb*(v[a]-v[0]))/determinant;
            return new Mapping(us, ut, u[0]-us*s[0]-ut*t[0], vs, vt, v[0]-vs*s[0]-vt*t[0]);
        }
        return new Mapping(0, 0, u[0], 0, 0, v[0]);
    }
}
