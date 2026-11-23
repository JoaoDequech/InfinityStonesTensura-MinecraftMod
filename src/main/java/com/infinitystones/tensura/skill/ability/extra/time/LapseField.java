package com.infinitystones.tensura.skill.ability.extra.time;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Displays;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lapse Field: left = fast field at the aimed point, right = slow field; speed ×2–×10 with Alt+scroll; 30 s, radius 6.
 * Fast: plants/saplings/copper (extra random ticks), furnaces, blast furnaces, smokers, campfires and brewing stands
 * (extra ticks), baby animals and breeding cooldowns run N× faster; everyone inside gets Speed + Haste.
 * Slow: mobs, players and projectiles of non-allies inside run at 1/N (heavy Slowness + Mining Fatigue; projectiles
 * crawl and fly on at full speed when they leave). One field per player.
 */
public final class LapseField implements Ability {
    public static final LapseField INSTANCE = new LapseField();
    private static final String MODE = "time.lapse_field";
    private static final int DURATION = 600;
    private static final float RADIUS = 6F;

    private static final class Field {
        final UUID owner; final ServerLevel level; final Vec3 c; final boolean fast; final int n; final int id = TimeNet.newId();
        final List<BlockPos> machines = new ArrayList<>();
        final Map<Entity, Vec3> slowed = new IdentityHashMap<>();
        Tasks.Handle handle;
        Field(UUID owner, ServerLevel level, Vec3 c, boolean fast, int n) { this.owner = owner; this.level = level; this.c = c; this.fast = fast; this.n = n; }
    }

    private static final Map<UUID, Field> FIELDS = new HashMap<>();

