package com.infinitystones.tensura.skill.ability.extra.fear.scene;

/** Small deterministic value noise used by the scene generators. */
public final class Noise {
    public static float hash(int x, int z, int seed) {
        int h = x * 374761393 + z * 668265263 + seed * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    public static float hash3(int x, int y, int z, int seed) {
        return hash(x * 31 + y * 7919, z, seed + y);
    }

    public static float value(float x, float z, int seed) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        float fx = x - x0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        float a = hash(x0, z0, seed), b = hash(x0 + 1, z0, seed), c = hash(x0, z0 + 1, seed), d = hash(x0 + 1, z0 + 1, seed);
        float top = a + (b - a) * fx, bot = c + (d - c) * fx;
        return top + (bot - top) * fz;
    }

    public static float fbm(float x, float z, int seed, int oct) {
        float sum = 0, amp = 1, norm = 0;
        for (int i = 0; i < oct; i++) {
            sum += value(x, z, seed + i * 17) * amp;
            norm += amp;
            amp *= 0.5f;
            x *= 2;
            z *= 2;
        }
        return sum / norm;
    }

    private Noise() {}
}
