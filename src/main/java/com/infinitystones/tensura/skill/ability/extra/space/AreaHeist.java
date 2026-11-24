package com.infinitystones.tensura.skill.ability.extra.space;

import com.infinitystones.tensura.skill.ability.extra.Terrain;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Displays;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.MenuNet;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Roubo de Área / Area Heist (Create-schematic style). Selecting: left marks corner A, corner B, then steals the box
 * (≤ 16³): the REAL blocks (states + block-entity NBT) leave the world in waves and are stored on the player (≤ 8
 * heists, persisted). Menu (right click in the wheel): pick a heist → placing mode with an owner-only translucent ghost
 * following the aim; Alt+scroll = distance, right = rotate 90°, Shift+right = mirror, left = place. Placing only
 * replaces air / fluids / replaceable plants; blocks are moved, never copied (a cell is cleared when its block lands).
 * Unbreakable blocks (hardness &lt; 0, portals) are never taken; block-break protection (claims) is respected.
 */
public final class AreaHeist implements Ability {
    public static final AreaHeist INSTANCE = new AreaHeist();
    static final String NODE = "area_heist", MODE = "space.area_heist", K = "infinity.extra.area_heist.";
    static final int OFF = 0, SELECT = 1, PLACE = 2;
    static final double RANGE = 64;
    private static final int TAKE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    @Override public Stone stone() { return Stone.SPACE; }

    private static int color() { return Stone.SPACE.color() & 0xFFFFFF; }

    // ================================================================== session

    static final class Session {
        int mode;
        @Nullable BlockPos a, b;
        long heist;
        int rot, dist = 10;
        boolean mirror;
        ResourceKey<Level> dim;
        int idle;
        @Nullable Tasks.Handle task;
        @Nullable HeistData cache;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Set<UUID> BUSY = new HashSet<>();
    private static final Set<UUID> DELETE_MENU = new HashSet<>();

    private static Session begin(ServerPlayer p, int mode) {
        Session s = SESSIONS.computeIfAbsent(p.getUUID(), k -> new Session());
        s.mode = mode;
        s.a = s.b = null;
        s.cache = null;
        s.dim = p.level().dimension();
        s.idle = 0;
        if (s.task == null || !s.task.alive()) {
            final Session ss = s;
            s.task = Tasks.add(age -> tickSession(p, ss, age));
        }
        return s;
    }

    private static boolean tickSession(ServerPlayer p, Session s, int age) {
        if (s.mode == OFF || SESSIONS.get(p.getUUID()) != s) return false;
        if (p.isRemoved() || p.hasDisconnected() || !p.isAlive() || p.level().dimension() != s.dim
                || ++s.idle > 20 * 60 * 5 || (age % 10 == 0 && !Stone.SPACE.isHeldBy(p))) {
            exit(p);
            return false;
        }
        // corners shimmer for everybody nearby (the box itself is drawn for the owner only)
        if (age % 8 == 0 && s.mode == SELECT) {
            ServerLevel l = p.serverLevel();
            if (s.a != null) Fx.at(l, Fx.light(Stone.SPACE, 1.2F), Vec3.atCenterOf(s.a), 3, 0.35, 0);
            if (s.b != null) Fx.at(l, Fx.light(Stone.SPACE, 1.2F), Vec3.atCenterOf(s.b), 3, 0.35, 0);
        }
        return true;
    }

    private static void exit(ServerPlayer p) {
        Session s = SESSIONS.remove(p.getUUID());
        if (s != null) { s.mode = OFF; if (s.task != null) s.task.cancel(); }
        if (!p.hasDisconnected()) sync(p, null, false);
    }

