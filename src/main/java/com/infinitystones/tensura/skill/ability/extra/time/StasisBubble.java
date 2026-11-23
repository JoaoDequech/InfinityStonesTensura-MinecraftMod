package com.infinitystones.tensura.skill.ability.extra.time;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Displays;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stasis Bubble: left = an 8 s dome at the aimed point (radius 3–8, Alt+scroll); right = pop yours early.
 * Inside, time stops for everyone except you and your allies: mobs freeze in the air, projectiles hang (and fly on
 * afterwards), players cannot move, falls stop, fire does not burn. All damage dealt to frozen targets is stored and
 * lands at once when the bubble breaks. Bosses are immune. One bubble per player.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class StasisBubble implements Ability {
    public static final StasisBubble INSTANCE = new StasisBubble();
    private static final String MODE = "time.stasis_bubble";
    private static final int DURATION = 160;

    private static final class Frozen {
        final Entity e; final Vec3 pos; final Vec3 vel; final boolean noAi, noGrav; final int fire, fuse; final float fall;
        final int fx = TimeNet.newId();
        float stored;
        Frozen(Entity e) {
            this.e = e; pos = e.position(); vel = e.getDeltaMovement(); noGrav = e.isNoGravity(); fire = e.getRemainingFireTicks(); fall = e.fallDistance;
            noAi = e instanceof Mob m && m.isNoAi();
            fuse = e instanceof PrimedTnt t ? t.getFuse() : 0;
        }
    }

    private static final class Bubble {
        final UUID owner; final ServerLevel level; final Vec3 c; final float r; final int id = TimeNet.newId();
        final Map<Entity, Frozen> frozen = new IdentityHashMap<>();
        Tasks.Handle handle; boolean releasing;
        Bubble(UUID owner, ServerLevel level, Vec3 c, float r) { this.owner = owner; this.level = level; this.c = c; this.r = r; }
    }

    private static final Map<UUID, Bubble> BUBBLES = new HashMap<>();
    private static final Map<Entity, Bubble> HELD = new IdentityHashMap<>();

    @Override public Stone stone() { return Stone.TIME; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        Bubble old = BUBBLES.get(p.getUUID());
        if (alt) {
            if (old == null) return Fx.fail(p, "infinity.extra.time.stasis.none");
            old.handle.cancel();
            return true;
        }
        if (old != null) old.handle.cancel();
        float r = ExtraSettings.get(p, MODE, 5, 3, 8, 1);
        Vec3 c = Aim.ground(p, 48);
        ServerLevel l = p.serverLevel();
        Bubble b = new Bubble(p.getUUID(), l, c, r);
        BUBBLES.put(p.getUUID(), b);
        // opening: flash, shock, layered sounds
        TimeNet.stasis(l, b.id, c, r, 40);
        FxNet.shockwave(l, c.add(0, 0.1, 0), r * 1.6F, Stone.TIME.color(), 22);
        FxNet.flashAround(l, c, r + 6, 0xD8FFE8, 0.45F, 10);
        FxNet.shake(l, c, r + 10, 0.6F, 10);
        Fx.sound(l, c, SoundEvents.BEACON_ACTIVATE, 0.7F, 1.8F);
        Fx.sound(l, c, SoundEvents.BELL_BLOCK, 0.6F, 0.6F);
        Fx.sound(l, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.5F);
        Fx.shell(l, c, r, 60, Fx.light(Stone.TIME, 1.2F));
        b.handle = Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) { return tickBubble(b, age); }
            @Override public void end() { release(b); }
        });
        Fx.bar(p, Stone.TIME, "infinity.extra.time.stasis.on", (int) r);
        return true;
    }

    private static boolean eligible(ServerPlayer owner, Entity e) {
        if (e.getTags().contains(Displays.TAG) || HELD.containsKey(e) || !e.isAlive()) return false;
        if (e instanceof LivingEntity) return owner != null ? Rules.canControl(owner, e) && Rules.isLiving(e) : !Rules.isBoss(e);
        return e instanceof Projectile || e instanceof ItemEntity || e instanceof PrimedTnt || e instanceof FallingBlockEntity;
    }

    private static boolean tickBubble(Bubble b, int age) {
        if (BUBBLES.get(b.owner) != b || age >= DURATION) return false;
        ServerLevel l = b.level;
        ServerPlayer owner = l.getServer().getPlayerList().getPlayer(b.owner);
        if (age % 20 == 0) {
            TimeNet.stasis(l, b.id, b.c, b.r, 40);
            Fx.sound(l, b.c, SoundEvents.NOTE_BLOCK_HAT.value(), 0.4F, age % 40 == 0 ? 0.7F : 0.6F);
        }
        if (age == DURATION - 30) Fx.sound(l, b.c, SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.6F);
        // capture newcomers
        AABB box = new AABB(b.c.x - b.r, b.c.y - 1, b.c.z - b.r, b.c.x + b.r, b.c.y + b.r + 1, b.c.z + b.r);
        for (Entity e : l.getEntities((Entity) null, box, e -> e.getBoundingBox().getCenter().distanceToSqr(b.c) <= b.r * b.r && eligible(owner, e))) {
            Frozen f = new Frozen(e);
            b.frozen.put(e, f);
            HELD.put(e, b);
            if (e instanceof Mob m) m.setNoAi(true);
            e.setNoGravity(true);
            e.clearFire();
            TimeNet.frozen(l, f.fx, e, 40);
            Fx.at(l, Fx.light(Stone.TIME, 1F), e.getBoundingBox().getCenter(), 8, e.getBbWidth() * 0.5, 0);
            if (e instanceof ServerPlayer sp) Fx.barGray(sp, "infinity.extra.time.stasis.frozen");
        }
        // hold everything in place
        List<Entity> gone = new ArrayList<>();
        for (Frozen f : b.frozen.values()) {
            Entity e = f.e;
            if (!e.isAlive() || e.level() != l) { gone.add(e); continue; }
            e.setDeltaMovement(Vec3.ZERO);
            e.fallDistance = 0;
            if (e.position().distanceToSqr(f.pos) > 1.0E-4) {
                if (e instanceof ServerPlayer sp) sp.connection.teleport(f.pos.x, f.pos.y, f.pos.z, sp.getYRot(), sp.getXRot());
                else e.setPos(f.pos);
            }
            if (e instanceof PrimedTnt t) t.setFuse(f.fuse);
            if (e.isOnFire()) e.clearFire();
            if (age % 20 == 0) TimeNet.frozen(l, f.fx, e, 40);
        }
        for (Entity e : gone) { Frozen f = b.frozen.remove(e); HELD.remove(e); if (f != null) TimeNet.kill(l, b.c, f.fx); }
        return true;
    }

    private static void release(Bubble b) {
        BUBBLES.remove(b.owner, b);
        b.releasing = true;
        ServerLevel l = b.level;
        ServerPlayer owner = l.getServer().getPlayerList().getPlayer(b.owner);
        TimeNet.kill(l, b.c, b.id);
        for (Frozen f : b.frozen.values()) {
            Entity e = f.e;
            HELD.remove(e);
            TimeNet.kill(l, b.c, f.fx);
            if (!e.isAlive()) continue;
            if (e instanceof Mob m) m.setNoAi(f.noAi);
            e.setNoGravity(f.noGrav);
            e.fallDistance = f.fall;
            if (f.fire > 0) e.setRemainingFireTicks(f.fire);
            if (!(e instanceof LivingEntity)) { e.setDeltaMovement(f.vel); e.hurtMarked = true; }
            if (e instanceof PrimedTnt t) t.setFuse(f.fuse);
            if (f.stored > 0 && e instanceof LivingEntity le) {
                le.invulnerableTime = 0;
                if (owner != null) le.hurt(Rules.magic(owner), f.stored);
                else le.hurt(le.damageSources().magic(), f.stored);
                Vec3 mid = le.getBoundingBox().getCenter();
                Fx.at(l, ParticleTypes.CRIT, mid, 20, 0.4, 0.4);
                Fx.at(l, ParticleTypes.DAMAGE_INDICATOR, mid, (int) Math.min(15, f.stored / 2 + 2), 0.3, 0.2);
                FxNet.flare(l, mid, 1.5F, Stone.TIME.color(), 8);
                Fx.sound(l, mid, SoundEvents.PLAYER_ATTACK_CRIT, 0.7F, 0.8F);
            }
        }
        b.frozen.clear();
        // shatter
        FxNet.shockwave(l, b.c.add(0, 0.1, 0), b.r * 2.2F, Stone.TIME.color(), 20);
        FxNet.flashAround(l, b.c, b.r + 6, 0xFFFFFF, 0.35F, 8);
        FxNet.shake(l, b.c, b.r + 10, 0.8F, 8);
        Fx.shell(l, b.c, b.r, 80, Fx.dust(Stone.TIME, 1.3F));
        Fx.at(l, ParticleTypes.END_ROD, b.c.add(0, b.r * 0.5, 0), 30, b.r * 0.5, 0.15);
        Fx.sound(l, b.c, SoundEvents.GLASS_BREAK, 0.7F, 0.6F);
        Fx.sound(l, b.c, SoundEvents.AMETHYST_BLOCK_BREAK, 0.7F, 0.7F);
    }

    /** Damage to a frozen target is banked and lands when time resumes. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void damage(LivingIncomingDamageEvent e) {
        if (HELD.isEmpty()) return;
        Bubble b = HELD.get(e.getEntity());
        if (b == null || b.releasing) return;
        Frozen f = b.frozen.get(e.getEntity());
        if (f == null) return;
        if (e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return; // void / kill
        f.stored = Math.min(f.stored + e.getAmount(), 10000F);
        e.setCanceled(true);
        Fx.at(b.level, Fx.light(Stone.TIME, 0.8F), e.getEntity().getBoundingBox().getCenter(), 4, 0.3, 0);
    }
}
