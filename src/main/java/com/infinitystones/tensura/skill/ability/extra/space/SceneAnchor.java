package com.infinitystones.tensura.skill.ability.extra.space;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Âncora de Cenário / Scene Anchor. Left: drives a blue anchor (pillar + spinning rings) where you stand; the scene
 * in a cube of radius 4–12 (Alt+scroll) is recorded for 10 min. While anchored, blocks broken / blown up inside do
 * NOT drop (the scene is held) and containers inside resist explosions. Right: a blue wave sweeps the area and every
 * block broken while anchored comes back; blocks placed there since jump out as items (real drops); then you are
 * pulled back to the anchor from anywhere in the same dimension. One anchor per player, in memory only.
 * Dupe-safe: only blocks whose break was suppressed are recreated (block-entity contents are never restored).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class SceneAnchor implements Ability {
    public static final SceneAnchor INSTANCE = new SceneAnchor();
    private static final String MODE = "space.scene_anchor", K = "infinity.extra.scene_anchor.";
    private static final int LIFE = 20 * 60 * 10;

    private static final class Anchor {
        ServerLevel l; BlockPos c; int r; BlockState[] states;
        final LongOpenHashSet broken = new LongOpenHashSet();
        boolean dead, restoring;
        final int pillarId = FxNet.newId(), runeId = FxNet.newId(), cubeId = FxNet.newId();

        int side() { return r * 2 + 1; }
        boolean contains(Level lv, BlockPos p) {
            return lv == l && Math.abs(p.getX() - c.getX()) <= r && Math.abs(p.getY() - c.getY()) <= r && Math.abs(p.getZ() - c.getZ()) <= r;
        }
        int index(BlockPos p) { int s = side(); return ((p.getY() - c.getY() + r) * s + (p.getZ() - c.getZ() + r)) * s + (p.getX() - c.getX() + r); }
    }

    private static final Map<UUID, Anchor> ANCHORS = new HashMap<>();
    /** Explosion item suppression window: level → game time. */
    private static final Map<Level, Long> BOOM = new HashMap<>();
    private static boolean restoringNow;

    @Override public Stone stone() { return Stone.SPACE; }

    private static int col() { return Stone.SPACE.color() & 0xFFFFFF; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) { return alt ? recall(p) : drop(p); }

    // ------------------------------------------------------------------ drop the anchor

    private boolean drop(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Anchor old = ANCHORS.remove(p.getUUID());
        if (old != null) old.dead = true;
        Anchor a = new Anchor();
        a.l = l;
        a.c = p.blockPosition();
        a.r = ExtraSettings.get(p, MODE, 8, 4, 12, 1);
        int s = a.side();
        a.states = new BlockState[s * s * s];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = -a.r; y <= a.r; y++)
            for (int z = -a.r; z <= a.r; z++)
                for (int x = -a.r; x <= a.r; x++) {
                    m.set(a.c.getX() + x, a.c.getY() + y, a.c.getZ() + z);
                    a.states[a.index(m)] = l.isLoaded(m) ? l.getBlockState(m) : null;
                }
        ANCHORS.put(p.getUUID(), a);
        UUID owner = p.getUUID();
        Vec3 base = Vec3.atBottomCenterOf(a.c);

        FxNet.pillar(l, a.pillarId, base, 0.45F, 5F, col(), 40);
        FxNet.runes(l, a.runeId, base.add(0, 0.03, 0), a.r * 0.6F, col(), 40, 0.8F);
        FxNet.shockwave(l, base.add(0, 0.05, 0), a.r + 1.5F, col(), 22);
        FxNet.shockwave(l, base.add(0, 0.08, 0), a.r * 0.6F, 0xFFFFFF, 16);
        FxNet.dome(l, 0, Vec3.atCenterOf(a.c), a.r + 0.5F, col(), 24);
        FxNet.beam(l, base.add(0, 30, 0), base, col(), 0.4F, 10);
        FxNet.flashAround(l, base, 16, col(), 0.2F, 8);
        FxNet.shake(l, base, 16, 0.8F, 8);
        Fx.burstRing(l, base.add(0, 0.2, 0), 40, Fx.dust(Stone.SPACE, 1.5F), 0.4);
        Fx.sound(l, base, SoundEvents.ANVIL_LAND, 0.5F, 0.6F);
        Fx.sound(l, base, SoundEvents.BEACON_ACTIVATE, 0.6F, 1.2F);
        Fx.sound(l, base, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 0.6F, 1.3F);
        Fx.bar(p, Stone.SPACE, K + "set", a.r);

        Tasks.add(new Tasks.Task() {
            @Override
            public boolean tick(int age) {
                if (a.dead || age > LIFE || ANCHORS.get(owner) != a) return false;
                if (age % 30 == 0) {
                    FxNet.pillar(l, a.pillarId, base, 0.45F, 5F, col(), 45);
                    FxNet.runes(l, a.runeId, base.add(0, 0.03, 0), a.r * 0.6F, col(), 45, 0.8F);
                    FxNet.cube(l, a.cubeId, Vec3.atCenterOf(a.c), a.r + 0.5F, col(), 45, -1);
                }
                if (age % 10 == 0) Fx.ring(l, base.add(0, 0.2 + (age / 10 % 5), 0), 0.9, 10, Fx.light(Stone.SPACE, 0.8F));
                if (age == LIFE - 200) {
                    ServerPlayer op = l.getServer().getPlayerList().getPlayer(owner);
                    if (op != null) Fx.barGray(op, K + "fading");
                }
                return true;
            }

            @Override
            public void end() {
                ANCHORS.remove(owner, a);
                FxNet.kill(l, base, a.pillarId); FxNet.kill(l, base, a.runeId); FxNet.kill(l, base, a.cubeId);
                Fx.at(l, ParticleTypes.END_ROD, base.add(0, 1.5, 0), 20, 0.4, 0.05);
            }
        });
        return true;
    }

    // ------------------------------------------------------------------ recall

    private boolean recall(ServerPlayer p) {
        Anchor a = ANCHORS.get(p.getUUID());
        if (a == null) return Fx.fail(p, K + "none");
        if (a.restoring) return Fx.fail(p, K + "busy");
        if (p.level() != a.l) return Fx.fail(p, K + "other_dim");
        ServerLevel l = a.l;
        Vec3 base = Vec3.atBottomCenterOf(a.c);

        // pull the caster back first
        Vec3 from = p.position();
        Vec3 land = Ground.fits(l, p, base) ? base : Ground.safeAlong(l, p, base.add(0, 3, 0), base);
        if (land != null) {
            FxNet.tunnel(l, from.add(0, 1, 0), land.add(0, 1, 0), 1.2F, col(), 20);
            FxNet.portal(l, 0, from.add(0, 1, 0), land.subtract(from).normalize(), 1.4F, col(), 16);
            Fx.sound(l, from, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 0.8F);
            if (p.isPassenger()) p.stopRiding();
            p.teleportTo(land.x, land.y, land.z);
            p.resetFallDistance();
            p.setDeltaMovement(Vec3.ZERO);
            FxNet.flash(p, col(), 0.3F, 8);
            FxNet.spiral(l, land, 0.9F, 2.4F, col(), 20, p.getId());
        }

        // which cells to rebuild / clear, ordered by distance (the wave)
        List<long[]> work = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = -a.r; y <= a.r; y++)
            for (int z = -a.r; z <= a.r; z++)
                for (int x = -a.r; x <= a.r; x++) {
                    m.set(a.c.getX() + x, a.c.getY() + y, a.c.getZ() + z);
                    BlockState rec = a.states[a.index(m)];
                    if (rec == null || !l.isLoaded(m)) continue;
                    BlockState cur = l.getBlockState(m);
                    if (cur == rec) continue;
                    boolean wasBroken = a.broken.contains(m.asLong());
                    boolean placedSince = (rec.isAir() || rec.canBeReplaced()) && !cur.isAir();
                    if (!wasBroken && !placedSince) continue;
                    work.add(new long[]{m.asLong(), x * x + y * y + z * z});
                }
        work.sort((u, v) -> Long.compare(u[1], v[1]));
        a.restoring = true;
        FxNet.shockwave(l, base.add(0, 0.05, 0), a.r + 2F, col(), 30);
        FxNet.dome(l, 0, Vec3.atCenterOf(a.c), a.r + 0.5F, col(), 30);
        FxNet.flashAround(l, base, a.r + 8, col(), 0.25F, 10);
        FxNet.shake(l, base, a.r + 10, 0.7F, 12);
        Fx.sound(l, base, SoundEvents.BEACON_POWER_SELECT, 0.7F, 0.5F);
        Fx.sound(l, base, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.6F, 0.8F);

        Tasks.add(new Tasks.Task() {
            int cursor, rebuilt, popped;
            @Override
            public boolean tick(int age) {
                double radius = (age + 1) * 0.8;
                long lim = (long) (radius * radius);
                int done = 0;
                restoringNow = true;
                try {
                    while (cursor < work.size() && work.get(cursor)[1] <= lim && done < 400) {
                        BlockPos pos = BlockPos.of(work.get(cursor)[0]);
                        cursor++;
                        done++;
                        if (!l.isLoaded(pos)) continue;
                        BlockState rec = a.states[a.index(pos)];
                        BlockState cur = l.getBlockState(pos);
                        if (cur == rec) continue;
                        if (!cur.isAir() && !(cur.canBeReplaced() && cur.getFluidState().isEmpty())) {
                            BlockEntity be = l.getBlockEntity(pos);
                            Block.dropResources(cur, l, pos, be);
                            popped++;
                        }
                        l.setBlock(pos, rec, Block.UPDATE_ALL);
                        if (a.broken.remove(pos.asLong())) rebuilt++;
                        if (done % 3 == 0) l.sendParticles(Fx.light(Stone.SPACE, 1F), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0);
                    }
                } finally { restoringNow = false; }
                if (age % 3 == 0 && radius <= a.r * 1.8) {
                    FxNet.shockwave(l, base.add(0, 0.1, 0), (float) radius, col(), 8);
                    Fx.ring(l, base.add(0, 0.3, 0), radius, Math.min(80, (int) (radius * 6) + 8), Fx.dust(Stone.SPACE, 1.2F));
                }
                return cursor < work.size() && age < 400;
            }

            @Override
            public void end() {
                a.restoring = false;
                Fx.sound(l, base, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 0.7F);
                Fx.sound(l, base, SoundEvents.BEACON_DEACTIVATE, 0.5F, 1.5F);
                Fx.at(l, ParticleTypes.END_ROD, Vec3.atCenterOf(a.c), 40, a.r * 0.5, 0.05);
                if (!p.hasDisconnected()) Fx.bar(p, Stone.SPACE, K + "restored", rebuilt, popped);
            }
        });
        return true;
    }

    // ------------------------------------------------------------------ the scene is held

    private static @Nullable Anchor at(Level l, BlockPos pos) {
        if (ANCHORS.isEmpty() || l.isClientSide()) return null;
        for (Anchor a : ANCHORS.values()) if (!a.dead && a.contains(l, pos)) return a;
        return null;
    }

    /** A block was MOVED out of the scene by another power (Area Heist): never recreate it (no dupes). */
    static void moved(Level l, BlockPos pos) {
        Anchor a = at(l, pos);
        if (a != null) a.broken.remove(pos.asLong());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void broken(BlockEvent.BreakEvent e) {
        if (e.isCanceled() || !(e.getLevel() instanceof Level l)) return;
        Anchor a = at(l, e.getPos());
        if (a != null) a.broken.add(e.getPos().asLong());
    }

    @SubscribeEvent
    public static void drops(BlockDropsEvent e) {
        if (restoringNow) return;
        Anchor a = at(e.getLevel(), e.getPos());
        if (a == null) return;
        a.broken.add(e.getPos().asLong());
        e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void explosion(ExplosionEvent.Detonate e) {
        Level l = e.getLevel();
        if (l.isClientSide() || ANCHORS.isEmpty()) return;
        boolean any = false;
        for (var it = e.getAffectedBlocks().iterator(); it.hasNext(); ) {
            BlockPos pos = it.next();
            Anchor a = at(l, pos);
            if (a == null) continue;
            // containers inside the held scene resist (their contents would be lost otherwise)
            if (l.getBlockEntity(pos) != null) { it.remove(); continue; }
            if (!l.getBlockState(pos).isAir()) { a.broken.add(pos.asLong()); any = true; }
        }
        if (any) BOOM.put(l, l.getGameTime());
    }

    @SubscribeEvent
    public static void items(EntityJoinLevelEvent e) {
        if (restoringNow || BOOM.isEmpty() || !(e.getEntity() instanceof ItemEntity it)) return;
        Level l = e.getLevel();
        Long t = BOOM.get(l);
        if (t == null) return;
        if (t != l.getGameTime()) { BOOM.remove(l); return; }
        if (at(l, it.blockPosition()) != null) e.setCanceled(true);
    }
}
