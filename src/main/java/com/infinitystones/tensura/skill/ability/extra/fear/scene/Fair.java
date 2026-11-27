package com.infinitystones.tensura.skill.ability.extra.fear.scene;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Random;

import static com.infinitystones.tensura.skill.ability.extra.fear.scene.St.*;

/** Dead amusement park: rusted ferris wheel, carousel of skull horses, torn big top, stalls, a broken coaster and a funhouse with a grinning face. */
public final class Fair extends Scene {
    private static final Pal RUST = Pal.of(Blocks.RAW_IRON_BLOCK, 3, Blocks.BROWN_TERRACOTTA, 2, Blocks.EXPOSED_COPPER, 1, Blocks.ORANGE_TERRACOTTA, 1);
    private static final Pal FADED = Pal.of(Blocks.RED_TERRACOTTA, 3, Blocks.WHITE_TERRACOTTA, 3, Blocks.YELLOW_TERRACOTTA, 1, Blocks.BROWN_TERRACOTTA, 1);

    @Override
    public String id() { return "fair"; }

    @Override
    public int minY() { return -8; }

    @Override
    public int maxY() { return 44; }

    @Override
    public void plan(Arena a, Ctx c, List<Runnable> s) {
        a.spawn(0, 1, 36);
        s.add(() -> ground(a));
        s.add(() -> gate(a));
        s.add(() -> wheel(a, -24, -6, 16, 14));
        s.add(() -> carousel(a, 22, -12));
        s.add(() -> bigTop(a, 24, 14));
        s.add(() -> funhouse(a));
        s.add(() -> coaster(a));
        s.add(() -> booths(a));
        s.add(() -> litter(a));
    }

    // ------------------------------------------------------------------ site

    private static void ground(Arena a) {
        Random r = a.rnd;
        Kit.groundFlat(a, Pal.of(Blocks.COARSE_DIRT, 4, Blocks.PODZOL, 3, Blocks.GRAVEL, 2, Blocks.MUD, 1), Pal.of(Blocks.DIRT, 3, Blocks.STONE, 1), 5);
        // cracked promenade
        for (int z = -26; z <= 46; z++) for (int x = -4; x <= 4; x++) if (r.nextInt(8) != 0) a.set(x, 0, z, Pal.of(Blocks.STONE_BRICKS, 3, Blocks.CRACKED_STONE_BRICKS, 3, Blocks.COBBLESTONE, 2, Blocks.GRAVEL, 1).pick(r));
        for (int x = -40; x <= 40; x++) for (int z = 20; z <= 22; z++) if (r.nextInt(7) != 0 && Math.abs(x) > 4) a.set(x, 0, z, Pal.of(Blocks.CRACKED_STONE_BRICKS, 2, Blocks.COBBLESTONE, 2).pick(r));
        Kit.fenceLine(a, -45, -45, 45, -45, 1, 3, Blocks.IRON_BARS, Blocks.COBBLESTONE_WALL, 4, 0.08f);
        Kit.fenceLine(a, -45, -45, -45, 45, 1, 3, Blocks.IRON_BARS, Blocks.COBBLESTONE_WALL, 4, 0.08f);
        Kit.fenceLine(a, 45, -45, 45, 45, 1, 3, Blocks.IRON_BARS, Blocks.COBBLESTONE_WALL, 4, 0.08f);
        for (int z = -22; z <= 40; z += 12) for (int sx : new int[]{-6, 6}) {
            if (r.nextInt(3) == 0) { for (int y = 1; y <= 4; y++) a.set(sx, y, z, Blocks.DARK_OAK_FENCE); continue; }
            Kit.lampPost(a, sx, 1, z, 4, Blocks.DARK_OAK_FENCE);
        }
        // sagging garlands of chain between posts
        for (int z = -22; z < 40; z += 12) for (int y : new int[]{5}) {
            for (int k = 1; k < 12; k++) {
                int sag = (int) Math.round(Math.sin(Math.PI * k / 12.0) * 1.4);
                if (r.nextInt(5) != 0) a.setIfAir(-6, y - sag, z + k, St.chain(Direction.Axis.Z));
                if (r.nextInt(5) != 0) a.setIfAir(6, y - sag, z + k, St.chain(Direction.Axis.Z));
            }
        }
        for (int i = 0; i < 10; i++) {
            int x = r.nextInt(86) - 43, z = r.nextInt(86) - 43;
            if (Math.abs(x) < 10) continue;
            Kit.deadTree(a, x, 1, z, 5 + r.nextInt(5), Blocks.DARK_OAK_LOG);
        }
    }

