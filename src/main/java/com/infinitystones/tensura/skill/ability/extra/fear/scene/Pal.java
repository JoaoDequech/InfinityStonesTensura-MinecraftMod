package com.infinitystones.tensura.skill.ability.extra.fear.scene;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/** Weighted block palette: {@code Pal.of(Blocks.A, 5, Blocks.B, 2)} (blocks or states, each followed by its weight). */
public final class Pal {
    private final BlockState[] s;
    private final int[] w;
    private final int total;

    private Pal(BlockState[] s, int[] w) {
        this.s = s;
        this.w = w;
        int t = 0;
        for (int x : w) t += x;
        total = t;
    }

    public static Pal of(Object... a) {
        int n = a.length / 2;
        BlockState[] s = new BlockState[n];
        int[] w = new int[n];
        for (int i = 0; i < n; i++) {
            s[i] = conv(a[i * 2]);
            w[i] = (Integer) a[i * 2 + 1];
        }
        return new Pal(s, w);
    }

    public static Pal one(Object o) {
        return new Pal(new BlockState[]{conv(o)}, new int[]{1});
    }

    private static BlockState conv(Object o) {
        return o instanceof Block b ? b.defaultBlockState() : (BlockState) o;
    }

    public BlockState pick(Random r) {
        if (s.length == 1) return s[0];
        int v = r.nextInt(total);
        for (int i = 0; i < s.length; i++) {
            v -= w[i];
            if (v < 0) return s[i];
        }
        return s[0];
    }

    public BlockState first() { return s[0]; }
}
