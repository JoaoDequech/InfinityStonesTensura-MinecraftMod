package com.infinitystones.tensura.skill.ability.extra.space.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Arrays;

/**
 * Textured quad batcher for the Spiritual Gathering: either draws immediately (dynamic effects) or records into a
 * {@link Mesh} that is built once and re-emitted every frame (static scenery). Coordinates are relative to the scene centre.
 */
final class GatherQ {
    static final int NORMAL = 0, ADD = 1;
    private static final int ALWAYS = 519, LEQUAL = 515;

    /** Cached geometry: 9 floats per vertex (x y z u v r g b a). */
    static final class Mesh {
        float[] d = new float[9 * 1024];
        int n;
        void add(float x, float y, float z, float u, float v, float r, float g, float b, float a) {
            if (n * 9 + 9 > d.length) d = Arrays.copyOf(d, d.length * 2);
            int i = n * 9;
            d[i] = x; d[i + 1] = y; d[i + 2] = z; d[i + 3] = u; d[i + 4] = v; d[i + 5] = r; d[i + 6] = g; d[i + 7] = b; d[i + 8] = a;
            n++;
        }
    }

    private BufferBuilder bb;
    private final Matrix4f m;
    private final Mesh rec;
    final Vec3 cam;

    /** Immediate mode. m = modelView translated to the scene centre; cam = camera relative to the centre. */
    GatherQ(Matrix4f m, Vec3 cam) { this(m, cam, null); }

    /** Recording mode (static meshes). */
    GatherQ(Mesh rec) { this(new Matrix4f(), new Vec3(0, 0, 1), rec); }

    private GatherQ(Matrix4f m, Vec3 cam, Mesh rec) {
        this.m = m; this.cam = cam; this.rec = rec;
    }

    // ------------------------------------------------------------------ batches

