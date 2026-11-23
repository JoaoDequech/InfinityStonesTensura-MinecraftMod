package com.infinitystones.tensura.skill.ability.extra.space.client;

import com.infinitystones.tensura.skill.ability.extra.space.GatherNet;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The portal and the angelic hand: a glowing white-gold hand of light (palm, four fingers, thumb, wrist ring, sleeve of
 * light, feathers) that reaches out of a runed portal, closes around the target and pulls back in. Purely visual.
 */
final class GatherHands {
    private record Inst(GatherNet.Hand h, float start) {}

    private static final List<Inst> LIST = new ArrayList<>();
    private static final float[] GOLD = {1F, 0.88F, 0.52F}, WHITE = {1F, 1F, 1F}, SKY = {0.72F, 0.88F, 1F};

    static void add(GatherNet.Hand h, float ticksNow) {
        if (LIST.size() < 64) LIST.add(new Inst(h, ticksNow));
    }

    static void clear() { LIST.clear(); }

    static boolean any() { return !LIST.isEmpty(); }

    /** m = modelView translated so vertices are in world coordinates; cam = camera world position. */
    static void render(Matrix4f m, Vec3 cam, Vec3 right, Vec3 up, float ticks) {
        if (LIST.isEmpty()) return;
        GatherTex.ensure();
        GatherQ q = new GatherQ(m, cam);
        q.begin();
        List<Inst> runes = new ArrayList<>();
        for (Iterator<Inst> it = LIST.iterator(); it.hasNext(); ) {
            Inst i = it.next();
            float age = ticks - i.start;
            if (age > i.h.life()) { it.remove(); continue; }
            if (age < 0) continue;
            draw(q, i, age, right, up);
            runes.add(i);
        }
        q.flush(GatherTex.SHEET, GatherQ.ADD, false, false);
        q.begin();
        for (Inst i : runes) drawRunes(q, i, ticks - i.start);
        q.flush(GatherTex.MAGIC, GatherQ.ADD, false, false);
    }

    private static float smooth(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
    private static float out(float x) { x = Mth.clamp(x, 0, 1); return 1 - (1 - x) * (1 - x) * (1 - x); }
    private static float in(float x) { x = Mth.clamp(x, 0, 1); return x * x * x; }

    private static float portalScale(float t, float life) {
        return Math.min(smooth(t / 14F), smooth((life - t) / 14F));
    }

    private static Vec3 target(GatherNet.Hand h) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(h.target());
        return e == null ? new Vec3(h.tx(), h.ty(), h.tz()) : e.position().add(0, 1.1, 0);
    }

    private static final class Frame {
        Vec3 p, f, r, u; float dist;
    }

    private static Frame frame(GatherNet.Hand h) {
        Frame fr = new Frame();
        fr.p = new Vec3(h.px(), h.py(), h.pz());
        Vec3 t = target(h);
        Vec3 d = t.subtract(fr.p);
        fr.dist = (float) d.length();
        fr.f = fr.dist < 1e-3 ? new Vec3(h.nx(), h.ny(), h.nz()) : d.scale(1 / fr.dist);
        Vec3 up0 = Math.abs(fr.f.y) > 0.92 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        fr.r = fr.f.cross(up0).normalize();
        fr.u = fr.r.cross(fr.f).normalize();
        return fr;
    }

