package com.infinitystones.tensura.skill.ability.extra.v2a;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.MenuNet;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wish (complete Infinity Gauntlet). Left: grants the selected wish; right: menu of wishes; scroll: cycle the wish. Every
 * wish is temporary and leaves the world untouched, but each one helps: the flower rain heals allies (3% of max HP a
 * second) with Regeneration; the feast heals 40% of max HP at once and gives Regeneration II, Resistance II, Strength II
 * and Saturation for a minute; the rainbow gives Luck, Saturation and a shield worth 20% of max HP; the fireworks hurt
 * hostile mobs around (3% of max HP a second); the starry night gives Night Vision, Speed II and Jump Boost II.
 * "Allies" are the caster, its team, its pets. A new wish replaces the caster's previous one.
 */
public final class GauntletWish implements Ability {
    public static final GauntletWish INSTANCE = new GauntletWish();
    static final String MODE = "power.gauntlet_wish";
    static final String NODE = "gauntlet_wish";
    private static final int FLOWERS = 0, FEAST = 1, RAINBOW = 2, FIREWORKS = 3, STARS = 4, COUNT = 5;
    private static final String[] KEYS = {"flowers", "feast", "rainbow", "fireworks", "stars"};
    private static final String[] ICONS = {"minecraft:poppy", "minecraft:cooked_beef", "minecraft:light_blue_dye", "minecraft:firework_rocket", "minecraft:nether_star"};
    private static final Map<UUID, Tasks.Handle> ACTIVE = new HashMap<>();

    @Override public Stone stone() { return Stone.POWER; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (alt) return MenuNet.open(p, Stone.POWER, NODE, this) || Fx.fail(p, "infinity.v2a.wish.none");
        int sel = ExtraSettings.get(p, MODE, 1, 1, COUNT, 1) - 1;
        grant(p, sel);
        return true;
    }

