package com.infinitystones.tensura.skill.ability.extra.fear.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Block buffer of one victim's arena in local coordinates: x and z in [-48, 48), y relative to the origin, where y = 0 is the
 * top block of the ground and y = 1 is where the victim stands. Scenes draw into it; {@link FearScenes} copies it into the
 * world in batches. Writes outside the buffer are ignored.
 */
public final class Arena {
    public static final int N = 96, HALF = 48;
    public static final int FLICK_BAD = 0, FLICK_DYING = 1, FLICK_GUST = 2, FLICK_PULSE = 3;

    public interface Behavior {
        void tick(ServerLevel level, ServerPlayer victim, BlockPos origin, int age);
    }

    /** Teleports the victim when they walk into the box (local coordinates). Relative triggers shift, absolute ones set the position. */
    public static final class Trigger {
        public final int x1, y1, z1, x2, y2, z2;
        public final double tx, ty, tz;
        public final boolean relative;
        public final float yaw;
        public final boolean setYaw;

        Trigger(int x1, int y1, int z1, int x2, int y2, int z2, double tx, double ty, double tz, boolean relative, boolean setYaw, float yaw) {
            this.x1 = Math.min(x1, x2); this.y1 = Math.min(y1, y2); this.z1 = Math.min(z1, z2);
            this.x2 = Math.max(x1, x2); this.y2 = Math.max(y1, y2); this.z2 = Math.max(z1, z2);
            this.tx = tx; this.ty = ty; this.tz = tz; this.relative = relative; this.setYaw = setYaw; this.yaw = yaw;
        }
    }

    public record Flick(int x, int y, int z, int kind, int a, int b) {}

    public final int minY, maxY;
    public BlockState[] data;
    public final Random rnd;
    public final int[] ground = new int[N * N];
    public int spawnX, spawnY = 1, spawnZ;
    public final List<Flick> flicks = new ArrayList<>();
    public final List<Trigger> triggers = new ArrayList<>();
    public final List<Behavior> behaviors = new ArrayList<>();

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    public Arena(int minY, int maxY, long seed) {
        this.minY = minY;
        this.maxY = maxY;
        this.data = new BlockState[N * N * (maxY - minY + 1)];
        this.rnd = new Random(seed);
    }

    /** Frees the block buffer once it has been copied into the world (flicks, triggers and behaviours stay). */
    public void release() { data = null; }

    // ------------------------------------------------------------ access

    public static boolean inXZ(int x, int z) { return x >= -HALF && x < HALF && z >= -HALF && z < HALF; }

    public boolean in(int x, int y, int z) { return inXZ(x, z) && y >= minY && y <= maxY; }

    public int idx(int x, int y, int z) { return ((y - minY) * N + (z + HALF)) * N + (x + HALF); }

    public void set(int x, int y, int z, BlockState s) {
        if (in(x, y, z)) data[idx(x, y, z)] = s;
    }

    public void set(int x, int y, int z, Block b) { set(x, y, z, b.defaultBlockState()); }

    public void setIfAir(int x, int y, int z, BlockState s) {
        if (in(x, y, z) && air(x, y, z)) data[idx(x, y, z)] = s;
    }

    public void setIfAir(int x, int y, int z, Block b) { setIfAir(x, y, z, b.defaultBlockState()); }

    public void carve(int x, int y, int z) { set(x, y, z, AIR); }

    public BlockState get(int x, int y, int z) { return in(x, y, z) ? data[idx(x, y, z)] : null; }

    public boolean air(int x, int y, int z) {
        BlockState s = get(x, y, z);
        return s == null || s.isAir();
    }

    public boolean solid(int x, int y, int z) {
        BlockState s = get(x, y, z);
        return s != null && s.blocksMotion();
    }

    public boolean is(int x, int y, int z, Block b) {
        BlockState s = get(x, y, z);
        return s != null && s.is(b);
    }

    public int gy(int x, int z) { return inXZ(x, z) ? ground[(z + HALF) * N + (x + HALF)] : 0; }

    public void setGy(int x, int z, int y) { if (inXZ(x, z)) ground[(z + HALF) * N + (x + HALF)] = y; }

    public void spawn(int x, int y, int z) { spawnX = x; spawnY = y; spawnZ = z; }