    private static void sync(ServerPlayer p, @Nullable Session s, boolean blocks) {
        if (s == null || s.mode == OFF) {
            SpaceNet.to(p, new SpaceNet.HeistState(OFF, false, 0, 0, 0, false, 0, 0, 0, 0, false, 0, false, 0, 0, 0, new int[0], 0, 0));
            return;
        }
        HeistData h = s.cache;
        boolean send = blocks && h != null;
        SpaceNet.to(p, new SpaceNet.HeistState(s.mode,
                s.a != null, s.a == null ? 0 : s.a.getX(), s.a == null ? 0 : s.a.getY(), s.a == null ? 0 : s.a.getZ(),
                s.b != null, s.b == null ? 0 : s.b.getX(), s.b == null ? 0 : s.b.getY(), s.b == null ? 0 : s.b.getZ(),
                s.rot, s.mirror, s.dist, send, h == null ? 0 : h.sx, h == null ? 0 : h.sy, h == null ? 0 : h.sz,
                send ? h.stateIds() : new int[0], HeistData.list(p).size(), h == null ? 0 : h.count()));
    }

    // ================================================================== wheel

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (BUSY.contains(p.getUUID())) return Fx.fail(p, K + "busy");
        if (alt) {
            DELETE_MENU.remove(p.getUUID());
            return MenuNet.open(p, Stone.SPACE, NODE, this) || Fx.fail(p, K + "none");
        }
        Session cur = SESSIONS.get(p.getUUID());
        if (cur != null && cur.mode != OFF) {
            exit(p);
            Fx.barGray(p, K + "cancelled");
            return true;
        }
        Session s = begin(p, SELECT);
        BlockHitResult hit = Aim.block(p, RANGE);
        if (hit.getType() == HitResult.Type.BLOCK) {
            s.a = hit.getBlockPos();
            cornerFx(p.serverLevel(), s.a, false);
        }
        sync(p, s, false);
        Fx.bar(p, Stone.SPACE, s.a == null ? K + "select" : K + "corner_a");
        Fx.sound(p.serverLevel(), p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 1.4F);
        return true;
    }

