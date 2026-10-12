package de.selectiverender;
/** Detached storage only; never writes geometry by itself. */
public final class BoundaryQuadCopy extends net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableQuadViewImpl {
    public BoundaryQuadCopy() { data = new int[net.caffeinemc.mods.sodium.client.render.frapi.mesh.EncodingFormat.TOTAL_STRIDE]; }
    @Override public void emitDirectly() { }
}