    private static void gate(Arena a) {
        int z = 42;
        for (int sx : new int[]{-7, 7}) for (int y = 1; y <= 10; y++) { a.set(sx, y, z, RUST.pick(a.rnd)); a.set(sx + (sx < 0 ? -1 : 1), y, z, y % 3 == 0 ? Blocks.RED_TERRACOTTA.defaultBlockState() : Blocks.WHITE_TERRACOTTA.defaultBlockState()); }
        for (int x = -7; x <= 7; x++) {
            int h = 10 + (int) Math.round(Math.sin(Math.PI * (x + 7) / 14.0) * 3);
            a.set(x, h, z, FADED.pick(a.rnd));
            a.set(x, h - 1, z, Blocks.BLACK_CONCRETE);
            if ((x + 7) % 2 == 0) {
                if (a.rnd.nextInt(3) == 0) a.dyingLamp(x, h + 1, z);
                else if (a.rnd.nextInt(2) == 0) a.badLamp(x, h + 1, z);
                else a.deadLamp(x, h + 1, z);
            }
        }
        // ticket booth
        a.fill(-14, 1, 38, -10, 4, 40, Pal.of(Blocks.RED_TERRACOTTA, 3, Blocks.WHITE_TERRACOTTA, 2));
        a.clearBox(-13, 1, 39, -11, 3, 39);
        a.fill(-14, 3, 40, -10, 3, 40, Blocks.GLASS_PANE);
        a.fill(-15, 5, 37, -9, 5, 41, Pal.of(Blocks.RED_TERRACOTTA, 1, Blocks.WHITE_TERRACOTTA, 1));
        a.candle(-12, 2, 39, 3, Blocks.CANDLE);
        Kit.bones(a, -12, 1, 38);
        Kit.blood(a, -12, 1, 37, 2f);
        Kit.decay(a, -15, 1, 37, -9, 5, 41, 0.3f, 4);
    }

    // ------------------------------------------------------------------ ferris wheel (plane x-y, thickness along z)