    @Override
    public @Nullable List<MenuNet.Option> menu(ServerPlayer p) {
        int sel = ExtraSettings.get(p, MODE, 1, 1, COUNT, 1) - 1;
        List<MenuNet.Option> out = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) out.add(MenuNet.opt("infinity.v2a.wish." + KEYS[i], ICONS[i], i == sel));
        return out;
    }

    @Override
    public void pick(ServerPlayer p, int index) {
        if (index < 0 || index >= COUNT) return;
        grant(p, index);
    }

    private static void grant(ServerPlayer p, int wish) {
        Tasks.Handle old = ACTIVE.remove(p.getUUID());
        if (old != null) old.cancel();
        ServerLevel l = p.serverLevel();
        Tasks.Task t = switch (wish) {
            case FLOWERS -> new FlowerRain(p, l);
            case FEAST -> new Feast(p, l);
            case RAINBOW -> new Rainbow(p, l);
            case FIREWORKS -> new Fireworks_(p, l);
            default -> new Stars(p, l);
        };
        Fx.bar(p, Stone.POWER, "infinity.v2a.wish." + KEYS[wish] + ".granted");
        FxNet.flare(l, p.position().add(0, 1.2, 0), 2.4F, Bits.GOLD, 12);
        Fx.sound(l, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.4F);
        Fx.sound(l, p.position(), SoundEvents.BEACON_POWER_SELECT, 0.45F, 1.6F);
        ACTIVE.put(p.getUUID(), Tasks.add(t));
    }

    /** The caster plus allies, team members and pets within r of c. */
    private static List<LivingEntity> allies(ServerPlayer p, ServerLevel l, Vec3 c, double r) {
        return l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                e -> e.isAlive() && !e.isSpectator() && Rules.isAlly(p, e) && e.getBoundingBox().getCenter().distanceToSqr(c) <= r * r);
    }

    private static void buff(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(effect, ticks, amp, true, true, true));
    }

    private static void heal(LivingEntity e, float share) {
        e.heal(e.getMaxHealth() * share);
        if (e.level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.HEART, e.getX(), e.getY(0.8), e.getZ(), 3, 0.35, 0.3, 0.35, 0);
    }

    /** Common base: runs for a fixed time and forgets the caster's slot when it ends. */
    private abstract static class Wish implements Tasks.Task {
        final ServerPlayer p; final ServerLevel l; final Vec3 home; final int life;
        Wish(ServerPlayer p, ServerLevel l, int life) { this.p = p; this.l = l; this.life = life; this.home = p.position(); }
        @Override public void end() { ACTIVE.values().removeIf(h -> !h.alive()); }
    }

    // ------------------------------------------------------------------ flower rain

    private static final class FlowerRain extends Wish {
        static final Block[] FLOWERS_ = {Blocks.POPPY, Blocks.DANDELION, Blocks.BLUE_ORCHID, Blocks.ALLIUM, Blocks.AZURE_BLUET,
                Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP, Blocks.OXEYE_DAISY,
                Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY, Blocks.PINK_PETALS};
        int live;

        FlowerRain(ServerPlayer p, ServerLevel l) { super(p, l, 240); }

        @Override
        public boolean tick(int age) {
            RandomSource rnd = l.random;
            Vec3 c = p.isAlive() && p.level() == l ? p.position() : home;
            if (age < life - 40) {
                for (int k = 0; k < 1 && live < 80; k++) spawn(c, rnd);
                if (age % 2 == 0) {
                    double a = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble() * 14;
                    l.sendParticles(ParticleTypes.CHERRY_LEAVES, c.x + Math.cos(a) * rr, c.y + 10 + rnd.nextDouble() * 4, c.z + Math.sin(a) * rr, 6, 1.5, 0.5, 1.5, 0.01);
                }
                if (age % 20 == 0) {
                    Fx.sound(l, c, SoundEvents.AMETHYST_BLOCK_CHIME, 0.35F, 1.2F + rnd.nextFloat() * 0.5F);
                    for (LivingEntity e : allies(p, l, c, 14)) { heal(e, 0.03F); buff(e, MobEffects.REGENERATION, 60, 1); }
                }
            }
            return age < life;
        }

        private void spawn(Vec3 c, RandomSource rnd) {
            double a = rnd.nextDouble() * Math.PI * 2, rr = Math.sqrt(rnd.nextDouble()) * 13;
            Vec3 from = c.add(Math.cos(a) * rr, 11 + rnd.nextDouble() * 3, Math.sin(a) * rr);
            Block b = FLOWERS_[rnd.nextInt(FLOWERS_.length)];
            float s = 0.5F + rnd.nextFloat() * 0.35F;
            Display.BlockDisplay d = Dsp.block(l, b.defaultBlockState(), from, s, s, s, rnd.nextFloat() * 6.28F, 0, 3);
            if (d == null) return;
            live++;
            final float spin = (rnd.nextFloat() - 0.5F) * 0.7F, sway = rnd.nextFloat() * 6.28F;
            Tasks.add(new Tasks.Task() {
                Vec3 pos = from;
                @Override public boolean tick(int a2) {
                    if (d.isRemoved()) return false;
                    if (a2 % 3 != 0) return true;
                    pos = pos.add(Math.sin(a2 * 0.15 + sway) * 0.12, -0.42, Math.cos(a2 * 0.13 + sway) * 0.12);
                    Vec3 floor = Ground.below(l, pos.add(0, 0.5, 0), 3);
                    if (pos.y <= floor.y + 0.2 || a2 > 200) {
                        Dsp.pose(d, 0.01F, 0.01F, 0.01F, 0, 0, 3);
                        l.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y, pos.z, 1, 0.2, 0.1, 0.2, 0);
                        return false;
                    }
                    d.setPos(pos.x, pos.y, pos.z);
                    Dsp.pose(d, s, s, s, a2 * spin, 0, 3);
                    return true;
                }
                @Override public void end() { Dsp.remove(d); live--; }
            });
        }
    }

    // ------------------------------------------------------------------ feast

    private static final class Feast extends Wish {
        static final Item_[] FOOD = {Item_.of(Items.BREAD), Item_.of(Items.COOKED_BEEF), Item_.of(Items.COOKED_PORKCHOP), Item_.of(Items.APPLE),
                Item_.of(Items.GOLDEN_APPLE), Item_.of(Items.COOKED_CHICKEN), Item_.of(Items.PUMPKIN_PIE), Item_.of(Items.COOKIE),
                Item_.of(Items.MELON_SLICE), Item_.of(Items.BAKED_POTATO), Item_.of(Items.GOLDEN_CARROT), Item_.of(Items.COOKED_SALMON),
                Item_.of(Items.HONEY_BOTTLE), Item_.of(Items.SWEET_BERRIES), Item_.of(Items.COOKED_MUTTON), Item_.of(Items.BEETROOT_SOUP)};
        record Item_(ItemStack stack) { static Item_ of(net.minecraft.world.item.Item i) { return new Item_(new ItemStack(i)); } }

        final List<Display> parts = new ArrayList<>();
        final List<float[]> full = new ArrayList<>();
        final List<Vec3> candles = new ArrayList<>();
        Vec3 base; float yaw;

        Feast(ServerPlayer p, ServerLevel l) { super(p, l, 640); }

        @Override
        public boolean tick(int age) {
            RandomSource rnd = l.random;
            if (age == 0) build(rnd);
            // dishes pop in one after another
            if (age < 60 && age % 2 == 0) {
                int i = age / 2;
                if (i < parts.size()) {
                    float[] f = full.get(i);
                    Display d = parts.get(i);
                    if (!d.isRemoved()) {
                        Dsp.pose(d, f[0], f[1], f[2], f[3], f[4], 6);
                        if (i > 8) l.sendParticles(ParticleTypes.HAPPY_VILLAGER, d.getX(), d.getY() + 0.2, d.getZ(), 2, 0.15, 0.1, 0.15, 0);
                    }
                    if (i % 4 == 0) Fx.sound(l, base, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.45F, 0.8F + 0.06F * (i % 12));
                }
            }
            if (age % 6 == 0) for (Vec3 c : candles) l.sendParticles(ParticleTypes.SMALL_FLAME, c.x, c.y, c.z, 1, 0.01, 0.01, 0.01, 0);
            if (age % 10 == 0) l.sendParticles(ParticleTypes.END_ROD, base.x, base.y + 1.4, base.z, 3, 1.8, 0.2, 0.7, 0.01);
            if (age == 20)
                for (LivingEntity e : allies(p, l, base, 8)) {
                    heal(e, 0.40F);
                    buff(e, MobEffects.SATURATION, 40, 1);
                    buff(e, MobEffects.REGENERATION, 600, 1);
                    buff(e, MobEffects.DAMAGE_RESISTANCE, 1200, 1);
                    buff(e, MobEffects.DAMAGE_BOOST, 1200, 1);
                }
            if (age == life - 20) for (int i = 0; i < parts.size(); i++) {
                Display d = parts.get(i);
                if (!d.isRemoved()) Dsp.pose(d, 0.01F, 0.01F, 0.01F, full.get(i)[3], full.get(i)[4], 18);
            }
            return age < life;
        }

        private void build(RandomSource rnd) {
            Vec3 look = p.getLookAngle();
            Vec3 fwd = new Vec3(look.x, 0, look.z);
            fwd = fwd.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : fwd.normalize();
            Vec3 spot = p.position().add(fwd.scale(3.6));
            Vec3 g = Ground.below(l, spot.add(0, 2, 0), 6);
            base = g;
            yaw = (float) Math.atan2(-fwd.x, -fwd.z);
            Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
            // table: legs, top, cloth (all stretched block copies, popping in from nothing)
            add(Blocks.SPRUCE_PLANKS.defaultBlockState(), g.add(0, 0.9, 0), 4.6F, 0.2F, 1.9F, yaw);
            add(Blocks.RED_CARPET.defaultBlockState(), g.add(0, 1.02, 0), 4.4F, 1.0F, 1.7F, yaw);
            add(Blocks.GOLD_BLOCK.defaultBlockState(), g.add(0, 1.05, 0), 0.5F, 0.06F, 0.5F, yaw);
            for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2)
                add(Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), g.add(side.scale(sx * 2.05).add(fwd.scale(sz * 0.75))).add(0, 0.45, 0), 0.22F, 0.9F, 0.22F, yaw);
            // two rows of dishes along the table
            int n = 0;
            for (int row = 0; row < 2; row++) for (int k = 0; k < 8; k++) {
                Vec3 at = g.add(side.scale(-1.85 + k * 0.53 + rnd.nextDouble() * 0.12)).add(fwd.scale(row == 0 ? -0.38 : 0.38)).add(0, 1.12, 0);
                ItemStack st = FOOD[(n++ + rnd.nextInt(2)) % FOOD.length].stack().copy();
                Display.ItemDisplay d = Dsp.item(l, st, at, 0.01F, (float) -Math.PI / 2, yaw, 4);
                if (d != null) { parts.add(d); full.add(new float[]{0.5F, 0.5F, 0.5F, yaw + rnd.nextFloat() * 6.28F, (float) -Math.PI / 2}); }
            }
            // a cake and candles in the middle
            add(Blocks.CAKE.defaultBlockState(), g.add(0, 1.17, 0), 0.62F, 0.62F, 0.62F, yaw);
            for (int k = -1; k <= 1; k += 2) {
                Vec3 cp = g.add(side.scale(k * 0.9)).add(0, 1.12, 0);
                add(Blocks.CANDLE.defaultBlockState(), cp.add(0, 0.17, 0), 0.4F, 0.4F, 0.4F, yaw);
                candles.add(cp.add(0, 0.5, 0));
            }
            FxNet.shockwave(l, g.add(0, 0.1, 0), 3.6F, Bits.GOLD, 18);
            FxNet.flare(l, g.add(0, 1.4, 0), 3F, Bits.GOLD, 16);
        }

        private void add(BlockState st, Vec3 centre, float sx, float sy, float sz, float yaw) {
            Display.BlockDisplay d = Dsp.block(l, st, centre, 0.01F, 0.01F, 0.01F, yaw, 0, 4);
            if (d == null) return;
            parts.add(d);
            full.add(new float[]{sx, sy, sz, yaw, 0});
        }

        @Override
        public void end() {
            for (Display d : parts) Dsp.remove(d);
            parts.clear();
            super.end();
        }
    }

    // ------------------------------------------------------------------ rainbow / stars (client shows)

    private static final class Rainbow extends Wish {
        Rainbow(ServerPlayer p, ServerLevel l) { super(p, l, 460); }
        int id;

        @Override
        public boolean tick(int age) {
            if (age == 0) {
                Vec3 look = p.getLookAngle();
                Vec3 fwd = new Vec3(look.x, 0, look.z);
                fwd = fwd.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : fwd.normalize();
                Vec3 centre = Ground.below(l, p.position().add(fwd.scale(70)).add(0, 4, 0), 40);
                if (centre.y < p.getY() - 30) centre = new Vec3(centre.x, p.getY() - 6, centre.z);
                id = V2aNet.newId();
                V2aNet.send(l, p.position(), 240, V2aNet.msg(V2aNet.RAINBOW, id, 0, life, 1F, centre, (float) Math.atan2(fwd.x, fwd.z), 0, 0, 0, 0));
                Fx.sound(l, p.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 0.6F, 1.6F);
            }
            if (age % 30 == 10) Fx.sound(l, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.35F, 1.5F);
            if (age % 40 == 0 && age < life - 40) for (LivingEntity e : allies(p, l, p.position(), 24)) {
                buff(e, MobEffects.LUCK, 3600, 1);
                buff(e, MobEffects.SATURATION, 40, 1);
                buff(e, MobEffects.ABSORPTION, 1200, 4);
                if (age == 0) e.setAbsorptionAmount(Math.max(e.getAbsorptionAmount(), Math.min(e.getMaxHealth() * 0.2F, 300F)));
            }
            return age < life;
        }

        @Override public void end() { if (id != 0) V2aNet.kill(l, p.position(), id); super.end(); }
    }

    private static final class Stars extends Wish {
        Stars(ServerPlayer p, ServerLevel l) { super(p, l, 700); }
        int id;

        @Override
        public boolean tick(int age) {
            if (age == 0) {
                id = V2aNet.newId();
                V2aNet.send(l, p.position(), 200, V2aNet.msg(V2aNet.STARS, id, 0, life, 1F, p.position(), 0, 0, 0, 0, 0));
                Fx.sound(l, p.position(), SoundEvents.BEACON_ACTIVATE, 0.4F, 1.8F);
            }
            if (age % 50 == 25) Fx.sound(l, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.3F, 0.8F + l.random.nextFloat() * 0.8F);
            if (age % 40 == 0 && age < life - 40) for (LivingEntity e : allies(p, l, p.position(), 30)) {
                buff(e, MobEffects.NIGHT_VISION, 400, 0);
                buff(e, MobEffects.MOVEMENT_SPEED, 100, 1);
                buff(e, MobEffects.JUMP, 100, 1);
            }
            return age < life;
        }

        @Override public void end() { if (id != 0) V2aNet.kill(l, p.position(), id); super.end(); }
    }

    // ------------------------------------------------------------------ fireworks (visual only: the burst is announced to the clients, no rocket explodes)

    private static final class Fireworks_ extends Wish {
        static final int[] PALETTE = {0xFF3B3B, 0xFFB02E, 0xFFE94A, 0x4CE04C, 0x3BB8FF, 0x8A5CFF, 0xFF6BD8, 0xFFFFFF};
        Fireworks_(ServerPlayer p, ServerLevel l) { super(p, l, 360); }

        @Override
        public boolean tick(int age) {
            RandomSource rnd = l.random;
            Vec3 c = p.isAlive() && p.level() == l ? p.position() : home;
            boolean finale = age > 260 && age < 320;
            if ((age % 8 == 0 && age < 300) || (finale && age % 3 == 0)) burst(c, rnd, finale ? 2 : 1);
            if (age % 8 == 0 && age < 300) Fx.sound(l, c, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.5F, 0.9F + rnd.nextFloat() * 0.3F);
            if (age % 20 == 10 && age < 320)
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(22, 14, 22),
                        e -> e instanceof Enemy && Rules.canHarm(p, e) && e.getBoundingBox().getCenter().distanceToSqr(c) <= 22 * 22)) {
                    Rules.hurtPct(p, e, 4F, 0.03F);
                    l.sendParticles(ParticleTypes.FIREWORK, e.getX(), e.getY(0.6), e.getZ(), 6, 0.3, 0.4, 0.3, 0.05);
                }
            return age < life;
        }

        private void burst(Vec3 c, RandomSource rnd, int n) {
            for (int k = 0; k < n; k++) {
                double a = rnd.nextDouble() * Math.PI * 2, rr = 6 + rnd.nextDouble() * 18;
                Vec3 at = c.add(Math.cos(a) * rr, 16 + rnd.nextDouble() * 16, Math.sin(a) * rr);
                FireworkExplosion.Shape shape = FireworkExplosion.Shape.values()[rnd.nextInt(FireworkExplosion.Shape.values().length)];
                IntArrayList colors = new IntArrayList();
                int first = PALETTE[rnd.nextInt(PALETTE.length)];
                colors.add(first);
                if (rnd.nextBoolean()) colors.add(PALETTE[rnd.nextInt(PALETTE.length)]);
                IntArrayList fade = new IntArrayList();
                if (rnd.nextInt(3) == 0) fade.add(PALETTE[rnd.nextInt(PALETTE.length)]);
                FireworkExplosion ex = new FireworkExplosion(shape, colors, fade, rnd.nextInt(3) == 0, rnd.nextInt(3) == 0);
                ItemStack st = new ItemStack(Items.FIREWORK_ROCKET);
                st.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(ex)));
                FireworkRocketEntity rocket = new FireworkRocketEntity(l, at.x, at.y, at.z, st);
                rocket.setNoGravity(true);
                l.addFreshEntity(rocket);
                l.broadcastEntityEvent(rocket, (byte) 17);
                Tasks.add(new Tasks.Task() {
                    @Override public boolean tick(int a2) { return a2 < 2; }
                    @Override public void end() { rocket.discard(); }
                });
            }
        }
    }
}
