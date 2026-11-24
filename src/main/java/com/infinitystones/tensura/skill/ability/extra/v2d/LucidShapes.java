package com.infinitystones.tensura.skill.ability.extra.v2d;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Lucid Dream brushes. Pure geometry shared by the server (what is really placed) and the client (the ghost preview), so
 * both always agree. Everything is relative to the free cell the player aims at.
 */
public final class LucidShapes {
    public static final String[] NAMES = {"single", "line", "wall", "floor", "stairs", "bridge", "dome"};
    public static final String[] ICONS = {"minecraft:amethyst_block", "minecraft:end_rod", "minecraft:glass", "minecraft:smooth_stone_slab",
            "minecraft:quartz_stairs", "minecraft:rail", "minecraft:glass_bottle"};
    public static final int MAX_SIZE = 6, LINE_LENGTH = 96, BRIDGE_LENGTH = 64, MAX_PLACE = 400;

    /**
     * @param brush index into {@link #NAMES}; size 1..6 (scroll)
     * @param target the aimed free cell; fwd the player's horizontal facing
     * @param from the last placement (line start), or null; feet the player's feet cell (bridge start, dome door side)
     */
    public static List<BlockPos> shape(int brush, int size, BlockPos target, Direction fwd, @Nullable BlockPos from, BlockPos feet) {
        int sz = Mth.clamp(size, 1, MAX_SIZE);
        Set<BlockPos> out = new LinkedHashSet<>();
        Direction right = fwd.getClockWise();
        switch (brush) {
            case 1 -> line(out, from != null ? from : feet.below(), target, 1 + (sz - 1) / 3, LINE_LENGTH, false);
            case 2 -> {
                int h = sz + 2;
                for (int w = -sz; w <= sz; w++) for (int y = 0; y < h; y++) out.add(target.relative(right, w).above(y));
            }
            case 3 -> { for (int dx = -sz; dx <= sz; dx++) for (int dz = -sz; dz <= sz; dz++) out.add(target.offset(dx, 0, dz)); }
            case 4 -> {
                int half = (sz + 1) / 2, steps = 4 + 2 * sz;
                for (int i = 0; i < steps; i++) for (int w = -half; w <= half; w++) out.add(target.relative(fwd, i).above(i).relative(right, w));
            }
            case 5 -> {
                BlockPos a = feet.below();
                line(out, a, new BlockPos(target.getX(), a.getY(), target.getZ()), sz >= 5 ? 5 : sz >= 2 ? 3 : 1, BRIDGE_LENGTH, true);
            }
            case 6 -> dome(out, target, 2 + sz, fwd.getOpposite());
            default -> cube(out, target, sz, false);
        }
        List<BlockPos> list = new ArrayList<>(out);
        return list.size() > MAX_PLACE ? new ArrayList<>(list.subList(0, MAX_PLACE)) : list;
    }

    private static void cube(Set<BlockPos> out, BlockPos c, int side, boolean flat) {
        int lo = -(side - 1) / 2;
        for (int x = 0; x < side; x++) for (int y = 0; y < (flat ? 1 : side); y++) for (int z = 0; z < side; z++)
            out.add(c.offset(lo + x, flat ? 0 : lo + y, lo + z));
    }

    private static void line(Set<BlockPos> out, BlockPos a, BlockPos b, int thick, int maxLen, boolean flat) {
        int dx = b.getX() - a.getX(), dy = b.getY() - a.getY(), dz = b.getZ() - a.getZ();
        int steps = Math.max(1, Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))));
        int n = Math.min(steps, maxLen);
        for (int i = 0; i <= n; i++) {
            double t = (double) i / steps;
            cube(out, new BlockPos(Mth.floor(a.getX() + dx * t + 0.5), Mth.floor(a.getY() + dy * t + 0.5), Mth.floor(a.getZ() + dz * t + 0.5)), thick, flat);
        }
    }

    /** Hemisphere shell around c with a 1x2 doorway on the side of door. */
    private static void dome(Set<BlockPos> out, BlockPos c, int r, Direction door) {
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = 0; dy <= r; dy++) {
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > r + 0.5 || d <= r - 0.9) continue;
            int along = dx * door.getStepX() + dz * door.getStepZ();
            int across = dx * door.getClockWise().getStepX() + dz * door.getClockWise().getStepZ();
            if (along > 0 && across == 0 && dy <= 1) continue;
            out.add(c.offset(dx, dy, dz));
        }
    }

    private LucidShapes() {}
}
