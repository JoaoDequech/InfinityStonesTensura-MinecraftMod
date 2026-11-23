package com.infinitystones.tensura.client.will;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.wheel.WheelPayloads;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Client side of construct design: blocks being designed glow as hard light in the Reality Stone's red. */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class ConstructClient {
    private static final float R = 1F, G = 0.2F, B = 0.27F;
    private static final DustParticleOptions MOTE = new DustParticleOptions(new Vector3f(1F, 0.35F, 0.4F), 0.55F);

    public static boolean designing() { return WheelPayloads.constructs.designing(); }
    public static int designCount() { return WheelPayloads.designBlocks.size(); }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        List<BlockPos> blocks = WheelPayloads.designBlocks;
        if (mc.level == null || blocks.isEmpty() || mc.isPaused()) return;
        // motes drifting off the hard light
        for (int i = 0; i < Math.min(4, blocks.size()); i++) {
            BlockPos b = blocks.get(mc.level.random.nextInt(blocks.size()));
            mc.level.addParticle(MOTE, b.getX() + mc.level.random.nextDouble(), b.getY() + 1.02, b.getZ() + mc.level.random.nextDouble(), 0, 0.02, 0);
        }
    }

    // ---------------- summoned constructs: hard-light shell that follows the body ----------------

    private static final java.util.Map<java.util.UUID, List<BlockPos>> BODY_BLOCKS = new java.util.HashMap<>();
    private static long bodyCacheAt;

    @SubscribeEvent
    public static void renderBodies(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !com.infinitystones.tensura.will.Bodies.present()) return;
        long now = mc.level.getGameTime();
        trackChanges(mc, now);
        if (WheelPayloads.constructBodies.isEmpty() && FX.isEmpty()) return;
        if (now - bodyCacheAt > 40 || now < bodyCacheAt || WheelPayloads.constructBodies != lastList) {
            lastList = WheelPayloads.constructBodies;
            BODY_BLOCKS.clear();
            for (String s : WheelPayloads.constructBodies) {
                try { java.util.UUID id = java.util.UUID.fromString(s); BODY_BLOCKS.put(id, com.infinitystones.tensura.will.Bodies.plotBlocks(mc.level, id)); }
                catch (IllegalArgumentException ignored) { }
            }
            bodyCacheAt = now;
        }
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 cp = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cp.x, -cp.y, -cp.z);
        Matrix4f m = ps.last().pose();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        float t = (now + pt) / 20F;
        seals(vc, m, now + pt, t);
        for (var en : BODY_BLOCKS.entrySet()) {
            Set<BlockPos> set = new HashSet<>(en.getValue());
            for (BlockPos b : en.getValue()) {
                // the 8 corners of the block, through the body's pose
                Vec3[] c = new Vec3[8];
                boolean ok = true;
                for (int i = 0; i < 8 && ok; i++) {
                    Vec3 w = com.infinitystones.tensura.will.Bodies.toWorld(mc.level, en.getKey(),
                            new Vec3(b.getX() + (i & 1), b.getY() + ((i >> 1) & 1), b.getZ() + ((i >> 2) & 1)), pt);
                    if (w == null) ok = false; else c[i] = w;
                }
                if (!ok) break;
                if (c[0].distanceToSqr(cp) > 96 * 96) continue;
                float wave = 0.5F + 0.5F * Mth.sin(t * 3 + (b.getX() + b.getY() * 0.7F + b.getZ() * 1.3F) * 0.6F);
                // lift the shell a hair off the faces (no z-fighting): scale the corners out from the block centre
                Vec3 ctr = c[0].add(c[7]).scale(0.5);
                for (int i = 0; i < 8; i++) c[i] = ctr.add(c[i].subtract(ctr).scale(1.012));
                float fill = 0.12F + 0.10F * wave;
                shellEdges(vc, m, b, set, c, cp, 0.45F + 0.35F * wave);
                // corner index = x | y<<1 | z<<2
                if (!set.contains(b.above())) quad(vc, m, c[2], c[3], c[7], c[6], fill);
                if (!set.contains(b.below())) quad(vc, m, c[0], c[4], c[5], c[1], fill);
                if (!set.contains(b.north())) quad(vc, m, c[0], c[1], c[3], c[2], fill);
                if (!set.contains(b.south())) quad(vc, m, c[4], c[6], c[7], c[5], fill);
                if (!set.contains(b.west())) quad(vc, m, c[0], c[2], c[6], c[4], fill);
                if (!set.contains(b.east())) quad(vc, m, c[1], c[5], c[7], c[3], fill);
            }
        }
        buf.endBatch(RenderType.lightning());
        ps.popPose();
    }

    // ---------------- summon / implode seals (every client sees them: driven by the shared construct list) ----------------

    private record Fx(Vec3 at, double size, long start, boolean summon) {}
    private static final List<Fx> FX = new java.util.ArrayList<>();
    private static final java.util.Map<java.util.UUID, Vec3[]> LAST = new java.util.HashMap<>(); // id → {centre, size}
    private static List<String> lastList = List.of();
    private static java.util.Set<String> known = new HashSet<>();
    private static Object seenLevel;

    private static void trackChanges(Minecraft mc, long now) {
        java.util.Set<String> current = new HashSet<>(WheelPayloads.constructBodies);
        if (mc.level != seenLevel) { seenLevel = mc.level; known = current; LAST.clear(); FX.clear(); } // joining: no fake summons
        for (String s : current) if (!known.contains(s)) {
            java.util.UUID id = uuid(s);
            Vec3 c = id == null ? null : com.infinitystones.tensura.will.Bodies.center(mc.level, id, 1F);
            var b = id == null ? null : com.infinitystones.tensura.will.Bodies.bounds(mc.level, id);
            if (c != null) FX.add(new Fx(c, b == null ? 2 : extent(b), now, true));
        }
        for (String s : known) if (!current.contains(s)) {
            Vec3[] last = LAST.remove(uuid(s));
            if (last != null) FX.add(new Fx(last[0], last[1].x, now, false));
        }
        known = current;
        // remember where each one is, so an implode can play where it vanished
        for (String s : current) {
            java.util.UUID id = uuid(s);
            if (id == null) continue;
            Vec3 c = com.infinitystones.tensura.will.Bodies.center(mc.level, id, 1F);
            var b = com.infinitystones.tensura.will.Bodies.bounds(mc.level, id);
            if (c != null) LAST.put(id, new Vec3[]{c, new Vec3(b == null ? 2 : extent(b), 0, 0)});
        }
        FX.removeIf(f -> now - f.start() > 30 || now < f.start());
    }

    private static double extent(net.minecraft.world.phys.AABB b) { return Math.max(1, Math.min(14, Math.max(b.getXsize(), Math.max(b.getYsize(), b.getZsize())) * 0.6)); }

    private static java.util.UUID uuid(String s) { try { return java.util.UUID.fromString(s); } catch (IllegalArgumentException e) { return null; } }

    private static void seals(VertexConsumer vc, Matrix4f m, float now, float t) {
        for (Fx f : FX) {
            float k = Mth.clamp((now - f.start()) / (f.summon() ? 18F : 14F), 0, 1);
            if (f.summon()) {
                // summon: a seal opens under the construct and a second one sweeps up through it, then both fade
                float grow = 1 - (1 - k) * (1 - k) * (1 - k);
                float a = k < 0.7F ? 1 : (1 - k) / 0.3F;
                double r = f.size() * (0.4 + 0.9 * grow);
                HardLightFx.seal(vc, m, f.at().add(0, -f.size() * 0.55, 0), new Vec3(0, 1, 0), r, t, 0.85F * a, 1);
                HardLightFx.seal(vc, m, f.at().add(0, -f.size() * 0.55 + f.size() * 1.1 * grow, 0), new Vec3(0, 1, 0), r * 0.8, t, 0.5F * a, -1);
            } else {
                // implode: three seals collapse into the centre and wink out
                float s = (1 - k) * (1 - k);
                float a = 1 - k * k;
                double r = f.size() * (0.1 + 1.2 * s);
                HardLightFx.seal(vc, m, f.at(), new Vec3(0, 1, 0), r, t * 3, 0.8F * a, 1);
                HardLightFx.seal(vc, m, f.at(), new Vec3(1, 0, 0), r * 0.9, t * 3, 0.5F * a, -1);
                HardLightFx.seal(vc, m, f.at(), new Vec3(0, 0, 1), r * 0.9, t * 3, 0.5F * a, 1);
            }
        }
    }

    /** Glowing silhouette edges of a summoned construct, following the body's pose (corner index = x | y<<1 | z<<2). */
    private static void shellEdges(VertexConsumer vc, Matrix4f m, BlockPos b, Set<BlockPos> set, Vec3[] c, Vec3 cam, float a) {
        boolean up = !set.contains(b.above()), dn = !set.contains(b.below()), n = !set.contains(b.north()), s = !set.contains(b.south()),
                w = !set.contains(b.west()), e = !set.contains(b.east());
        for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++)
            if ((y == 1 ? up : dn) && (z == 1 ? s : n)) edge(vc, m, c[(y << 1) | (z << 2)], c[1 | (y << 1) | (z << 2)], cam, a);
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
            if ((x == 1 ? e : w) && (z == 1 ? s : n)) edge(vc, m, c[x | (z << 2)], c[x | 2 | (z << 2)], cam, a);
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++)
            if ((x == 1 ? e : w) && (y == 1 ? up : dn)) edge(vc, m, c[x | (y << 1)], c[x | (y << 1) | 4], cam, a);
    }

    private static void edge(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 cam, float a) {
        bar(vc, m, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, cam, a);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float alpha) {
        // pushed out a hair so the glow sits on top of the block faces
        Vec3 ctr = a.add(b).add(c).add(d).scale(0.25);
        face(vc, m, a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, d.x, d.y, d.z, alpha);
        if (ctr == null) return;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        List<BlockPos> blocks = WheelPayloads.designBlocks;
        if (mc.level == null || blocks.isEmpty()) return;
        Vec3 cp = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cp.x, -cp.y, -cp.z);
        Matrix4f m = ps.last().pose();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        float t = (mc.level.getGameTime() + e.getPartialTick().getGameTimeDeltaPartialTick(true)) / 20F;
        Set<BlockPos> set = new HashSet<>(blocks);
        for (BlockPos b : blocks) {
            if (b.distToCenterSqr(cp) > 96 * 96) continue;
            float wave = 0.5F + 0.5F * Mth.sin(t * 3 + (b.getX() + b.getY() * 0.7F + b.getZ() * 1.3F) * 0.6F);
            float fill = 0.10F + 0.10F * wave;
            double in = -0.004, x0 = b.getX() + in, y0 = b.getY() + in, z0 = b.getZ() + in, x1 = b.getX() + 1 - in, y1 = b.getY() + 1 - in, z1 = b.getZ() + 1 - in;
            // only faces that touch the outside of the design: one solid shell of light
            if (!set.contains(b.above())) face(vc, m, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, fill);
            if (!set.contains(b.below())) face(vc, m, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, fill);
            if (!set.contains(b.north())) face(vc, m, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, fill);
            if (!set.contains(b.south())) face(vc, m, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, fill);
            if (!set.contains(b.west())) face(vc, m, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, fill);
            if (!set.contains(b.east())) face(vc, m, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, fill);
            edges(vc, m, b, set, cp, 0.35F + 0.35F * wave);
        }
        buf.endBatch(RenderType.lightning());
        ps.popPose();
    }

    private static void face(VertexConsumer vc, Matrix4f m, double ax, double ay, double az, double bx, double by, double bz,
                             double cx, double cy, double cz, double dx, double dy, double dz, float a) {
        vc.addVertex(m, (float) ax, (float) ay, (float) az).setColor(R, G, B, a);
        vc.addVertex(m, (float) bx, (float) by, (float) bz).setColor(R, G, B, a);
        vc.addVertex(m, (float) cx, (float) cy, (float) cz).setColor(R, G, B, a);
        vc.addVertex(m, (float) dx, (float) dy, (float) dz).setColor(R, G, B, a);
        // back side too (lightning has no culling guarantees in every pipeline)
        vc.addVertex(m, (float) dx, (float) dy, (float) dz).setColor(R, G, B, a);
        vc.addVertex(m, (float) cx, (float) cy, (float) cz).setColor(R, G, B, a);
        vc.addVertex(m, (float) bx, (float) by, (float) bz).setColor(R, G, B, a);
        vc.addVertex(m, (float) ax, (float) ay, (float) az).setColor(R, G, B, a);
    }

    /** Glowing outline on the silhouette edges of the design. */
    private static void edges(VertexConsumer vc, Matrix4f m, BlockPos b, Set<BlockPos> set, Vec3 cam, float a) {
        int x = b.getX(), y = b.getY(), z = b.getZ();
        boolean up = !set.contains(b.above()), dn = !set.contains(b.below()), n = !set.contains(b.north()), s = !set.contains(b.south()),
                w = !set.contains(b.west()), e = !set.contains(b.east());
        if (up && n) bar(vc, m, x, y + 1, z, x + 1, y + 1, z, cam, a);
        if (up && s) bar(vc, m, x, y + 1, z + 1, x + 1, y + 1, z + 1, cam, a);
        if (up && w) bar(vc, m, x, y + 1, z, x, y + 1, z + 1, cam, a);
        if (up && e) bar(vc, m, x + 1, y + 1, z, x + 1, y + 1, z + 1, cam, a);
        if (dn && n) bar(vc, m, x, y, z, x + 1, y, z, cam, a);
        if (dn && s) bar(vc, m, x, y, z + 1, x + 1, y, z + 1, cam, a);
        if (dn && w) bar(vc, m, x, y, z, x, y, z + 1, cam, a);
        if (dn && e) bar(vc, m, x + 1, y, z, x + 1, y, z + 1, cam, a);
        if (n && w) bar(vc, m, x, y, z, x, y + 1, z, cam, a);
        if (n && e) bar(vc, m, x + 1, y, z, x + 1, y + 1, z, cam, a);
        if (s && w) bar(vc, m, x, y, z + 1, x, y + 1, z + 1, cam, a);
        if (s && e) bar(vc, m, x + 1, y, z + 1, x + 1, y + 1, z + 1, cam, a);
    }

    private static void bar(VertexConsumer vc, Matrix4f m, double ax, double ay, double az, double bx, double by, double bz, Vec3 cam, float a) {
        Vec3 p0 = new Vec3(ax, ay, az), p1 = new Vec3(bx, by, bz);
        Vec3 seg = p1.subtract(p0), toCam = cam.subtract(p0.add(seg.scale(0.5)));
        Vec3 nrm = seg.cross(toCam);
        if (nrm.lengthSqr() < 1e-8) return;
        nrm = nrm.normalize().scale(0.03);
        Vec3 q0 = p0.add(nrm), q1 = p1.add(nrm), q2 = p1.subtract(nrm), q3 = p0.subtract(nrm);
        vc.addVertex(m, (float) q0.x, (float) q0.y, (float) q0.z).setColor(R, 0.45F, 0.5F, a);
        vc.addVertex(m, (float) q1.x, (float) q1.y, (float) q1.z).setColor(R, 0.45F, 0.5F, a);
        vc.addVertex(m, (float) q2.x, (float) q2.y, (float) q2.z).setColor(R, 0.45F, 0.5F, a);
        vc.addVertex(m, (float) q3.x, (float) q3.y, (float) q3.z).setColor(R, 0.45F, 0.5F, a);
    }

    private ConstructClient() {}
}
