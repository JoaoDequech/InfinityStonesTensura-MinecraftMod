package com.infinitystones.tensura.soul;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.BanishLog;
import com.infinitystones.tensura.skill.ability.Relocation;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Soul — Soul Capture. Left: the aimed mob's soul is torn out and pulled into the stone (exclusive animation: magic
 * circle, helix of soul fire, the body shrinking into the gauntlet hand). Right: the captured souls menu — release,
 * execute (sky sigils + ember lightning), send to the Soul World, or release already possessed.
 * Stored on the player (survives death): Rules.data(p) / "infinity_soul_jar" = [{nbt, name}].
 */
public final class SoulJar {
    private static final String KEY = "infinity_soul_jar";
    private static final int MAX = 54;
    public static final int RELEASE = 0, EXECUTE = 1, SOUL_WORLD = 2, POSSESS = 3, REFRESH = 9;
    private static final ResourceKey<Level> SOULWORLD = ResourceKey.create(Registries.DIMENSION, InfinityStones.id("soulworld"));
    private static final net.minecraft.resources.ResourceLocation SHRINK = InfinityStones.id("soul_shrink");
    private static final Map<UUID, Long> MENU_TOKEN = new HashMap<>();
    /** Mobs in a capture/execute animation (can't be picked twice). */
    private static final Set<UUID> BUSY = new HashSet<>();

    private static ListTag list(ServerPlayer p) { return Rules.data(p).getList(KEY, Tag.TAG_COMPOUND); }
    private static void save(ServerPlayer p, ListTag l) { Rules.data(p).put(KEY, l); }

    public static boolean press(ServerPlayer p, boolean menu) { return menu ? openMenu(p) : capture(p); }

    public static boolean openMenu(ServerPlayer p) {
        MENU_TOKEN.put(p.getUUID(), p.serverLevel().getGameTime() + 20 * 300);
        sendList(p, true);
        SoulFx.sound(p.serverLevel(), p.position(), SoundEvents.SOUL_ESCAPE.value(), 0.7F, 0.8F, p);
        return true;
    }

    private static void sendList(ServerPlayer p, boolean open) {
        List<CompoundTag> out = new ArrayList<>();
        for (Tag t : list(p)) out.add((CompoundTag) t);
        PacketDistributor.sendToPlayer(p, new SoulNet.Jar(out, open));
    }

    // ------------------------------------------------------------------ capture

    private static boolean capture(ServerPlayer p) {
        LivingEntity t = Aim.living(p, 32, e -> e instanceof Mob && !(e instanceof EnderDragon) && !BUSY.contains(e.getUUID()) && !Possession.isPossessed(e));
        if (!(t instanceof Mob m)) return Fx.fail(p, "infinity.extra.no_target");
        if (Rules.foreignOwner(p, m) != null) return Fx.fail(p, "infinity.extra.pet.owned");
        if (list(p).size() >= MAX) return Fx.fail(p, "infinity.extra.reliquary.full");
        m.ejectPassengers();
        m.stopRiding();
        CompoundTag nbt = new CompoundTag();
        if (!m.save(nbt)) return Fx.fail(p, "infinity.extra.no_target");
        nbt.remove("UUID");
        nbt.remove("Passengers");
        nbt.remove("leash");
        nbt.getCompound("NeoForgeData").remove("infinity_possessed_noai");
        CompoundTag entry = new CompoundTag();
        entry.put("nbt", nbt);
        entry.putString("name", Component.Serializer.toJson(m.getDisplayName(), p.registryAccess()));
        BUSY.add(m.getUUID());
        m.setNoAi(true);
        m.setInvulnerable(true);
        m.setNoGravity(true);
        SoulFx.play(new Capture(p, m, entry));
        return true;
    }

    /** Left hand (gauntlet) position. */
    private static Vec3 hand(ServerPlayer p) {
        float yr = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 left = new Vec3(Mth.cos(yr), 0, Mth.sin(yr));
        return p.getEyePosition().add(p.getLookAngle().scale(0.6)).add(left.scale(0.38)).add(0, -0.4, 0);
    }