    void begin() {
        if (rec == null) bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    void flush(ResourceLocation tex, int blend, boolean depthAlways, boolean depthWrite) {
        if (rec != null || bb == null) return;
        MeshData mesh = bb.build();
        bb = null;
        if (mesh == null) return;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.enableBlend();
        if (blend == ADD) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        else RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(depthAlways ? ALWAYS : LEQUAL);
        RenderSystem.depthMask(depthWrite);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.depthFunc(LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** Re-emits a cached mesh (alpha scaled) and draws it. */
    void drawMesh(Mesh mesh, ResourceLocation tex, int blend, boolean depthAlways, boolean depthWrite, float alpha, float shift, float vShift) {
        if (mesh == null || mesh.n == 0 || alpha <= 0.004F) return;
        begin();
        float[] d = mesh.d;
        for (int i = 0; i < mesh.n; i++) {
            int o = i * 9;
            bb.addVertex(m, d[o], d[o + 1], d[o + 2]).setUv(d[o + 3] + shift, d[o + 4] + vShift).setColor(d[o + 5], d[o + 6], d[o + 7], d[o + 8] * alpha);
        }
        flush(tex, blend, depthAlways, depthWrite);
    }

    // ------------------------------------------------------------------ vertices

    void v(double x, double y, double z, float u, float v, float r, float g, float b, float a) {
        if (rec != null) rec.add((float) x, (float) y, (float) z, u, v, r, g, b, Mth.clamp(a, 0, 1));
        else bb.addVertex(m, (float) x, (float) y, (float) z).setUv(u, v).setColor(r, g, b, Mth.clamp(a, 0, 1));
    }

    /** Quad given by 4 corners (a, b, c, d in order) and a uv rectangle; colour per quad. */
    void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float[] uv, float r, float g, float bl, float al) {
        v(a.x, a.y, a.z, uv[0], uv[3], r, g, bl, al);
        v(b.x, b.y, b.z, uv[2], uv[3], r, g, bl, al);
        v(c.x, c.y, c.z, uv[2], uv[1], r, g, bl, al);
        v(d.x, d.y, d.z, uv[0], uv[1], r, g, bl, al);
    }

    /** Quad with per-corner colours (rgba arrays) and explicit uvs per corner. */
    void quadC(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float[] uvA, float[] uvB, float[] uvC, float[] uvD, float[] ca, float[] cb, float[] cc, float[] cd) {
        v(a.x, a.y, a.z, uvA[0], uvA[1], ca[0], ca[1], ca[2], ca[3]);
        v(b.x, b.y, b.z, uvB[0], uvB[1], cb[0], cb[1], cb[2], cb[3]);
        v(c.x, c.y, c.z, uvC[0], uvC[1], cc[0], cc[1], cc[2], cc[3]);
        v(d.x, d.y, d.z, uvD[0], uvD[1], cd[0], cd[1], cd[2], cd[3]);
    }

    /** Camera-facing sprite. rot in radians around the view axis. */
    void billboard(Vec3 c, float halfW, float halfH, float rot, int cell, float r, float g, float b, float a, Vec3 right, Vec3 up) {
        float cs = Mth.cos(rot), sn = Mth.sin(rot);
        Vec3 R = right.scale(cs).add(up.scale(sn)), U = up.scale(cs).subtract(right.scale(sn));
        Vec3 rw = R.scale(halfW), uh = U.scale(halfH);
        quad(c.subtract(rw).subtract(uh), c.add(rw).subtract(uh), c.add(rw).add(uh), c.subtract(rw).add(uh), GatherTex.uv(cell), r, g, b, a);
    }

    /** Sprite lying in the plane spanned by two unit axes, rotated by rot. */
    void oriented(Vec3 c, Vec3 ax, Vec3 ay, float halfW, float halfH, float rot, float[] uv, float r, float g, float b, float a) {
        float cs = Mth.cos(rot), sn = Mth.sin(rot);
        Vec3 A = ax.scale(cs).add(ay.scale(sn)), B = ay.scale(cs).subtract(ax.scale(sn));
        Vec3 aw = A.scale(halfW), bh = B.scale(halfH);
        quad(c.subtract(aw).subtract(bh), c.add(aw).subtract(bh), c.add(aw).add(bh), c.subtract(aw).add(bh), uv, r, g, b, a);
    }

    /** Camera-facing ribbon between two points using the STRIP sprite (soft edges). */
    void ribbon(Vec3 p0, Vec3 p1, float half, float r, float g, float b, float a) {
        Vec3 seg = p1.subtract(p0);
        Vec3 toCam = cam.subtract(p0.add(seg.scale(0.5)));
        Vec3 n = seg.cross(toCam);
        if (n.lengthSqr() < 1e-10) return;
        n = n.normalize().scale(half);
        float[] uv = GatherTex.uv(GatherTex.STRIP);
        Vec3 a0 = p0.add(n), a1 = p1.add(n), b1 = p1.subtract(n), b0 = p0.subtract(n);
        v(a0.x, a0.y, a0.z, uv[0], uv[1], r, g, b, a);
        v(a1.x, a1.y, a1.z, uv[0], uv[3], r, g, b, a);
        v(b1.x, b1.y, b1.z, uv[2], uv[3], r, g, b, a);
        v(b0.x, b0.y, b0.z, uv[2], uv[1], r, g, b, a);
    }

    /** Glowing tube: wide halo, body, white core. */
    void beam(Vec3 p0, Vec3 p1, float w, float r, float g, float b, float a) {
        ribbon(p0, p1, w * 2.6F, r, g, b, a * 0.20F);
        ribbon(p0, p1, w, r, g, b, a * 0.62F);
        ribbon(p0, p1, w * 0.36F, 1, 1, 1, a);
    }

    // ------------------------------------------------------------------ shapes (mostly for recorded meshes)

    /** Axis-aligned box, flat colour with a bit of shading per face; uses the WHITE sprite. */
    void box(double x0, double y0, double z0, double x1, double y1, double z1, float r, float g, float b, float a, boolean top, boolean bottom) {
        float[] uv = GatherTex.uv(GatherTex.WHITE);
        Vec3 a0 = new Vec3(x0, y0, z0), a1 = new Vec3(x1, y0, z0), a2 = new Vec3(x1, y0, z1), a3 = new Vec3(x0, y0, z1);
        Vec3 b0 = new Vec3(x0, y1, z0), b1 = new Vec3(x1, y1, z0), b2 = new Vec3(x1, y1, z1), b3 = new Vec3(x0, y1, z1);
        quad(a0, a1, b1, b0, uv, r * 0.86F, g * 0.86F, b * 0.86F, a);
        quad(a2, a3, b3, b2, uv, r * 0.86F, g * 0.86F, b * 0.86F, a);
        quad(a3, a0, b0, b3, uv, r * 0.72F, g * 0.72F, b * 0.72F, a);
        quad(a1, a2, b2, b1, uv, r * 0.72F, g * 0.72F, b * 0.72F, a);
        if (top) quad(b0, b1, b2, b3, uv, r, g, b, a);
        if (bottom) quad(a3, a2, a1, a0, uv, r * 0.5F, g * 0.5F, b * 0.5F, a);
    }
}
