package com.infinitystones.tensura.skill.ability.extra.power;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Displays;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cataclysm Forge. Left: start forging (an orb above the hand absorbs the surroundings for up to 4 s); left again:
 * hurl it at the aimed point. Right: cancel. Alt+scroll: size 1–3. The orb takes the nature of the place:
 * STORM (thunder) / INFERNO (Nether, lava, fire) / TIDE (water, rain) / BLIZZARD (cold biome, snow) / QUAKE (default).
 * Never breaks blocks (Inferno lights a few fires only with mobGriefing).
 */
public final class CataclysmForge implements Ability {
    public static final CataclysmForge INSTANCE = new CataclysmForge();
    static final String MODE = "power.cataclysm_forge";
    static final int FULL = 80, MAX_HOLD = 600;
    enum Kind { QUAKE, INFERNO, TIDE, STORM, BLIZZARD }
    static final int[] KIND_COLOR = {0xA43CE6, 0xFF5A1E, 0x2F9CFF, 0xB070FF, 0x9EE8FF};

    private static final Map<UUID, Forge> FORGES = new HashMap<>();

    @Override public Stone stone() { return Stone.POWER; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        Forge f = FORGES.get(p.getUUID());
        if (alt) {
            if (f == null) return Fx.fail(p, "infinity.extra.power.forge.none");
            f.handle.cancel();
            Fx.barGray(p, "infinity.extra.power.forge.cancel");
            Fx.sound(p.serverLevel(), p.position(), SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.2F);
            return true;
        }
        if (f != null && f.thrown < 0) { f.hurl(); return true; }
        if (f != null) return Fx.fail(p, "infinity.extra.power.forge.busy");
        Kind k = detect(p);
        Forge n = new Forge(p, k, ExtraSettings.get(p, MODE, 1, 1, 3, 1));
        FORGES.put(p.getUUID(), n);
        n.handle = Tasks.add(n);
        Fx.bar(p, Stone.POWER, "infinity.extra.power.forge.start", net.minecraft.network.chat.Component.translatable("infinity.extra.power.forge.kind." + k.name().toLowerCase()));
        return true;
    }