    private static void draw(GatherQ q, Inst in, float t, Vec3 camR, Vec3 camU) {
        GatherNet.Hand h = in.h;
        Frame fr = frame(h);
        float life = h.life();
        float po = portalScale(t, life);
        float rad = 1.15F * po;
        Vec3 P = fr.p, f = fr.f, R = fr.r, U = fr.u;

        // ---- portal
        float[] ringUv = GatherTex.uv(GatherTex.RING), glowUv = GatherTex.uv(GatherTex.GLOW), softUv = GatherTex.uv(GatherTex.SOFT);
        if (rad > 0.02F) {
            q.oriented(P, R, U, rad * 0.97F, rad * 0.97F, 0, glowUv, 1F, 0.95F, 0.78F, 0.95F * po);
            q.oriented(P.add(f.scale(0.02)), R, U, rad * 1.5F, rad * 1.5F, 0, softUv, 1F, 0.84F, 0.5F, 0.42F * po);
            for (int k = 0; k < 3; k++) {
                float[] col = k == 0 ? GOLD : k == 1 ? WHITE : SKY;
                float rr = rad * (1.0F - 0.17F * k);
                q.oriented(P.add(f.scale(0.01 * k)), R, U, rr, rr, t * 0.13F * (k % 2 == 0 ? 1 : -1) + k, ringUv, col[0], col[1], col[2], 0.95F * po);
            }
            q.billboard(P, rad * 1.7F, rad * 1.7F, t * 0.05F, GatherTex.RAYS, 1F, 0.9F, 0.62F, 0.5F * po, camR, camU);
            q.billboard(P, rad * 1.3F, rad * 1.3F, -t * 0.08F, GatherTex.STAR, 1F, 1F, 0.9F, 0.42F * po, camR, camU);
            for (int k = 0; k < 18; k++) {
                float fr2 = (k / 18F + t * 0.025F) % 1F, ang = k * 2.4F + t * 0.22F;
                float rr = rad * (1.15F - fr2 * 0.95F);
                Vec3 sp = P.add(R.scale(Mth.cos(ang) * rr)).add(U.scale(Mth.sin(ang) * rr)).add(f.scale(0.05));
                q.billboard(sp, 0.09F, 0.09F, 0, GatherTex.GLOW, 1F, 0.95F, 0.75F, 0.85F * po * (0.3F + fr2), camR, camU);
            }
        }

        // ---- hand animation
        float ext = t < 10 ? 0 : t < 36 ? out((t - 10) / 26F) : t < 48 ? 1 : 1 - in((t - 48) / 16F);
        float curl = t < 34 ? 0 : smooth((t - 34) / 10F);
        if (ext <= 0.01F) return;
        float reach = Math.max(0.8F, fr.dist - 0.55F);
        float s = Mth.lerp(ext, -0.2F, reach);
        float fade = Mth.clamp(ext * 6, 0, 1) * Mth.clamp((life - t) / 8F, 0, 1);
        Vec3 C = P.add(f.scale(s)), W = P.add(f.scale(s - 0.42F));
        float pulse = 0.85F + 0.15F * Mth.sin(t * 0.5F);

        // sleeve of light + arm beam
        float armLen = s - 0.42F;
        if (armLen > 0.05F) {
            q.beam(P, W, 0.12F, GOLD[0], GOLD[1], GOLD[2], 0.9F * fade);
            int strands = 7, steps = 9;
            for (int k = 0; k < strands; k++) {
                float phi = k * Mth.TWO_PI / strands + t * 0.04F;
                Vec3 prev = null;
                for (int j = 0; j <= steps; j++) {
                    float u = j / (float) steps;
                    float rho = (0.13F + 0.30F * (float) Math.pow(1 - u, 1.6)) * (1 + 0.22F * Mth.sin(t * 0.3F + u * 6 + k));
                    float ang = phi + u * 1.2F + Mth.sin(t * 0.15F + k) * 0.2F;
                    Vec3 pt = P.add(f.scale(armLen * u)).add(R.scale(Mth.cos(ang) * rho)).add(U.scale(Mth.sin(ang) * rho));
                    if (prev != null) q.ribbon(prev, pt, 0.05F, 1F, 0.93F, 0.72F, 0.50F * fade * (0.4F + 0.6F * u));
                    prev = pt;
                }
            }
        }
        // wrist rings and feathers
        q.oriented(W, R, U, 0.30F, 0.30F, t * 0.2F, ringUv, 1F, 0.92F, 0.62F, 0.95F * fade);
        q.oriented(W.subtract(f.scale(0.14)), R, U, 0.40F, 0.40F, -t * 0.15F, ringUv, 1F, 1F, 0.95F, 0.65F * fade);
        q.oriented(W.add(f.scale(0.1)), R, U, 0.22F, 0.22F, t * 0.3F, ringUv, 0.8F, 0.92F, 1F, 0.7F * fade);
        for (int side = -1; side <= 1; side += 2)
            for (int k = 0; k < 5; k++) {
                float a = 0.30F + 0.2F * k, len = 0.55F + 0.22F * k, flap = Mth.sin(t * 0.35F + k * 0.6F) * 0.08F;
                Vec3 dir = f.scale(-Mth.cos(a)).add(R.scale(side * Mth.sin(a))).add(U.scale(0.12 + flap)).normalize();
                Vec3 b = W.subtract(f.scale(0.04));
                Vec3 p1 = b.add(dir.scale(len * 0.55F)).add(U.scale(0.05F * k)), p2 = b.add(dir.scale(len)).add(U.scale(0.14F * k + flap * 2)).subtract(f.scale(0.05));
                q.ribbon(b, p1, 0.045F, 1F, 0.97F, 0.88F, 0.6F * fade);
                q.ribbon(p1, p2, 0.035F, 1F, 0.97F, 0.88F, 0.45F * fade);
            }

        // palm
        float[] pu = GatherTex.uv(GatherTex.SOFT);
        q.oriented(C, R, f, 0.20F, 0.21F, 0, pu, 1F, 0.93F, 0.66F, 0.85F * fade);
        q.oriented(C.add(f.scale(0.02)), R, U, 0.17F, 0.17F, 0, pu, 1F, 1F, 0.9F, 0.45F * fade);
        q.billboard(C, 0.24F, 0.24F, 0, GatherTex.GLOW, 1F, 1F, 0.9F, 0.9F * fade * pulse, camR, camU);
        q.billboard(C, 0.95F, 0.95F, t * 0.04F, GatherTex.RAYS, 1F, 0.9F, 0.6F, 0.36F * fade * pulse, camR, camU);
        q.billboard(C, 0.7F, 0.7F, 0, GatherTex.STAR, 1F, 0.95F, 0.78F, 0.4F * fade, camR, camU);

        // fingers: index, middle, ring, little
        float[] off = {-0.135F, -0.045F, 0.045F, 0.135F}, spread = {-0.20F, -0.07F, 0.07F, 0.22F}, scale = {1.0F, 1.12F, 1.0F, 0.78F};
        for (int i = 0; i < 4; i++) {
            float sp = spread[i] * (1 - 0.7F * curl) + 0.04F * Mth.sin(t * 0.2F + i * 1.3F);
            Vec3 dflat = f.scale(Mth.cos(sp)).add(R.scale(Mth.sin(sp)));
            Vec3 p0 = C.add(f.scale(0.19F)).add(R.scale(off[i]));
            float alpha = 0;
            float[] seg = {0.15F, 0.13F, 0.11F};
            for (int j = 0; j < 3; j++) {
                alpha += (curl * (0.60F + 0.38F * j) + 0.06F * (1 - curl) * Mth.sin(t * 0.25F + i + j)) ;
                Vec3 dir = dflat.scale(Mth.cos(alpha)).subtract(U.scale(Mth.sin(alpha))).normalize();
                Vec3 p1 = p0.add(dir.scale(seg[j] * scale[i]));
                q.beam(p0, p1, 0.034F - j * 0.004F, GOLD[0], GOLD[1], GOLD[2], 0.95F * fade);
                q.billboard(p1, 0.05F, 0.05F, 0, GatherTex.GLOW, 1F, 1F, 0.92F, 0.9F * fade, camR, camU);
                p0 = p1;
            }
        }
        // thumb
        {
            Vec3 p0 = C.add(f.scale(0.02F)).subtract(R.scale(0.17F));
            float[] seg = {0.14F, 0.12F};
            float alpha = 0;
            for (int j = 0; j < 2; j++) {
                float outA = 0.95F * (1 - 0.8F * curl) - j * 0.1F;
                alpha += curl * 0.5F;
                Vec3 dir = f.scale(Mth.cos(outA)).subtract(R.scale(Mth.sin(outA))).add(R.scale(Mth.sin(alpha) * 0.9F)).subtract(U.scale(Mth.sin(alpha) * 0.5F)).normalize();
                Vec3 p1 = p0.add(dir.scale(seg[j]));
                q.beam(p0, p1, 0.04F - j * 0.005F, GOLD[0], GOLD[1], GOLD[2], 0.95F * fade);
                q.billboard(p1, 0.055F, 0.055F, 0, GatherTex.GLOW, 1F, 1F, 0.92F, 0.9F * fade, camR, camU);
                p0 = p1;
            }
        }
        // orbiting sparkles
        for (int k = 0; k < 16; k++) {
            float ang = k * 0.9F + t * (0.16F + 0.02F * (k % 3)), rr = 0.35F + 0.5F * ((k * 37 % 10) / 10F);
            Vec3 sp = C.add(R.scale(Mth.cos(ang) * rr)).add(U.scale(Mth.sin(ang) * rr)).add(f.scale(Mth.sin(t * 0.1F + k) * 0.3F - 0.1F));
            float tw = 0.5F + 0.5F * Mth.sin(t * 0.7F + k * 2);
            q.billboard(sp, 0.10F * tw + 0.03F, 0.10F * tw + 0.03F, t * 0.1F, GatherTex.STAR, 1F, 0.95F, 0.78F, 0.9F * fade, camR, camU);
        }
        // the grab
        if (t > 40 && t < 54) {
            float k = 1 - Math.abs(t - 45) / 7F;
            Vec3 T = target(h);
            if (k > 0) {
                q.billboard(T, 2.4F * k, 2.4F * k, 0, GatherTex.GLOW, 1F, 0.96F, 0.8F, 0.95F * k, camR, camU);
                q.billboard(T, 1.9F * k, 1.9F * k, t * 0.2F, GatherTex.STAR, 1F, 1F, 0.95F, 0.9F * k, camR, camU);
            }
        }
    }

    private static void drawRunes(GatherQ q, Inst in, float t) {
        GatherNet.Hand h = in.h;
        if (t < 0) return;
        Frame fr = frame(h);
        float po = portalScale(t, h.life());
        float rad = 1.15F * po;
        if (rad < 0.03F) return;
        float[] uv = {0, 0, 1, 1};
        q.oriented(fr.p.add(fr.f.scale(0.03)), fr.r, fr.u, rad * 1.5F, rad * 1.5F, t * 0.08F, uv, 1F, 0.9F, 0.6F, 0.9F * po);
        q.oriented(fr.p.subtract(fr.f.scale(0.02)), fr.r, fr.u, rad * 2.2F, rad * 2.2F, -t * 0.05F, uv, 0.9F, 0.95F, 1F, 0.5F * po);
    }

    private GatherHands() {}
}
