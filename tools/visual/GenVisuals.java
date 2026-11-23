import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;

/**
 * Generates the "AAA" visual assets of the Infinity Stones mod from scratch (no third-party art):
 * faceted gem models + textures, the Infinity Gauntlet model, glow/particle textures, 32x32 tree icons,
 * builtin/entity wrapper models for the custom item renderers, plus a Java layout class and PNG previews.
 *
 * Run from the repo root:  java tools/visual/GenVisuals.java [previewDir]
 * (pure JDK, headless; re-running is idempotent)
 */
public class GenVisuals {
    static final Path ROOT = Paths.get("src/main/resources/assets/infinity");
    static final Path DATA = Paths.get("src/main/resources/data/infinity/stone_trees");
    static final Path JAVA_OUT = Paths.get("src/main/java/com/infinitystones/tensura/client/render/VisualLayout.java");
    static Path PREVIEW;

    record StoneDef(String key, int rgb) {}
    static final StoneDef[] STONES = {
            new StoneDef("mind", 0xF2D02B), new StoneDef("power", 0xA43CE6), new StoneDef("space", 0x2F8CFF),
            new StoneDef("reality", 0xE22B3B), new StoneDef("soul", 0xF28A1A), new StoneDef("time", 0x2BD56A)};

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        PREVIEW = args.length > 0 ? Paths.get(args[0]) : null;
        if (PREVIEW != null) Files.createDirectories(PREVIEW);

        // ---------------------------------------------------------------- shared FX textures
        png(halo(64), "textures/render/halo.png");
        png(flare(64), "textures/render/flare.png");
        png(runeRing(128), "textures/render/rune_ring.png");
        png(mote(16), "textures/particle/stone_mote.png");
        png(spark(16), "textures/particle/stone_spark.png");

        // ---------------------------------------------------------------- gems (pixel / Create-style: 1 texel = 1 model pixel)
        for (StoneDef s : STONES) {
            Map<String, int[]> pal = Map.of("gem", gemPalette(s.rgb), "cube", gemPalette(s.rgb));
            List<El> els = gem(s.key);
            write("models/item/gem/" + s.key + ".json", model("item/gem/" + s.key, els, null, pal, 32));
            write("models/item/" + s.key + "_stone.json", wrapper("infinity:item/gem/" + s.key, STONE_DISPLAY));
            for (El e : els) e.tex = "all";
            preview(els, Map.of("all", ATLASES.get("item/gem/" + s.key)), 22, -30, s.key + "_stone");
        }

        // ---------------------------------------------------------------- gauntlet
        Map<String, int[]> metal = new HashMap<>();
        metal.put("gold", GOLD); metal.put("gold_dark", GOLD_DARK); metal.put("gold_light", GOLD_LIGHT); metal.put("socket", SOCKET);
        Gauntlet g = new Gauntlet();
        g.build();
        write("models/item/gauntlet/base.json", model("item/gauntlet/base", g.base, null, metal, 64));
        List<El> all = new ArrayList<>(g.base);
        Map<String, BufferedImage> pt = new HashMap<>();
        pt.put("all", ATLASES.get("item/gauntlet/base"));
        for (StoneDef s : STONES) {
            write("models/item/gauntlet/gem_" + s.key + ".json", model("item/gauntlet/gem_" + s.key, g.gems.get(s.key), null, Map.of("gem", gemPalette(s.rgb)), 16));
            pt.put("all_" + s.key, ATLASES.get("item/gauntlet/gem_" + s.key));
            for (El e : g.gems.get(s.key)) { El c = e.copy(); c.uvs = e.uvs; c.tex = "all_" + s.key; all.add(c); }
        }
        for (El e : g.base) e.tex = "all";
        write("models/item/infinity_gauntlet.json", wrapper("infinity:item/gauntlet/base", GAUNTLET_DISPLAY, "side"));
        preview(all, pt, 8, -28, "gauntlet_gui");
        preview(all, pt, 0, -90, "gauntlet_side");

        // ---------------------------------------------------------------- eye of agamotto (pixel amulet)
        Map<String, int[]> epal = new HashMap<>(metal);
        epal.put("gem", gemPalette(0x2BD56A));
        List<El> eye = eyeOfAgamotto();
        write("models/item/base/eye_of_agamotto.json", model("item/eye_of_agamotto_3d", eye, null, epal, 32));
        write("models/item/eye_of_agamotto.json", wrapper("infinity:item/eye_of_agamotto_3d", EYE_DISPLAY));
        for (El e : eye) e.tex = "all";
        preview(eye, Map.of("all", ATLASES.get("item/eye_of_agamotto_3d")), 10, -25, "eye_of_agamotto");

        // ---------------------------------------------------------------- carriers (full: builtin wrapper + item/base geometry; empty: plain models)
        StringBuilder carrierLayout = new StringBuilder();
        carriers(carrierLayout);
        emptyEye(metal);

        // ---------------------------------------------------------------- tree icons
        int icons = treeIcons();