    @Override public Stone stone() { return Stone.TIME; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        Field old = FIELDS.get(p.getUUID());
        if (old != null) old.handle.cancel();
        int n = ExtraSettings.get(p, MODE, 4, 2, 10, 1);
        Vec3 c = Aim.ground(p, 48);
        ServerLevel l = p.serverLevel();
        Field f = new Field(p.getUUID(), l, c, !alt, n);
        FIELDS.put(p.getUUID(), f);
        TimeNet.lapse(l, f.id, c, RADIUS, f.fast ? n : -n, 40);
        FxNet.shockwave(l, c.add(0, 0.1, 0), RADIUS * 1.4F, Stone.TIME.color(), 18);
        FxNet.pillar(l, 0, c, 0.3F, 6, Stone.TIME.color(), 20);
        Fx.sound(l, c, SoundEvents.BELL_BLOCK, 0.5F, f.fast ? 1.8F : 0.5F);
        Fx.sound(l, c, f.fast ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.BEACON_DEACTIVATE, 0.6F, f.fast ? 1.6F : 0.6F);
        scanMachines(f);
        f.handle = Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) { return FIELDS.get(f.owner) == f && age < DURATION && tickField(f, age); }
            @Override public void end() {
                FIELDS.remove(f.owner, f);
                TimeNet.kill(f.level, f.c, f.id);
                for (Map.Entry<Entity, Vec3> e : f.slowed.entrySet()) restore(e.getKey(), e.getValue());
                f.slowed.clear();
                Fx.sound(f.level, f.c, SoundEvents.AMETHYST_BLOCK_BREAK, 0.5F, 1F);
                FxNet.shockwave(f.level, f.c.add(0, 0.1, 0), RADIUS, Stone.TIME.color(), 14);
            }
        });
        Fx.bar(p, Stone.TIME, f.fast ? "infinity.extra.time.lapse.fast" : "infinity.extra.time.lapse.slow", n);
        return true;
    }

    private static boolean in(Field f, Vec3 v) {
        double dx = v.x - f.c.x, dz = v.z - f.c.z;
        return dx * dx + dz * dz <= RADIUS * RADIUS && v.y > f.c.y - 3 && v.y < f.c.y + 6;
    }

    private static void scanMachines(Field f) {
        f.machines.clear();
        int r = (int) Math.ceil(RADIUS);
        int minX = Mth.floor(f.c.x) - r, maxX = Mth.floor(f.c.x) + r, minZ = Mth.floor(f.c.z) - r, maxZ = Mth.floor(f.c.z) + r;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
            if (!f.level.hasChunk(cx, cz)) continue;
            LevelChunk ch = f.level.getChunk(cx, cz);
            for (Map.Entry<BlockPos, BlockEntity> e : ch.getBlockEntities().entrySet()) {
                BlockEntity be = e.getValue();
                if ((be instanceof AbstractFurnaceBlockEntity || be instanceof BrewingStandBlockEntity || be instanceof CampfireBlockEntity)
                        && in(f, Vec3.atCenterOf(e.getKey()))) f.machines.add(e.getKey().immutable());
                if (f.machines.size() >= 64) return;
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void tickMachines(Field f) {
        for (BlockPos pos : f.machines) {
            BlockEntity be = f.level.getBlockEntity(pos);
            if (be == null || be.isRemoved()) continue;
            BlockState s = be.getBlockState();
            BlockEntityTicker t = s.getTicker(f.level, be.getType());
            if (t == null) continue;
            for (int i = 1; i < f.n; i++) t.tick(f.level, pos, s, be);
            if (f.level.random.nextInt(10) == 0) Fx.at(f.level, Fx.light(Stone.TIME, 0.7F), Vec3.atCenterOf(pos).add(0, 0.6, 0), 2, 0.3, 0);
        }
    }

    private static boolean tickField(Field f, int age) {
        ServerLevel l = f.level;
        ServerPlayer owner = l.getServer().getPlayerList().getPlayer(f.owner);
        if (owner == null) return false;
        if (age % 20 == 0) TimeNet.lapse(l, f.id, f.c, RADIUS, f.fast ? f.n : -f.n, 40);
        int tickEvery = f.fast ? Math.max(2, 12 - f.n) : 10 + f.n * 3;
        if (age % tickEvery == 0) Fx.sound(l, f.c, SoundEvents.NOTE_BLOCK_HAT.value(), 0.25F, f.fast ? 1.6F : 0.5F);
        if (f.fast) {
            if (age % 40 == 0) scanMachines(f);
            tickMachines(f);
            RandomSource rnd = l.random;
            int extra = (f.n - 1) * 4;
            for (int i = 0; i < extra; i++) {
                double ang = rnd.nextDouble() * Math.PI * 2, rr = Math.sqrt(rnd.nextDouble()) * RADIUS;
                BlockPos pos = BlockPos.containing(f.c.x + Math.cos(ang) * rr, f.c.y - 2 + rnd.nextInt(7), f.c.z + Math.sin(ang) * rr);
                BlockState s = l.getBlockState(pos);
                if (s.isRandomlyTicking()) {
                    s.randomTick(l, pos, rnd);
                    if (rnd.nextInt(4) == 0) Fx.at(l, ParticleTypes.HAPPY_VILLAGER, Vec3.atCenterOf(pos), 1, 0.3, 0);
                }
            }
        }
        AABB box = new AABB(f.c.x - RADIUS, f.c.y - 3, f.c.z - RADIUS, f.c.x + RADIUS, f.c.y + 6, f.c.z + RADIUS);
        if (f.fast) {
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && in(f, e.position()))) {
                if (e instanceof AgeableMob am) {
                    int a = am.getAge();
                    if (a < 0) am.setAge(Math.min(0, a + (f.n - 1)));
                    else if (a > 0) am.setAge(Math.max(0, a - (f.n - 1)));
                }
                if (age % 20 == 0) {
                    int amp = Mth.clamp(f.n / 3, 0, 2);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, amp, true, false, true));
                    e.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 30, amp, true, false, true));
                }
            }
        } else {
            for (Entity e : l.getEntities((Entity) null, box, e -> e.isAlive() && !e.getTags().contains(Displays.TAG))) {
                boolean inside = in(f, e.position());
                if (e instanceof Projectile pr) {
                    Entity shooter = pr.getOwner();
                    if (shooter != null && Rules.isAlly(owner, shooter)) continue;
                    Vec3 orig = f.slowed.get(e);
                    if (inside && orig == null) {
                        f.slowed.put(e, e.getDeltaMovement());
                        e.setNoGravity(true);
                        e.setDeltaMovement(e.getDeltaMovement().scale(1.0 / f.n));
                        e.hurtMarked = true;
                    } else if (!inside && orig != null) { f.slowed.remove(e); restore(e, orig); }
                    else if (inside) Fx.at(l, Fx.dust(0x1E9C8C, 0.6F), e.position(), 1, 0, 0);
                } else if (e instanceof LivingEntity le && inside && age % 10 == 0 && Rules.canHarm(owner, le) && Rules.isLiving(le)) {
                    int amp = Mth.clamp(f.n - 1, 1, 6);
                    le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, amp, true, false, true));
                    le.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20, Mth.clamp(f.n / 3, 0, 2), true, false, true));
                    if (age % 20 == 0) le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 25, 0, true, false, true));
                }
            }
            f.slowed.keySet().removeIf(e -> !e.isAlive());
        }
        return true;
    }

    private static void restore(Entity e, Vec3 vel) {
        if (!e.isAlive()) return;
        e.setNoGravity(false);
        e.setDeltaMovement(vel);
        e.hurtMarked = true;
    }
}