    @Override
    public @Nullable List<MenuNet.Option> menu(ServerPlayer p) {
        List<HeistData> all = HeistData.all(p);
        boolean del = DELETE_MENU.contains(p.getUUID());
        List<MenuNet.Option> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            HeistData h = all.get(i);
            String label = "#" + (i + 1) + "  " + h.sx + "×" + h.sy + "×" + h.sz + "  (" + h.count() + ")";
            out.add(new MenuNet.Option(label, del ? K + "menu.delete_one.desc" : K + "menu.heist.desc",
                    del ? "minecraft:lava_bucket" : h.icon(), false));
        }
        Session s = SESSIONS.get(p.getUUID());
        if (del) out.add(MenuNet.opt(K + "menu.back", "minecraft:arrow", false));
        else {
            if (!all.isEmpty()) out.add(MenuNet.opt(K + "menu.delete", "minecraft:lava_bucket", false));
            if (s != null && s.mode != OFF) out.add(MenuNet.opt(K + "menu.cancel", "minecraft:barrier", false));
        }
        return out;
    }

    @Override
    public void pick(ServerPlayer p, int index) {
        if (BUSY.contains(p.getUUID())) { Fx.fail(p, K + "busy"); return; }
        List<HeistData> all = HeistData.all(p);
        boolean del = DELETE_MENU.contains(p.getUUID());
        int n = all.size();
        if (index < n) {
            HeistData h = all.get(index);
            if (del) {
                DELETE_MENU.remove(p.getUUID());
                Session s = SESSIONS.get(p.getUUID());
                if (s != null && s.mode == PLACE && s.heist == h.id) exit(p);
                HeistData.remove(p, h.id);
                Fx.barGray(p, K + "deleted", index + 1);
                Fx.sound(p.serverLevel(), p.position(), SoundEvents.FIRE_EXTINGUISH, 0.5F, 0.8F);
                return;
            }
            enterPlace(p, h);
            return;
        }
        int extra = index - n;
        if (del) { DELETE_MENU.remove(p.getUUID()); MenuNet.open(p, Stone.SPACE, NODE, this); return; }
        if (n > 0 && extra == 0) { DELETE_MENU.add(p.getUUID()); MenuNet.open(p, Stone.SPACE, NODE, this); return; }
        exit(p);
        Fx.barGray(p, K + "cancelled");
    }

    private static void enterPlace(ServerPlayer p, HeistData h) {
        if (h.count() == 0) { HeistData.remove(p, h.id); Fx.fail(p, K + "none"); return; }
        Session s = begin(p, PLACE);
        s.heist = h.id;
        s.cache = h;
        s.rot = 0;
        s.mirror = false;
        s.dist = ExtraSettings.get(p, MODE, 10, 3, 48, 1);
        sync(p, s, true);
        Fx.bar(p, Stone.SPACE, K + "placing");
        ServerLevel l = p.serverLevel();
        Fx.sound(l, p.position(), SoundEvents.BEACON_ACTIVATE, 0.5F, 1.7F);
        FxNet.spiral(l, p.position(), 0.9F, 2.4F, color(), 24, p.getId());
    }

    // ================================================================== in-world input

    static void input(ServerPlayer p, SpaceNet.HeistInput in) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.mode == OFF) { sync(p, null, false); return; }
        if (BUSY.contains(p.getUUID())) return;
        s.idle = 0;
        ServerLevel l = p.serverLevel();
        BlockPos at = new BlockPos(in.x(), in.y(), in.z());
        boolean aimOk = in.has() && p.getEyePosition().distanceToSqr(Vec3.atCenterOf(at)) <= (RANGE + 8) * (RANGE + 8);
        if (in.action() == 4) { exit(p); Fx.barGray(p, K + "cancelled"); return; }
        if (s.mode == SELECT) {
            switch (in.action()) {
                case 0 -> {
                    if (s.a != null && s.b != null) { steal(p, s); return; }
                    if (!aimOk) { Fx.fail(p, K + "aim_block"); return; }
                    if (s.a == null) {
                        s.a = at;
                        cornerFx(l, at, false);
                        Fx.bar(p, Stone.SPACE, K + "corner_a");
                    } else {
                        int dx = Math.abs(at.getX() - s.a.getX()) + 1, dy = Math.abs(at.getY() - s.a.getY()) + 1, dz = Math.abs(at.getZ() - s.a.getZ()) + 1;
                        if (dx > HeistData.MAX_SIDE || dy > HeistData.MAX_SIDE || dz > HeistData.MAX_SIDE) {
                            Fx.fail(p, K + "too_big", dx, dy, dz);
                            return;
                        }
                        s.b = at;
                        cornerFx(l, at, true);
                        Fx.bar(p, Stone.SPACE, K + "corner_b", dx, dy, dz);
                    }
                    sync(p, s, false);
                }
                case 1, 2 -> {
                    if (s.b != null) s.b = null;
                    else if (s.a != null) s.a = null;
                    else { exit(p); Fx.barGray(p, K + "cancelled"); return; }
                    Fx.sound(l, p.position(), SoundEvents.AMETHYST_BLOCK_HIT, 0.5F, 0.8F);
                    sync(p, s, false);
                }
                default -> { }
            }
            return;
        }
        // PLACE
        switch (in.action()) {
            case 0 -> { if (!aimOk) Fx.fail(p, K + "aim_place"); else place(p, s, at); }
            case 1 -> {
                s.rot = (s.rot + 1) & 3;
                Fx.sound(l, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.0F + s.rot * 0.15F);
                sync(p, s, false);
            }
            case 2 -> {
                s.mirror = !s.mirror;
                Fx.sound(l, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, s.mirror ? 1.6F : 0.9F);
                sync(p, s, false);
            }
            case 3 -> {
                s.dist = Math.max(3, Math.min(48, s.dist + Integer.signum(in.value())));
                ExtraSettings.set(p, MODE, s.dist);
                var node = com.infinitystones.tensura.skill.StoneTree.of(Stone.SPACE).nodes.get(NODE);
                if (node != null) com.infinitystones.tensura.wheel.AbilitySettings.set(p, node, s.dist);
                sync(p, s, false);
            }
            default -> { }
        }
    }

    private static void cornerFx(ServerLevel l, BlockPos pos, boolean second) {
        Vec3 c = Vec3.atCenterOf(pos);
        Fx.at(l, Fx.dust(Stone.SPACE, 1.6F), c, 18, 0.45, 0);
        Fx.at(l, ParticleTypes.END_ROD, c, 8, 0.3, 0.04);
        FxNet.flare(l, c, second ? 1.8F : 1.3F, color(), 14);
        Fx.sound(l, c, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, second ? 1.25F : 0.95F);
    }

    // ================================================================== steal

    static boolean canTake(ServerLevel l, BlockPos pos, BlockState s) {
        if (s.isAir()) return false;
        if (s.getDestroySpeed(l, pos) < 0) return false;
        Block b = s.getBlock();
        if (b instanceof NetherPortalBlock || b instanceof EndPortalBlock || b instanceof EndGatewayBlock || b instanceof MovingPistonBlock) return false;
        if (b instanceof LiquidBlock && !s.getFluidState().isSource()) return false;
        return true;
    }

    private static void steal(ServerPlayer p, Session s) {
        ServerLevel l = p.serverLevel();
        if (HeistData.list(p).size() >= HeistData.MAX) { Fx.fail(p, K + "full", HeistData.MAX); return; }
        int x0 = Math.min(s.a.getX(), s.b.getX()), y0 = Math.min(s.a.getY(), s.b.getY()), z0 = Math.min(s.a.getZ(), s.b.getZ());
        int sx = Math.abs(s.a.getX() - s.b.getX()) + 1, sy = Math.abs(s.a.getY() - s.b.getY()) + 1, sz = Math.abs(s.a.getZ() - s.b.getZ()) + 1;
        if (sx > HeistData.MAX_SIDE || sy > HeistData.MAX_SIDE || sz > HeistData.MAX_SIDE) { Fx.fail(p, K + "too_big", sx, sy, sz); return; }
        // candidates top-down (fragile things on top leave first)
        List<long[]> order = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = sy - 1; y >= 0; y--)
            for (int z = 0; z < sz; z++)
                for (int x = 0; x < sx; x++) {
                    m.set(x0 + x, y0 + y, z0 + z);
                    if (!l.isLoaded(m)) continue;
                    if (canTake(l, m, l.getBlockState(m))) order.add(new long[]{m.asLong(), (y * (long) sz + z) * sx + x});
                }
        if (order.isEmpty()) { Fx.fail(p, K + "nothing"); return; }
        HeistData h = new HeistData(System.nanoTime() ^ p.getUUID().getLeastSignificantBits(), sx, sy, sz);
        h.store(p);
        exit(p);
        UUID id = p.getUUID();
        BUSY.add(id);

        Vec3 boxMin = new Vec3(x0, y0, z0), boxMax = new Vec3(x0 + sx, y0 + sy, z0 + sz);
        Vec3 centre = boxMin.add(boxMax).scale(0.5);
        Vec3 gather = new Vec3(centre.x, boxMax.y + 2.5, centre.z);
        int col = color();
        int orbId = FxNet.newId(), tetherId = FxNet.newId(), portalId = FxNet.newId(), runeId = FxNet.newId();
        int n = order.size();
        int wave = Math.max(16, Math.min(400, (n + 9) / 10));
        int perWaveFx = 40;

        FxNet.runes(l, runeId, new Vec3(centre.x, y0 + 0.02, centre.z), Math.max(sx, sz) * 0.75F + 1, col, 80, 1.6F);
        FxNet.shockwave(l, new Vec3(centre.x, y0 + 0.05, centre.z), Math.max(sx, sz) * 0.9F + 2, col, 22);
        FxNet.flashAround(l, centre, 24, col, 0.18F, 8);
        FxNet.shake(l, centre, 24, 0.5F, 10);
        Fx.sound(l, centre, SoundEvents.BEACON_POWER_SELECT, 0.7F, 0.6F);
        Fx.sound(l, centre, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 0.5F);

        Animator anim = new Animator();
        Tasks.add(new Tasks.Task() {
            int cursor, taken, skipped;
            boolean aborted;

            @Override
            public boolean tick(int age) {
                if (age % 20 == 0) {
                    FxNet.orb(l, orbId, gather, 0.55F + Math.min(1.2F, taken / 900F), col, 40, -1, Vec3.ZERO);
                    FxNet.portal(l, portalId, gather, p.position().subtract(gather).normalize(), 1.4F, col, 40);
                    FxNet.tetherPoint(l, tetherId, p, gather, col, 0.12F, 40);
                }
                if (!aborted && cursor < n && age % 2 == 0) {
                    if (p.isRemoved() || p.hasDisconnected() || !p.isAlive()) aborted = true;
                    else wave(age);
                }
                anim.tick(l);
                if (age % 3 == 0) Fx.at(l, ParticleTypes.REVERSE_PORTAL, gather, 6, 0.4, 0.02);
                return (!aborted && cursor < n) || anim.busy();
            }

            private void wave(int age) {
                int end = Math.min(n, cursor + wave);
                int step = Math.max(1, (end - cursor) / perWaveFx);
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                double sumY = 0; int cnt = 0;
                try {
                    for (int k = cursor; k < end; k++) {
                        pos.set(order.get(k)[0]);
                        int idx = (int) order.get(k)[1];
                        if (!l.isLoaded(pos)) { skipped++; continue; }
                        BlockState st = l.getBlockState(pos);
                        if (!canTake(l, pos, st)) { skipped++; continue; }
                        BlockPos imm = pos.immutable();
                        if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(l, imm, st, p)).isCanceled()) { skipped++; continue; }
                        BlockEntity be = l.getBlockEntity(imm);
                        CompoundTag nbt = be == null ? null : be.saveWithFullMetadata(l.registryAccess());
                        h.set(idx, st, nbt);
                        if (be != null) { Clearable.tryClear(be); l.removeBlockEntity(imm); }
                        l.setBlock(imm, Blocks.AIR.defaultBlockState(), TAKE_FLAGS);
                        SceneAnchor.moved(l, imm);
                        taken++;
                        sumY += imm.getY(); cnt++;
                        if ((k - cursor) % step == 0 && anim.size() < 320) anim.steal(l, st, Vec3.atCenterOf(imm), gather, p);
                        else if (k % 3 == 0) l.sendParticles(Fx.dust(Stone.SPACE, 1.1F), imm.getX() + 0.5, imm.getY() + 0.5, imm.getZ() + 0.5, 1, 0.25, 0.25, 0.25, 0);
                    }
                } finally {
                    cursor = end;
                    h.store(p);
                }
                if (cnt > 0) {
                    Vec3 layer = new Vec3(centre.x, sumY / cnt + 0.5, centre.z);
                    FxNet.shockwave(l, layer, Math.max(sx, sz) * 0.7F + 1, col, 14);
                    Fx.line(l, layer, gather, 0.6, Fx.light(Stone.SPACE, 0.9F));
                    Fx.sound(l, layer, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.55F, 0.7F + (float) cursor / n * 0.9F);
                }
            }

            @Override
            public void end() {
                BUSY.remove(id);
                anim.clear();
                FxNet.kill(l, gather, orbId); FxNet.kill(l, gather, tetherId); FxNet.kill(l, gather, portalId); FxNet.kill(l, gather, runeId);
                if (taken == 0) HeistData.remove(p, h.id); else h.store(p);
                if (p.hasDisconnected()) return;
                FxNet.beam(l, gather, p.position().add(0, 1, 0), col, 0.35F, 10);
                FxNet.spiral(l, p.position(), 1.1F, 2.6F, col, 26, p.getId());
                FxNet.flash(p, col, 0.25F, 8);
                Fx.sound(l, p.position(), SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.6F);
                Fx.sound(l, p.position(), SoundEvents.PLAYER_LEVELUP, 0.4F, 1.8F);
                if (taken == 0) Fx.barGray(p, K + "nothing");
                else Fx.bar(p, Stone.SPACE, K + "stolen", taken, skipped, HeistData.list(p).size(), HeistData.MAX);
            }
        });
    }

    // ================================================================== place

    static boolean replaceable(BlockState s) { return s.isAir() || s.canBeReplaced(); }

    private static void place(ServerPlayer p, Session s, BlockPos anchor) {
        ServerLevel l = p.serverLevel();
        HeistData h = HeistData.find(p, s.heist);
        if (h == null || h.count() == 0) { exit(p); Fx.fail(p, K + "none"); return; }
        int rot = s.rot; boolean mirror = s.mirror;
        int[] dims = h.size(rot);
        BlockPos origin = anchor.offset(-dims[0] / 2, 0, -dims[2] / 2);
        List<int[]> cells = new ArrayList<>(); // cell index, x, y, z (world)
        int blocked = 0;
        List<BlockPos> bad = new ArrayList<>();
        for (int y = 0; y < h.sy; y++)
            for (int z = 0; z < h.sz; z++)
                for (int x = 0; x < h.sx; x++) {
                    int i = h.index(x, y, z);
                    if (h.cells[i] < 0) continue;
                    BlockPos w = origin.offset(h.offset(x, y, z, rot, mirror));
                    boolean ok = l.isInWorldBounds(w) && l.getWorldBorder().isWithinBounds(w) && l.isLoaded(w)
                            && Terrain.may(p, l, w) && replaceable(l.getBlockState(w));
                    if (!ok) { blocked++; if (bad.size() < 40) bad.add(w); continue; }
                    cells.add(new int[]{i, w.getX(), w.getY(), w.getZ()});
                }
        if (blocked > 0) {
            for (BlockPos b : bad) l.sendParticles(p, Fx.dust(0xFF3030, 1.4F), true, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0);
            Fx.sound(l, p.position(), SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.6F);
            Fx.fail(p, K + "obstructed", blocked);
            return;
        }
        cells.sort((a, b) -> Integer.compare(a[2], b[2]));
        exit(p);
        UUID id = p.getUUID();
        BUSY.add(id);

        int n = cells.size();
        int wave = Math.max(16, Math.min(400, (n + 9) / 10));
        int col = color();
        Vec3 centre = new Vec3(origin.getX() + dims[0] / 2.0, origin.getY(), origin.getZ() + dims[2] / 2.0);
        Vec3 source = p.getEyePosition().add(0, 1.6, 0).add(p.getLookAngle().scale(0.8));
        int orbId = FxNet.newId(), runeId = FxNet.newId(), tetherId = FxNet.newId();
        FxNet.runes(l, runeId, centre.add(0, 0.02, 0), Math.max(dims[0], dims[2]) * 0.75F + 1, col, 60, -1.4F);
        FxNet.orb(l, orbId, source, 0.7F, col, 60, -1, Vec3.ZERO);
        FxNet.shockwave(l, centre.add(0, 0.05, 0), Math.max(dims[0], dims[2]) * 0.9F + 2, col, 24);
        Fx.sound(l, centre, SoundEvents.BEACON_ACTIVATE, 0.6F, 0.8F);
        Fx.sound(l, source, SoundEvents.ENDERMAN_TELEPORT, 0.5F, 1.4F);

        final int FLY = 8;
        Animator anim = new Animator();
        Tasks.add(new Tasks.Task() {
            int announced, placed, kept;
            boolean aborted;

            @Override
            public boolean tick(int age) {
                if (age % 20 == 0) {
                    FxNet.orb(l, orbId, source, 0.7F, col, 40, -1, Vec3.ZERO);
                    FxNet.runes(l, runeId, centre.add(0, 0.02, 0), Math.max(dims[0], dims[2]) * 0.75F + 1, col, 40, -1.4F);
                    FxNet.tetherPoint(l, tetherId, p, source, col, 0.1F, 40);
                }
                if (!aborted && (p.isRemoved() || p.hasDisconnected() || !p.isAlive())) aborted = true;
                // announce: blocks fly out of the light...
                if (!aborted && announced < n && age % 2 == 0) {
                    int end = Math.min(n, announced + wave);
                    int step = Math.max(1, (end - announced) / 36);
                    for (int k = announced; k < end; k++) {
                        if ((k - announced) % step != 0 || anim.size() >= 300) continue;
                        int[] c = cells.get(k);
                        BlockState st = h.get(c[0]);
                        if (st != null) anim.materialize(l, HeistData.transform(st, rot, mirror), source, new Vec3(c[1] + 0.5, c[2] + 0.5, c[3] + 0.5), FLY);
                    }
                    announced = end;
                    Fx.sound(l, source, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.45F, 0.9F + (float) announced / n);
                }
                // ...and land FLY ticks later
                if (!aborted && age >= FLY && (age - FLY) % 2 == 0) {
                    int start = ((age - FLY) / 2) * wave, end = Math.min(Math.min(n, announced), start + wave);
                    if (start < end) land(start, end);
                }
                anim.tick(l);
                int landedUpTo = age < FLY ? 0 : Math.min(n, ((age - FLY) / 2 + 1) * wave);
                return (!aborted && landedUpTo < n) || anim.busy();
            }

            private void land(int start, int end) {
                double sumY = 0; int cnt = 0;
                try {
                    for (int k = start; k < end; k++) {
                        int[] c = cells.get(k);
                        BlockState st = h.get(c[0]);
                        if (st == null) continue;
                        BlockPos w = new BlockPos(c[1], c[2], c[3]);
                        if (!l.isLoaded(w) || !replaceable(l.getBlockState(w))) { kept++; continue; }
                        BlockState target = HeistData.transform(st, rot, mirror);
                        CompoundTag nbt = h.bes.get(c[0]);
                        h.clear(c[0]);
                        l.setBlock(w, target, PLACE_FLAGS);
                        if (nbt != null) {
                            BlockEntity be = l.getBlockEntity(w);
                            if (be != null) {
                                try { be.loadWithComponents(nbt, l.registryAccess()); }
                                catch (Exception ex) { com.infinitystones.tensura.InfinityStones.LOGGER.warn("[extra] data restore failed", ex); }
                                be.setChanged();
                                l.sendBlockUpdated(w, target, target, Block.UPDATE_ALL);
                            }
                        }
                        placed++;
                        sumY += c[2]; cnt++;
                        if (k % 4 == 0) l.sendParticles(ParticleTypes.END_ROD, c[1] + 0.5, c[2] + 0.5, c[3] + 0.5, 1, 0.3, 0.3, 0.3, 0.01);
                    }
                } finally {
                    if (h.count() == 0) HeistData.remove(p, h.id); else h.store(p);
                }
                if (cnt > 0) {
                    Vec3 layer = new Vec3(centre.x, sumY / cnt + 0.1, centre.z);
                    FxNet.shockwave(l, layer, Math.max(dims[0], dims[2]) * 0.65F + 1, col, 12);
                    Fx.sound(l, layer, SoundEvents.AMETHYST_BLOCK_PLACE, 0.6F, 0.8F + (float) end / n * 0.8F);
                }
            }

            @Override
            public void end() {
                BUSY.remove(id);
                anim.clear();
                FxNet.kill(l, source, orbId); FxNet.kill(l, centre, runeId); FxNet.kill(l, source, tetherId);
                int left = h.count();
                if (left == 0) HeistData.remove(p, h.id); else h.store(p);
                if (p.hasDisconnected()) return;
                FxNet.shockwave(l, centre.add(0, 0.05, 0), Math.max(dims[0], dims[2]) + 3, col, 26);
                FxNet.flashAround(l, centre, 24, col, 0.2F, 8);
                FxNet.shake(l, centre, 24, 0.6F, 10);
                Fx.at(l, ParticleTypes.END_ROD, centre.add(0, dims[1] / 2.0, 0), 40, Math.max(dims[0], dims[2]) / 2.0, 0.05);
                Fx.sound(l, centre, SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.3F);
                Fx.sound(l, centre, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 0.6F);
                if (left == 0) Fx.bar(p, Stone.SPACE, K + "placed", placed);
                else Fx.bar(p, Stone.SPACE, K + "placed_partial", placed, left);
            }
        });
    }

    // ================================================================== flying block displays (purely visual)

    /** Small scheduler for block_display animations (never touches real blocks). */
    static final class Animator {
        private static final class Fly {
            final Display.BlockDisplay d; int age; final int kind; final Vec3 to; final int flyTicks; final ServerPlayer p;
            Fly(Display.BlockDisplay d, int kind, Vec3 to, int flyTicks, ServerPlayer p) { this.d = d; this.kind = kind; this.to = to; this.flyTicks = flyTicks; this.p = p; }
        }
        private final List<Fly> flies = new ArrayList<>();

        int size() { return flies.size(); }
        boolean busy() { return !flies.isEmpty(); }

        /** Block copy at from: shrinks while flying into the gather light, then into the player. */
        void steal(ServerLevel l, BlockState s, Vec3 from, Vec3 gather, ServerPlayer p) {
            Display.BlockDisplay d = Displays.block(l, s, from, 0.98F, flies.size() % 3 == 0 ? Stone.SPACE.color() : 0, 10);
            if (d != null) flies.add(new Fly(d, 0, gather.add((l.random.nextDouble() - 0.5) * 0.8, (l.random.nextDouble() - 0.5) * 0.8, (l.random.nextDouble() - 0.5) * 0.8), 10, p));
        }

        /** Tiny copy born in the light at from: grows while flying to its landing cell. */
        void materialize(ServerLevel l, BlockState s, Vec3 from, Vec3 to, int ticks) {
            Display.BlockDisplay d = Displays.block(l, s, from, 0.12F, flies.size() % 3 == 0 ? Stone.SPACE.color() : 0, ticks - 1);
            if (d != null) flies.add(new Fly(d, 1, to, ticks, null));
        }

        void tick(ServerLevel l) {
            for (Iterator<Fly> it = flies.iterator(); it.hasNext(); ) {
                Fly f = it.next();
                int a = f.age++;
                if (f.d.isRemoved()) { it.remove(); continue; }
                if (f.kind == 0) {
                    if (a == 2) {
                        Displays.pose(f.d, 0.45F, l.random.nextFloat() * 6.28F, l.random.nextFloat() * 6.28F, 10);
                        f.d.teleportTo(f.to.x, f.to.y, f.to.z);
                    } else if (a == 13) {
                        Vec3 chest = f.p == null || f.p.isRemoved() ? f.to : f.p.position().add(0, 1.1, 0);
                        Displays.pose(f.d, 0.04F, l.random.nextFloat() * 6.28F, l.random.nextFloat() * 6.28F, 8);
                        f.d.teleportTo(chest.x, chest.y, chest.z);
                    } else if (a >= 22) { Displays.remove(f.d); it.remove(); }
                } else {
                    if (a == 1) {
                        Displays.pose(f.d, 1.0F, 0, 0, f.flyTicks - 1);
                        f.d.teleportTo(f.to.x, f.to.y, f.to.z);
                    } else if (a >= f.flyTicks + 1) {
                        l.sendParticles(Fx.light(Stone.SPACE, 0.8F), f.to.x, f.to.y, f.to.z, 2, 0.3, 0.3, 0.3, 0);
                        Displays.remove(f.d); it.remove();
                    }
                }
            }
        }

        void clear() { for (Fly f : flies) Displays.remove(f.d); flies.clear(); }
    }
}
