package com.infinitystones.tensura.skill.ability.extra.reality;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reality — Greed (Orange-Lantern style). The world's things decide they belong to you.
 * Left (aim): a creature/player under the crosshair loses its held item (players: hand, else a random inventory slot;
 * mobs: hands, then armour) and it flies to you; aiming at no creature pulls every item lying around the aimed point.
 * Right: area — everything lying around you (items + XP) and what nearby mobs hold is pulled in.
 * Wheel level (scroll) 1–5 = radius 6/10/14/18/24, pull speed and how many items per use.
 * Items are MOVED, never copied: the stack leaves the victim and is reserved for the caster while it flies.
 */
public final class Greed implements Ability {
    public static final Greed INSTANCE = new Greed();
    public static final String MODE = "reality.greed";
    private static final String K = "infinity.extra.greed.";
    private static final String FLYING = "infinity_greed_flying";
    private static final int ORANGE = 0xFF8A1A, GOLD = 0xFFD54A;
    private static final int[] RADIUS = {6, 10, 14, 18, 24};

    @Override public Stone stone() { return Stone.REALITY; }

    private static int level(ServerPlayer p) { return ExtraSettings.get(p, MODE, 3, 1, 5, 1); }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        int lvl = level(p);
        double radius = RADIUS[lvl - 1] * Rules.mult(p, "reality");
        int max = 16 * lvl;
        ServerLevel l = p.serverLevel();
        if (alt) return area(p, l, p.position(), radius, max, lvl, true);

