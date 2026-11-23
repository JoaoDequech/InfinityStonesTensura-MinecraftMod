package com.infinitystones.tensura.gauntlet;

import com.infinitystones.tensura.skill.ability.Relocation;
import com.infinitystones.tensura.skill.ability.extra.Effects;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.RealityPets;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The Snap and its aftermath (original gauntlet powers, rebuilt in Java):
 * <ul>
 *   <li><b>Snap</b> — half of every living creature in every loaded dimension turns to dust over ~5 s and is sent away
 *       (kept in the {@link SnapArchive}). Never touched: you, your allies, pets / tamed animals / pact creatures of
 *       ANY player, name-tagged creatures, the Ender Dragon and bosses. Other players are included only when the server
 *       allows PvP, and like the original they are sent to the Soulworld, not killed.</li>
 *   <li><b>Kill All Hostile</b> — the same dust, for every hostile monster.</li>
 *   <li><b>Bring Back</b> — left: everything you snapped reappears around you (players too); right: each one returns to
 *       the exact place it vanished from (if that area is loaded; the rest wait for the next call).</li>
 * </ul>
 */
public final class Snap {
    static final ResourceKey<Level> SOULWORLD = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("infinity:soulworld"));
    static final String SNAPPED = "infinity_snapped";
    private static final Vector3f WHITE = new Vector3f(0.92F, 0.9F, 0.85F);

    public static boolean snap(ServerPlayer p) {
        if (!GauntletPowers.ready(p, true)) return false;
        List<Entity> victims = new ArrayList<>();
        for (ServerLevel l : p.server.getAllLevels()) {
            for (Entity e : l.getAllEntities()) {
                if (!(e instanceof LivingEntity) || !snappable(p, e)) continue;
                if (l.random.nextBoolean()) victims.add(e);
            }
        }
        start(p, victims, "infinity.gauntlet.snap.done");
        return true;
    }

    public static boolean purge(ServerPlayer p) {
        if (!GauntletPowers.ready(p, true)) return false;
        List<Entity> victims = new ArrayList<>();
        for (ServerLevel l : p.server.getAllLevels())
            for (Entity e : l.getAllEntities()) if (e instanceof Enemy && !(e instanceof Player) && snappable(p, e)) victims.add(e);
        start(p, victims, "infinity.gauntlet.purge.done");
        return true;
    }

    static boolean snappable(ServerPlayer caster, Entity e) {
        if (e == caster || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand || Rules.isBoss(e)) return false;
        if (e instanceof Player pl) return !pl.isCreative() && Rules.canHarm(caster, pl);
        if (Rules.isAlly(caster, e) || e.hasCustomName()) return false;
        if (e instanceof OwnableEntity o && o.getOwnerUUID() != null) return false;
        if (RealityPets.ownerOf(e) != null || e.getPersistentData().hasUUID("infinity_pact_owner")) return false;
        for (Entity pass : e.getPassengers()) if (pass instanceof Player) return false;
        return true;
    }

    private static void start(ServerPlayer p, List<Entity> victims, String doneKey) {
        Collections.shuffle(victims);
        ServerLevel l = p.serverLevel();
        Fx.at(l, ParticleTypes.FLASH, p.position().add(0, 1, 0), 1, 0, 0);
        Fx.at(l, ParticleTypes.FIREWORK, p.position().add(0, 1, 0), 100, 0.3, 0.25);
        GauntletPowers.global(p, SoundEvents.FIREWORK_ROCKET_BLAST, 0.7F, 2F);
        GauntletPowers.global(p, SoundEvents.WITHER_SPAWN, 0.5F, 1F);
        GauntletPowers.global(p, SoundEvents.TRIDENT_THUNDER.value(), 0.6F, 0F);
        GauntletPowers.burn(p);
        Effects.add(new Dust(l, p.getUUID(), victims, doneKey));
    }

    /** Turns the victims to dust a few at a time (all gone in ~5 s), archiving each one. */
    static final class Dust extends Effects.Effect {
        final List<Entity> queue; final String doneKey; final int perTick; int gone;
        Dust(ServerLevel l, UUID owner, List<Entity> queue, String doneKey) {
            super(l, owner, 0);
            this.queue = queue; this.doneKey = doneKey; this.perTick = Math.max(1, queue.size() / 100 + 1);
        }

        @Override protected void tick() {
            if (age < 20) return; // the snap echoes first
            SnapArchive archive = SnapArchive.get(level.getServer());
            for (int i = 0; i < perTick && !queue.isEmpty(); i++) {
                Entity e = queue.remove(queue.size() - 1);
                if (!e.isAlive() || e.isRemoved()) continue;
                ServerLevel el = (ServerLevel) e.level();
                Vec3 c = e.position().add(0, e.getBbHeight() / 2, 0);
                el.sendParticles(new net.minecraft.core.particles.DustParticleOptions(WHITE, 1.4F), c.x, c.y, c.z, 30, e.getBbWidth() * 0.5, e.getBbHeight() * 0.4, e.getBbWidth() * 0.5, 0.02);
                el.sendParticles(ParticleTypes.ASH, c.x, c.y, c.z, 20, 0.4, 0.5, 0.4, 0.02);
                if (gone % 8 == 0) Fx.sound(el, c, SoundEvents.SAND_BREAK, 0.4F, 0.6F);
                if (e instanceof ServerPlayer victim) sendAway(victim, owner);
                else archive(archive, e, owner);
                gone++;
            }
            if (queue.isEmpty()) {
                ServerPlayer o = ownerPlayer();
                if (o != null) Fx.bar(o, doneKey, gone);
                kill();
            }
        }
    }

    static void archive(SnapArchive archive, Entity e, UUID by) {
        e.stopRiding();
        e.ejectPassengers();
        CompoundTag tag = new CompoundTag();
        if (!e.save(tag)) { e.discard(); return; }
        tag.remove("Passengers");
        archive.add(new SnapArchive.Entry(by, e.level().dimension().location().toString(), e.getX(), e.getY(), e.getZ(), tag));
        e.discard();
    }

    /** Players are not killed: like the original, they are sent to the Soulworld (and remembered for Bring Back). */
    static void sendAway(ServerPlayer victim, UUID by) {
        CompoundTag mark = new CompoundTag();
        mark.putUUID("by", by);
        mark.putString("dim", victim.level().dimension().location().toString());
        mark.putDouble("x", victim.getX()); mark.putDouble("y", victim.getY()); mark.putDouble("z", victim.getZ());
        Rules.data(victim).put(SNAPPED, mark);
        victim.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 200, 0, false, false));
        victim.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 2400, 1, false, false));
        ServerLevel soul = victim.server.getLevel(SOULWORLD);
        if (soul != null) victim.teleportTo(soul, 1000.5, 78, 1000.5, victim.getYRot(), victim.getXRot());
        victim.displayClientMessage(net.minecraft.network.chat.Component.translatable("infinity.gauntlet.snap.you"), false);
    }

    // ------------------------------------------------------------ Bring Back

    public static boolean bringBack(ServerPlayer p, boolean toOrigin) {
        if (!GauntletPowers.ready(p, true)) return false;
        SnapArchive archive = SnapArchive.get(p.server);
        UUID me = p.getUUID();
        List<SnapArchive.Entry> list = archive.take(e -> e.by().equals(me) && (!toOrigin || loaded(p, e)));
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            CompoundTag m = Rules.data(o).getCompound(SNAPPED);
            if (m.hasUUID("by") && m.getUUID("by").equals(me)) players.add(o);
        }
        int pending = toOrigin ? archive.count(me) : 0;
        if (list.isEmpty() && players.isEmpty()) return Fx.fail(p, pending > 0 ? "infinity.gauntlet.return.unloaded" : "infinity.gauntlet.return.none");
        for (ServerPlayer o : players) {
            CompoundTag m = Rules.data(o).getCompound(SNAPPED);
            Rules.data(o).remove(SNAPPED);
            ServerLevel dest = toOrigin ? p.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(m.getString("dim")))) : p.serverLevel();
            Vec3 at = toOrigin && dest != null ? new Vec3(m.getDouble("x"), m.getDouble("y"), m.getDouble("z")) : p.position();
            if (dest == null) { dest = p.serverLevel(); at = p.position(); }
            Relocation.move(o, dest, at, 1.0F);
            o.removeEffect(MobEffects.SLOW_FALLING);
        }
        Effects.add(new Rebuild(p.serverLevel(), me, list, toOrigin, p.position()));
        GauntletPowers.global(p, SoundEvents.BEACON_ACTIVATE, 0.6F, 0.6F);
        if (pending > 0) Fx.bar(p, "infinity.gauntlet.return.pending", pending);
        return true;
    }

    private static boolean loaded(ServerPlayer p, SnapArchive.Entry e) {
        ServerLevel l = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(e.dim())));
        return l != null && l.isLoaded(BlockPos.containing(e.x(), e.y(), e.z()));
    }

    /** Re-creates the snapped, 12 per tick, with the dust running backwards. */
    static final class Rebuild extends Effects.Effect {
        final List<SnapArchive.Entry> list; final boolean toOrigin; final Vec3 around; int i;
        Rebuild(ServerLevel l, UUID owner, List<SnapArchive.Entry> list, boolean toOrigin, Vec3 around) {
            super(l, owner, 0); this.list = list; this.toOrigin = toOrigin; this.around = around;
        }

        @Override protected void tick() {
            ServerPlayer o = ownerPlayer();
            for (int n = 0; n < 12 && i < list.size(); n++, i++) {
                SnapArchive.Entry e = list.get(i);
                ServerLevel dest = toOrigin ? level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(e.dim()))) : (o != null ? o.serverLevel() : level);
                if (dest == null) dest = level;
                Entity ent = EntityType.create(e.nbt(), dest).orElse(null);
                if (ent == null) continue;
                Vec3 at = toOrigin ? new Vec3(e.x(), e.y(), e.z()) : spot(dest, o != null ? o.position() : around, ent, i);
                ent.moveTo(at.x, at.y, at.z, ent.getYRot(), ent.getXRot());
                ent.setDeltaMovement(Vec3.ZERO);
                ent.fallDistance = 0;
                if (!dest.addFreshEntity(ent)) continue;
                Vec3 c = at.add(0, ent.getBbHeight() / 2, 0);
                dest.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 16, 0.3, 0.4, 0.3, 0.02);
                dest.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new Vector3f(1F, 0.85F, 0.3F), 1.2F), c.x, c.y, c.z, 12, 0.3, 0.4, 0.3, 0);
                if (ent instanceof Mob m) m.setTarget(null);
            }
            if (i >= list.size()) kill();
        }

        /** Rings around the caster, on a free spot near their height. */
        private static Vec3 spot(ServerLevel l, Vec3 c, Entity ent, int i) {
            double ring = 2 + Math.sqrt(i) * 0.9, ang = i * 2.399963;
            Vec3 t = new Vec3(c.x + Math.cos(ang) * ring, c.y, c.z + Math.sin(ang) * ring);
            for (int dy : new int[]{0, 1, -1, 2, -2, 3}) {
                Vec3 q = t.add(0, dy, 0);
                if (l.noCollision(ent, ent.getType().getDimensions().makeBoundingBox(q))) return q;
            }
            return c;
        }
    }

    private Snap() {}
}
