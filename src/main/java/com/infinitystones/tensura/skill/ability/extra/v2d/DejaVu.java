package com.infinitystones.tensura.skill.ability.extra.v2d;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.compat.ClaimsCompat;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import com.infinitystones.tensura.skill.ability.extra.time.TimeNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deja Vu (Space + Time, hidden). Left (aim): marks a sphere (Alt+scroll radius 4-16) with a blue-green ring and
 * remembers every block (with block-entity data), every creature (position, health) and every player (position, health,
 * food, inventory) inside. After 10 s - a big clock dome counts down - a rewind wave sweeps outwards and everything
 * returns to that moment. Right: bring it back at once. Blocks inside claims of others are left alone. Items dropped
 * since the mark vanish (their owners get the old inventory back). Snapshot is capped (radius 16, 256 block entities,
 * 128 creatures) and kept in memory only.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class DejaVu implements Ability {
    public static final DejaVu INSTANCE = new DejaVu();
    private static final String MODE = "space.deja_vu";
    private static final int TEAL = 0x2FC8B8, BLUE = 0x2F8CFF, DELAY = 200, SWEEP = 24, MAX_BE = 256, MAX_ENTITIES = 128;

    private record EntSnap(UUID id, Vec3 pos, float yaw, float pitch, float health) {}
    private record PlayerSnap(UUID id, Vec3 pos, float yaw, float pitch, float health, int food, ListTag inventory) {}

    private static final class Snap {
        final UUID owner; final ServerLevel level; final Vec3 c; final int r; final long time;
        final List<BlockState> palette = new ArrayList<>(); final Map<BlockState, Integer> index = new IdentityHashMap<>();
        int[] cells; final Map<BlockPos, CompoundTag> be = new HashMap<>();
        final List<EntSnap> ents = new ArrayList<>(); final List<PlayerSnap> players = new ArrayList<>();
        final int ring = FxNet.newId(), dome = FxNet.newId(), clock = V2dNet.newId();
        boolean trigger, cancelled, sweeping, entitiesDone; int sweepAge; List<BlockPos> plan; int cursor;
        Map<Long, Boolean> claim = new HashMap<>();
        Snap(UUID owner, ServerLevel level, Vec3 c, int r) { this.owner = owner; this.level = level; this.c = c; this.r = r; this.time = level.getGameTime(); }
    }

    private static final Map<UUID, Snap> SNAPS = new HashMap<>();

    @Override public Stone stone() { return Stone.SPACE; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (!Stone.TIME.isHeldBy(p)) return Fx.fail(p, "infinity.extra.v2d.deja.need_time");
        Snap old = SNAPS.get(p.getUUID());
        if (alt) {
            if (old == null || old.sweeping) return Fx.fail(p, "infinity.extra.v2d.deja.none");
            old.trigger = true;
            return true;
        }
        if (old != null) {
            if (old.sweeping) return Fx.fail(p, "infinity.extra.v2d.deja.busy");
            old.cancelled = true;
            SNAPS.remove(p.getUUID(), old);
        }
        int r = ExtraSettings.get(p, MODE, 8, 4, 16, 1);
        ServerLevel l = p.serverLevel();
        Vec3 ground = Aim.ground(p, 48);
        Vec3 c = ground.add(0, r * 0.4, 0);
        Snap s = new Snap(p.getUUID(), l, c, r);
        capture(s);
        SNAPS.put(p.getUUID(), s);
        FxNet.shockwave(l, ground.add(0, 0.1, 0), r * 1.2F, TEAL, 22);
        FxNet.shockwave(l, ground.add(0, 0.2, 0), r * 0.8F, BLUE, 18);
        FxNet.flare(l, c, 3F, TEAL, 10);
        Fx.sound(l, c, SoundEvents.BEACON_ACTIVATE, 0.6F, 1.5F);
        Fx.sound(l, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.6F);
        Fx.sound(l, c, SoundEvents.BELL_RESONATE, 0.5F, 1.3F);
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) { return step(s, age, ground); }
            @Override public void end() { finish(s, ground); }
        });
        Fx.bar(p, Stone.SPACE, "infinity.extra.v2d.deja.marked", r);
        return true;
    }

    // ------------------------------------------------------------------ capture

    private static void capture(Snap s) {
        ServerLevel l = s.level;
        int r = s.r, side = 2 * r + 1;
        s.cells = new int[side * side * side];
        BlockPos center = BlockPos.containing(s.c);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++, i++) {
            s.cells[i] = -1;
            if (x * x + y * y + z * z > r * r) continue;
            m.set(center.getX() + x, center.getY() + y, center.getZ() + z);
            if (m.getY() < l.getMinBuildHeight() || m.getY() >= l.getMaxBuildHeight() || !l.isLoaded(m)) continue;
            BlockState st = l.getBlockState(m);
            Integer idx = s.index.get(st);
            if (idx == null) { idx = s.palette.size(); s.palette.add(st); s.index.put(st, idx); }
            s.cells[i] = idx;
            if (st.hasBlockEntity() && s.be.size() < MAX_BE) {
                BlockEntity be = l.getBlockEntity(m);
                if (be != null) s.be.put(m.immutable(), be.saveWithFullMetadata(l.registryAccess()));
            }
        }
        AABB box = new AABB(s.c, s.c).inflate(r);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator() && e.getBoundingBox().getCenter().distanceToSqr(s.c) <= (double) r * r)) {
            if (e instanceof ServerPlayer sp) {
                s.players.add(new PlayerSnap(sp.getUUID(), sp.position(), sp.getYRot(), sp.getXRot(), sp.getHealth(), sp.getFoodData().getFoodLevel(), sp.getInventory().save(new ListTag())));
            } else if (s.ents.size() < MAX_ENTITIES) {
                s.ents.add(new EntSnap(e.getUUID(), e.position(), e.getYRot(), e.getXRot(), e.getHealth()));
            }
        }
    }

    // ------------------------------------------------------------------ the countdown and the rewind

    private static boolean step(Snap s, int age, Vec3 ground) {
        if (s.cancelled) return false;
        ServerLevel l = s.level;
        ServerPlayer owner = l.getServer().getPlayerList().getPlayer(s.owner);
        if (owner == null || owner.isRemoved()) { s.cancelled = true; return false; }
        if (!s.sweeping) {
            int left = DELAY - age;
            if (s.trigger || left <= 0) { beginSweep(s, owner, ground); return true; }
            if (age % 20 == 0) {
                FxNet.runes(l, s.ring, ground.add(0, 0.05, 0), s.r * 0.9F, TEAL, 30, 1.5F);
                FxNet.dome(l, s.dome, s.c, s.r, BLUE, 30);
            }
            if (age % 60 == 0) V2dNet.clock(l, s.clock, s.c.add(0, s.r * 0.55, 0), Vec3.ZERO, Math.max(2F, s.r * 0.4F), TEAL, BLUE, left + 5, age / (float) DELAY);
            int interval = left < 60 ? 5 : left < 100 ? 10 : 20;
            if (age % interval == 0) {
                Fx.sound(l, s.c, SoundEvents.NOTE_BLOCK_HAT, 0.4F, 0.5F + (1 - left / (float) DELAY) * 1.0F);
                Fx.at(l, Fx.dust(TEAL, 1.2F), s.c.add(0, s.r * 0.55, 0), 4, s.r * 0.15, 0);
            }
            return true;
        }
        return sweep(s, owner, ground);
    }

    private static void beginSweep(Snap s, ServerPlayer owner, Vec3 ground) {
        ServerLevel l = s.level;
        s.sweeping = true;
        s.plan = new ArrayList<>();
        int r = s.r, side = 2 * r + 1;
        BlockPos center = BlockPos.containing(s.c);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++, i++) {
            int idx = s.cells[i];
            if (idx < 0) continue;
            m.set(center.getX() + x, center.getY() + y, center.getZ() + z);
            if (!l.isLoaded(m)) continue;
            BlockState want = s.palette.get(idx), cur = l.getBlockState(m);
            boolean differs = cur != want;
            if (!differs && s.be.containsKey(m)) {
                BlockEntity be = l.getBlockEntity(m);
                differs = be == null || !be.saveWithFullMetadata(l.registryAccess()).equals(s.be.get(m));
            }
            if (differs) s.plan.add(m.immutable());
        }
        s.plan.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(s.c)));
        V2dNet.kill(l, s.c, s.clock);
        TimeNet.restore(l, ground, s.r * 1.1F, SWEEP + 16);
        FxNet.shockwave(l, ground.add(0, 0.1, 0), s.r * 1.4F, TEAL, 24);
        FxNet.spiral(l, ground, s.r * 0.8F, -s.r * 1.4F, BLUE, 30, -1);
        FxNet.flashAround(l, s.c, s.r + 10, 0xCFFFF6, 0.35F, 10);
        FxNet.shake(l, s.c, s.r + 16, 0.9F, 16);
        Fx.sound(l, s.c, SoundEvents.BEACON_DEACTIVATE, 0.7F, 0.6F);
        Fx.sound(l, s.c, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 0.6F, 0.7F);
        Fx.sound(l, s.c, SoundEvents.BELL_RESONATE, 0.6F, 0.6F);
        discardNew(s);
    }

    private static boolean sweep(Snap s, ServerPlayer owner, Vec3 ground) {
        ServerLevel l = s.level;
        int age = s.sweepAge++;
        double front = (age + 1) * (s.r * 1.1 / SWEEP);
        while (s.cursor < s.plan.size()) {
            BlockPos pos = s.plan.get(s.cursor);
            if (Math.sqrt(pos.distToCenterSqr(s.c)) > front) break;
            s.cursor++;
            restore(s, owner, pos);
        }
        if (age % 4 == 0) FxNet.shockwave(l, s.c, (float) Math.max(1, front), age % 8 == 0 ? TEAL : BLUE, 12);
        if (age == SWEEP / 2) restoreEntities(s);
        if (age % 6 == 0) Fx.sound(l, s.c, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 1.8F - age * 0.03F);
        return age < SWEEP || s.cursor < s.plan.size();
    }

    private static void restore(Snap s, ServerPlayer owner, BlockPos pos) {
        ServerLevel l = s.level;
        if (!l.isLoaded(pos)) return;
        if (!s.claim.computeIfAbsent(new ChunkPos(pos).toLong(), k -> ClaimsCompat.canModify(owner, l, pos))) return;
        int r = s.r, side = 2 * r + 1;
        BlockPos center = BlockPos.containing(s.c);
        int idx = s.cells[((pos.getX() - center.getX() + r) * side + (pos.getY() - center.getY() + r)) * side + (pos.getZ() - center.getZ() + r)];
        if (idx < 0) return;
        BlockState want = s.palette.get(idx);
        if (l.getBlockState(pos) != want) l.setBlock(pos, want, 2 | 16);
        CompoundTag tag = s.be.get(pos);
        if (tag != null) {
            BlockEntity be = l.getBlockEntity(pos);
            if (be != null) { be.loadWithComponents(tag.copy(), l.registryAccess()); be.setChanged(); }
        }
        if (l.random.nextInt(4) == 0) {
            Vec3 v = Vec3.atCenterOf(pos);
            Fx.at(l, l.random.nextBoolean() ? Fx.dust(TEAL, 1F) : Fx.dust(BLUE, 1F), v, 2, 0.35, 0);
            if (l.random.nextInt(6) == 0) Fx.at(l, ParticleTypes.END_ROD, v, 1, 0.3, 0.03);
        }
    }

    private static void restoreEntities(Snap s) {
        ServerLevel l = s.level;
        for (EntSnap e : s.ents) {
            Entity en = l.getEntity(e.id);
            if (!(en instanceof LivingEntity le) || !le.isAlive()) continue;
            Vec3 from = le.getBoundingBox().getCenter();
            le.moveTo(e.pos.x, e.pos.y, e.pos.z, e.yaw, e.pitch);
            le.setDeltaMovement(Vec3.ZERO);
            le.fallDistance = 0;
            le.clearFire();
            le.setHealth(Math.min(e.health, le.getMaxHealth()));
            if (le instanceof net.minecraft.world.entity.Mob mob) mob.getNavigation().stop();
            trail(l, from, le.getBoundingBox().getCenter());
        }
        for (PlayerSnap ps : s.players) {
            ServerPlayer sp = l.getServer().getPlayerList().getPlayer(ps.id);
            if (sp == null || !sp.isAlive() || sp.serverLevel() != l) continue;
            Vec3 from = sp.getBoundingBox().getCenter();
            sp.teleportTo(l, ps.pos.x, ps.pos.y, ps.pos.z, ps.yaw, ps.pitch);
            sp.setDeltaMovement(Vec3.ZERO);
            sp.fallDistance = 0;
            sp.clearFire();
            sp.setHealth(Math.min(ps.health, sp.getMaxHealth()));
            sp.getFoodData().setFoodLevel(ps.food);
            sp.getInventory().clearContent();
            sp.getInventory().load(ps.inventory.copy());
            sp.inventoryMenu.broadcastChanges();
            sp.containerMenu.broadcastChanges();
            trail(l, from, sp.getBoundingBox().getCenter());
            FxNet.flash(sp, 0xCFFFF6, 0.3F, 8);
        }
    }

    private static void trail(ServerLevel l, Vec3 a, Vec3 b) {
        Fx.line(l, a, b, 0.6, Fx.dust(BLUE, 0.8F));
        FxNet.flare(l, b, 1.6F, TEAL, 8);
    }

    /** Items and orbs created since the mark are gone (the inventories they came from are restored). */
    private static void discardNew(Snap s) {
        ServerLevel l = s.level;
        long elapsed = l.getGameTime() - s.time;
        AABB box = new AABB(s.c, s.c).inflate(s.r + 1);
        for (Entity e : l.getEntities((Entity) null, box, e -> (e instanceof ItemEntity || e instanceof ExperienceOrb) && e.isAlive()
                && e.getBoundingBox().getCenter().distanceToSqr(s.c) <= (double) (s.r + 1) * (s.r + 1) && e.tickCount < elapsed)) {
            Fx.at(l, Fx.dust(TEAL, 0.8F), e.position().add(0, 0.2, 0), 4, 0.2, 0);
            e.discard();
        }
    }

    private static void finish(Snap s, Vec3 ground) {
        SNAPS.remove(s.owner, s);
        ServerLevel l = s.level;
        if (s.sweeping && !s.cancelled) {
            ServerPlayer owner = l.getServer().getPlayerList().getPlayer(s.owner);
            if (owner != null) {
                while (s.cursor < s.plan.size()) restore(s, owner, s.plan.get(s.cursor++));
                if (s.sweepAge <= SWEEP / 2) restoreEntities(s);
            }
        }
        V2dNet.kill(l, s.c, s.clock);
        FxNet.kill(l, ground, s.ring);
        FxNet.kill(l, s.c, s.dome);
        if (!s.sweeping) Fx.at(l, Fx.dust(TEAL, 1.2F), s.c, 40, s.r * 0.4, 0.02);
        else {
            FxNet.shockwave(l, ground.add(0, 0.1, 0), s.r * 1.6F, 0xFFFFFF, 14);
            Fx.sound(l, s.c, SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.6F);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        Snap s = SNAPS.get(e.getEntity().getUUID());
        if (s != null && !s.sweeping) s.cancelled = true;
    }

    private DejaVu() {}
}
