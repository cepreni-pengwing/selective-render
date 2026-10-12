package de.selectiverender;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.*;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
/** Resolves a configured block model at a virtual boundary, using real loaded-pack quads and CTM transforms. */
public final class BoundaryTextureResolver {
    public record Face(TextureAtlasSprite sprite, float u0, float u1, float v0, float v1, int color, boolean translucent, float area) { }
    private static final AtomicInteger EPOCH = new AtomicInteger();
    private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(Cache::new);
    private static final ThreadLocal<Face> BOUND = new ThreadLocal<>();
    private static final Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final class Cache { long pos = Long.MIN_VALUE; int generation = -1, epoch = -1; Object world; final Face[] faces = new Face[6]; final boolean[] resolved = new boolean[6]; }
    private BoundaryTextureResolver() { }
    public static void invalidate() { EPOCH.incrementAndGet(); }
    public static Face bound() { return BOUND.get(); }
    public static void clear() { BOUND.remove(); }
    public static BlockState selectedState() {
        var id = Identifier.tryParse(SelectiveRenderSettings.boundaryTexture());
        return id == null ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState() : BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
    }
    public static void bind(BlockPos pos, Direction face) {
        clear(); var client = Minecraft.getInstance(); var world = client.level;
        if (pos == null || world == null) return;
        var cache = CACHE.get(); int generation = SelectiveRenderState.visibilityGeneration(), epoch = EPOCH.get();
        if (cache.pos != pos.asLong() || cache.generation != generation || cache.epoch != epoch || cache.world != world) {
            cache.pos = pos.asLong(); cache.generation = generation; cache.epoch = epoch; cache.world = world;
            Arrays.fill(cache.faces, null); Arrays.fill(cache.resolved, false);
        }
        int index = face.ordinal();
        if (!cache.resolved[index]) {
            cache.resolved[index] = true;
            try { cache.faces[index] = resolve(pos, face, selectedState()); }
            catch (Exception error) { if (WARNED.add(SelectiveRenderSettings.boundaryTexture()))
                SelectiveRenderClient.LOGGER.warn("Could not resolve boundary model {}: {}; using representative texture", SelectiveRenderSettings.boundaryTexture(), error.toString()); }
        }
        BOUND.set(cache.faces[index]);
    }
    static net.minecraft.client.renderer.block.BlockAndTintGetter virtualWorld(BlockPos pos, Direction face, BlockState state) {
        Object delegate = Minecraft.getInstance().level;
        return (net.minecraft.client.renderer.block.BlockAndTintGetter)Proxy.newProxyInstance(net.minecraft.client.renderer.block.BlockAndTintGetter.class.getClassLoader(), new Class<?>[]{net.minecraft.client.renderer.block.BlockAndTintGetter.class}, (proxy, method, args) -> {
            // Use the same signature-based virtual read on all supported game versions.
            if (method.getReturnType() == BlockState.class && args != null && args.length == 1 && args[0] instanceof BlockPos query) {
                if (query.equals(pos) || (BoundaryStyle.texture(SelectiveRenderState.boundaryModeAt(query.getX(), query.getY(), query.getZ()))
                        && !Minecraft.getInstance().level.getBlockState(query).isAir() && SelectiveRenderState.isBoundaryFace(query, face))) return state;
            }
            try { return method.invoke(delegate, args); } catch (InvocationTargetException e) { throw e.getCause(); }
        });
    }
    private static Face capture(QuadView quad, BlockPos pos, Direction face, BlockState state, net.minecraft.client.renderer.block.BlockAndTintGetter world) {
        if (quad.lightFace() != face) return null;
        float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        float minU=minX,minV=minX,maxU=maxX,maxV=maxX;
        for (int i=0;i<4;i++) { minX=Math.min(minX,quad.x(i)); maxX=Math.max(maxX,quad.x(i)); minY=Math.min(minY,quad.y(i)); maxY=Math.max(maxY,quad.y(i));
            minZ=Math.min(minZ,quad.z(i)); maxZ=Math.max(maxZ,quad.z(i)); minU=Math.min(minU,quad.u(i)); maxU=Math.max(maxU,quad.u(i)); minV=Math.min(minV,quad.v(i)); maxV=Math.max(maxV,quad.v(i)); }
        float area = switch (face.getAxis()) { case X -> (maxY-minY)*(maxZ-minZ); case Y -> (maxX-minX)*(maxZ-minZ); case Z -> (maxX-minX)*(maxY-minY); };
        if (area <= .000001f || !Float.isFinite(area)) return null;
        var sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS).spriteFinder().find(quad);
        if (sprite == null) return null;
        int color = quad.color(0), tintIndex = quad.tintIndex();
        if (tintIndex >= 0) {
            var source = Minecraft.getInstance().getBlockColors().getTintSource(state, tintIndex);
            int tint = source == null ? -1 : source.colorInWorld(state, world, pos);
            color = 0xFF000000 | ((color >> 16 & 255)*(tint >> 16 & 255)/255)<<16 | ((color >> 8 & 255)*(tint >> 8 & 255)/255)<<8 | (color & 255)*(tint & 255)/255;
        }
        return new Face(sprite, minU, maxU, minV, maxV, color | 0xFF000000, quad.chunkLayer() == net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT, area);
    }
    private static Face resolve(BlockPos pos, Direction face, BlockState state) {
        var client = net.minecraft.client.Minecraft.getInstance(); var world = virtualWorld(pos, face, state);
        var model = client.getModelManager().getBlockStateModelSet().get(state);
        Face[] result={null};
        var emitter=Renderer.get().quadEmitter(quad -> {
            var candidate=capture(quad,pos,face,state,world);
            if (candidate!=null && (result[0]==null || candidate.area()>result[0].area())) result[0]=candidate;
        });
        model.emitQuads(emitter,world,pos,state,net.minecraft.util.RandomSource.create(pos.asLong()),direction -> direction != null && direction != face);
        return result[0];
    }

}