    private static void wheel(Arena a, int cx, int cz, int cy, int r) {
        Random rnd = a.rnd;
        for (int dz : new int[]{-1, 1}) for (double ang = 0; ang < 360; ang += 1.5) {
            int x = (int) Math.round(cx + Math.cos(Math.toRadians(ang)) * r), y = (int) Math.round(cy + Math.sin(Math.toRadians(ang)) * r);
            if (rnd.nextInt(14) == 0 && ang > 40) continue;
            a.set(x, y, cz + dz, RUST.pick(rnd));
        }
        for (double ang = 0; ang < 360; ang += 22.5) {
            int x = (int) Math.round(cx + Math.cos(Math.toRadians(ang)) * (r - 1)), y = (int) Math.round(cy + Math.sin(Math.toRadians(ang)) * (r - 1));
            if (rnd.nextInt(7) != 0) { a.line(cx, cy, cz - 1, x, y, cz - 1, Blocks.IRON_BARS); a.line(cx, cy, cz + 1, x, y, cz + 1, Blocks.IRON_BARS); }
        }
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) a.set(cx + dx, cy + dy, cz + dz, Blocks.RAW_IRON_BLOCK);
        // gondolas
        for (int g = 0; g < 10; g++) {
            double ang = Math.toRadians(g * 36 + 18);
            int px = (int) Math.round(cx + Math.cos(ang) * r), py = (int) Math.round(cy + Math.sin(ang) * r);
            if (g == 4 || g == 7) continue;
            a.set(px, py - 1, cz, St.chain(Direction.Axis.Y));
            a.set(px, py - 2, cz, St.chain(Direction.Axis.Y));
            a.shell(px - 1, py - 6, cz - 1, px + 1, py - 3, cz + 1, Pal.of(Blocks.IRON_BARS, 4, Blocks.AIR, 1));
            a.fill(px - 1, py - 3, cz - 1, px + 1, py - 3, cz + 1, Pal.of(Blocks.RED_TERRACOTTA, 1, Blocks.YELLOW_TERRACOTTA, 1));
            a.fill(px - 1, py - 6, cz - 1, px + 1, py - 6, cz + 1, Blocks.BROWN_TERRACOTTA);
            a.clearBox(px, py - 5, cz, px, py - 4, cz);
            if (g == 2 || g == 9) { a.set(px, py - 5, cz, St.skull(Blocks.SKELETON_SKULL, rnd.nextInt(16))); }
            if (g == 5) a.set(px, py - 5, cz, Blocks.RED_CARPET);
        }
        // A-frame supports and platform
        a.line(cx - 10, 1, cz - 2, cx, cy, cz - 2, Blocks.IRON_BLOCK);
        a.line(cx + 10, 1, cz - 2, cx, cy, cz - 2, Blocks.IRON_BLOCK);
        a.line(cx - 10, 1, cz + 2, cx, cy, cz + 2, Blocks.IRON_BLOCK);
        a.line(cx + 10, 1, cz + 2, cx, cy, cz + 2, Blocks.IRON_BLOCK);
        a.fill(cx - 4, 1, cz - 3, cx + 4, 1, cz + 3, Pal.of(Blocks.SPRUCE_PLANKS, 3, Blocks.DARK_OAK_PLANKS, 1));
        a.fill(cx - 4, 2, cz + 3, cx + 4, 2, cz + 3, Blocks.IRON_BARS);
        a.candle(cx - 3, 2, cz + 1, 3, Blocks.CANDLE);
        a.pulseLight(cx, 6, cz + 2, 3, 6);
        Kit.blood(a, cx + 2, 2, cz, 1.8f);
        Kit.decay(a, cx - 12, 1, cz - 3, cx + 12, cy + r + 2, cz + 3, 0.12f, 6);
    }

    // ------------------------------------------------------------------ carousel

    private static void carousel(Arena a, int cx, int cz) {
        Random r = a.rnd;
        a.disc(cx, 1, cz, 8, Pal.of(Blocks.SPRUCE_PLANKS, 3, Blocks.DARK_OAK_PLANKS, 2, Blocks.RED_TERRACOTTA, 1));
        a.ring(cx, 2, cz, 8, 0.5f, Pal.of(Blocks.IRON_BARS, 2, Blocks.AIR, 1));
        for (int y = 2; y <= 7; y++) a.set(cx, y, cz, y % 2 == 0 ? Blocks.RAW_IRON_BLOCK.defaultBlockState() : Blocks.IRON_BARS.defaultBlockState());
        for (int i = 0; i < 8; i++) {
            double ang = Math.toRadians(i * 45 + 10);
            int px = (int) Math.round(cx + Math.cos(ang) * 5), pz = (int) Math.round(cz + Math.sin(ang) * 5);
            for (int y = 2; y <= 6; y++) a.set(px, y, pz, Blocks.IRON_BARS);
            if (i == 3) continue;
            Direction d = Math.abs(Math.cos(ang)) > Math.abs(Math.sin(ang)) ? Direction.NORTH : Direction.EAST;
            a.set(px + d.getStepX(), 3, pz + d.getStepZ(), stair(Blocks.QUARTZ_STAIRS, d, false));
            a.set(px - d.getStepX(), 3, pz - d.getStepZ(), slab(Blocks.QUARTZ_SLAB, false));
            a.set(px, 3, pz, Blocks.BONE_BLOCK);
            a.set(px + d.getStepX(), 4, pz + d.getStepZ(), St.skull(i % 2 == 0 ? Blocks.SKELETON_SKULL : Blocks.WITHER_SKELETON_SKULL, (i * 5) & 15));
        }
        // canopy
        for (int k = 0; k < 5; k++) {
            float rad = 9.5f - k * 2.2f;
            int ri = (int) Math.ceil(rad);
            for (int z = -ri; z <= ri; z++) for (int x = -ri; x <= ri; x++) {
                float d = (float) Math.sqrt(x * x + z * z);
                if (d <= rad && d >= rad - 1.2f) {
                    double ang = Math.atan2(z, x);
                    boolean red = ((int) Math.floor((ang + Math.PI) / (Math.PI / 8))) % 2 == 0;
                    a.set(cx + x, 8 + k, cz + z, red ? Blocks.RED_TERRACOTTA.defaultBlockState() : Blocks.WHITE_TERRACOTTA.defaultBlockState());
                }
            }
        }
        a.set(cx, 13, cz, Blocks.IRON_BARS);
        a.set(cx, 14, cz, Blocks.IRON_BARS);
        a.set(cx, 12, cz, Blocks.RAW_IRON_BLOCK);
        // the music box at the centre: a few candles
        a.candle(cx + 1, 2, cz, 4, Blocks.RED_CANDLE);
        a.candle(cx - 1, 2, cz, 3, Blocks.CANDLE);
        a.pulseLight(cx, 5, cz, 3, 7);
        Kit.decay(a, cx - 10, 1, cz - 10, cx + 10, 14, cz + 10, 0.2f, 15);
        Kit.webs(a, cx - 9, 2, cz - 9, cx + 9, 8, cz + 9, 0.1f);
        Kit.bones(a, cx + 5, 2, cz - 2);
        Kit.blood(a, cx + 3, 2, cz + 4, 2.2f);
    }

    // ------------------------------------------------------------------ big top

    private static void bigTop(Arena a, int cx, int cz) {
        Random r = a.rnd;
        a.disc(cx, 0, cz, 12, Pal.of(Blocks.COARSE_DIRT, 3, Blocks.PODZOL, 1, Blocks.BROWN_TERRACOTTA, 1));
        for (int y = 1; y <= 5; y++)
            for (double ang = 0; ang < 360; ang += 2) {
                int x = (int) Math.round(cx + Math.cos(Math.toRadians(ang)) * 11), z = (int) Math.round(cz + Math.sin(Math.toRadians(ang)) * 11);
                boolean entrance = Math.abs(ang - 90) < 14;
                if (entrance && y <= 4) continue;
                if (r.nextInt(9) == 0) continue;
                a.set(x, y, z, ((int) (ang / 22.5)) % 2 == 0 ? Blocks.RED_TERRACOTTA.defaultBlockState() : Blocks.WHITE_TERRACOTTA.defaultBlockState());
            }
        for (int k = 0; k < 11; k++) {
            float rad = 11.5f - k * 1.05f;
            for (double ang = 0; ang < 360; ang += 1.5) {
                int x = (int) Math.round(cx + Math.cos(Math.toRadians(ang)) * rad), z = (int) Math.round(cz + Math.sin(Math.toRadians(ang)) * rad);
                if (r.nextInt(7) == 0) continue;
                a.set(x, 6 + k, z, ((int) (ang / 15)) % 2 == 0 ? Blocks.RED_TERRACOTTA.defaultBlockState() : Blocks.WHITE_TERRACOTTA.defaultBlockState());
            }
        }
        for (int y = 1; y <= 17; y++) a.set(cx, y, cz, y < 6 ? Blocks.DARK_OAK_LOG.defaultBlockState() : Blocks.SPRUCE_LOG.defaultBlockState());
        // ring seating, stage, swing
        for (int ring = 0; ring < 2; ring++)
            for (double ang = 200; ang < 340; ang += 3) {
                int x = (int) Math.round(cx + Math.cos(Math.toRadians(ang)) * (9 - ring)), z = (int) Math.round(cz + Math.sin(Math.toRadians(ang)) * (9 - ring));
                a.set(x, 1 + ring, z, r.nextInt(4) == 0 ? Blocks.AIR.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
        a.disc(cx, 1, cz + 1, 3.5f, Pal.of(Blocks.SPRUCE_SLAB, 1));
        a.disc(cx, 0, cz + 1, 3.5f, Pal.one(Blocks.RED_TERRACOTTA));
        for (int x = cx - 3; x <= cx + 3; x += 6) { a.set(x, 2, cz + 1, Blocks.IRON_BARS); a.candle(x, 3, cz + 1, 3, Blocks.CANDLE); }
        // trapeze
        for (int y = 12; y <= 15; y++) { a.set(cx - 3, y, cz - 4, St.chain(Direction.Axis.Y)); a.set(cx + 3, y, cz - 4, St.chain(Direction.Axis.Y)); }
        a.fill(cx - 3, 11, cz - 4, cx + 3, 11, cz - 4, Pal.of(Blocks.OAK_FENCE, 1));
        a.line(cx - 3, 16, cz - 4, cx + 3, 16, cz - 4, Blocks.IRON_BARS);
        Kit.cage(a, cx + 5, 1, cz - 5, true);
        a.pulseLight(cx, 5, cz, 3, 6);
        Kit.blood(a, cx, 1, cz - 2, 3f);
        Kit.bones(a, cx - 4, 1, cz - 4);
        Kit.webs(a, cx - 10, 1, cz - 10, cx + 10, 10, cz + 10, 0.06f);
    }

    // ------------------------------------------------------------------ funhouse (x -10..10, z -40..-22)

    private static void funhouse(Arena a) {
        Random r = a.rnd;
        int z0 = -40, z1 = -22, front = z1;
        a.fill(-11, 0, z0, 11, 0, z1, Pal.of(Blocks.BLACK_TERRACOTTA, 2, Blocks.RED_TERRACOTTA, 1));
        // facade (south side, face looks at the promenade): 22 wide, 16 high
        a.fill(-11, 1, front, 11, 15, front, FADED);
        a.walls(-11, 1, z0, 11, 14, front, Pal.of(Blocks.PURPLE_TERRACOTTA, 3, Blocks.BLACK_TERRACOTTA, 2, Blocks.MAGENTA_TERRACOTTA, 1));
        a.fill(-11, 15, z0, 11, 15, front, Blocks.BLACK_CONCRETE);
        // hair
        for (int x = -11; x <= 11; x++) a.fill(x, 16, front, x, 16 + (x * 7 + 100) % 3 + 1, front, x % 2 == 0 ? Blocks.ORANGE_TERRACOTTA : Blocks.RED_TERRACOTTA);
        // eyes (glowing, flickering), nose, mouth
        for (int sx : new int[]{-5, 5}) {
            a.fill(sx - 1, 9, front, sx + 1, 12, front, Blocks.WHITE_CONCRETE);
            a.fill(sx, 10, front + 1, sx, 11, front + 1, Blocks.RED_STAINED_GLASS);
            a.pulseLight(sx, 10, front + 2, 4, 9);
        }
        a.fill(-1, 7, front, 1, 9, front, Blocks.RED_CONCRETE);
        a.fill(-1, 7, front + 1, 1, 8, front + 1, Blocks.RED_CONCRETE);
        for (int x = -7; x <= 7; x++) {
            a.carve(x, 2, front); a.carve(x, 3, front); a.carve(x, 4, front);
            if (x >= -2 && x <= 2) { a.carve(x, 1, front); }
            if (Math.abs(x) > 2) a.set(x, 1, front, Blocks.WHITE_CONCRETE);
            if (x % 2 == 0) { a.set(x, 5, front, Blocks.WHITE_CONCRETE); a.set(x, 4, front, x == 0 ? Blocks.AIR.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState()); }
            if (x % 2 != 0) a.set(x, 2, front, Blocks.WHITE_CONCRETE);
        }
        a.fill(-8, 5, front, 8, 6, front, Pal.of(Blocks.RED_CONCRETE, 3, Blocks.RED_TERRACOTTA, 1));
        a.fill(-8, 1, front, -8, 4, front, Blocks.RED_CONCRETE);
        a.fill(8, 1, front, 8, 4, front, Blocks.RED_CONCRETE);
        for (int x = -7; x <= 7; x += 2) { a.set(x, 4, front, Blocks.WHITE_CONCRETE); }
        a.clearBox(-2, 1, front, 2, 3, front);
        // interior maze: 6 x 4 cells of 3 x 3
        Maze m = new Maze(6, 5, false, 5, r);
        int mx0 = -9, mz0 = z0 + 1;
        for (int cy = 0; cy < 5; cy++) for (int cx = 0; cx < 6; cx++) {
            int bx = mx0 + cx * 3, bz = mz0 + cy * 3;
            boolean east = cx < 5 && !m.east(cx, cy), south = cy < 4 && !m.south(cx, cy);
            if (east) mazeWall(a, bx + 2, bz, bx + 2, bz + 2, r);
            if (south) mazeWall(a, bx, bz + 2, bx + 2, bz + 2, r);
            if (r.nextInt(5) == 0) a.set(bx + 1, 1, bz + 1, ((cx + cy) & 1) == 0 ? Blocks.RED_CARPET.defaultBlockState() : Blocks.BLACK_CARPET.defaultBlockState());
        }
        a.clearBox(-1, 1, front - 1, 1, 4, front - 1);
        a.clearBox(-1, 1, front - 2, 1, 4, front - 2);
        for (int x = -10; x <= 10; x++) for (int z = z0 + 1; z < front; z++) if (((x + z) & 1) == 0) a.set(x, 0, z, Blocks.RED_TERRACOTTA);
        a.fill(-10, 5, z0 + 1, 10, 5, front - 1, Blocks.BLACK_TERRACOTTA);
        a.fill(-10, 6, z0 + 1, 10, 14, front - 1, Blocks.AIR);
        a.fill(-10, 15, z0, 10, 15, front, Blocks.BLACK_CONCRETE);
        for (int i = 0; i < 10; i++) {
            int x = -9 + r.nextInt(19), z = z0 + 2 + r.nextInt(16);
            if (a.air(x, 1, z) && a.air(x, 2, z)) doll(a, x, z, r);
        }
        for (int i = 0; i < 8; i++) {
            int x = -9 + r.nextInt(19), z = z0 + 2 + r.nextInt(16);
            if (a.air(x, 4, z)) a.badLamp(x, 4, z);
        }
        a.dyingLamp(0, 4, front - 3);
        a.pulseLight(0, 2, front - 2, 3, 6);
        Kit.bloodTrail(a, 0, front + 5, 0, front - 8, 1);
        Kit.blood(a, 0, 1, front + 2, 3f);
        Kit.decay(a, -11, 2, z0, 11, 15, front, 0.1f, 22);
    }

    private static void mazeWall(Arena a, int x1, int z1, int x2, int z2, Random r) {
        for (int y = 1; y <= 4; y++) for (int z = z1; z <= z2; z++) for (int x = x1; x <= x2; x++) {
            int pick = r.nextInt(8);
            a.set(x, y, z, pick == 0 ? Blocks.TINTED_GLASS.defaultBlockState() : pick == 1 ? Blocks.IRON_BARS.defaultBlockState() : y <= 1 ? Blocks.BLACK_TERRACOTTA.defaultBlockState() : Blocks.PURPLE_TERRACOTTA.defaultBlockState());
        }
    }

    private static void doll(Arena a, int x, int z, Random r) {
        Block body = r.nextBoolean() ? Blocks.RED_WOOL : Blocks.YELLOW_WOOL;
        a.set(x, 1, z, body);
        a.set(x, 2, z, r.nextBoolean() ? Blocks.WHITE_WOOL : Blocks.BLUE_WOOL);
        a.set(x, 3, z, St.skull(r.nextInt(3) == 0 ? Blocks.ZOMBIE_HEAD : Blocks.SKELETON_SKULL, r.nextInt(16)));
    }

    // ------------------------------------------------------------------ coaster

    private static void coaster(Arena a) {
        Random r = a.rnd;
        int z = 30;
        for (int x = -44; x <= -8; x++) {
            int y = 4 + (int) Math.round(Math.sin(Math.PI * (x + 44) / 36.0) * 10);
            if (x > -30 && x < -24 && y > 12) continue;
            a.set(x, y, z, Pal.of(Blocks.SPRUCE_SLAB, 1).first());
            a.set(x, y, z - 1, Blocks.IRON_BARS);
            a.set(x, y, z + 1, Blocks.IRON_BARS);
            if ((x + 44) % 4 == 0) {
                for (int yy = 1; yy < y; yy++) { a.set(x, yy, z - 1, Blocks.OAK_FENCE); a.set(x, yy, z + 1, Blocks.OAK_FENCE); }
                if (y > 4) a.line(x, y - 1, z, x + 2, y - 3, z, Blocks.IRON_BARS);
            }
        }
        // the wrecked car in the gap
        a.fill(-27, 1, 29, -25, 2, 31, Pal.of(Blocks.RED_TERRACOTTA, 3, Blocks.IRON_BARS, 1));
        a.clearBox(-26, 2, 30, -26, 2, 30);
        Kit.skull(a, -26, 2, 30, Blocks.SKELETON_SKULL);
        Kit.blood(a, -27, 1, 33, 2.5f);
        Kit.decay(a, -44, 1, 28, -8, 15, 32, 0.1f, 17);
        a.candle(-43, 5, 30, 3, Blocks.CANDLE);
    }

    // ------------------------------------------------------------------ booths along the promenade

    private static void booths(Arena a) {
        Random r = a.rnd;
        for (int i = 0; i < 6; i++) {
            int z = -16 + i * 8, side = i % 2 == 0 ? -1 : 1;
            int x0 = side < 0 ? -13 : 8;
            booth(a, x0, z, side, i, r);
        }
        // popcorn cart and a cotton-candy machine on the promenade
        a.fill(-3, 1, 10, -2, 1, 11, Blocks.RED_TERRACOTTA);
        a.fill(-3, 2, 10, -2, 2, 11, Blocks.GLASS);
        a.fill(-3, 3, 10, -2, 3, 11, Blocks.WHITE_TERRACOTTA);
        a.set(-3, 0, 10, Blocks.BLACK_CONCRETE);
        Kit.bones(a, -1, 1, 11);
        a.set(2, 1, 6, Blocks.CAULDRON);
        a.set(2, 2, 6, Blocks.WHITE_WOOL);
        a.set(2, 3, 6, Blocks.PINK_WOOL);
    }

    private static void booth(Arena a, int x0, int z, int side, int kind, Random r) {
        int w = 5;
        a.fill(x0, 1, z, x0 + w, 1, z + 3, Pal.of(Blocks.SPRUCE_PLANKS, 3, Blocks.DARK_OAK_PLANKS, 1));
        for (int cx : new int[]{x0, x0 + w}) for (int cz : new int[]{z, z + 3}) for (int y = 2; y <= 5; y++) a.set(cx, y, cz, Blocks.SPRUCE_FENCE);
        int fx = side < 0 ? x0 + w : x0;
        // back wall and counter facing the promenade
        int bx = side < 0 ? x0 : x0 + w;
        a.fill(bx, 2, z, bx, 4, z + 3, Pal.of(Blocks.SPRUCE_PLANKS, 3, Blocks.AIR, 1));
        a.fill(fx, 2, z, fx, 2, z + 3, Pal.of(Blocks.SPRUCE_SLAB, 3, Blocks.AIR, 1));
        // awning: alternating red / white stairs
        for (int k = 0; k <= 3; k++) {
            for (int d = 0; d <= w; d++) {
                int x = x0 + d;
                if (r.nextInt(8) == 0) continue;
                a.set(x, 6, z + k, (k % 2 == 0) ? Blocks.RED_TERRACOTTA.defaultBlockState() : Blocks.WHITE_TERRACOTTA.defaultBlockState());
            }
        }
        switch (kind % 3) {
            case 0 -> { // shooting gallery: rows of skulls
                for (int k = 0; k < 4; k++) a.set(bx + (side < 0 ? 1 : -1), 3, z + k, k % 2 == 0 ? St.skull(Blocks.SKELETON_SKULL, side < 0 ? 4 : 12) : Blocks.AIR.defaultBlockState());
            }
            case 1 -> { // prize shelf of dolls
                for (int k = 0; k < 4; k++) { a.set(bx + (side < 0 ? 1 : -1), 2, z + k, k % 2 == 0 ? Blocks.PINK_WOOL.defaultBlockState() : Blocks.AIR.defaultBlockState()); a.set(bx + (side < 0 ? 1 : -1), 3, z + k, k % 2 == 0 ? St.skull(Blocks.ZOMBIE_HEAD, side < 0 ? 4 : 12) : Blocks.AIR.defaultBlockState()); }
            }
            default -> { // ring toss
                for (int k = 0; k < 4; k++) a.set(x0 + 2 + (side < 0 ? 0 : 1), 2, z + k, Blocks.OAK_FENCE);
            }
        }
        a.candle(fx, 3, z + 1, 2, Blocks.CANDLE);
        if (kind % 2 == 0) { a.set(x0 + 2, 5, z + 1, lantern(Blocks.SOUL_LANTERN, true)); }
        Kit.decay(a, x0, 2, z, x0 + w, 6, z + 3, 0.18f, 50 + kind);
        Kit.webs(a, x0, 2, z, x0 + w, 5, z + 3, 0.2f);
        if (kind == 3) Kit.blood(a, x0 + 3, 1, z + 1, 2f);
    }

    private static void litter(Arena a) {
        Random r = a.rnd;
        for (int i = 0; i < 40; i++) {
            int x = r.nextInt(80) - 40, z = r.nextInt(80) - 38;
            if (!a.air(x, 1, z) || a.solid(x, 0, z) == false) continue;
            switch (r.nextInt(5)) {
                case 0 -> a.set(x, 1, z, Blocks.DEAD_BUSH);
                case 1 -> a.set(x, 1, z, r.nextBoolean() ? Blocks.RED_CARPET.defaultBlockState() : Blocks.YELLOW_CARPET.defaultBlockState());
                case 2 -> Kit.bones(a, x, 1, z);
                case 3 -> a.set(x, 1, z, Blocks.COBWEB);
                default -> { a.set(x, 1, z, Blocks.CAULDRON); }
            }
        }
        Kit.bloodTrail(a, 0, 34, 0, -20, 1);
    }
}