    // ------------------------------------------------------------ volumes

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int y = Math.max(minY, Math.min(y1, y2)); y <= Math.min(maxY, Math.max(y1, y2)); y++)
            for (int z = Math.max(-HALF, Math.min(z1, z2)); z <= Math.min(HALF - 1, Math.max(z1, z2)); z++)
                for (int x = Math.max(-HALF, Math.min(x1, x2)); x <= Math.min(HALF - 1, Math.max(x1, x2)); x++)
                    data[idx(x, y, z)] = s;
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) { fill(x1, y1, z1, x2, y2, z2, b.defaultBlockState()); }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Pal p) {
        for (int y = Math.max(minY, Math.min(y1, y2)); y <= Math.min(maxY, Math.max(y1, y2)); y++)
            for (int z = Math.max(-HALF, Math.min(z1, z2)); z <= Math.min(HALF - 1, Math.max(z1, z2)); z++)
                for (int x = Math.max(-HALF, Math.min(x1, x2)); x <= Math.min(HALF - 1, Math.max(x1, x2)); x++)
                    data[idx(x, y, z)] = p.pick(rnd);
    }

    public void clearBox(int x1, int y1, int z1, int x2, int y2, int z2) { fill(x1, y1, z1, x2, y2, z2, AIR); }

    /** Fills only cells that are currently empty. */
    public void fillAir(int x1, int y1, int z1, int x2, int y2, int z2, Pal p) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                    if (air(x, y, z)) set(x, y, z, p.pick(rnd));
    }

    /** Hollow box: walls, floor and ceiling in {@code p}, interior untouched. */
    public void shell(int x1, int y1, int z1, int x2, int y2, int z2, Pal p) {
        int ax = Math.min(x1, x2), bx = Math.max(x1, x2), ay = Math.min(y1, y2), by = Math.max(y1, y2), az = Math.min(z1, z2), bz = Math.max(z1, z2);
        for (int y = ay; y <= by; y++)
            for (int z = az; z <= bz; z++)
                for (int x = ax; x <= bx; x++)
                    if (x == ax || x == bx || y == ay || y == by || z == az || z == bz) set(x, y, z, p.pick(rnd));
    }

    /** Four walls only (no floor or ceiling). */
    public void walls(int x1, int y1, int z1, int x2, int y2, int z2, Pal p) {
        int ax = Math.min(x1, x2), bx = Math.max(x1, x2), az = Math.min(z1, z2), bz = Math.max(z1, z2);
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            for (int z = az; z <= bz; z++)
                for (int x = ax; x <= bx; x++)
                    if (x == ax || x == bx || z == az || z == bz) set(x, y, z, p.pick(rnd));
    }

    public void line(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1), dz = Math.abs(z2 - z1);
        int n = Math.max(dx, Math.max(dy, dz));
        if (n == 0) { set(x1, y1, z1, s); return; }
        for (int i = 0; i <= n; i++) {
            set(Math.round(x1 + (x2 - x1) * (float) i / n), Math.round(y1 + (y2 - y1) * (float) i / n), Math.round(z1 + (z2 - z1) * (float) i / n), s);
        }
    }

    public void line(int x1, int y1, int z1, int x2, int y2, int z2, Block b) { line(x1, y1, z1, x2, y2, z2, b.defaultBlockState()); }

    public void disc(int cx, int y, int cz, float r, Pal p) {
        int ri = (int) Math.ceil(r);
        for (int z = -ri; z <= ri; z++)
            for (int x = -ri; x <= ri; x++)
                if (x * x + z * z <= r * r) set(cx + x, y, cz + z, p.pick(rnd));
    }

    public void cyl(int cx, int y1, int y2, int cz, float r, Pal p) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) disc(cx, y, cz, r, p);
    }

    public void ring(int cx, int y, int cz, float r, float thick, Pal p) {
        int ri = (int) Math.ceil(r + thick);
        float in = Math.max(0, r - thick);
        for (int z = -ri; z <= ri; z++)
            for (int x = -ri; x <= ri; x++) {
                float d2 = x * x + z * z;
                if (d2 <= (r + thick) * (r + thick) && d2 >= in * in) set(cx + x, y, cz + z, p.pick(rnd));
            }
    }

    public void sphereCarve(int cx, int cy, int cz, float rx, float ry, float rz) {
        for (int y = (int) -ry; y <= ry; y++)
            for (int z = (int) -rz; z <= rz; z++)
                for (int x = (int) -rx; x <= rx; x++)
                    if ((x * x) / (rx * rx) + (y * y) / (ry * ry) + (z * z) / (rz * rz) <= 1) carve(cx + x, cy + y, cz + z);
    }

    // ------------------------------------------------------------ behaviour registration

    public void flick(int x, int y, int z, int kind, int a, int b) { flicks.add(new Flick(x, y, z, kind, a, b)); }

    /** A lamp block of {@link St#lamp}; it stays dark most of the time and only coughs light now and then. */
    public void badLamp(int x, int y, int z) {
        set(x, y, z, St.lamp(false));
        flick(x, y, z, FLICK_BAD, 0, 0);
    }

    public void dyingLamp(int x, int y, int z) {
        set(x, y, z, St.lamp(true));
        flick(x, y, z, FLICK_DYING, 0, 0);
    }

    public void deadLamp(int x, int y, int z) { set(x, y, z, St.lamp(false)); }

    /** Candle that mostly burns, with an occasional gust of wind. */
    public void candle(int x, int y, int z, int n, Block candleBlock) {
        set(x, y, z, St.candle(candleBlock, n, true));
        flick(x, y, z, FLICK_GUST, n, 0);
    }

    /** A hidden light block that swells between two levels (lighting set-piece without a visible source). */
    public void pulseLight(int x, int y, int z, int lo, int hi) {
        set(x, y, z, St.light(hi));
        flick(x, y, z, FLICK_PULSE, lo, hi);
    }

    public void trigger(int x1, int y1, int z1, int x2, int y2, int z2, double dx, double dy, double dz) {
        triggers.add(new Trigger(x1, y1, z1, x2, y2, z2, dx, dy, dz, true, false, 0));
    }

    public void trigger(int x1, int y1, int z1, int x2, int y2, int z2, double tx, double ty, double tz, float yaw) {
        triggers.add(new Trigger(x1, y1, z1, x2, y2, z2, tx, ty, tz, false, true, yaw));
    }
}