    private static void scale(LivingEntity e, double amount) {
        AttributeInstance a = e.getAttribute(Attributes.SCALE);
        if (a != null) a.addOrUpdateTransientModifier(new AttributeModifier(SHRINK, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void unscale(LivingEntity e) {
        AttributeInstance a = e.getAttribute(Attributes.SCALE);
        if (a != null) a.removeModifier(SHRINK);
    }

    private static final class Capture implements SoulFx.Anim {
        static final int LEN = 40;
        final ServerPlayer p; final Mob m; final CompoundTag entry; final Vec3 start; int t;
        Capture(ServerPlayer p, Mob m, CompoundTag entry) { this.p = p; this.m = m; this.entry = entry; this.start = m.position(); }

        @Override public boolean tick() {
            if (p.hasDisconnected() || !m.isAlive() || m.level() != p.level()) { abort(); return true; }
            ServerLevel l = (ServerLevel) m.level();
            double k = t / (double) LEN;
            double ease = k * k * (3 - 2 * k);
            Vec3 hand = hand(p);
            Vec3 c = m.getBoundingBox().getCenter();
            double phase = t * 0.22;
            if (t == 0) {
                SoulFx.sound(l, c, SoundEvents.SOUL_ESCAPE.value(), 2F, 0.45F, null);
                SoulFx.sound(l, c, SoundEvents.WARDEN_SONIC_CHARGE, 1.2F, 1.3F, null);
                SoulFx.sound(l, c, SoundEvents.BEACON_ACTIVATE, 1.2F, 1.6F, null);
                SoulFx.bolt(l, hand, c);
            }
            if (t == 16) SoulFx.sound(l, c, SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.0F, 1.5F, null);
            // phase 1 (0-15): seized — circle under it, lifted, chains of light
            if (t < 16) {
                SoulFx.sigil(l, start.add(0, 0.05, 0), Math.max(1.8, m.getBbWidth() * 1.5), phase, null);
                Vec3 up = start.add(0, ease * 1.6, 0);
                m.teleportTo(up.x, up.y, up.z);
                if (t % 4 == 0) SoulFx.bolt(l, hand, c);
            } else {
                // phase 2: torn out and pulled into the hand, shrinking
                double q = (t - 16) / (double) (LEN - 16);
                Vec3 from = start.add(0, 1.6, 0);
                Vec3 at = from.lerp(hand.subtract(0, m.getBbHeight() * 0.3 * (1 - q), 0), q * q);
                m.teleportTo(at.x, at.y, at.z);
                scale(m, -0.96 * q);
                SoulFx.ring(l, c, Math.max(0.6, m.getBbWidth()) * (1.4 - q), 18, -phase * 2, SoulFx.D_GOLD, null);
            }
            m.setYRot(m.getYRot() + (float) (6 + 30 * ease)); m.yBodyRot = m.getYRot(); m.setYHeadRot(m.getYRot());
            SoulFx.helix(l, c, hand, 3, 0.35 + 0.25 * (1 - ease), phase * 2, SoulFx.D_ORANGE, null);
            SoulFx.emit(l, ParticleTypes.SOUL, c, 3, 0.3, 0.03, null);
            SoulFx.emit(l, ParticleTypes.SOUL_FIRE_FLAME, c, 2, 0.25, 0.02, null);
            if (t % 5 == 0) SoulFx.sound(l, c, SoundEvents.SOUL_SAND_BREAK, 0.6F, 0.5F + (float) ease, null);
            if (++t >= LEN) {
                ListTag li = list(p);
                li.add(entry);
                save(p, li);
                SoulFx.burst(l, hand, 1.0, null);
                SoulFx.sound(l, hand, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.2F, 1.4F, null);
                SoulFx.sound(l, hand, SoundEvents.BEACON_DEACTIVATE, 1F, 1.8F, null);
                SoulFx.sound(l, hand, SoundEvents.GENERIC_EXPLODE.value(), 0.35F, 1.9F, null);
                Fx.bar(p, "infinity.soul.jar.captured", m.getDisplayName(), li.size());
                BUSY.remove(m.getUUID());
                m.discard();
                sendList(p, false);
                return true;
            }
            return false;
        }

        @Override public void abort() {
            BUSY.remove(m.getUUID());
            if (m.isAlive()) { unscale(m); m.setNoAi(false); m.setInvulnerable(false); m.setNoGravity(false); }
        }
    }

    // ------------------------------------------------------------------ menu actions

    static void action(ServerPlayer p, int action, int index) {
        Long until = MENU_TOKEN.get(p.getUUID());
        if (until == null || p.serverLevel().getGameTime() > until || Possession.isGhost(p)) return;
        if (action == REFRESH) { sendList(p, false); return; }
        ListTag li = list(p);
        if (index < 0 || index >= li.size()) return;
        CompoundTag entry = li.getCompound(index);
        ServerLevel l = p.serverLevel();
        Vec3 at = Aim.point(p, 20).subtract(p.getLookAngle().scale(1.0));
        Entity e = EntityType.create(entry.getCompound("nbt").copy(), l).orElse(null);
        li.remove(index);
        save(p, li);
        if (e == null) { Fx.fail(p, "infinity.extra.reliquary.lost"); sendList(p, false); return; }
        e.moveTo(at.x, Math.floor(at.y), at.z, p.getYRot() + 180, 0);
        if (!(e instanceof Mob m) || !l.addFreshEntity(e)) {
            li.add(entry); save(p, li);
            Fx.fail(p, "infinity.dest.failed"); sendList(p, false); return;
        }
        switch (action) {
            case EXECUTE -> { BUSY.add(m.getUUID()); m.setNoAi(true); m.setInvulnerable(true); m.setNoGravity(true); SoulFx.play(new Execute(p, m)); }
            case SOUL_WORLD -> { BUSY.add(m.getUUID()); m.setNoAi(true); m.setInvulnerable(true); m.setNoGravity(true); SoulFx.play(new Exile(p, m)); }
            case POSSESS -> { SoulFx.play(new Release(p, m, false)); Possession.startMob(p, m, true); }
            default -> { m.setNoAi(true); SoulFx.play(new Release(p, m, true)); }
        }
        MENU_TOKEN.put(p.getUUID(), l.getGameTime() + 20 * 300);
        sendList(p, false);
    }

    /** The soul flows back out of the hand and the body grows to full size. */
    private static final class Release implements SoulFx.Anim {
        static final int LEN = 16;
        final ServerPlayer p; final Mob m; final boolean freeze; int t;
        Release(ServerPlayer p, Mob m, boolean freeze) { this.p = p; this.m = m; this.freeze = freeze; }
        @Override public boolean tick() {
            if (!m.isAlive()) return true;
            ServerLevel l = (ServerLevel) m.level();
            double q = t / (double) LEN;
            scale(m, -0.95 * (1 - q));
            Vec3 c = m.getBoundingBox().getCenter();
            if (t == 0) {
                SoulFx.sound(l, c, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 1.2F, 1.2F, null);
                SoulFx.sound(l, c, SoundEvents.SOUL_ESCAPE.value(), 1.5F, 0.7F, null);
            }
            if (!p.hasDisconnected() && p.level() == l) SoulFx.helix(l, hand(p), c, 2, 0.4, t * 0.4, SoulFx.D_ORANGE, null);
            SoulFx.sigil(l, m.position().add(0, 0.05, 0), Math.max(1.5, m.getBbWidth() * 1.4), t * 0.25, null);
            if (++t >= LEN) {
                unscale(m);
                SoulFx.burst(l, c, 1.2, null);
                if (freeze) m.setNoAi(false);
                return true;
            }
            return false;
        }
        @Override public void abort() { unscale(m); if (freeze) m.setNoAi(false); }
    }

    /** Execution: giant sigils in the sky, the body hoisted in chains, ember lightning, soul set free. */
    private static final class Execute implements SoulFx.Anim {
        static final int LEN = 56;
        final ServerPlayer p; final Mob m; final Vec3 base; int t;
        Execute(ServerPlayer p, Mob m) { this.p = p; this.m = m; this.base = m.position(); }
        @Override public boolean tick() {
            if (!m.isAlive()) { BUSY.remove(m.getUUID()); return true; }
            ServerLevel l = (ServerLevel) m.level();
            double k = Math.min(1, t / 40.0);
            Vec3 c = m.getBoundingBox().getCenter();
            double sc = Math.max(1, m.getBbHeight() / 2);
            if (t == 0) {
                SoulFx.sound(l, c, SoundEvents.EVOKER_PREPARE_SUMMON, 2F, 0.6F, null);
                SoulFx.sound(l, c, SoundEvents.BEACON_ACTIVATE, 2F, 0.5F, null);
            }
            if (t == 20) SoulFx.sound(l, c, SoundEvents.WARDEN_SONIC_CHARGE, 2F, 0.6F, null);
            if (t % 2 == 0) SoulFx.skySigil(l, base, sc * (0.4 + 0.6 * k), t * 0.12);
            if (t < 40) {
                Vec3 up = base.add((l.random.nextDouble() - 0.5) * 0.08 * k, k * 1.8, (l.random.nextDouble() - 0.5) * 0.08 * k);
                m.teleportTo(up.x, up.y, up.z);
                // chains from the outer circle to the body
                if (t % 3 == 0) for (int i = 0; i < 6; i++) {
                    double a = t * 0.12 + i * Math.PI / 3;
                    Vec3 from = base.add(Math.cos(a) * 9 * sc * 0.82, 7 * sc, Math.sin(a) * 9 * sc * 0.82);
                    SoulFx.line(l, from, c, 0.5, SoulFx.D_GOLD, null);
                }
                SoulFx.emit(l, ParticleTypes.SOUL_FIRE_FLAME, c, 3, m.getBbWidth() * 0.5, 0.02, null);
            }
            if (t == 40) {
                for (int i = 0; i < 4; i++) SoulFx.bolt(l, base.add(l.random.triangle(0, 4), 11 * sc, l.random.triangle(0, 4)), c);
                SoulFx.burst(l, c, 3 * sc, null);
                SoulFx.ring(l, base.add(0, 0.2, 0), 4 * sc, 70, 0, SoulFx.D_BIG, null);
                SoulFx.ring(l, base.add(0, 0.2, 0), 7 * sc, 90, 0.3, SoulFx.D_EMBER, null);
                SoulFx.sound(l, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 3F, 0.6F, null);
                SoulFx.sound(l, c, SoundEvents.GENERIC_EXPLODE.value(), 1.5F, 0.7F, null);
                SoulFx.sound(l, c, SoundEvents.SOUL_ESCAPE.value(), 2F, 0.4F, null);
                BUSY.remove(m.getUUID());
                m.setInvulnerable(false);
                m.setNoGravity(false);
                m.hurt(p.damageSources().playerAttack(p), Float.MAX_VALUE);
                if (m.isAlive()) m.kill();
            }
            if (t > 40) SoulFx.helix(l, c, c.add(0, 8, 0), 2, 0.5, t * 0.3, SoulFx.D_PALE, null);
            return ++t >= LEN;
        }
        @Override public void abort() { BUSY.remove(m.getUUID()); if (m.isAlive()) { m.setNoAi(false); m.setInvulnerable(false); m.setNoGravity(false); } }
    }

    /** Exile: an orange portal opens, the body is drawn in and reappears in the Soul World (else the End, like banishment). */
    private static final class Exile implements SoulFx.Anim {
        static final int LEN = 36;
        final ServerPlayer p; final Mob m; final Vec3 base; final float yaw; int t;
        Exile(ServerPlayer p, Mob m) { this.p = p; this.m = m; this.base = m.position(); this.yaw = p.getYRot(); }
        @Override public boolean tick() {
            if (!m.isAlive()) { BUSY.remove(m.getUUID()); return true; }
            ServerLevel l = (ServerLevel) m.level();
            double r = Math.max(1.6, m.getBbHeight() * 0.9);
            Vec3 gate = base.add(0, r, 0).add(Vec3.directionFromRotation(0, yaw).scale(-1.5));
            double k = Math.min(1, t / 12.0);
            SoulFx.ringV(l, gate, r * k, 40, t * 0.3, yaw, SoulFx.D_ORANGE);
            SoulFx.ringV(l, gate, r * k * 0.75, 30, -t * 0.4, yaw, SoulFx.D_GOLD);
            SoulFx.emit(l, ParticleTypes.SOUL, gate, 4, r * 0.3, 0.02, null);
            if (t == 0) { SoulFx.sound(l, gate, SoundEvents.END_PORTAL_SPAWN, 0.7F, 1.6F, null); SoulFx.sound(l, gate, SoundEvents.SOUL_ESCAPE.value(), 2F, 0.5F, null); }
            if (t >= 12) {
                double q = (t - 12) / (double) (LEN - 12);
                Vec3 at = base.lerp(gate.subtract(0, m.getBbHeight() * 0.5, 0), q);
                m.teleportTo(at.x, at.y, at.z);
                scale(m, -0.9 * q);
                SoulFx.helix(l, m.getBoundingBox().getCenter(), gate, 3, 0.4, t * 0.4, SoulFx.D_EMBER, null);
            }
            if (++t < LEN) return false;
            BUSY.remove(m.getUUID());
            unscale(m);
            m.setInvulnerable(false); m.setNoGravity(false); m.setNoAi(false);
            var fromDim = l.dimension();
            Vec3 from = m.position();
            ServerLevel soul = l.getServer().getLevel(SOULWORLD);
            Entity moved = null;
            if (soul != null && soul != l) {
                int x = l.random.nextInt(129) - 64, z = l.random.nextInt(129) - 64;
                soul.getChunk(x >> 4, z >> 4);
                int y = soul.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                moved = Relocation.move(m, soul, new Vec3(x + 0.5, y, z + 0.5), 0.5F);
            }
            if (moved == null) {
                ServerLevel end = l.getServer().getLevel(Level.END);
                Vec3 land = end == null ? null : Relocation.central(end, m, false);
                if (land != null) moved = Relocation.move(m, end, land, 1.18F);
            }
            SoulFx.burst(l, gate, 2, null);
            SoulFx.sound(l, gate, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.5F, 0.6F, null);
            if (moved == null) { Fx.fail(p, "infinity.dest.failed"); return true; }
            BanishLog.record(p, m, fromDim, from, moved);
            Fx.bar(p, "infinity.soul.jar.exiled", moved.getDisplayName());
            return true;
        }
        @Override public void abort() { BUSY.remove(m.getUUID()); if (m.isAlive()) { unscale(m); m.setNoAi(false); m.setInvulnerable(false); m.setNoGravity(false); } }
    }

    private SoulJar() {}
}