        LivingEntity t = Aim.living(p, 48, e -> Rules.canHarm(p, e) && !Rules.isBoss(e));
        if (t != null) {
            List<ItemStack> taken = rob(p, t, lvl >= 4 ? 2 : 1);
            if (taken.isEmpty()) return Fx.fail(p, K + "empty", t.getDisplayName());
            Vec3 from = t.position().add(0, t.getBbHeight() * 0.6, 0);
            for (ItemStack s : taken) launch(p, l, s, from, lvl);
            castFx(p, l, from, false);
            FxNet.beam(l, p.getEyePosition().add(0, -0.3, 0), from, GOLD, 0.12F, 14);
            FxNet.flare(l, from, 2.5F, ORANGE, 14);
            Fx.bar(p, Stone.REALITY, K + "took", taken.get(0).getHoverName(), t.getDisplayName());
            return true;
        }
        Vec3 at = Aim.point(p, 64);
        return area(p, l, at, radius, max, lvl, false);
    }

    /** Pulls ground items / XP around c (and, around the caster, what nearby mobs hold). */
    private static boolean area(ServerPlayer p, ServerLevel l, Vec3 c, double r, int max, int lvl, boolean includeMobs) {
        AABB box = new AABB(c, c).inflate(r);
        int n = 0;
        for (ItemEntity it : l.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && !e.getTags().contains(FLYING)
                && e.distanceToSqr(c) <= r * r)) {
            if (n >= max) break;
            fly(p, l, it, lvl);
            n++;
        }
        for (ExperienceOrb orb : l.getEntitiesOfClass(ExperienceOrb.class, box, e -> e.isAlive() && e.distanceToSqr(c) <= r * r)) {
            orbFly(p, l, orb, lvl);
            n++;
        }
        if (includeMobs) {
            for (Mob m : l.getEntitiesOfClass(Mob.class, box, e -> e.isAlive() && Rules.canHarm(p, e) && !Rules.isBoss(e)
                    && e.distanceToSqr(c) <= r * r)) {
                if (n >= max) break;
                Vec3 from = m.position().add(0, m.getBbHeight() * 0.6, 0);
                for (ItemStack s : rob(p, m, 1)) { launch(p, l, s, from, lvl); n++; }
            }
        }
        if (n == 0) return Fx.fail(p, K + "nothing");
        castFx(p, l, c, true);
        // inward rush of particles along the rim
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48;
            Vec3 rim = c.add(Math.cos(a) * r, 0.4, Math.sin(a) * r);
            Vec3 d = c.subtract(rim).normalize();
            l.sendParticles(Fx.dust(i % 2 == 0 ? ORANGE : GOLD, 1.4F), rim.x, rim.y, rim.z, 0, d.x, 0.05, d.z, 1.0);
        }
        Fx.bar(p, Stone.REALITY, K + "pulled", n);
        return true;
    }

    /** Removes up to count stacks from the victim and returns them (moved, not copied). */
    private static List<ItemStack> rob(ServerPlayer p, LivingEntity t, int count) {
        List<ItemStack> out = new ArrayList<>();
        if (t instanceof Player victim) {
            Inventory inv = victim.getInventory();
            for (int k = 0; k < count; k++) {
                int slot = inv.selected;
                if (inv.getItem(slot).isEmpty()) {
                    List<Integer> full = new ArrayList<>();
                    for (int i = 0; i < inv.items.size(); i++) if (!inv.items.get(i).isEmpty()) full.add(i);
                    if (full.isEmpty()) break;
                    slot = full.get(victim.getRandom().nextInt(full.size()));
                }
                out.add(inv.removeItemNoUpdate(slot));
            }
            victim.inventoryMenu.broadcastChanges();
            return out;
        }
        EquipmentSlot[] order = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot s : order) {
            if (out.size() >= count) break;
            ItemStack st = t.getItemBySlot(s);
            if (st.isEmpty()) continue;
            out.add(st.copy());
            t.setItemSlot(s, ItemStack.EMPTY);
            if (t instanceof Mob m) m.setDropChance(s, 0F);
        }
        return out;
    }

    private static void launch(ServerPlayer p, ServerLevel l, ItemStack s, Vec3 from, int lvl) {
        if (s.isEmpty()) return;
        ItemEntity it = new ItemEntity(l, from.x, from.y, from.z, s);
        l.addFreshEntity(it);
        fly(p, l, it, lvl);
    }

    /** The item belongs to the caster while it flies: nobody else can pick it up, then it lands in the inventory. */
    private static void fly(ServerPlayer p, ServerLevel l, ItemEntity it, int lvl) {
        UUID owner = p.getUUID();
        it.addTag(FLYING);
        it.setTarget(owner);
        it.setPickUpDelay(32767);
        it.setNoGravity(true);
        double speed = 0.45 + 0.12 * lvl;
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                ServerPlayer c = p.server.getPlayerList().getPlayer(owner);
                if (!it.isAlive()) return false;
                if (c == null || c.level() != it.level() || age > 160) return false;
                Vec3 dest = c.position().add(0, c.getBbHeight() * 0.55, 0);
                Vec3 d = dest.subtract(it.position());
                double dist = d.length();
                if (dist < 1.4) {
                    ItemStack s = it.getItem().copy();
                    boolean all = c.getInventory().add(s);
                    if (all) it.discard(); else it.setItem(s);
                    Fx.sound(l, dest, SoundEvents.ITEM_PICKUP, 0.5F, 0.8F + l.random.nextFloat() * 0.6F);
                    l.sendParticles(Fx.dust(GOLD, 1.0F), dest.x, dest.y, dest.z, 6, 0.25, 0.25, 0.25, 0);
                    return false;
                }
                // accelerate + slight spiral so a swarm of items reads as a vortex
                double v = Math.min(speed * (1 + age * 0.04), 2.2);
                Vec3 side = d.cross(new Vec3(0, 1, 0));
                Vec3 swirl = side.lengthSqr() < 1e-6 ? Vec3.ZERO : side.normalize().scale(Math.sin(age * 0.5 + it.getId()) * 0.12 * Math.min(1, dist / 6));
                it.setDeltaMovement(d.scale(v / dist).add(swirl));
                it.hurtMarked = true;
                if (age % 2 == 0) {
                    l.sendParticles(Fx.dust(ORANGE, 1.1F), it.getX(), it.getY() + 0.2, it.getZ(), 1, 0.05, 0.05, 0.05, 0);
                    l.sendParticles(Fx.dust(GOLD, 0.7F), it.getX(), it.getY() + 0.25, it.getZ(), 1, 0.1, 0.1, 0.1, 0);
                }
                if (age % 6 == 0) l.sendParticles(ParticleTypes.WAX_ON, it.getX(), it.getY() + 0.2, it.getZ(), 1, 0, 0, 0, 0);
                return true;
            }

            @Override public void end() {
                if (!it.isAlive()) return;
                // gave up (caster left / too long): a normal item again, where it is
                it.removeTag(FLYING);
                it.setNoGravity(false);
                it.setTarget(null);
                it.setPickUpDelay(10);
            }
        });
    }

    private static void orbFly(ServerPlayer p, ServerLevel l, ExperienceOrb orb, int lvl) {
        UUID owner = p.getUUID();
        orb.setNoGravity(true);
        Tasks.add(age -> {
            ServerPlayer c = p.server.getPlayerList().getPlayer(owner);
            if (!orb.isAlive() || c == null || c.level() != orb.level() || age > 120) { if (orb.isAlive()) orb.setNoGravity(false); return false; }
            Vec3 d = c.position().add(0, 0.6, 0).subtract(orb.position());
            double dist = d.length();
            if (dist < 1.0) { orb.setNoGravity(false); return false; }
            orb.setDeltaMovement(d.scale(Math.min(0.5 + 0.1 * lvl + age * 0.03, 2) / dist));
            orb.hurtMarked = true;
            if (age % 3 == 0) l.sendParticles(Fx.dust(GOLD, 0.8F), orb.getX(), orb.getY(), orb.getZ(), 1, 0, 0, 0, 0);
            return true;
        });
    }

    private static void castFx(ServerPlayer p, ServerLevel l, Vec3 c, boolean area) {
        Vec3 feet = p.position();
        FxNet.runes(l, FxNet.newId(), feet.add(0, 0.05, 0), 2.4F, ORANGE, 30, 2F);
        FxNet.runes(l, FxNet.newId(), feet.add(0, 0.08, 0), 1.5F, GOLD, 30, -3F);
        FxNet.orb(l, FxNet.newId(), feet, 0.35F, GOLD, 18, p.getId(), new Vec3(0, 1.2, 0));
        if (area) {
            FxNet.runes(l, FxNet.newId(), c.add(0, 0.05, 0), 5F, ORANGE, 36, -1.2F);
            FxNet.spiral(l, c, 4F, -4F, GOLD, 24, -1);
            FxNet.shockwave(l, c.add(0, 0.1, 0), 7F, ORANGE, 16);
        }
        Fx.sound(l, feet, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 0.6F);
        Fx.sound(l, feet, SoundEvents.EVOKER_PREPARE_SUMMON, 0.35F, 1.6F);
        Fx.sound(l, c, SoundEvents.CHAIN_PLACE, 0.5F, 0.7F);
    }

    private Greed() {}
}
