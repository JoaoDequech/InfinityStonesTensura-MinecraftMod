package com.infinitystones.tensura.skill.ability.extra.fear.mob.client;

import net.minecraft.util.Mth;

import java.util.Arrays;

/** A keyframed clip: per bone, additive rotation (degrees) and position (pixels, y up) deltas with per-key easing. */
final class Clip {
    final String name;
    final float length, period, blend;
    final boolean loop;
    final int[] bone;
    final float[][] times;
    final float[][] vals;   // 6 per key: rx ry rz px py pz
    final byte[][] eases;

    private Clip(String name, float length, boolean loop, float period, float blend, int[] bone, float[][] times, float[][] vals, byte[][] eases) {
        this.name = name; this.length = length; this.loop = loop; this.period = period; this.blend = blend;
        this.bone = bone; this.times = times; this.vals = vals; this.eases = eases;
    }

    static float ease(int id, float x) {
        x = Mth.clamp(x, 0F, 1F);
        final float c1 = 1.70158F;
        switch (id) {
            case 0: return x;
            case 1: return x * x;
            case 2: return 1 - (1 - x) * (1 - x);
            case 3: return x < .5F ? 2 * x * x : 1 - (float) Math.pow(-2 * x + 2, 2) / 2;
            case 4: return x * x * x;
            case 5: return 1 - (float) Math.pow(1 - x, 3);
            case 6: return x < .5F ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
            case 7: { float c3 = c1 + 1; float y = x - 1; return 1 + c3 * y * y * y + c1 * y * y; }
            case 8: { float c3 = c1 + 1; return c3 * x * x * x - c1 * x * x; }
            case 9: {
                float c2 = c1 * 1.525F;
                return x < .5F ? ((float) Math.pow(2 * x, 2) * ((c2 + 1) * 2 * x - c2)) / 2
                        : ((float) Math.pow(2 * x - 2, 2) * ((c2 + 1) * (x * 2 - 2) + c2) + 2) / 2;
            }
            case 10: return x == 0 || x == 1 ? x : (float) (Math.pow(2, -10 * x) * Math.sin((x * 10 - .75) * (2 * Math.PI / 3)) + 1);
            case 11: {
                float n1 = 7.5625F, d1 = 2.75F;
                if (x < 1 / d1) return n1 * x * x;
                if (x < 2 / d1) { x -= 1.5F / d1; return n1 * x * x + .75F; }
                if (x < 2.5F / d1) { x -= 2.25F / d1; return n1 * x * x + .9375F; }
                x -= 2.625F / d1; return n1 * x * x + .984375F;
            }
            case 12: return x >= 1 ? 1 : 0;
            case 13: return 1 - (float) Math.pow(2, -10 * x);
            case 14: return x == 0 ? 0 : (float) Math.pow(2, 10 * x - 10);
            default: return x;
        }
    }

    /** Format: header line "name;length;loop;period;blend", then "bone;t,rx,ry,rz,px,py,pz,ease;..." per bone. */
    static Clip parse(String s, String[] bonePaths) {
        String[] lines = s.split("\n");
        String[] h = lines[0].split(";");
        int n = lines.length - 1;
        int[] bone = new int[n];
        float[][] times = new float[n][];
        float[][] vals = new float[n][];
        byte[][] eases = new byte[n][];
        for (int i = 0; i < n; i++) {
            String[] parts = lines[i + 1].split(";");
            bone[i] = -1;
            for (int b = 0; b < bonePaths.length; b++) {
                String p = bonePaths[b];
                if (p.equals(parts[0]) || p.endsWith("/" + parts[0])) { bone[i] = b; break; }
            }
            int k = parts.length - 1;
            times[i] = new float[k]; vals[i] = new float[k * 6]; eases[i] = new byte[k];
            for (int j = 0; j < k; j++) {
                String[] f = parts[j + 1].split(",");
                times[i][j] = Float.parseFloat(f[0]);
                for (int q = 0; q < 6; q++) vals[i][j * 6 + q] = Float.parseFloat(f[1 + q]);
                eases[i][j] = Byte.parseByte(f[7]);
            }
        }
        return new Clip(h[0], Float.parseFloat(h[1]), h[2].equals("1"), Float.parseFloat(h[3]), Float.parseFloat(h[4]), bone, times, vals, eases);
    }

    /** Adds weight * clip(time) into pose (6 floats per bone). */
    void sample(float time, float[] pose, float w) {
        if (w <= 0F) return;
        float t2 = loop && length > 0 ? ((time % length) + length) % length : Math.min(Math.max(time, 0F), length);
        for (int i = 0; i < bone.length; i++) {
            int b = bone[i];
            if (b < 0) continue;
            float[] ts = times[i];
            float[] v = vals[i];
            int k = ts.length;
            int o = b * 6;
            if (t2 <= ts[0]) { for (int q = 0; q < 6; q++) pose[o + q] += v[q] * w; continue; }
            boolean done = false;
            for (int j = 1; j < k; j++) {
                if (t2 <= ts[j]) {
                    float f = ease(eases[i][j], (t2 - ts[j - 1]) / Math.max(1e-6F, ts[j] - ts[j - 1]));
                    for (int q = 0; q < 6; q++) pose[o + q] += (v[(j - 1) * 6 + q] + (v[j * 6 + q] - v[(j - 1) * 6 + q]) * f) * w;
                    done = true;
                    break;
                }
            }
            if (done) continue;
            int l = (k - 1) * 6;
            if (loop && ts[k - 1] < length) {
                float f = (t2 - ts[k - 1]) / Math.max(1e-6F, length + ts[0] - ts[k - 1]);
                for (int q = 0; q < 6; q++) pose[o + q] += (v[l + q] + (v[q] - v[l + q]) * f) * w;
            } else {
                for (int q = 0; q < 6; q++) pose[o + q] += v[l + q] * w;
            }
        }
    }

    @Override public String toString() { return name + Arrays.toString(bone); }
}