    private static Kind detect(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        BlockPos c = p.blockPosition();
        if (l.isThundering() && l.canSeeSky(c)) return Kind.STORM;
        if (l.dimension() == Level.NETHER) return Kind.INFERNO;
        boolean lava = false, water = false, snow = false;
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-5, -3, -5), c.offset(5, 3, 5))) {
            BlockState s = l.getBlockState(q);
            if (s.getFluidState().is(FluidTags.LAVA) || s.getBlock() instanceof BaseFireBlock || s.is(Blocks.MAGMA_BLOCK)) lava = true;
            else if (s.getFluidState().is(FluidTags.WATER)) water = true;
            else if (s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE)) snow = true;
        }
        if (lava) return Kind.INFERNO;
        if (water || l.isRainingAt(c.above(2))) return Kind.TIDE;
        if (snow || l.getBiome(c).value().coldEnoughToSnow(c)) return Kind.BLIZZARD;
        return Kind.QUAKE;
    }

    private static final class Forge implements Tasks.Task {
        final ServerPlayer p; final ServerLevel l; final Kind kind; final int size; final int color;
        final int orbId = FxNet.newId(), circleId = PowerNet.newId();
        Tasks.Handle handle;
        int age, thrown = -1;
        float charge;
        Vec3 pos, target, from;
        double flight;

        Forge(ServerPlayer p, Kind kind, int size) {
            this.p = p; this.l = p.serverLevel(); this.kind = kind; this.size = size; this.color = KIND_COLOR[kind.ordinal()];
        }

        void hurl() {
            thrown = age;
            from = hand();
            LivingEntity e = Aim.living(p, 64, x -> Rules.canHarm(p, x));
            target = e != null ? e.getBoundingBox().getCenter() : Aim.point(p, 64);
            pos = from;
            flight = Math.max(4, from.distanceTo(target) / 2.2);
            Fx.sound(l, from, SoundEvents.BLAZE_SHOOT, 0.7F, 0.5F);
            Fx.sound(l, from, SoundEvents.WARDEN_SONIC_BOOM, 0.4F, 1.6F);
            PowerNet.kill(l, p.position(), circleId);
        }

        Vec3 hand() { return p.getEyePosition().add(p.getLookAngle().scale(0.8)).add(0, 0.9 + charge * 0.4 * size, 0); }

        float orbSize() { return 0.25F + charge * (0.45F + 0.35F * size); }

        @Override
        public boolean tick(int a) {
            age = a;
            if (!p.isAlive() || p.isRemoved() || p.level() != l) return false;
            RandomSource rnd = l.random;
            if (thrown < 0) {
                if (a > MAX_HOLD) { Fx.barGray(p, "infinity.extra.power.forge.fade"); return false; }
                charge = Math.min(1F, a / (float) FULL);
                Vec3 h = hand();
                if (a % 3 == 0) FxNet.orb(l, orbId, h, orbSize(), color, 12, -1, Vec3.ZERO);
                if (a % 20 == 0) PowerNet.circle(l, circleId, new Vec3(0, 0.05, 0), new Vec3(0, 1, 0), 1.6F + size * 0.6F, color, PowerUtil.ACCENT, 30, 1.5F + charge * 3, PowerNet.SIMPLE, p);
                // the environment flows into the orb
                for (int k = 0; k < 2 + size; k++) {
                    double an = rnd.nextDouble() * Math.PI * 2, rr = 3 + rnd.nextDouble() * 3;
                    Vec3 s = p.position().add(Math.cos(an) * rr, rnd.nextDouble() * 2, Math.sin(an) * rr);
                    Vec3 v = h.subtract(s);
                    l.sendParticles(feed(), s.x, s.y, s.z, 0, v.x, v.y, v.z, 0.12);
                }
                if (a % 10 == 0 && charge < 1) Fx.sound(l, h, SoundEvents.BEACON_AMBIENT, 0.5F, 0.6F + charge);
                if (a == FULL) { Fx.sound(l, h, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 0.6F, 0.8F); Fx.bar(p, Stone.POWER, "infinity.extra.power.forge.full"); FxNet.flare(l, h, 2.5F, color, 10); }
                return true;
            }
            // flight
            int ft = a - thrown;
            double t = Math.min(1, ft / flight);
            Vec3 next = from.lerp(target, t).add(0, Math.sin(t * Math.PI) * Math.min(6, flight * 0.3), 0);
            Fx.line(l, pos, next, 0.5, Fx.dust(color, 1.8F));
            l.sendParticles(feed(), next.x, next.y, next.z, 4, 0.2, 0.2, 0.2, 0.02);
            pos = next;
            FxNet.orb(l, orbId, pos, orbSize(), color, 6, -1, Vec3.ZERO);
            BlockPos bp = BlockPos.containing(pos);
            boolean hitBlock = ft > 2 && !l.getBlockState(bp).getCollisionShape(l, bp).isEmpty();
            if (t >= 1 || hitBlock) { FxNet.kill(l, pos, orbId); detonate(pos); return false; }
            return true;
        }

        ParticleOptions feed() {
            return switch (kind) {
                case INFERNO -> ParticleTypes.FLAME;
                case TIDE -> ParticleTypes.SPLASH;
                case STORM -> ParticleTypes.ELECTRIC_SPARK;
                case BLIZZARD -> ParticleTypes.SNOWFLAKE;
                case QUAKE -> new BlockParticleOption(ParticleTypes.BLOCK, PowerUtil.surface(l, p.position()));
            };
        }

        void detonate(Vec3 at) {
            Vec3 g = Ground.below(l, at.add(0, 0.5, 0), 10);
            float c = Math.max(0.25F, charge);
            double r = 3 + 3 * c + 2 * (size - 1);
            float dmg = 6 + 14 * c + 4 * (size - 1);
            PowerNet.circle(l, 0, g.add(0, 0.1, 0), new Vec3(0, 1, 0), (float) r, color, PowerUtil.ACCENT, 40, -2F, 0);
            FxNet.shockwave(l, g.add(0, 0.2, 0), (float) r * 1.6F, color, 24);
            FxNet.shake(l, g, 30 + r * 2, 1.5F + c * 2, 20);
            FxNet.flashAround(l, g, r * 2, color, 0.35F, 8);
            Fx.sound(l, g, SoundEvents.GENERIC_EXPLODE, 0.7F, 0.7F);
            switch (kind) {
                case INFERNO -> inferno(g, r, dmg);
                case TIDE -> tide(g, r, dmg);
                case STORM -> storm(g, r, dmg);
                case BLIZZARD -> blizzard(g, r, dmg);
                case QUAKE -> quake(g, r, dmg);
            }
        }

        void inferno(Vec3 g, double r, float dmg) {
            // a magma meteor drops onto the impact first
            Vec3 top = g.add(0, 14, 0);
            Display.BlockDisplay m = Displays.block(l, Blocks.MAGMA_BLOCK.defaultBlockState(), top, 1.6F + size * 0.5F, 0xFF5A1E, 2);
            Tasks.add(new Tasks.Task() {
                @Override public boolean tick(int a) {
                    if (m == null) return false;
                    Vec3 v = top.lerp(g, Math.min(1, a / 8.0));
                    m.setPos(v.x, v.y, v.z);
                    l.sendParticles(ParticleTypes.FLAME, v.x, v.y, v.z, 6, 0.4, 0.4, 0.4, 0.02);
                    l.sendParticles(ParticleTypes.LARGE_SMOKE, v.x, v.y + 0.5, v.z, 2, 0.3, 0.3, 0.3, 0.01);
                    if (a >= 8) {
                        Fx.at(l, ParticleTypes.LAVA, g, 30, r * 0.4, 0.2);
                        Fx.at(l, ParticleTypes.FLAME, g, 80, r * 0.5, 0.15);
                        Fx.at(l, ParticleTypes.EXPLOSION_EMITTER, g, 1, 0, 0);
                        for (LivingEntity e : PowerUtil.victims(p, l, g, r)) { Rules.hurt(p, e, dmg); e.igniteForSeconds(6); PowerUtil.push(e, g, 0.8, 0.5); }
                        if (Rules.griefing(l)) {
                            RandomSource rnd = l.random;
                            for (int k = 0; k < 6 + size * 4; k++) {
                                BlockPos fp = BlockPos.containing(g.add((rnd.nextDouble() - 0.5) * r * 1.6, 1, (rnd.nextDouble() - 0.5) * r * 1.6));
                                BlockPos floor = Ground.floorBlock(l, Vec3.atCenterOf(fp).add(0, 1, 0), 4);
                                if (floor != null && l.getBlockState(floor.above()).isAir()) l.setBlockAndUpdate(floor.above(), BaseFireBlock.getState(l, floor.above()));
                            }
                        }
                        return false;
                    }
                    return true;
                }
                @Override public void end() { Displays.remove(m); }
            });
        }

        void tide(Vec3 g, double r, float dmg) {
            // ring wave of "water" blocks rolling outward
            Tasks.add(a -> {
                double rr = 1 + a * 0.9;
                int n = Math.min(24, (int) (rr * 3));
                for (int k = 0; k < n; k++) {
                    double an = Math.PI * 2 * k / n + a * 0.1;
                    Vec3 q = g.add(Math.cos(an) * rr, 0.2, Math.sin(an) * rr);
                    l.sendParticles(ParticleTypes.SPLASH, q.x, q.y + 0.6, q.z, 4, 0.3, 0.5, 0.3, 0.1);
                    l.sendParticles(ParticleTypes.BUBBLE_POP, q.x, q.y + 1.2, q.z, 1, 0.2, 0.4, 0.2, 0.02);
                    if (k % 3 == 0 && a % 2 == 0) PowerUtil.debris(l, q, (k % 2 == 0 ? Blocks.LIGHT_BLUE_STAINED_GLASS : Blocks.BLUE_STAINED_GLASS).defaultBlockState(),
                            new Vec3(Math.cos(an) * 0.3, 0.5 + size * 0.1, Math.sin(an) * 0.3), 0.9F + size * 0.2F, 14, 0);
                }
                for (LivingEntity e : PowerUtil.victims(p, l, g, rr + 1)) {
                    if (e.position().distanceTo(g) < rr - 1.5) continue;
                    if (a % 4 == 0) Rules.hurt(p, e, dmg * 0.35F);
                    PowerUtil.push(e, g, 0.55 + size * 0.1, 0.15);
                    e.clearFire();
                }
                if (a % 4 == 0) Fx.sound(l, g, SoundEvents.GENERIC_SPLASH, 0.6F, 0.6F);
                return rr < r * 2;
            });
        }

        void storm(Vec3 g, double r, float dmg) {
            int domeId = FxNet.newId();
            FxNet.dome(l, domeId, g, (float) r, 0xB070FF, 110);
            Tasks.add(new Tasks.Task() {
                @Override public boolean tick(int a) {
                    if (a % 5 == 0) {
                        RandomSource rnd = l.random;
                        double an = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble() * r;
                        Vec3 hit = Ground.below(l, g.add(Math.cos(an) * rr, 4, Math.sin(an) * rr), 12);
                        FxNet.beam(l, hit.add(0, 30, 0), hit, 0xB070FF, 0.35F, 8);
                        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(l);
                        if (bolt != null) { bolt.moveTo(hit); bolt.setVisualOnly(true); l.addFreshEntity(bolt); }
                        l.sendParticles(ParticleTypes.ELECTRIC_SPARK, hit.x, hit.y + 0.5, hit.z, 30, 0.6, 0.6, 0.6, 0.3);
                        for (LivingEntity e : PowerUtil.victims(p, l, hit, 2.5 + size * 0.5)) { Rules.hurt(p, e, dmg * 0.45F); PowerUtil.push(e, hit, 0.4, 0.3); }
                    }
                    return a < 100;
                }
                @Override public void end() { FxNet.kill(l, g, domeId); }
            });
        }

        void blizzard(Vec3 g, double r, float dmg) {
            RandomSource rnd = l.random;
            int spikes = 8 + size * 5;
            for (int k = 0; k < spikes; k++) {
                double an = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble() * r;
                Vec3 base = Ground.below(l, g.add(Math.cos(an) * rr, 3, Math.sin(an) * rr), 8);
                int delay = (int) (rr * 1.5);
                float sc = 0.7F + rnd.nextFloat() * (0.6F + size * 0.3F);
                BlockState ice = rnd.nextBoolean() ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.BLUE_ICE.defaultBlockState();
                Tasks.add(new Tasks.Task() {
                    Display.BlockDisplay d;
                    @Override public boolean tick(int a) {
                        if (a == delay) {
                            d = Displays.block(l, ice, base, 0.1F, 0x9EE8FF, 1);
                            if (d != null) Displays.pose(d, sc * 2, rnd.nextFloat() * 6, 0.3F * (rnd.nextFloat() - 0.5F), 3);
                            l.sendParticles(ParticleTypes.SNOWFLAKE, base.x, base.y + 0.5, base.z, 12, 0.4, 0.6, 0.4, 0.1);
                            Fx.sound(l, base, SoundEvents.GLASS_BREAK, 0.4F, 0.6F + rnd.nextFloat() * 0.4F);
                            for (LivingEntity e : PowerUtil.victims(p, l, base.add(0, 1, 0), 1.8 + sc)) {
                                Rules.hurt(p, e, dmg * 0.5F);
                                e.setTicksFrozen(Math.max(e.getTicksFrozen(), 300));
                                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3));
                                PowerUtil.push(e, base, 0.2, 0.6);
                            }
                        }
                        if (d != null && a == delay + 40) Displays.pose(d, 0.05F, 0, 0, 10);
                        return a < delay + 52;
                    }
                    @Override public void end() { Displays.remove(d); }
                });
            }
            Fx.at(l, ParticleTypes.SNOWFLAKE, g.add(0, 1, 0), 120, r * 0.6, 0.1);
        }

        void quake(Vec3 g, double r, float dmg) {
            Tasks.add(a -> {
                double rr = 1 + a * 1.1;
                int n = Math.min(20, (int) (rr * 2.5));
                for (int k = 0; k < n; k++) {
                    double an = Math.PI * 2 * k / n + a;
                    Vec3 q = Ground.below(l, g.add(Math.cos(an) * rr, 2, Math.sin(an) * rr), 6);
                    BlockState st = PowerUtil.surface(l, q);
                    if (k % 2 == 0) PowerUtil.debris(l, q.add(0, 0.5, 0), st, new Vec3(0, 0.45 + size * 0.1, 0), 0.9F, 14, 0);
                    l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), q.x, q.y + 0.2, q.z, 5, 0.4, 0.1, 0.4, 0.15);
                }
                for (LivingEntity e : PowerUtil.victims(p, l, g, rr + 1)) {
                    if (e.position().distanceTo(g) < rr - 1.5) continue;
                    Rules.hurt(p, e, dmg * 0.7F);
                    PowerUtil.push(e, g, 0.3, 0.9 + size * 0.15);
                }
                if (a % 3 == 0) Fx.sound(l, g, SoundEvents.DRIPSTONE_BLOCK_BREAK, 0.7F, 0.4F);
                return rr < r;
            });
        }

        @Override
        public void end() {
            FORGES.remove(p.getUUID(), this);
            if (thrown < 0 || pos != null) FxNet.kill(l, p.position(), orbId);
            PowerNet.kill(l, p.position(), circleId);
        }
    }
}