        // ---------------------------------------------------------------- Java layout constants
        writeLayout(g, carrierLayout.toString());
        System.out.println("done; tree icons: " + icons);
    }

    // =====================================================================================================
    // model elements
    // =====================================================================================================

    static final class El {
        double[] from, to;
        String axis; double angle; double[] origin;
        String tex; boolean emissive; boolean fullUv; boolean shade = true;
        String name;
        double[][] uvs;

        El(String name, double x0, double y0, double z0, double x1, double y1, double z1, String tex) {
            this.name = name; this.from = new double[]{x0, y0, z0}; this.to = new double[]{x1, y1, z1}; this.tex = tex;
        }
        El rot(String axis, double angle, double ox, double oy, double oz) { this.axis = axis; this.angle = angle; this.origin = new double[]{ox, oy, oz}; return this; }
        El glow() { emissive = true; shade = false; return this; }
        El full() { fullUv = true; return this; }
        El copy() {
            El e = new El(name, from[0], from[1], from[2], to[0], to[1], to[2], tex);
            e.axis = axis; e.angle = angle; e.origin = origin == null ? null : origin.clone(); e.emissive = emissive; e.fullUv = fullUv; e.shade = shade;
            return e;
        }
    }

    static El box(String name, double cx, double cy, double cz, double hx, double hy, double hz, String tex) {
        return new El(name, cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz, tex);
    }

    // ---------------------------------------------------------------- gems (chunky pixel cuts)
    static List<El> gem(String key) {
        List<El> o = new ArrayList<>();
        double e = 0.02; // keeps overlapping slabs off each other's planes
        switch (key) {
            case "space" -> {
                o.add(new El("cube", 5, 5, 5, 11, 11, 11, "cube"));
                o.add(new El("cap_y", 6, 4.5, 6, 10, 11.5, 10, "gem"));
                o.add(new El("cap_x", 4.5, 6, 6 + e, 11.5, 10, 10 - e, "gem"));
                o.add(new El("cap_z", 6 + e, 6 + e, 4.5, 10 - e, 10 - e, 11.5, "gem"));
            }
            case "reality" -> {
                o.add(new El("spine", 7, 3, 7, 9, 13, 9, "gem"));
                o.add(new El("body", 6, 5, 6, 10, 11, 10, "gem"));
                o.add(new El("wide_x", 5, 7, 6 + e, 11, 9, 10 - e, "gem"));
                o.add(new El("wide_z", 6 + e, 7 + e, 5, 10 - e, 9 - e, 11, "gem"));
            }
            case "mind" -> {
                o.add(new El("body", 6, 4, 6, 10, 12, 10, "gem"));
                o.add(new El("wide_x", 5, 6, 6 + e, 11, 10, 10 - e, "gem"));
                o.add(new El("wide_z", 6 + e, 6 + e, 5, 10 - e, 10 - e, 11, "gem"));
                o.add(new El("top", 7, 12, 7, 9, 13, 9, "gem"));
                o.add(new El("bottom", 7, 3, 7, 9, 4, 9, "gem"));
            }
            case "soul" -> {
                o.add(new El("body", 5, 6, 6, 11, 10, 10, "gem"));
                o.add(new El("wide_z", 6, 6 + e, 5, 10, 10 - e, 11, "gem"));
                o.add(new El("top", 6 + e, 10, 6 + e, 10 - e, 11, 10 - e, "gem"));
                o.add(new El("bottom", 6 + e, 5, 6 + e, 10 - e, 6, 10 - e, "gem"));
                o.add(new El("crown", 7, 11, 7, 9, 11.5, 9, "gem"));
            }
            case "time" -> {
                o.add(new El("body", 6, 5, 6, 10, 11, 10, "gem"));
                o.add(new El("wide_x", 5, 6, 6.5, 11, 10, 9.5, "gem"));
                o.add(new El("wide_z", 6.5, 6 + e, 5, 9.5, 10 - e, 11, "gem"));
                o.add(new El("top", 7, 11, 7, 9, 12, 9, "gem"));
                o.add(new El("bottom", 7, 4, 7, 9, 5, 9, "gem"));
            }
            default -> { // power
                o.add(new El("body", 6, 5, 6, 10, 11, 10, "gem"));
                o.add(new El("wide_x", 5, 6, 6 + e, 11, 10, 10 - e, "gem"));
                o.add(new El("wide_z", 6 + e, 6 + e, 5, 10 - e, 10 - e, 11, "gem"));
                o.add(new El("top", 7, 11, 7, 9, 12, 9, "gem"));
                o.add(new El("bottom", 7, 4, 7, 9, 5, 9, "gem"));
            }
        }
        for (El el : o) el.glow();
        return o;
    }

    /** Pixel gem set in a socket, facing +Z: a cube plus a small crown; optionally tilted about Z (thumb). */
    static List<El> socketGem(double cx, double cy, double z0, double s, double tilt, boolean glow) {
        List<El> out = new ArrayList<>();
        El a = box("gem", cx, cy, z0 + s / 2, s / 2, s / 2, s / 2, "gem");
        El b = box("gem_crown", cx, cy, z0 + s + 0.15, s / 4, s / 4, 0.15, "gem");
        for (El e : List.of(a, b)) { if (tilt != 0) e.rot("z", tilt, cx, cy, z0); if (glow) e.glow(); out.add(e); }
        return out;
    }

    static double[] rotZ(double x, double y, double deg) {
        double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
        return new double[]{x * c - y * s, x * s + y * c};
    }

    // ---------------------------------------------------------------- gauntlet (pixel-aligned plates)
    static final class Gauntlet {
        final List<El> base = new ArrayList<>();
        final Map<String, List<El>> gems = new LinkedHashMap<>();
        final Map<String, double[]> gemCenters = new LinkedHashMap<>();

        void build() {
            base.add(new El("cuff_flare", 3.5, 0, 4.5, 12.5, 1, 11.5, "gold_dark"));
            base.add(new El("cuff", 4, 1, 5, 12, 3, 11, "gold"));
            base.add(new El("cuff_band", 3.5, 3, 4.5, 12.5, 4, 11.5, "gold_light"));
            base.add(new El("cuff_stud_l", 3, 1.5, 7.5, 3.5, 2.5, 8.5, "gold_light"));
            base.add(new El("cuff_stud_r", 12.5, 1.5, 7.5, 13, 2.5, 8.5, "gold_light"));
            base.add(new El("hand", 4, 4, 6, 12, 10, 10, "gold_dark"));
            base.add(new El("palm", 4.5, 4.5, 5.5, 11.5, 9.5, 6, "gold_dark"));
            base.add(new El("plate_low", 4.5, 4, 10, 11.5, 5.5, 11, "gold"));
            base.add(new El("plate_mid", 4.5, 6, 10, 11.5, 8.5, 11, "gold"));
            base.add(new El("plate_top", 4.5, 9, 10, 11.5, 10, 10.5, "gold_light"));
            base.add(new El("knuckles", 4, 10, 6, 12, 11, 11, "gold"));
            // Mind socket in the middle of the back plate
            base.add(new El("mind_rim", 6.5, 5.75, 11, 9.5, 8.75, 11.5, "socket"));
            base.add(new El("mind_rim_top", 7, 8.75, 11, 9, 9.25, 11.25, "gold_light"));
            addGem("mind", 8, 7.25, 11.25, 2);
            // fingers, pinky -> index (thumb on +X)
            finger("soul", 5, new double[]{2.5, 1.5, 1.25});
            finger("reality", 7, new double[]{3, 2, 1.5});
            finger("space", 9, new double[]{3.25, 2.25, 1.5});
            finger("power", 11, new double[]{3, 2, 1.5});
            // thumb (Time)
            double ox = 12, oy = 4.5, oz = 8.25, th = -22.5;
            base.add(new El("thumb_1", ox, oy, oz - 1.5, ox + 2, oy + 4, oz + 1.5, "gold_dark").rot("z", th, ox, oy, oz));
            base.add(new El("thumb_1_plate", ox + 0.25, oy + 0.5, oz + 1.5, ox + 1.75, oy + 3.75, oz + 2, "gold").rot("z", th, ox, oy, oz));
            double[] tip = rotZ(1, 4, th);
            double px = ox + tip[0], py = oy + tip[1];
            base.add(new El("thumb_2", px - 0.875, py - 0.25, oz - 1.25, px + 0.875, py + 2, oz + 1.25, "gold_dark"));
            base.add(new El("thumb_2_plate", px - 0.625, py, oz + 1.25, px + 0.625, py + 1.75, oz + 1.75, "gold_light"));
            double[] gc = rotZ(1, 2, th);
            double gx = ox + gc[0], gy = oy + gc[1];
            base.add(box("time_rim", gx, gy, 10.125, 0.75, 0.75, 0.125, "socket").rot("z", th, gx, gy, 10.125));
            gems.put("time", socketGem(gx, gy, 10.2, 1, th, false));
            gemCenters.put("time", new double[]{gx, gy, 10.8});
        }

        void finger(String stone, double cx, double[] len) {
            double hw = 0.875, y = 11, z = 8.25;
            base.add(new El(stone + "_f1", cx - hw, y, z - 1.5, cx + hw, y + len[0], z + 1.5, "gold_dark"));
            base.add(new El(stone + "_f1_plate", cx - hw + 0.25, y + 0.25, z + 1.5, cx + hw - 0.25, y + len[0] - 0.25, z + 2, "gold"));
            double j1 = y + len[0];
            base.add(new El(stone + "_f2", cx - hw, j1, z - 1.375, cx + hw, j1 + len[1], z + 1.375, "gold_dark").rot("x", -22.5, cx, j1, z));
            base.add(new El(stone + "_f2_plate", cx - hw + 0.25, j1 + 0.25, z + 1.375, cx + hw - 0.25, j1 + len[1] - 0.25, z + 1.875, "gold").rot("x", -22.5, cx, j1, z));
            double c = Math.cos(Math.toRadians(-22.5)), s = Math.sin(Math.toRadians(-22.5));
            double j2y = j1 + len[1] * c, j2z = z + len[1] * s;
            base.add(new El(stone + "_f3", cx - hw + 0.125, j2y, j2z - 1.25, cx + hw - 0.125, j2y + len[2], j2z + 1.25, "gold_dark").rot("x", -45, cx, j2y, j2z));
            base.add(new El(stone + "_f3_plate", cx - hw + 0.375, j2y + 0.125, j2z + 1.25, cx + hw - 0.375, j2y + len[2], j2z + 1.75, "gold_light").rot("x", -45, cx, j2y, j2z));
            base.add(new El(stone + "_rim", cx - 0.75, 9.75, 11, cx + 0.75, 11.25, 11.25, "socket"));
            addGem(stone, cx, 10.5, 11.1, 1);
        }

        void addGem(String stone, double cx, double cy, double z0, double size) {
            gems.put(stone, socketGem(cx, cy, z0, size, 0, false));
            gemCenters.put(stone, new double[]{cx, cy, z0 + size * 0.7});
        }
    }

    // ---------------------------------------------------------------- eye of agamotto (pixel amulet)
    static List<El> eyeOfAgamotto() {
        List<El> o = new ArrayList<>();
        o.add(new El("disc_a", 4, 5, 7, 12, 11, 8.5, "gold_dark"));
        o.add(new El("disc_b", 5, 4, 7.02, 11, 12, 8.48, "gold_dark"));
        o.add(new El("face", 5, 5, 8.5, 11, 11, 9, "gold"));
        o.add(new El("lid_top", 6, 9, 9, 10, 10, 9.5, "gold_light"));
        o.add(new El("lid_bottom", 6, 6, 9, 10, 7, 9.5, "gold_light"));
        o.add(new El("lid_left", 5, 7, 9, 6, 9, 9.5, "gold_light"));
        o.add(new El("lid_right", 10, 7, 9, 11, 9, 9.5, "gold_light"));
        o.add(new El("wing_l", 2.5, 6.5, 7.25, 4, 9.5, 8.25, "gold"));
        o.add(new El("wing_r", 12, 6.5, 7.25, 13.5, 9.5, 8.25, "gold"));
        o.add(new El("bail", 7, 12, 7.25, 9, 13.5, 8.25, "gold_light"));
        o.add(new El("bail_ring", 7.5, 13.5, 7.5, 8.5, 14.5, 8, "gold"));
        o.addAll(socketGem(8, 8, 9, 2, 0, true));
        return o;
    }

    // ---------------------------------------------------------------- carriers (Orb, Tesseract, Aether, Scepter, Cosmi-Rods + empty_*)
    static final int[] DARK = {0x07080C, 0x14171E, 0x232834, 0x333A49, 0x4B5568, 0x6F7B92};
    static final int[] STEEL = {0x15181F, 0x2E3441, 0x485061, 0x646E82, 0x8892A8, 0xC0C9DB};
    static final int[] SILVER = {0x262E3F, 0x55637C, 0x8190AB, 0xABBAD2, 0xD0DCEE, 0xFFFFFF};
    static final int[] DULL = {0x0E1015, 0x1C1F27, 0x2B303B, 0x3B4250, 0x50586A, 0x6C7587};

    static int[] dim(int[] p, double f) { int[] o = new int[p.length]; for (int i = 0; i < p.length; i++) o[i] = shade(p[i], f); return o; }

    static Map<String, int[]> carrierMetals(boolean empty) {
        double f = empty ? 0.78 : 1.0;
        Map<String, int[]> m = new HashMap<>();
        m.put("dark", dim(DARK, f)); m.put("steel", dim(STEEL, f)); m.put("silver", dim(SILVER, f));
        m.put("gold", dim(GOLD, f)); m.put("gold_dark", dim(GOLD_DARK, f)); m.put("gold_light", dim(GOLD_LIGHT, f)); m.put("socket", dim(SOCKET, f));
        return m;
    }

    static Map<String, int[]> withGem(Map<String, int[]> metals, int[] gem) {
        Map<String, int[]> m = new HashMap<>(metals);
        m.put("gem", gem); m.put("cube", gem);
        return m;
    }

    static El maybeGlow(El e, boolean glow) { return glow ? e.glow() : e; }

    /** Faceted pixel gem (same cut as the gauntlet/stone gems, smaller): body w x h x w, a belly, an optional octagon and two tips. */
    static List<El> miniGem(double cx, double cy, double cz, double w, double h, boolean round, boolean glow) {
        List<El> o = new ArrayList<>();
        double e = 0.02, hw = w / 2, hh = h / 2, qh = h / 4;
        o.add(maybeGlow(new El("g_body", cx - hw, cy - hh, cz - hw, cx + hw, cy + hh, cz + hw, "gem"), glow));
        o.add(maybeGlow(new El("g_wx", cx - hw - 1, cy - qh, cz - hw + e, cx + hw + 1, cy + qh, cz + hw - e, "gem"), glow));
        o.add(maybeGlow(new El("g_wz", cx - hw + e, cy - qh + e, cz - hw - 1, cx + hw - e, cy + qh - e, cz + hw + 1, "gem"), glow));
        if (round) o.add(maybeGlow(new El("g_diag", cx - hw - 0.5, cy - qh + 0.5, cz - hw - 0.5, cx + hw + 0.5, cy + qh - 0.5, cz + hw + 0.5, "gem").rot("y", 45, cx, cy, cz), glow));
        o.add(maybeGlow(new El("g_top", cx - hw / 2, cy + hh, cz - hw / 2, cx + hw / 2, cy + hh + 1, cz + hw / 2, "gem"), glow));
        o.add(maybeGlow(new El("g_bottom", cx - hw / 2, cy - hh - 1, cz - hw / 2, cx + hw / 2, cy - hh, cz + hw / 2, "gem"), glow));
        return o;
    }

    /** Segments {length, absolute angle about X}; each one starts where the previous ended (curved blade profile in the YZ plane). */
    static List<El> chain(String name, double x0, double x1, double y, double zc, double th, double[][] segs, String tex) {
        List<El> o = new ArrayList<>();
        for (int i = 0; i < segs.length; i++) {
            double len = segs[i][0], a = segs[i][1];
            o.add(new El(name + i, x0, y, zc - th / 2, x1, y + len, zc + th / 2, tex).rot("x", a, (x0 + x1) / 2, y, zc));
            y += len * Math.cos(Math.toRadians(a));
            zc += len * Math.sin(Math.toRadians(a));
        }
        return o;
    }

    static List<El> orb(boolean full) {
        List<El> o = new ArrayList<>();
        o.add(new El("foot", 4, 0, 4, 12, 1, 12, "dark"));
        o.add(new El("foot_up", 5, 1, 5, 11, 2, 11, "gold"));
        for (int[] p : new int[][]{{4, 4}, {11, 4}, {4, 11}, {11, 11}}) {
            o.add(new El("post", p[0], 1, p[1], p[0] + 1, 13, p[1] + 1, "steel"));
            o.add(new El("collar_lo", p[0] - 0.5, 3, p[1] - 0.5, p[0] + 1.5, 4, p[1] + 1.5, "gold"));
            o.add(new El("collar_hi", p[0] - 0.5, 10, p[1] - 0.5, p[0] + 1.5, 11, p[1] + 1.5, "gold_light"));
        }
        o.add(new El("top", 4, 13, 4, 12, 14, 12, "dark"));
        o.add(new El("top_up", 5, 14, 5, 11, 15, 11, "gold"));
        o.add(new El("finial", 7, 15, 7, 9, 16, 9, "steel"));
        o.add(new El("tip", 7.5, 16, 7.5, 8.5, 17, 8.5, "gold_light"));
        o.addAll(miniGem(8, 7.5, 8, 4, 8, true, full));
        return o;
    }

    static List<El> tesseract(boolean full) {
        List<El> o = new ArrayList<>();
        double[] lo = {3, 11};
        for (double x : lo) for (double y : lo) for (double z : lo) o.add(new El("corner", x, y, z, x + 2, y + 2, z + 2, "silver"));
        double[] bl = {3.5, 11.5};
        for (double a : bl) for (double b : bl) {
            o.add(new El("bar_x", 5, a, b, 11, a + 1, b + 1, "steel"));
            o.add(new El("bar_y", a, 5, b, a + 1, 11, b + 1, "steel"));
            o.add(new El("bar_z", a, b, 5, a + 1, b + 1, 11, "steel"));
        }
        List<El> core = new ArrayList<>();
        core.add(maybeGlow(new El("core", 5, 5, 5, 11, 11, 11, "cube"), full));
        core.add(maybeGlow(new El("cap_y", 5.5, 4.5, 5.5, 10.5, 11.5, 10.5, "gem"), full));
        core.add(maybeGlow(new El("cap_x", 4.5, 5.5, 5.52, 11.5, 10.5, 10.48, "gem"), full));
        core.add(maybeGlow(new El("cap_z", 5.52, 5.52, 4.5, 10.48, 10.48, 11.5, "gem"), full));
        for (El e : core) e.rot("y", 45, 8, 8, 8);
        o.addAll(core);
        return o;
    }

    static List<El> aether(boolean full) {
        List<El> o = new ArrayList<>();
        o.add(new El("foot", 4, 0, 4, 12, 1, 12, "dark"));
        o.add(new El("foot_up", 5, 1, 5, 11, 2, 11, "gold"));
        for (int[] p : new int[][]{{5, 5}, {10, 5}, {5, 10}, {10, 10}}) {
            o.add(new El("post", p[0], 2, p[1], p[0] + 1, 12, p[1] + 1, "gold_dark"));
            o.add(new El("ring", p[0] - 0.5, 6, p[1] - 0.5, p[0] + 1.5, 7, p[1] + 1.5, "gold_light"));
        }
        if (full) {
            o.add(new El("liquid", 6, 2, 6, 10, 12, 10, "gem").glow());
            o.add(new El("belly_x", 5.5, 5, 6.2, 10.5, 9, 9.8, "gem").glow());
            o.add(new El("belly_z", 6.2, 5.02, 5.5, 9.8, 8.98, 10.5, "gem").glow());
            o.add(new El("wisp", 7, 14, 7, 9, 15.5, 9, "gem").glow());
            o.add(new El("wisp_tip", 7.5, 15.5, 7.5, 8.5, 16.5, 8.5, "gem").glow());
        } else {
            o.add(new El("liquid", 6, 2, 6, 10, 4, 10, "gem"));
            o.add(new El("belly_x", 5.5, 3, 6.2, 10.5, 4.5, 9.8, "gem"));
        }
        o.add(new El("top", 4, 12, 4, 12, 13, 12, "dark"));
        o.add(new El("top_up", 5, 13, 5, 11, 14, 11, "gold"));
        if (!full) o.add(new El("finial", 7, 14, 7, 9, 15, 9, "gold_light"));
        return o;
    }

    static List<El> scepter(boolean full) {
        List<El> o = new ArrayList<>();
        o.add(new El("spike", 7.5, -4, 7.5, 8.5, -2, 8.5, "gold_light"));
        o.add(new El("butt", 7, -2, 7, 9, 0, 9, "gold"));
        o.add(new El("shaft", 7, 0, 7, 9, 15, 9, "gold_dark"));
        o.add(new El("grip", 6.5, 3, 6.5, 9.5, 8, 9.5, "socket"));
        o.add(new El("band_lo", 6.25, 2, 6.25, 9.75, 3, 9.75, "gold_light"));
        o.add(new El("band_hi", 6.25, 8, 6.25, 9.75, 9, 9.75, "gold_light"));
        o.add(new El("band_mid", 6.25, 11, 6.25, 9.75, 12, 9.75, "gold"));
        o.add(new El("collar", 6.5, 15, 6.5, 9.5, 16, 9.5, "gold"));
        o.add(new El("collar_up", 6, 16, 6, 10, 17, 10, "gold_light"));
        o.addAll(chain("blade_f", 7, 9, 17, 9.5, 1, new double[][]{{3, 22.5}, {2, 0}, {3, -22.5}}, "gold"));
        o.addAll(chain("blade_b", 7, 9, 17, 6.5, 1, new double[][]{{3, -22.5}, {2, 0}, {3, 22.5}}, "gold"));
        o.addAll(miniGem(8, 20, 8, 2, 4, false, full));
        o.add(new El("finial", 7.5, 23, 7.5, 8.5, 24, 8.5, "gold_light"));
        return o;
    }

    static List<El> rod(boolean full) {
        List<El> o = new ArrayList<>();
        o.add(new El("head", 4.5, -5, 4, 11.5, 3, 12, "steel"));
        o.add(new El("plate_f", 3.5, -5.5, 3, 12.5, 3.5, 4, "gold"));
        o.add(new El("plate_b", 3.5, -5.5, 12, 12.5, 3.5, 13, "gold"));
        o.add(maybeGlow(new El("gem_f", 6, -3.5, 2, 10, 1.5, 3, "gem"), full));
        o.add(maybeGlow(new El("gem_f_crown", 7, -2.5, 1.5, 9, 0.5, 2, "gem"), full));
        o.add(maybeGlow(new El("gem_b", 6, -3.5, 13, 10, 1.5, 14, "gem"), full));
        o.add(maybeGlow(new El("gem_b_crown", 7, -2.5, 14, 9, 0.5, 14.5, "gem"), full));
        for (double x : new double[]{4.3, 11.5}) {
            o.add(new El("rune_h", x, -2.5, 5.5, x + 0.2, -0.5, 10.5, full ? "gem" : "dark").glow().copy());
            o.add(new El("rune_v", x, -4, 7.5, x + 0.2, 2, 8.5, full ? "gem" : "dark").glow().copy());
        }
        if (!full) for (El e : o) if (e.name.startsWith("rune")) { e.emissive = false; e.shade = true; }
        o.add(new El("spike", 7, -7, 7, 9, -5, 9, "steel"));
        o.add(new El("neck", 6, 3, 6, 10, 4, 10, "gold"));
        o.add(new El("handle", 7, 4, 7, 9, 23, 9, "dark"));
        o.add(new El("grip", 6.5, 8, 6.5, 9.5, 16, 9.5, "socket"));
        o.add(new El("band_lo", 6.25, 7, 6.25, 9.75, 8, 9.75, "gold"));
        o.add(new El("band_hi", 6.25, 16, 6.25, 9.75, 17, 9.75, "gold"));
        o.add(new El("pommel", 6.5, 23, 6.5, 9.5, 24, 9.5, "gold"));
        o.addAll(miniGem(8, 26, 8, 2, 4, false, full));
        return o;
    }

    // ---------------------------------------------------------------- display transforms
    static String tf(String ctx, double[] rot, double[] tr, double s) { return tf(ctx, rot, tr, new double[]{s, s, s}); }
    static String tf(String ctx, double[] rot, double[] tr, double[] s) {
        return "    \"" + ctx + "\": {\"rotation\": " + v(rot) + ", \"translation\": " + v(tr) + ", \"scale\": " + v(s) + "}";
    }
    static String display(String... t) { return "{\n" + String.join(",\n", t) + "\n  }"; }

    /** Translation (item pixels) that puts the model point c in the middle of the slot after rotation (X then Y then Z) and scale about (8,8,8). */
    static double[] centre(double rx, double ry, double rz, double s, double cx, double cy, double cz) {
        double x = cx - 8, y = cy - 8, z = cz - 8, a = Math.toRadians(rx), b = Math.toRadians(ry), c = Math.toRadians(rz);
        double y1 = y * Math.cos(a) - z * Math.sin(a), z1 = y * Math.sin(a) + z * Math.cos(a);
        double x2 = x * Math.cos(b) + z1 * Math.sin(b), z2 = -x * Math.sin(b) + z1 * Math.cos(b);
        double x3 = x2 * Math.cos(c) - y1 * Math.sin(c), y3 = x2 * Math.sin(c) + y1 * Math.cos(c);
        return new double[]{-x3 * s, -y3 * s, -z2 * s};
    }

    /** Symmetric, upright relic (orb, tesseract, aether) whose middle is at height cy. */
    static String upright(double gs, double cy, double hs) {
        double[] g = centre(20, -35, 0, gs, 8, cy, 8);
        double d = (8 - cy);
        return display(
                tf("gui", new double[]{20, -35, 0}, new double[]{g[0], g[1], 0}, gs),
                tf("ground", new double[]{0, 0, 0}, new double[]{0, 2 + d * 0.5, 0}, 0.5),
                tf("fixed", new double[]{0, 0, 0}, new double[]{0, d * 0.8, 0}, 0.8),
                tf("head", new double[]{0, 0, 0}, new double[]{0, 13 + d * 0.7, 0}, 0.7),
                tf("thirdperson_righthand", new double[]{0, 0, 0}, new double[]{0, 3 + d * hs, 1}, hs),
                tf("thirdperson_lefthand", new double[]{0, 0, 0}, new double[]{0, 3 + d * hs, 1}, hs),
                tf("firstperson_righthand", new double[]{10, -35, 0}, new double[]{1.13, 3.6 + d * (hs + 0.2), 1.13}, hs + 0.2),
                tf("firstperson_lefthand", new double[]{10, -35, 0}, new double[]{1.13, 3.6 + d * (hs + 0.2), 1.13}, hs + 0.2));
    }

    static String scepterDisplay() {
        double[] g = centre(10, 70, -45, 0.68, 8, 10.25, 8), f = centre(10, 70, -45, 0.78, 8, 10.25, 8);
        return display(
                tf("gui", new double[]{10, 70, -45}, new double[]{g[0], g[1], 0}, 0.68),
                tf("ground", new double[]{90, 90, 0}, new double[]{0, -1.5, 0}, 1),
                tf("fixed", new double[]{10, 70, -45}, new double[]{f[0], f[1], 0}, 0.78),
                tf("head", new double[]{-56, 0, 0}, new double[]{0, 3.25, -4.5}, 1),
                tf("thirdperson_righthand", new double[]{0, 0, 0}, new double[]{0, -2, 1.5}, 1),
                tf("thirdperson_lefthand", new double[]{0, 0, 0}, new double[]{0, -2, 1.5}, 1),
                tf("firstperson_righthand", new double[]{0, 0, -10}, new double[]{0, 0, 1.75}, 1),
                tf("firstperson_lefthand", new double[]{0, 0, -10}, new double[]{0, 0, 1.75}, 1));
    }

    /** Head on low Y, handle on high Y: the same orientation and extents as the previous cosmi-rod, so its hand transforms still fit. */
    static final String ROD_DISPLAY = """
            {
                "thirdperson_righthand": {"rotation": [180, 180, 0], "translation": [0, 11.25, 1.75], "scale": [1, 1, 1]},
                "thirdperson_lefthand": {"rotation": [180, 180, 0], "translation": [0, 11.25, 1.75], "scale": [1, 1, 1]},
                "firstperson_righthand": {"rotation": [160, 180, 0], "translation": [2.5, 2, 0], "scale": [1, 1, 1]},
                "firstperson_lefthand": {"rotation": [160, 180, 0], "translation": [2.5, 2, 0], "scale": [1, 1, 1]},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 9.5, 0], "scale": [1, 1, 1]},
                "gui": {"rotation": [-160, -35, 0], "translation": [0, 1.75, 0], "scale": [0.4, 0.4, 0.4]},
                "fixed": {"rotation": [180, 90, 0], "translation": [0, 2.75, 0], "scale": [1, 0.5, 0.5]}
              }""";

    record Pv(double rx, double ry, double scale, double cy) {}

    static void carrierFull(StringBuilder layout, String name, List<El> els, Map<String, int[]> pal, int S, String display, double[] anchor, Pv pv) throws IOException {
        String tex = "item/carrier/" + name;
        write("models/item/base/" + name + ".json", model(tex, els, null, pal, S));
        write("models/item/" + name + ".json", wrapper("infinity:" + tex, display));
        layout.append("        CARRIERS.put(\"").append(name).append("\", new float[]{").append(f(anchor[0] / 16)).append("F, ").append(f(anchor[1] / 16))
                .append("F, ").append(f(anchor[2] / 16)).append("F, ").append(f(anchor[3] / 16)).append("F});\n");
        for (El e : els) e.tex = "all";
        preview(els, Map.of("all", ATLASES.get(tex)), pv.rx, pv.ry, name, pv.scale, pv.cy);
    }

    static void carrierEmpty(String name, List<El> els, Map<String, int[]> pal, int S, String display, Pv pv) throws IOException {
        String tex = "item/empty/" + name;
        write("models/item/" + name + ".json", model(tex, els, null, pal, S, display));
        for (El e : els) e.tex = "all";
        preview(els, Map.of("all", ATLASES.get(tex)), pv.rx, pv.ry, name, pv.scale, pv.cy);
    }

    static void carriers(StringBuilder layout) throws IOException {
        Map<String, int[]> metals = carrierMetals(false), tired = carrierMetals(true);
        Map<String, int[]> dull = withGem(tired, DULL);
        int[] powerPal = gemPalette(0xA43CE6), spacePal = gemPalette(0x2F8CFF), realityPal = gemPalette(0xE22B3B), mindPal = gemPalette(0xF2D02B);

        carrierFull(layout, "orb", orb(true), withGem(metals, powerPal), 64, upright(0.8, 8.5, 0.5), new double[]{8, 7.5, 8, 4.5}, new Pv(22, -30, 22, 8.5));
        carrierEmpty("empty_orb", orb(false), dull, 64, upright(0.8, 8.5, 0.5), new Pv(22, -30, 22, 8.5));

        carrierFull(layout, "tesseract", tesseract(true), withGem(metals, spacePal), 64, upright(1.05, 8, 0.5), new double[]{8, 8, 8, 4.5}, new Pv(22, -30, 28, 8));
        carrierEmpty("empty_tesseract", tesseract(false), dull, 64, upright(1.05, 8, 0.5), new Pv(22, -30, 28, 8));

        carrierFull(layout, "aether", aether(true), withGem(metals, realityPal), 64, upright(0.85, 8.25, 0.55), new double[]{8, 7, 8, 4.5}, new Pv(22, -30, 22, 8.25));
        carrierEmpty("empty_aether", aether(false), dull, 64, upright(0.85, 8.25, 0.55), new Pv(22, -30, 22, 8.25));

        String sd = scepterDisplay();
        carrierFull(layout, "scepter", scepter(true), withGem(metals, mindPal), 64, sd, new double[]{8, 20, 8, 3.5}, new Pv(0, 90, 14, 10));
        carrierEmpty("empty_scepter", scepter(false), dull, 64, sd, new Pv(0, 90, 14, 10));

        for (StoneDef s : STONES)
            carrierFull(layout, "cosmi-rod_" + s.key, rod(true), withGem(metals, gemPalette(s.rgb)), 128, ROD_DISPLAY, new double[]{8, -1, 8, 4.5}, new Pv(0, 35, 12, 12));
        carrierEmpty("cosmi-rod", rod(false), dull, 128, ROD_DISPLAY, new Pv(0, 35, 12, 12));
    }

    static void emptyEye(Map<String, int[]> metal) throws IOException {
        Map<String, int[]> pal = new HashMap<>();
        for (var e : metal.entrySet()) pal.put(e.getKey(), dim(e.getValue(), 0.78));
        pal.put("gem", DULL);
        List<El> eye = eyeOfAgamotto();
        for (El e : eye) if (e.tex.equals("gem")) { e.emissive = false; e.shade = true; }
        write("models/item/empty_eye_of_agamotto.json", model("item/empty/empty_eye_of_agamotto", eye, null, pal, 32, EYE_DISPLAY));
        for (El e : eye) e.tex = "all";
        preview(eye, Map.of("all", ATLASES.get("item/empty/empty_eye_of_agamotto")), 10, -25, "empty_eye_of_agamotto");
    }

    // ---------------------------------------------------------------- pixel palettes {outline, shadow, mid, base, light, highlight}
    static final int[] GOLD = {0x4A2E06, 0x8A5A10, 0xB8841C, 0xDCA72A, 0xF5CF4E, 0xFFF0A0};
    static final int[] GOLD_DARK = {0x3A2405, 0x6E4710, 0x8F6418, 0xAE7E20, 0xC99A35, 0xE8C060};
    static final int[] GOLD_LIGHT = {0x5A3A08, 0xA87418, 0xD8A42A, 0xF2C744, 0xFFE278, 0xFFF8C8};
    static final int[] SOCKET = {0x1E1206, 0x3A2408, 0x553612, 0x6E4816, 0x8A5E20, 0xA87C34};

    static int[] gemPalette(int c) { return new int[]{shade(c, 0.3), shade(c, 0.52), shade(c, 0.75), c, shade(c, 1.35), shade(c, 1.75)}; }

    // =====================================================================================================
    // json writing
    // =====================================================================================================

    static final Map<String, BufferedImage> ATLASES = new HashMap<>();

    /**
     * Writes a model whose faces are all packed into one generated texture (textures/PATH.png, S x S) with exactly one
     * texel per model pixel; every face is hand-shaded from its palette (light from the top-left, outline at the bottom).
     */
    static String model(String path, List<El> els, String renderType, Map<String, int[]> palettes, int S) throws IOException {
        return model(path, els, renderType, palettes, S, null);
    }

    static String model(String path, List<El> els, String renderType, Map<String, int[]> palettes, int S, String display) throws IOException {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        int[] cur = {0, 0, 0}; // x, y, rowH
        String[] dirs = {"north", "east", "south", "west", "up", "down"};
        for (El e : els) {
            e.uvs = new double[6][];
            int[] pal = palettes.get(e.tex);
            int kind = e.tex.equals("cube") ? 2 : e.tex.equals("gem") ? 1 : 0;
            for (int d = 0; d < 6; d++) {
                double[] wh = faceSize(e, dirs[d]);
                int w = Math.max(1, (int) Math.round(wh[0])), h = Math.max(1, (int) Math.round(wh[1]));
                if (cur[0] + w > S) { cur[0] = 0; cur[1] += cur[2]; cur[2] = 0; }
                if (cur[1] + h > S) throw new IllegalStateException("atlas " + path + " too small");
                int x = cur[0], y = cur[1];
                cur[0] += w; cur[2] = Math.max(cur[2], h);
                paintFace(img, x, y, w, h, dirs[d], pal, kind, (e.name + d).hashCode());
                double k = 16.0 / S;
                e.uvs[d] = new double[]{x * k, y * k, (x + w) * k, (y + h) * k};
            }
        }
        png(img, "textures/" + path + ".png");
        ATLASES.put(path, img);
        StringBuilder b = new StringBuilder();
        b.append("{\n  \"credit\": \"Generated by tools/visual/GenVisuals.java\",\n");
        if (renderType != null) b.append("  \"render_type\": \"").append(renderType).append("\",\n");
        b.append("  \"texture_size\": [").append(S).append(", ").append(S).append("],\n");
        b.append("  \"textures\": {\n    \"all\": \"infinity:").append(path).append("\",\n    \"particle\": \"infinity:").append(path).append("\"\n  },\n");
        if (display != null) b.append("  \"display\": ").append(display).append(",\n");
        b.append("  \"elements\": [");
        for (int k = 0; k < els.size(); k++) {
            El e = els.get(k);
            b.append(k == 0 ? "\n" : ",\n").append("    {\"name\": \"").append(e.name).append("\", \"from\": ").append(v(e.from)).append(", \"to\": ").append(v(e.to));
            if (e.axis != null && e.angle != 0) b.append(", \"rotation\": {\"angle\": ").append(n(e.angle)).append(", \"axis\": \"").append(e.axis).append("\", \"origin\": ").append(v(e.origin)).append('}');
            if (!e.shade) b.append(", \"shade\": false");
            if (e.emissive) b.append(", \"neoforge_data\": {\"block_light\": 15, \"sky_light\": 15}");
            b.append(", \"faces\": {");
            for (int d = 0; d < 6; d++) b.append(d == 0 ? "" : ", ").append('"').append(dirs[d]).append("\": {\"uv\": ").append(v(e.uvs[d])).append(", \"texture\": \"#all\"}");
            b.append("}}");
        }
        b.append("\n  ]\n}\n");
        return b.toString();
    }

    static double[] faceSize(El e, String dir) {
        double dx = e.to[0] - e.from[0], dy = e.to[1] - e.from[1], dz = e.to[2] - e.from[2];
        return switch (dir) {
            case "north", "south" -> new double[]{dx, dy};
            case "east", "west" -> new double[]{dz, dy};
            default -> new double[]{dx, dz};
        };
    }

    /** kind 0 = metal plate, 1 = gem facet, 2 = space cube (framed face with a glowing core). */
    static void paintFace(BufferedImage img, int x0, int y0, int w, int h, String dir, int[] P, int kind, int seed) {
        boolean up = dir.equals("up"), down = dir.equals("down"), side = !up && !down;
        boolean ew = dir.equals("east") || dir.equals("west");
        Random r = new Random(seed);
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++) {
                int c;
                if (kind == 0) {
                    c = up ? P[4] : down ? P[2] : ew ? P[2] : P[3];
                    if (side || up) {
                        if (j == 0 && h > 1) c = up ? P[5] : P[4];
                        else if (i == 0 && w > 1) c = up ? P[4] : (ew ? P[3] : P[4]);
                        if (j == h - 1 && h > 2) c = P[1];
                        else if (i == w - 1 && w > 2) c = ew ? P[1] : P[2];
                    }
                    if (i == 0 && j == 0 && w > 1 && h > 1) c = P[5];
                    if (w >= 7 && i == w / 2 && j > 0 && j < h - 1) c = P[2]; // plate seam
                    if (w >= 5 && h >= 4 && j == h - 2 && (i == 1 || i == w - 2)) c = P[5]; // rivets
                } else if (kind == 2) {
                    boolean border = i == 0 || j == 0 || i == w - 1 || j == h - 1;
                    double d = Math.hypot(i - (w - 1) / 2.0, j - (h - 1) / 2.0);
                    c = border ? (i == 0 || j == 0 ? P[4] : P[1]) : d < 1.0 ? P[5] : d < 2.0 ? P[4] : P[2];
                } else {
                    c = up ? P[4] : down ? P[1] : ew ? P[2] : P[3];
                    if (side) {
                        if (j == 0) c = P[4];
                        else if (j == h - 1 && h > 2) c = P[1];
                        else if (i == 0 && w > 2) c = ew ? P[3] : P[4];
                        else if (i == w - 1 && w > 2) c = P[2];
                    } else if (up && (i == 0 || j == 0 || i == w - 1 || j == h - 1) && w > 2 && h > 2) c = P[3];
                    if (w >= 3 && h >= 3 && i == 1 && j == 1) c = P[5];
                    if (w >= 4 && h >= 4 && ((i == 2 && j == 1) || (i == 1 && j == 2))) c = P[4];
                    if (w >= 4 && h >= 4 && i == w - 2 && j == h - 2 && r.nextBoolean()) c = P[5];
                }
                img.setRGB(x0 + i, y0 + j, 0xFF000000 | c);
            }
    }

    static String n(double d) {
        double r = Math.round(d * 1000) / 1000.0;
        if (r == Math.rint(r)) return String.valueOf((long) r);
        return String.valueOf(r);
    }

    static String v(double[] a) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) b.append(i == 0 ? "" : ", ").append(n(a[i]));
        return b.append(']').toString();
    }

    static final String STONE_DISPLAY = """
            {
                "gui": {"rotation": [22, -30, 0], "translation": [0, 0, 0], "scale": [1.6, 1.6, 1.6]},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.55, 0.55, 0.55]},
                "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.1, 1.1, 1.1]},
                "head": {"rotation": [0, 0, 0], "translation": [0, 13, 0], "scale": [0.7, 0.7, 0.7]},
                "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.5, 0.5, 0.5]},
                "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.5, 0.5, 0.5]},
                "firstperson_righthand": {"rotation": [10, -35, 0], "translation": [1.13, 3.6, 1.13], "scale": [0.75, 0.75, 0.75]},
                "firstperson_lefthand": {"rotation": [10, -35, 0], "translation": [1.13, 3.6, 1.13], "scale": [0.75, 0.75, 0.75]}
              }""";

    static final String GAUNTLET_DISPLAY = """
            {
                "gui": {"rotation": [8, -28, 0], "translation": [0, 0.6, 0], "scale": [0.72, 0.72, 0.72]},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 4, 0], "scale": [0.45, 0.45, 0.45]},
                "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
                "head": {"rotation": [0, 0, 0], "translation": [0, 10, 0], "scale": [0.6, 0.6, 0.6]},
                "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0, 0, 0]},
                "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0, 0, 0]},
                "firstperson_righthand": {"rotation": [0, -12, 6], "translation": [-1, 1.5, -3], "scale": [0, 0, 0]},
                "firstperson_lefthand": {"rotation": [0, -12, 6], "translation": [2.5, -2.5, -1], "scale": [0, 0, 0]}
              }""";

    static final String EYE_DISPLAY = """
            {
                "gui": {"rotation": [10, -25, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
                "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
                "head": {"rotation": [0, 0, 0], "translation": [0, 10, 0], "scale": [0.6, 0.6, 0.6]},
                "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.45, 0.45, 0.45]},
                "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.45, 0.45, 0.45]},
                "firstperson_righthand": {"rotation": [0, -25, 0], "translation": [1.13, 3.2, 1.13], "scale": [0.6, 0.6, 0.6]},
                "firstperson_lefthand": {"rotation": [0, -25, 0], "translation": [1.13, 3.2, 1.13], "scale": [0.6, 0.6, 0.6]}
              }""";

    static String wrapper(String particle, String display) { return wrapper(particle, display, "front"); }
    static String wrapper(String particle, String display, String light) {
        return "{\n  \"credit\": \"Generated by tools/visual/GenVisuals.java - drawn by client/render (builtin/entity)\",\n  \"parent\": \"minecraft:builtin/entity\",\n"
                + "  \"gui_light\": \"" + light + "\",\n"
                + "  \"textures\": {\"particle\": \"" + particle + "\"},\n  \"display\": " + display + "\n}\n";
    }

    static String f(double d) { return String.format(Locale.ROOT, "%.4f", d); }

    static void writeLayout(Gauntlet g, String carriers) throws IOException {
        StringBuilder b = new StringBuilder();
        b.append("// GENERATED by tools/visual/GenVisuals.java. Do not edit by hand.\n");
        b.append("package com.infinitystones.tensura.client.render;\n\nimport java.util.HashMap;\nimport java.util.Map;\n\n");
        b.append("/** Positions (block units, model space 0..1) of the gauntlet sockets and of the carriers' glow anchors. */\n");
        b.append("public final class VisualLayout {\n");
        b.append("    /** Socket gem centre + size per stone key (mind, power, space, reality, soul, time). */\n");
        b.append("    public static final Map<String, float[]> GAUNTLET_SOCKETS = new HashMap<>();\n");
        b.append("    /** Glow anchor per carrier item path: x, y, z, radius. */\n");
        b.append("    public static final Map<String, float[]> CARRIERS = new HashMap<>();\n\n    static {\n");
        for (var e : g.gemCenters.entrySet()) {
            double[] c = e.getValue();
            b.append("        GAUNTLET_SOCKETS.put(\"").append(e.getKey()).append("\", new float[]{").append(f(c[0] / 16)).append("F, ").append(f(c[1] / 16)).append("F, ").append(f(c[2] / 16)).append("F});\n");
        }
        b.append(carriers);
        b.append("    }\n\n    private VisualLayout() {}\n}\n");
        Files.createDirectories(JAVA_OUT.getParent());
        Files.writeString(JAVA_OUT, b.toString());
    }

    // =====================================================================================================
    // textures
    // =====================================================================================================

    static int rgb(int r, int g, int b) { return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b); }
    static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
    static int argb(int a, int rgb) { return (clamp(a) << 24) | (rgb & 0xFFFFFF); }
    static int shade(int c, double f) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        if (f >= 1) { double k = Math.min(1, (f - 1)); return rgb((int) (r + (255 - r) * k), (int) (g + (255 - g) * k), (int) (b + (255 - b) * k)); }
        return rgb((int) (r * f), (int) (g * f), (int) (b * f));
    }
    static int mix(int a, int b, double t) {
        return rgb((int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), (int) ((a & 255) * (1 - t) + (b & 255) * t));
    }

    /** Facet texture: four shaded triangles (light from the top), a bright table, rim highlight and sparkles. */
    static BufferedImage gemTexture(int base, boolean shell) {
        int S = 32;
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(base * 31L + (shell ? 7 : 0));
        int deep = shade(base, 0.42), bright = shade(base, 1.38);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                double u = (x + 0.5) / S, v = (y + 0.5) / S;
                double f;
                if (v < u && v < 1 - u) f = 1.30;          // top facet
                else if (u < v && u < 1 - v) f = 1.06;     // left facet
                else if (u > v && u > 1 - v) f = 0.82;     // right facet
                else f = 0.64;                             // bottom facet
                double cu = Math.abs(u - 0.5), cv = Math.abs(v - 0.5);
                if (cu < 0.17 && cv < 0.17) f = 1.16 + (0.17 - Math.max(cu, cv)) * 1.4; // table
                double d = Math.hypot(u - 0.5, v - 0.5);
                f += (0.5 - d) * 0.35;                      // inner glow
                int c = shade(base, f);
                c = mix(c, deep, Math.max(0, d - 0.42) * 2.0);
                if (Math.abs(u - v) < 0.025 || Math.abs(u - (1 - v)) < 0.025) c = shade(c, 1.35); // facet edges
                c = shade(c, 1 + (r.nextDouble() - 0.5) * 0.08);
                boolean edge = x == 0 || y == 0 || x == S - 1 || y == S - 1;
                if (edge) c = shade(bright, 1.25);
                int a = 255;
                if (shell) {
                    a = edge ? 190 : (int) (55 + 70 * Math.max(0, 1 - d * 2.2));
                    c = shade(c, 1.25);
                }
                img.setRGB(x, y, argb(a, c));
            }
        // sparkles
        int[][] sp = {{8, 6}, {22, 9}, {12, 20}, {25, 23}};
        for (int[] p : sp) {
            if (shell && r.nextBoolean()) continue;
            img.setRGB(p[0], p[1], argb(255, 0xFFFFFF));
            img.setRGB(p[0] + 1, p[1], argb(shell ? 200 : 255, shade(base, 1.7)));
            img.setRGB(p[0] - 1, p[1], argb(shell ? 200 : 255, shade(base, 1.7)));
            img.setRGB(p[0], p[1] + 1, argb(shell ? 200 : 255, shade(base, 1.7)));
            img.setRGB(p[0], p[1] - 1, argb(shell ? 200 : 255, shade(base, 1.7)));
        }
        return img;
    }

    /** Brushed metal with bevel: light top/left edges, dark bottom/right, vertical streaks. */
    static BufferedImage metal(int base, float bright) {
        int S = 32;
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(base);
        double[] streak = new double[S];
        for (int x = 0; x < S; x++) streak[x] = (r.nextDouble() - 0.5) * 0.16;
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                double f = bright * (1.02 - 0.18 * (x + y) / (2.0 * S)) + streak[x] + (r.nextDouble() - 0.5) * 0.05;
                // soft specular band
                double band = Math.exp(-Math.pow((x - y * 0.6 - 8) / 3.0, 2)) * 0.22;
                f += band;
                int c = shade(base, f);
                if ((x % 16 == 0) || (y % 16 == 0)) c = shade(base, bright * 1.28);
                if ((x % 16 == 15) || (y % 16 == 15)) c = shade(base, bright * 0.62);
                img.setRGB(x, y, argb(255, c));
            }
        return img;
    }

    static BufferedImage halo(int S) {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                double d = Math.hypot((x + 0.5) / S - 0.5, (y + 0.5) / S - 0.5) * 2;
                double k = Math.max(0, 1 - d);
                k = Math.pow(k, 2.2) * 0.85 + Math.pow(Math.max(0, 1 - d * 3.2), 2) * 0.15;
                int v = (int) Math.round(255 * Math.min(1, k));
                img.setRGB(x, y, (v << 24) | (v << 16) | (v << 8) | v);
            }
        return img;
    }

    static BufferedImage flare(int S) {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                double u = ((x + 0.5) / S - 0.5) * 2, v = ((y + 0.5) / S - 0.5) * 2;
                double d = Math.hypot(u, v);
                double rays = Math.exp(-Math.abs(u) * 26) * Math.max(0, 1 - Math.abs(v)) + Math.exp(-Math.abs(v) * 26) * Math.max(0, 1 - Math.abs(u));
                double diag = (Math.exp(-Math.abs(u - v) * 30) + Math.exp(-Math.abs(u + v) * 30)) * Math.max(0, 1 - d * 1.6) * 0.45;
                double core = Math.pow(Math.max(0, 1 - d * 2.5), 2);
                double k = Math.min(1, rays + diag + core);
                int c = (int) (255 * k);
                img.setRGB(x, y, (c << 24) | (c << 16) | (c << 8) | c);
            }
        return img;
    }

    /** Ground magic circle: two rings, tick marks and rune-like glyph blocks. White, intensity in rgb+alpha. */
    static BufferedImage runeRing(int S) {
        BufferedImage img = new BufferedImage(S * 4, S * 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(4, 4);
        double c = S / 2.0;
        g.setColor(new Color(255, 255, 255, 230));
        g.setStroke(new BasicStroke(1.6f));
        g.draw(new Ellipse2D.Double(c - 60, c - 60, 120, 120));
        g.setStroke(new BasicStroke(0.9f));
        g.draw(new Ellipse2D.Double(c - 52, c - 52, 104, 104));
        g.draw(new Ellipse2D.Double(c - 34, c - 34, 68, 68));
        Random r = new Random(6);
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48;
            double len = i % 4 == 0 ? 6 : 3;
            g.setStroke(new BasicStroke(i % 4 == 0 ? 1.4f : 0.8f));
            g.draw(new Line2D.Double(c + Math.cos(a) * 52, c + Math.sin(a) * 52, c + Math.cos(a) * (52 - len), c + Math.sin(a) * (52 - len)));
        }
        // glyph blocks between the outer rings
        for (int i = 0; i < 24; i++) {
            double a = (i + 0.5) * Math.PI * 2 / 24;
            AffineTransform t = g.getTransform();
            g.translate(c + Math.cos(a) * 56, c + Math.sin(a) * 56);
            g.rotate(a + Math.PI / 2);
            g.setStroke(new BasicStroke(0.8f));
            for (int k = 0; k < 3; k++) {
                if (r.nextBoolean()) g.draw(new Line2D.Double(-2.5 + k * 2, -2, -2.5 + k * 2, 2));
                else g.draw(new Line2D.Double(-2.5 + k * 2, -2, -0.5 + k * 2, r.nextBoolean() ? 2 : 0));
            }
            g.setTransform(t);
        }
        // inner hexagram (six stones)
        g.setStroke(new BasicStroke(1.0f));
        for (int tri = 0; tri < 2; tri++) {
            Path2D p = new Path2D.Double();
            for (int k = 0; k < 3; k++) {
                double a = -Math.PI / 2 + tri * Math.PI / 3 + k * Math.PI * 2 / 3;
                if (k == 0) p.moveTo(c + Math.cos(a) * 34, c + Math.sin(a) * 34); else p.lineTo(c + Math.cos(a) * 34, c + Math.sin(a) * 34);
            }
            p.closePath();
            g.draw(p);
        }
        for (int k = 0; k < 6; k++) {
            double a = -Math.PI / 2 + k * Math.PI / 3;
            g.fill(new Ellipse2D.Double(c + Math.cos(a) * 43 - 3, c + Math.sin(a) * 43 - 3, 6, 6));
        }
        g.dispose();
        BufferedImage out = downscale(img, S);
        // premultiply into rgb for additive blending
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                int a = (out.getRGB(x, y) >>> 24);
                out.setRGB(x, y, (a << 24) | (a << 16) | (a << 8) | a);
            }
        return out;
    }

    static BufferedImage mote(int S) {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                double d = Math.hypot((x + 0.5) / S - 0.5, (y + 0.5) / S - 0.5) * 2;
                double k = Math.pow(Math.max(0, 1 - d), 1.6);
                if (d < 0.3) k = 1;
                int a = (int) (255 * k);
                img.setRGB(x, y, (a << 24) | 0xFFFFFF);
            }
        return img;
    }

    static BufferedImage spark(int S) {
        BufferedImage f = flare(S);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                int a = f.getRGB(x, y) & 255;
                f.setRGB(x, y, (a << 24) | 0xFFFFFF);
            }
        return f;
    }

    static BufferedImage downscale(BufferedImage src, int S) {
        BufferedImage out = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        int k = src.getWidth() / S;
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                long a = 0, r = 0, g = 0, b = 0;
                for (int j = 0; j < k; j++)
                    for (int i = 0; i < k; i++) {
                        int c = src.getRGB(x * k + i, y * k + j);
                        int ca = c >>> 24;
                        a += ca; r += ((c >> 16) & 255) * ca; g += ((c >> 8) & 255) * ca; b += (c & 255) * ca;
                    }
                int n = k * k;
                int A = (int) (a / n);
                int R = a == 0 ? 0 : (int) (r / a), G = a == 0 ? 0 : (int) (g / a), B = a == 0 ? 0 : (int) (b / a);
                out.setRGB(x, y, (A << 24) | (R << 16) | (G << 8) | B);
            }
        return out;
    }

    // =====================================================================================================
    // tree icons (32x32, drawn at 4x and downsampled)
    // =====================================================================================================

    static int treeIcons() throws IOException {
        int count = 0;
        Pattern node = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"original_type\"\\s*:\\s*\"[^\"]*\"\\s*,\\s*\"title\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"description\"\\s*:\\s*\"[^\"]*\"\\s*,\\s*\"icon\"\\s*:\\s*(null|\"[^\"]*\")");
        for (StoneDef s : STONES) {
            List<Path> files;
            try (var walk = Files.walk(DATA)) { files = walk.filter(p -> p.getFileName().toString().equals(s.key + ".json")).sorted().toList(); }
            for (Path file : files) {
                Matcher m = node.matcher(Files.readString(file));
                while (m.find()) {
                    String id = m.group(1), title = m.group(2), icon = m.group(3).replace("\"", "");
                    if (!placeholder(icon)) continue;
                    String glyph = glyphFor((id + " " + title).toLowerCase(Locale.ROOT));
                    BufferedImage img = pixelIcon(glyph, s.rgb, id, 2);
                    png(img, "textures/icon/tree/" + s.key + "/" + sanitize(id) + ".png");
                    count++;
                    if (PREVIEW != null && count % 9 == 1) ImageIO.write(scaleUp(img, 4), "png", PREVIEW.resolve("icon_" + s.key + "_" + sanitize(id) + ".png").toFile());
                }
            }
        }
        // Palladium isn't in the pack: the original trees still point at palladium:textures/icon/*.png -> ship them ourselves
        Pattern pal = Pattern.compile("\"palladium:textures/icon/([a-z0-9_]+)\\.png\"");
        Set<String> names = new TreeSet<>();
        try (var walk = Files.walk(DATA)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) { Matcher m = pal.matcher(Files.readString(p)); while (m.find()) names.add(m.group(1)); }
        }
        for (String name : names) {
            BufferedImage img = pixelIcon(glyphFor(name), 0xE22B3B, name, 1);
            Path out = ROOT.getParent().resolve("palladium/textures/icon/" + name + ".png");
            Files.createDirectories(out.getParent());
            ImageIO.write(img, "png", out.toFile());
            count++;
        }
        return count;
    }

    /**
     * Pixel-art icon: the glyph is rasterised at 16x16 (alpha threshold), shaded top-light/bottom-dark from the stone
     * palette with a dark 1px outline and a highlight row, then scaled up by {@code upscale} with nearest neighbour.
     */
    static BufferedImage pixelIcon(String glyph, int color, String seed, int upscale) {
        int S = 128, N = 16;
        BufferedImage mask = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = mask.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.translate(10, 10);
        g.scale(108.0 / 128, 108.0 / 128);
        drawGlyph(g, glyph, S, new Random(seed.hashCode()));
        g.dispose();
        BufferedImage small = downscale(mask, N);
        boolean[] on = new boolean[N * N];
        for (int i = 0; i < N * N; i++) on[i] = (small.getRGB(i % N, i / N) >>> 24) > 100;
        int[] P = gemPalette(color);
        BufferedImage out = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                int i = y * N + x;
                if (on[i]) {
                    boolean topEdge = y == 0 || !on[i - N], botEdge = y == N - 1 || !on[i + N];
                    int c = y < 5 ? P[4] : y < 11 ? P[3] : P[2];
                    if (topEdge) c = P[5];
                    else if (botEdge) c = P[1];
                    out.setRGB(x, y, 0xFF000000 | c);
                } else {
                    boolean near = false;
                    for (int dy = -1; dy <= 1 && !near; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx, yy = y + dy;
                            if ((dx != 0 || dy != 0) && xx >= 0 && yy >= 0 && xx < N && yy < N && on[yy * N + xx]) { near = true; break; }
                        }
                    if (near) out.setRGB(x, y, 0xFF000000 | P[0]);
                }
            }
        if (upscale <= 1) return out;
        BufferedImage big = new BufferedImage(N * upscale, N * upscale, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < N * upscale; y++) for (int x = 0; x < N * upscale; x++) big.setRGB(x, y, out.getRGB(x / upscale, y / upscale));
        return big;
    }

    static boolean placeholder(String icon) {
        return icon.equals("null") || icon.isEmpty() || icon.endsWith("icon/nothing.png") || icon.equals("minecraft:command_block")
                || icon.equals("minecraft:barrier") || icon.startsWith("palladium:") || icon.matches("infinity:[a-z]+_stone");
    }

    static String sanitize(String id) { return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"); }

    static final String[][] GLYPHS = {
            {"toggle", "toggle", "texture", "mode", "switch", "setting", "visual"},
            {"shield", "shield", "barrier", "field", "guard", "protect", "bastion", "defen", "armor", "armour", "reflect", "resist", "sanctuary", "immun"},
            {"portal", "teleport", "portal", "warp", "relocat", "exchange", "banish", "dimension", "travel", "recall", "home"},
            {"hourglass", "time", "clock", "rewind", "loop", "echo", "slow", "stop", "freeze", "haste", "age", "pause", "season", "temporal"},
            {"wisp", "soul", "spirit", "reaper", "life", "death", "sever", "essence", "siphon", "drain", "revive", "undying"},
            {"eye", "mind", "psi", "psy", "control", "telepath", "synap", "thought", "illusion", "dominion", "terror", "fear", "vision", "sense", "read"},
            {"burst", "strength", "punch", "power", "empower", "impower", "kinetic", "charge", "titan", "smash", "rend", "tectonic", "explos", "radiation"},
            {"wing", "fly", "flight", "levit", "wing", "jump", "step", "float", "glide"},
            {"cross", "heal", "regen", "health", "restore", "immortal", "totem", "cure", "saturat", "breath"},
            {"arrows", "size", "grow", "shrink", "scale", "giant", "tiny"},
            {"ghost", "invis", "phase", "harmless", "intang", "ghost", "stealth"},
            {"spiral", "gravity", "black", "singular", "horizon", "orbit", "pull", "vortex", "void"},
            {"bolt", "lightning", "thunder", "storm", "weather", "rain", "shock"},
            {"comet", "meteor", "fire", "burn", "flame", "sun", "heat", "nova"},
            {"beam", "blast", "beam", "laser", "ray", "shot", "projectile", "bolt", "missile"},
            {"legion", "summon", "clone", "legion", "army", "construct", "arsenal", "minion", "copy"},
            {"shards", "reality", "transmut", "matter", "aether", "lattice", "shatter", "convert", "will"},
            {"star", "space", "star", "celestial", "cosmic", "galax", "universe"},
    };

    static String glyphFor(String text) {
        for (String[] g : GLYPHS) for (int i = 1; i < g.length; i++) if (text.contains(g[i])) return g[0];
        return "sigil";
    }

    static BufferedImage icon(String glyph, int color, String seed) {
        int S = 128;
        BufferedImage mask = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = mask.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        drawGlyph(g, glyph, S, new Random(seed.hashCode()));
        g.dispose();
        // compose: tinted glow (blurred mask) + gradient body + white-hot core
        float[] alpha = new float[S * S];
        for (int i = 0; i < S * S; i++) alpha[i] = (mask.getRGB(i % S, i / S) >>> 24) / 255f;
        float[] glow = blur(alpha, S, 10, 3);
        float[] core = erode(alpha, S, 3);
        BufferedImage big = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        int light = shade(color, 1.45), dark = shade(color, 0.85);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                int i = y * S + x;
                float gA = Math.min(1, glow[i] * 1.35f) * 0.75f, bA = alpha[i], cA = core[i];
                int body = mix(light, dark, y / (double) S);
                int c = color; float a = gA;
                // layer body over glow
                c = mixArgb(c, a, body, bA);
                a = a + bA * (1 - a);
                c = mixArgb(c, a, shade(color, 1.85), cA * 0.85f);
                img(big, x, y, c, a);
            }
        return downscale(big, 32);
    }

    static int mixArgb(int c0, float a0, int c1, float a1) {
        if (a1 <= 0) return c0;
        double t = a1 / Math.max(1e-4, a1 + a0 * (1 - a1));
        return mix(c0, c1, t);
    }

    static void img(BufferedImage b, int x, int y, int c, float a) { b.setRGB(x, y, argb(Math.round(a * 255), c)); }

    static float[] blur(float[] a, int S, int r, int passes) {
        float[] cur = a.clone(), tmp = new float[a.length];
        for (int p = 0; p < passes; p++) {
            for (int y = 0; y < S; y++) {
                float acc = 0;
                for (int x = -r; x <= r; x++) acc += cur[y * S + Math.max(0, Math.min(S - 1, x))];
                for (int x = 0; x < S; x++) {
                    tmp[y * S + x] = acc / (2 * r + 1);
                    acc += cur[y * S + Math.min(S - 1, x + r + 1)] - cur[y * S + Math.max(0, x - r)];
                }
            }
            for (int x = 0; x < S; x++) {
                float acc = 0;
                for (int y = -r; y <= r; y++) acc += tmp[Math.max(0, Math.min(S - 1, y)) * S + x];
                for (int y = 0; y < S; y++) {
                    cur[y * S + x] = acc / (2 * r + 1);
                    acc += tmp[Math.min(S - 1, y + r + 1) * S + x] - tmp[Math.max(0, y - r) * S + x];
                }
            }
        }
        return cur;
    }

    static float[] erode(float[] a, int S, int r) {
        float[] out = new float[a.length];
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                float m = 1;
                for (int j = -r; j <= r; j += r) for (int i = -r; i <= r; i += r) m = Math.min(m, a[Math.max(0, Math.min(S - 1, y + j)) * S + Math.max(0, Math.min(S - 1, x + i))]);
                out[y * S + x] = m;
            }
        return out;
    }

    static void drawGlyph(Graphics2D g, String glyph, int S, Random r) {
        double c = S / 2.0;
        switch (glyph) {
            case "toggle" -> {
                g.draw(new RoundRectangle2D.Double(24, 44, 80, 40, 40, 40));
                g.fill(new Ellipse2D.Double(66, 50, 28, 28));
            }
            case "shield" -> {
                Path2D p = new Path2D.Double();
                p.moveTo(c, 18); p.lineTo(102, 34); p.quadTo(102, 92, c, 112); p.quadTo(26, 92, 26, 34); p.closePath();
                g.draw(p);
                Path2D q = new Path2D.Double();
                q.moveTo(c, 36); q.lineTo(84, 46); q.quadTo(84, 82, c, 96); q.closePath();
                g.fill(q);
            }
            case "portal" -> {
                for (int i = 0; i < 3; i++) g.draw(new Ellipse2D.Double(22 + i * 14, 22 + i * 14, 84 - i * 28, 84 - i * 28));
                g.fill(new Ellipse2D.Double(c - 8, c - 8, 16, 16));
                g.draw(new Arc2D.Double(10, 10, 108, 108, 20, 60, Arc2D.OPEN));
                g.draw(new Arc2D.Double(10, 10, 108, 108, 200, 60, Arc2D.OPEN));
            }
            case "hourglass" -> {
                g.draw(new Line2D.Double(32, 18, 96, 18));
                g.draw(new Line2D.Double(32, 110, 96, 110));
                Path2D p = new Path2D.Double();
                p.moveTo(38, 20); p.quadTo(38, 52, c, 64); p.quadTo(90, 52, 90, 20);
                p.moveTo(38, 108); p.quadTo(38, 76, c, 64); p.quadTo(90, 76, 90, 108);
                g.draw(p);
                Path2D sand = new Path2D.Double();
                sand.moveTo(46, 106); sand.quadTo(c, 80, 82, 106); sand.closePath();
                g.fill(sand);
            }
            case "wisp" -> {
                Path2D p = new Path2D.Double();
                p.moveTo(c, 14); p.curveTo(92, 44, 100, 70, c + 18, 100); p.curveTo(c, 116, 36, 108, 34, 84);
                p.curveTo(32, 64, 52, 56, 56, 38); p.curveTo(62, 54, 70, 50, c, 14);
                g.draw(p);
                g.fill(new Ellipse2D.Double(c - 12, 74, 24, 24));
            }
            case "eye" -> {
                Path2D p = new Path2D.Double();
                p.moveTo(14, c); p.quadTo(c, 18, 114, c); p.quadTo(c, 110, 14, c); p.closePath();
                g.draw(p);
                g.fill(new Ellipse2D.Double(c - 16, c - 16, 32, 32));
                g.draw(new Line2D.Double(c, 6, c, 22));
            }
            case "burst" -> {
                Path2D p = new Path2D.Double();
                for (int i = 0; i < 16; i++) {
                    double a = i * Math.PI / 8 - Math.PI / 2, rr = i % 2 == 0 ? 54 : 24;
                    if (i == 0) p.moveTo(c + Math.cos(a) * rr, c + Math.sin(a) * rr); else p.lineTo(c + Math.cos(a) * rr, c + Math.sin(a) * rr);
                }
                p.closePath();
                g.draw(p);
                g.fill(new Ellipse2D.Double(c - 12, c - 12, 24, 24));
            }
            case "wing" -> {
                for (int i = 0; i < 4; i++) {
                    g.draw(new Arc2D.Double(18 + i * 8, 26 + i * 12, 92 - i * 16, 70, 20, 140, Arc2D.OPEN));
                }
                g.draw(new Line2D.Double(c, 40, c, 112));
                Path2D a = new Path2D.Double();
                a.moveTo(c - 16, 54); a.lineTo(c, 34); a.lineTo(c + 16, 54);
                g.draw(a);
            }
            case "cross" -> {
                g.fill(new RoundRectangle2D.Double(c - 13, 22, 26, 84, 10, 10));
                g.fill(new RoundRectangle2D.Double(22, c - 13, 84, 26, 10, 10));
                g.draw(new Ellipse2D.Double(10, 10, 108, 108));
            }
            case "arrows" -> {
                double[][] d = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
                for (double[] k : d) {
                    g.draw(new Line2D.Double(c + k[0] * 12, c + k[1] * 12, c + k[0] * 46, c + k[1] * 46));
                    g.draw(new Line2D.Double(c + k[0] * 46, c + k[1] * 46, c + k[0] * 26, c + k[1] * 46));
                    g.draw(new Line2D.Double(c + k[0] * 46, c + k[1] * 46, c + k[0] * 46, c + k[1] * 26));
                }
            }
            case "ghost" -> {
                Path2D p = new Path2D.Double();
                p.moveTo(30, 108); p.lineTo(30, 56); p.curveTo(30, 14, 98, 14, 98, 56); p.lineTo(98, 108);
                p.lineTo(86, 96); p.lineTo(74, 108); p.lineTo(64, 96); p.lineTo(54, 108); p.lineTo(42, 96); p.closePath();
                g.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{14, 10}, 0));
                g.draw(p);
                g.fill(new Ellipse2D.Double(46, 48, 12, 16));
                g.fill(new Ellipse2D.Double(70, 48, 12, 16));
            }
            case "spiral" -> {
                Path2D p = new Path2D.Double();
                for (int i = 0; i <= 120; i++) {
                    double a = i * 0.16, rr = 4 + i * 0.44;
                    if (i == 0) p.moveTo(c + Math.cos(a) * rr, c + Math.sin(a) * rr); else p.lineTo(c + Math.cos(a) * rr, c + Math.sin(a) * rr);
                }
                g.draw(p);
                g.fill(new Ellipse2D.Double(c - 10, c - 10, 20, 20));
            }
            case "bolt" -> {
                Path2D p = new Path2D.Double();
                p.moveTo(74, 10); p.lineTo(36, 70); p.lineTo(62, 70); p.lineTo(50, 118); p.lineTo(94, 52); p.lineTo(66, 52); p.closePath();
                g.fill(p);
            }
            case "comet" -> {
                g.fill(new Ellipse2D.Double(70, 18, 40, 40));
                for (int i = 0; i < 3; i++) g.draw(new Line2D.Double(74 - i * 4, 52 + i * 8, 18 + i * 6, 108 - i * 2));
            }
            case "beam" -> {
                g.fill(new Ellipse2D.Double(14, 86, 28, 28));
                g.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(28, 100, 104, 24));
                g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(84, 18, 114, 48));
            }
            case "legion" -> {
                for (int i = -1; i <= 1; i++) {
                    double x = c + i * 32, y = i == 0 ? 0 : 12;
                    g.fill(new Ellipse2D.Double(x - 10, 28 + y, 20, 20));
                    g.draw(new Arc2D.Double(x - 18, 54 + y, 36, 56, 0, 180, Arc2D.OPEN));
                }
            }
            case "shards" -> {
                double[][] sh = {{c, 12, 22, 60}, {34, 52, 14, 40}, {94, 48, 14, 44}, {c - 4, 84, 12, 34}};
                for (double[] s : sh) {
                    Path2D p = new Path2D.Double();
                    p.moveTo(s[0], s[1]); p.lineTo(s[0] + s[2] / 2, s[1] + s[3] * 0.6); p.lineTo(s[0], s[1] + s[3]); p.lineTo(s[0] - s[2] / 2, s[1] + s[3] * 0.6); p.closePath();
                    g.fill(p);
                }
            }
            case "star" -> {
                Path2D p = new Path2D.Double();
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4 - Math.PI / 2, rr = i % 2 == 0 ? 56 : 14;
                    if (i == 0) p.moveTo(c + Math.cos(a) * rr, c + Math.sin(a) * rr); else p.lineTo(c + Math.cos(a) * rr, c + Math.sin(a) * rr);
                }
                p.closePath();
                g.fill(p);
                g.draw(new Ellipse2D.Double(24, 24, 80, 80));
            }
            default -> { // seeded sigil: circle + 3..5 chords + a centre dot
                g.draw(new Ellipse2D.Double(18, 18, 92, 92));
                int n = 3 + r.nextInt(3);
                double[] ang = new double[n];
                for (int i = 0; i < n; i++) ang[i] = r.nextDouble() * Math.PI * 2;
                for (int i = 0; i < n; i++) {
                    double a0 = ang[i], a1 = ang[(i + 1 + r.nextInt(Math.max(1, n - 1))) % n];
                    g.draw(new Line2D.Double(c + Math.cos(a0) * 46, c + Math.sin(a0) * 46, c + Math.cos(a1) * 46, c + Math.sin(a1) * 46));
                    g.fill(new Ellipse2D.Double(c + Math.cos(a0) * 46 - 7, c + Math.sin(a0) * 46 - 7, 14, 14));
                }
                g.fill(new Ellipse2D.Double(c - 9, c - 9, 18, 18));
            }
        }
    }

    // =====================================================================================================
    // io + preview
    // =====================================================================================================

    static void png(BufferedImage img, String rel) throws IOException {
        Path p = ROOT.resolve(rel);
        Files.createDirectories(p.getParent());
        ImageIO.write(img, "png", p.toFile());
    }

    static void write(String rel, String content) throws IOException {
        Path p = ROOT.resolve(rel);
        // models refined by hand in Blockbench are kept (their credit says so)
        if (Files.exists(p) && Files.readString(p, StandardCharsets.UTF_8).contains("Blockbench")) return;
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }

    static BufferedImage scaleUp(BufferedImage src, int k) {
        BufferedImage out = new BufferedImage(src.getWidth() * k, src.getHeight() * k, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(12, 13, 20));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, out.getWidth(), out.getHeight(), null);
        g.dispose();
        return out;
    }

    /** Tiny software renderer: element rotation, then view rotY/rotX, orthographic, painter's algorithm on sub-quads. */
    static void preview(List<El> els, Map<String, BufferedImage> tex, double rx, double ry, String name) throws IOException {
        preview(els, tex, rx, ry, name, 20, 8.5);
    }

    static void preview(List<El> els, Map<String, BufferedImage> tex, double rx, double ry, String name, double scale, double cy) throws IOException {
        if (PREVIEW == null) return;
        int W = 480;
        record Q(double depth, double[] xs, double[] ys, int color) {}
        List<Q> qs = new ArrayList<>();
        String[] dirs = {"north", "east", "south", "west", "up", "down"};
        for (El e : els) {
            double x0 = e.from[0], y0 = e.from[1], z0 = e.from[2], x1 = e.to[0], y1 = e.to[1], z1 = e.to[2];
            double[][][] faces = {
                    {{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}},
                    {{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}},
                    {{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}},
                    {{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}},
                    {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}},
                    {{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}}};
            double[] shadeF = {0.8, 0.6, 0.8, 0.6, 1.0, 0.5};
            BufferedImage t = tex.get(e.tex);
            for (int f = 0; f < 6; f++) {
                double[][] cs = new double[4][];
                for (int k = 0; k < 4; k++) cs[k] = view(elemRot(e, faces[f][k]), rx, ry);
                // back-face cull (viewer at +Z)
                double nx = (cs[1][1] - cs[0][1]) * (cs[3][2] - cs[0][2]) - (cs[1][2] - cs[0][2]) * (cs[3][1] - cs[0][1]);
                double ux = cs[1][0] - cs[0][0], uy = cs[1][1] - cs[0][1], vx = cs[3][0] - cs[0][0], vy = cs[3][1] - cs[0][1];
                double nz = ux * vy - uy * vx;
                if (nz > 0) continue; // screen y is flipped later; this keeps front faces
                double[] uv = e.uvs != null ? e.uvs[f] : new double[]{0, 0, 16, 16};
                int N = 6;
                for (int i = 0; i < N; i++)
                    for (int j = 0; j < N; j++) {
                        double s0 = i / (double) N, s1 = (i + 1) / (double) N, t0 = j / (double) N, t1 = (j + 1) / (double) N;
                        double[] a = bil(cs, s0, t0), b = bil(cs, s1, t0), cc = bil(cs, s1, t1), d = bil(cs, s0, t1);
                        double su = uv[0] + (uv[2] - uv[0]) * (s0 + s1) / 2, sv = uv[1] + (uv[3] - uv[1]) * (t0 + t1) / 2;
                        int col = 0xFF00FF;
                        int alpha = 255;
                        if (t != null) {
                            int px = (int) Math.min(t.getWidth() - 1, su / 16 * t.getWidth()), py = (int) Math.min(t.getHeight() - 1, sv / 16 * t.getHeight());
                            int argb = t.getRGB(px, py);
                            col = argb & 0xFFFFFF; alpha = argb >>> 24;
                        }
                        if (alpha < 20) continue;
                        col = shade(col, e.shade ? shadeF[f] : 1.0);
                        double depth = (a[2] + b[2] + cc[2] + d[2]) / 4;
                        qs.add(new Q(depth, new double[]{a[0], b[0], cc[0], d[0]}, new double[]{a[1], b[1], cc[1], d[1]}, argb(alpha, col)));
                    }
            }
        }
        qs.sort(Comparator.comparingDouble(Q::depth));
        BufferedImage img = new BufferedImage(W, W, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(14, 15, 22));
        g.fillRect(0, 0, W, W);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (Q q : qs) {
            Path2D p = new Path2D.Double();
            for (int k = 0; k < 4; k++) {
                double sx = W / 2.0 + (q.xs[k] - 8) * scale, sy = W / 2.0 - (q.ys[k] - cy) * scale;
                if (k == 0) p.moveTo(sx, sy); else p.lineTo(sx, sy);
            }
            p.closePath();
            g.setColor(new Color(q.color, true));
            g.fill(p);
        }
        g.dispose();
        ImageIO.write(img, "png", PREVIEW.resolve(name + ".png").toFile());
    }

    static double[] bil(double[][] c, double s, double t) {
        double[] top = lerp(c[0], c[1], s), bot = lerp(c[3], c[2], s);
        return lerp(top, bot, t);
    }

    static double[] lerp(double[] a, double[] b, double t) { return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t}; }

    static double[] elemRot(El e, double[] p) {
        if (e.axis == null || e.angle == 0) return p.clone();
        double a = Math.toRadians(e.angle), c = Math.cos(a), s = Math.sin(a);
        double x = p[0] - e.origin[0], y = p[1] - e.origin[1], z = p[2] - e.origin[2];
        double nx = x, ny = y, nz = z;
        switch (e.axis) {
            case "x" -> { ny = y * c - z * s; nz = y * s + z * c; }
            case "y" -> { nx = x * c + z * s; nz = -x * s + z * c; }
            case "z" -> { nx = x * c - y * s; ny = x * s + y * c; }
        }
        return new double[]{nx + e.origin[0], ny + e.origin[1], nz + e.origin[2]};
    }

    static double[] view(double[] p, double rxDeg, double ryDeg) {
        double x = p[0] - 8, y = p[1] - 8, z = p[2] - 8;
        double a = Math.toRadians(ryDeg), c = Math.cos(a), s = Math.sin(a);
        double x1 = x * c + z * s, z1 = -x * s + z * c;
        double b = Math.toRadians(rxDeg), cb = Math.cos(b), sb = Math.sin(b);
        double y2 = y * cb - z1 * sb, z2 = y * sb + z1 * cb;
        return new double[]{x1 + 8, y2 + 8, z2 + 8};
    }
}
