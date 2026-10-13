package de.selectiverender;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.world.BlockRenderView;
import net.minecraft.util.math.Direction;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.renderer.v1.mesh.*;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
/** Resolves a configured block model at a virtual boundary, using real loaded-pack quads and CTM transforms. */
public final class BoundaryTextureResolver {
    /** Marker for the detached model collector; native renderer adapters must not cast it to their terrain context. */
    public interface ModelContext { }
    public record Face(Sprite sprite, float u0, float u1, float v0, float v1, int color, boolean translucent, float area, BoundaryUv.Mapping uv) { }
    private static final AtomicInteger EPOCH = new AtomicInteger();
    private static final AtomicInteger NESTED_SMOKE = new AtomicInteger();
    public static int nestedSmokeFaces() { return NESTED_SMOKE.get(); }
    static void resetNestedSmokeFaces() { NESTED_SMOKE.set(0); }
    private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(Cache::new);
    private static final ThreadLocal<Face> BOUND = new ThreadLocal<>();
    private static final Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final class Cache { long pos = Long.MIN_VALUE; int generation = -1, epoch = -1; Object world; final Face[] faces = new Face[6]; final boolean[] resolved = new boolean[6]; }
    private BoundaryTextureResolver() { }
    public static void invalidate() { EPOCH.incrementAndGet(); }
    public static Face bound() { return BOUND.get(); }
    public static void clear() { BOUND.remove(); }
    public static BlockState selectedState() {
        var id = Identifier.tryParse(BoundaryRenderContext.texture());
        return id == null ? net.minecraft.block.Blocks.STONE.getDefaultState() : Registries.BLOCK.get(id).getDefaultState();
    }
    public static void bind(BlockPos pos, Direction face) {
        clear(); var client = MinecraftClient.getInstance(); var world = client.world;
        if (pos == null || world == null) return;
        var cache = CACHE.get(); int generation = SelectiveRenderState.visibilityGeneration(), epoch = EPOCH.get();
        if (cache.pos != pos.asLong() || cache.generation != generation || cache.epoch != epoch || cache.world != world) {
            cache.pos = pos.asLong(); cache.generation = generation; cache.epoch = epoch; cache.world = world;
            Arrays.fill(cache.faces, null); Arrays.fill(cache.resolved, false);
        }
        int index = face.ordinal();
        if (!cache.resolved[index]) {
            cache.resolved[index] = true;
            try (var scope = BoundaryModelScope.open()) {
                cache.faces[index] = resolve(pos, face, selectedState());
                if (Boolean.getBoolean("selectiverender.ctm.smoke") && scope.nested() && cache.faces[index] != null
                        && smokePixel(cache.faces[index]) == 0x20CC70) NESTED_SMOKE.incrementAndGet();
                scope.complete();
            }
            catch (Exception error) { if (WARNED.add(BoundaryRenderContext.texture()))
                SelectiveRenderClient.LOGGER.warn("Could not resolve boundary model {}: {}; using representative texture", BoundaryRenderContext.texture(), error.toString()); }
        }
        BOUND.set(cache.faces[index]);
    }
    static net.minecraft.world.BlockRenderView virtualWorld(BlockPos pos, Direction face, BlockState state) {
        Object delegate = MinecraftClient.getInstance().world;
        return (net.minecraft.world.BlockRenderView)Proxy.newProxyInstance(net.minecraft.world.BlockRenderView.class.getClassLoader(), new Class<?>[]{net.minecraft.world.BlockRenderView.class}, (proxy, method, args) -> {
            // Minecraft method names are remapped in production; identify this read by its signature.
            if (method.getReturnType() == BlockState.class && args != null && args.length == 1 && args[0] instanceof BlockPos query) {
                if (query.equals(pos) || (BoundaryStyle.texture(SelectiveRenderState.boundaryModeAt(query.getX(), query.getY(), query.getZ()))
                        && !MinecraftClient.getInstance().world.getBlockState(query).isAir() && SelectiveRenderState.isBoundaryFace(query, face))) return state;
            }
            try { return method.invoke(delegate, args); } catch (InvocationTargetException e) { throw e.getCause(); }
        });
    }
    static int smokePixel(Face face) {
        var image=((de.selectiverender.mixin.BoundarySpriteImagesAccess)(Object)face.sprite().getContents()).selectiverender$images()[0];
        return BoundaryStyle.swapRedBlue(image.getColor(0,0)) & 0xFFFFFF;
    }
    private static Face capture(QuadView quad, BlockPos pos, Direction face, BlockState state, net.minecraft.world.BlockRenderView world) {
        if (quad.lightFace() != face) return null;
        float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        float minU=minX,minV=minX,maxU=maxX,maxV=maxX;
        for (int i=0;i<4;i++) { minX=Math.min(minX,quad.x(i)); maxX=Math.max(maxX,quad.x(i)); minY=Math.min(minY,quad.y(i)); maxY=Math.max(maxY,quad.y(i));
            minZ=Math.min(minZ,quad.z(i)); maxZ=Math.max(maxZ,quad.z(i)); minU=Math.min(minU,quad.u(i)); maxU=Math.max(maxU,quad.u(i)); minV=Math.min(minV,quad.v(i)); maxV=Math.max(maxV,quad.v(i)); }
        float area = switch (face.getAxis()) { case X -> (maxY-minY)*(maxZ-minZ); case Y -> (maxX-minX)*(maxZ-minZ); case Z -> (maxX-minX)*(maxY-minY); };
        if (area <= .000001f || !Float.isFinite(area)) return null;
        var sprite = net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder.get(MinecraftClient.getInstance().getBakedModelManager().getAtlas(net.minecraft.client.texture.SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)).find(quad);
        if (sprite == null) return null;
        int color = quad.color(0), tintIndex = quad.colorIndex();
        if (tintIndex >= 0) {
            int tint = MinecraftClient.getInstance().getBlockColors().getColor(state, world, pos, tintIndex);
            color = 0xFF000000 | ((color >> 16 & 255)*(tint >> 16 & 255)/255)<<16 | ((color >> 8 & 255)*(tint >> 8 & 255)/255)<<8 | (color & 255)*(tint & 255)/255;
        }
        return new Face(sprite, minU, maxU, minV, maxV, color | 0xFF000000, quad.material().blendMode() == net.fabricmc.fabric.api.renderer.v1.material.BlendMode.TRANSLUCENT || net.minecraft.client.render.RenderLayers.getBlockLayer(state) != net.minecraft.client.render.RenderLayer.getSolid(), area, mapping(quad, face));
    }
    private static BoundaryUv.Mapping mapping(QuadView quad, Direction face) {
        float[] s=new float[4], t=new float[4], u=new float[4], v=new float[4];
        for (int i=0;i<4;i++) {
            s[i]=face.getAxis()==Direction.Axis.X?quad.z(i):quad.x(i);
            t[i]=face.getAxis()==Direction.Axis.Y?quad.z(i):quad.y(i);
            u[i]=quad.u(i); v[i]=quad.v(i);
        }
        return BoundaryUv.of(s,t,u,v);
    }
    private static BoundaryUv.Mapping vanillaMapping(net.minecraft.client.render.model.BakedQuad quad, Direction face) {
        int[] data=quad.getVertexData(); int stride=data.length/4;
        float[] s=new float[4], t=new float[4], u=new float[4], v=new float[4];
        for (int i=0;i<4;i++) {
            int offset=i*stride;
            s[i]=Float.intBitsToFloat(data[offset+(face.getAxis()==Direction.Axis.X?2:0)]);
            t[i]=Float.intBitsToFloat(data[offset+(face.getAxis()==Direction.Axis.Y?2:1)]);
            u[i]=Float.intBitsToFloat(data[offset+4]); v[i]=Float.intBitsToFloat(data[offset+5]);
        }
        return BoundaryUv.of(s,t,u,v);
    }
    private static Face resolve(BlockPos pos, Direction face, BlockState state) {
        var client = MinecraftClient.getInstance(); var world = virtualWorld(pos, face, state);
        var model = client.getBakedModelManager().getBlockModels().getModel(state);
        var renderer = net.fabricmc.fabric.api.renderer.v1.RendererAccess.INSTANCE.getRenderer();
        if (renderer == null) {
            var quads = new ArrayList<net.minecraft.client.render.model.BakedQuad>(model.getQuads(state, face, net.minecraft.util.math.random.Random.create(pos.asLong())));
            quads.addAll(model.getQuads(state, null, net.minecraft.util.math.random.Random.create(pos.asLong())));
            return quads.stream().filter(quad -> quad.getFace() == face).findFirst().map(quad -> new Face(quad.getSprite(), quad.getSprite().getMinU(), quad.getSprite().getMaxU(), quad.getSprite().getMinV(), quad.getSprite().getMaxV(), -1,
                    net.minecraft.client.render.RenderLayers.getBlockLayer(state) != net.minecraft.client.render.RenderLayer.getSolid(), 1, vanillaMapping(quad, face))).orElse(null);
        }
        var delegate = renderer.meshBuilder().getEmitter();
        var transforms = new ArrayList<net.fabricmc.fabric.api.renderer.v1.render.RenderContext.QuadTransform>();
        Face[] result = {null};
        var emitter = (QuadEmitter)Proxy.newProxyInstance(QuadEmitter.class.getClassLoader(), new Class<?>[]{QuadEmitter.class}, (proxy, method, args) -> {
            if (method.getName().equals("emit")) {
                boolean visible = true;
                for (int i=transforms.size()-1;i>=0;i--) if (!transforms.get(i).transform(delegate)) { visible=false; break; }
                if (visible) { var candidate=capture(delegate,pos,face,state,world); if (candidate!=null && (result[0]==null || candidate.area()>result[0].area())) result[0]=candidate; }
            }
            try { Object value=method.invoke(delegate,args); return value == delegate ? proxy : value; }
            catch (InvocationTargetException e) { throw e.getCause(); }
        });
        var context = (net.fabricmc.fabric.api.renderer.v1.render.RenderContext)Proxy.newProxyInstance(net.fabricmc.fabric.api.renderer.v1.render.RenderContext.class.getClassLoader(),
                new Class<?>[]{net.fabricmc.fabric.api.renderer.v1.render.RenderContext.class, ModelContext.class}, (proxy,method,args) -> {
            switch (method.getName()) {
                case "getEmitter": return emitter;
                case "pushTransform": transforms.add((net.fabricmc.fabric.api.renderer.v1.render.RenderContext.QuadTransform)args[0]); return null;
                case "popTransform": transforms.remove(transforms.size()-1); return null;
                case "hasTransform": return !transforms.isEmpty();
                case "isFaceCulled": return args[0] != null && args[0] != face;
                case "bakedModelConsumer": return new net.fabricmc.fabric.api.renderer.v1.render.RenderContext.BakedModelConsumer() {
                    public void accept(net.minecraft.client.render.model.BakedModel baked) { accept(baked,state); }
                    public void accept(net.minecraft.client.render.model.BakedModel baked, BlockState usedState) {
                        var material=renderer.materialFinder().find();
                        for (var quad : baked.getQuads(usedState,face,net.minecraft.util.math.random.Random.create(pos.asLong()))) emitter.fromVanilla(quad,material,face).emit();
                        for (var quad : baked.getQuads(usedState,null,net.minecraft.util.math.random.Random.create(pos.asLong()))) if (quad.getFace()==face) emitter.fromVanilla(quad,material,null).emit();
                    }
                };
                default: if (method.isDefault()) return InvocationHandler.invokeDefault(proxy,method,args==null?new Object[0]:args);
                    throw new UnsupportedOperationException(method.toString());
            }
        });
        model.emitBlockQuads(world,state,pos,()->net.minecraft.util.math.random.Random.create(pos.asLong()),context);
        return result[0];
    }

}
