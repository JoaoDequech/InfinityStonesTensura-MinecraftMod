package com.infinitystones.tensura.skill.ability.extra.fear.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Copy of the victim's claimed chunks (Open Parties and Claims) or fallback to respawn area, rotted like Home. */
public final class Claims extends Scene {
    private static final int HW = 32, HH = 24, HD = 32; // covers ~16 chunks when chunk-sized

    @Override
    public String id() { return "claims"; }

    @Override
    public int minY() { return -HH / 2; }

    @Override
    public int maxY() { return HH / 2; }

    @Override
    public void plan(Arena a, Ctx c, List<Runnable> s) {
        a.spawn(0, 1, 0);
        s.add(() -> copyClaims(a, c));
        s.add(() -> rot(a));
        s.add(() -> props(a));
    }

    private static void copyClaims(Arena a, Ctx c) {
        List<BlockPos> claimCenters = findClaims(c);
        if (claimCenters.isEmpty()) {
            // fallback: copy around respawn position
            copyAround(a, c, c.srcPos(), HW / 2, HD / 2);
            return;
        }
        // copy up to ~16 chunks worth, centred on first claim
        BlockPos first = claimCenters.get(0);
        copyAround(a, c, first, HW / 2, HD / 2);
    }

    private static List<BlockPos> findClaims(Ctx c) {
        List<BlockPos> centers = new ArrayList<>();
        try {
            // Try Open Parties and Claims via reflection
            Class<?> opacClass = Class.forName("net.mehvahdjunkie.openpartiesandclaims.OpenPartiesAndClaims");
            Method getManager = opacClass.getMethod("getManager");
            Object manager = getManager.invoke(null);
            if (manager == null) return centers;
            // Get claims for the player
            Class<?> claimManagerClass = Class.forName("net.mehvahdjunkie.openpartiesandclaims.forge.claim.ClaimManager");
            Method getClaimsForPlayer = claimManagerClass.getMethod("getClaimsForPlayer", java.util.UUID.class);
            Object claims = getClaimsForPlayer.invoke(manager, c.victim());
            if (claims instanceof Iterable) {
                for (Object claim : (Iterable<?>) claims) {
                    // Extract chunk positions from claim
                    Class<?> claimClass = claim.getClass();
                    Method getChunks = claimClass.getMethod("getChunks");
                    Object chunks = getChunks.invoke(claim);
                    if (chunks instanceof Iterable) {
                        for (Object chunkPos : (Iterable<?>) chunks) {
                            // chunkPos is likely a ChunkPos or similar
                            long packed = (Long) chunkPos.getClass().getMethod("toLong").invoke(chunkPos);
                            int cx = net.minecraft.world.level.ChunkPos.getX(packed);
                            int cz = net.minecraft.world.level.ChunkPos.getZ(packed);
                            centers.add(new BlockPos(cx * 16 + 8, 0, cz * 16 + 8));
                            if (centers.size() >= 16) break;
                        }
                    }
                    if (centers.size() >= 16) break;
                }
            }
        } catch (Exception e) {
            // OPAC not present or API changed — silent fallback
        }
        return centers;
    }

    private static void copyAround(Arena a, Ctx c, BlockPos center, int hw, int hd) {
        ServerLevel src = c.server().getLevel(c.srcDim());
        if (src == null) { emptyGround(a); return; }
        Random r = a.rnd;
        for (int dy = -HH / 2; dy < HH / 2; dy++) {
            int sy = center.getY() + dy;
            if (sy < src.getMinBuildHeight() || sy >= src.getMaxBuildHeight()) continue;
            for (int dz = -hd; dz < hd; dz++) {
                for (int dx = -hw; dx < hw; dx++) {
                    int sx = center.getX() + dx, sz = center.getZ() + dz;
                    LevelChunk ch = src.getChunk(sx >> 4, sz >> 4);
                    BlockState bs = ch.getBlockState(sx & 15, sy, sz & 15);
                    if (bs.isAir()) continue;
                    if (!bs.canSurvive(src, new BlockPos(sx, sy, sz))) continue;
                    a.set(dx, dy, dz, bs);
                }
            }
        }
    }

    private static void emptyGround(Arena a) {
        Kit.groundFlat(a, Pal.of(Blocks.COARSE_DIRT, 3, Blocks.DIRT, 1), Pal.of(Blocks.STONE, 1), 4);
    }

    private static void rot(Arena a) {
        Random r = a.rnd;
        for (int y = a.minY; y <= a.maxY; y++)
            for (int z = -HD; z < HD; z++)
                for (int x = -HW; x < HW; x++) {
                    BlockState bs = a.get(x, y, z);
                    if (bs == null || bs.isAir()) continue;
                    String name = bs.getBlock().getDescriptionId();
                    if (name.contains("planks")) {
                        a.set(x, y, z, r.nextBoolean() ? Blocks.DARK_OAK_PLANKS : Blocks.SPRUCE_PLANKS);
                    } else if (name.contains("stone_bricks")) {
                        a.set(x, y, z, r.nextInt(3) == 0 ? Blocks.CRACKED_STONE_BRICKS : r.nextInt(2) == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS);
                    } else if (name.contains("glass")) {
                        if (r.nextInt(3) == 0) a.carve(x, y, z);
                    }
                }
        Kit.decay(a, -HW, a.minY, -HD, HW - 1, a.maxY, HD - 1, 0.18f, 9);
        Kit.webs(a, -HW, a.minY, -HD, HW - 1, a.maxY, HD - 1, 0.28f);
    }

    private static void props(Arena a) {
        Random r = a.rnd;
        for (int i = 0; i < 10; i++) {
            int x = r.nextInt(HW * 2) - HW, z = r.nextInt(HD * 2) - HD;
            int y = Kit.top(a, x, z);
            if (y > a.minY) Kit.blood(a, x, y + 1, z, 1.5f + r.nextFloat() * 2f);
        }
        for (int i = 0; i < 6; i++) Kit.bones(a, r.nextInt(HW * 2) - HW, a.minY + 1, r.nextInt(HD * 2) - HD);
        for (int i = 0; i < 5; i++) {
            int x = r.nextInt(HW * 2) - HW, z = r.nextInt(HD * 2) - HD;
            int y = Kit.top(a, x, z);
            if (y > a.minY + 2) {
                a.set(x, y + 1, z, Blocks.REDSTONE_LAMP);
                a.flick(x, y + 1, z, Arena.FLICK_BAD, 5, 0);
            }
        }
    }
}