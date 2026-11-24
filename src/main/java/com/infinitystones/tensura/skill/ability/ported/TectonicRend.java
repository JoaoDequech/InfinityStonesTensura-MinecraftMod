package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.StoneTree;
import com.infinitystones.tensura.skill.ability.extra.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Power Stone "Tectonic Rend" — port of TectonicRendAbility (timed session, 24 ticks): a ground-hugging energy fissure
 * advances one block per tick from where the caster stood, in the horizontal look direction, up to {@code range}
 * blocks. Every hostile (and player) within hit_radius of the crack front is struck once for a share of its max HP
 * (18%, {@code damage} is only the floor for small mobs) and launched upward. The crack really opens: a trench about
 * 5 blocks wide and up to 5 deep is carved along the path (no drops; claims, spawn protection and unbreakables kept).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class TectonicRend implements Ported {

    private static final class Rend {
        final Vec3 origin, direction;
        final Set<UUID> struck = new HashSet<>();
        final float damage, hitRadius, launch;
        final int range, color;
        int nextStep = 1;
        @Nullable Terrain.Session session;
        Rend(Vec3 origin, Vec3 direction, float damage, float hitRadius, float launch, int range, int color) {
            this.origin = origin; this.direction = direction; this.damage = damage; this.hitRadius = hitRadius;
            this.launch = launch; this.range = range; this.color = color;
        }
    }

    private static final Map<UUID, Rend> RENDS = new HashMap<>();

    @Override
    public void start(ServerPlayer p, StoneTree.Node n) {
        ServerLevel level = p.serverLevel();
        Vec3 direction = p.getLookAngle().multiply(1D, 0D, 1D);
        if (direction.lengthSqr() < 0.001D) return;
        Rend rend = new Rend(p.position(), direction.normalize(), PortFx.f(n, "damage", 120F),
                PortFx.f(n, "hit_radius", 2.2F), PortFx.f(n, "launch_strength", 1.05F), PortFx.i(n, "range", 24), n.stone().color());
        rend.session = new Terrain.Session(p);
        RENDS.put(p.getUUID(), rend);
        PortFx.sound(level, p.position(), n, "infinity:power_stone_beam1", 0.7F, 0.62F);
        PortFx.sound(level, p.position(), SoundEvents.WARDEN_SONIC_BOOM, 0.5F, 0.72F);
    }

    @Override
    public void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
        ServerLevel level = p.serverLevel();
        Rend rend = RENDS.get(p.getUUID());
        if (rend == null || rend.nextStep > rend.range) return;
        Vec3 sample = rend.origin.add(rend.direction.scale(rend.nextStep));
        BlockPos surface = findSurface(level, sample.x, rend.origin.y, sample.z);
        if (surface != null) { rupture(level, p, rend, Vec3.atBottomCenterOf(surface.above()), surface); trench(level, rend, sample); }
        renderPersistentCrack(level, rend);
        rend.nextStep++;
    }

    @Override
    public void stop(ServerPlayer p, StoneTree.Node n) { RENDS.remove(p.getUUID()); }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { RENDS.remove(e.getEntity().getUUID()); }

    private static DustParticleOptions main(Rend r) { return PortFx.dust(r.color, 0.85F); }
    private static DustParticleOptions accent(Rend r) {
        int c = r.color, red = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        red = red + (255 - red) * 45 / 100; g = g + (255 - g) * 25 / 100; b = b + (255 - b) * 45 / 100;
        return PortFx.dust((red << 16) | (g << 8) | b, 0.75F);
    }

    private static void rupture(ServerLevel level, ServerPlayer caster, Rend rend, Vec3 point, BlockPos surface) {
        BlockState state = level.getBlockState(surface);
        DustParticleOptions violet = main(rend), magenta = accent(rend);
        Vec3 side = new Vec3(-rend.direction.z, 0D, rend.direction.x);
        Vec3 center = point.add(side.scale(Math.sin(rend.nextStep * 1.85D) * 0.65D));

        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), center.x, center.y + 0.12D, center.z,
                11, 0.62D, 0.22D, 0.62D, 0.16D);
        level.sendParticles(rend.nextStep % 2 == 0 ? violet : magenta, center.x, center.y + 0.18D, center.z,
                7, 0.42D, 0.15D, 0.42D, 0.04D);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y + 0.25D, center.z, 3, 0.22D, 0.12D, 0.22D, 0.02D);
        for (int branch = -2; branch <= 2; branch++) {
            Vec3 bp = center.add(side.scale(branch * 0.43D)).add(rend.direction.scale(Math.abs(branch) * -0.14D));
            level.sendParticles(branch % 2 == 0 ? magenta : violet, bp.x, bp.y + 0.08D + Math.abs(branch) * 0.035D, bp.z, 1, 0, 0, 0, 0);
        }
        if (rend.nextStep % 6 == 0) {
            level.sendParticles(magenta, center.x, center.y + 0.3D, center.z, 12, 0.8D, 0.32D, 0.8D, 0.065D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y + 0.25D, center.z, 8, 0.7D, 0.28D, 0.7D, 0.13D);
        }

        AABB hitArea = new AABB(center, center).inflate(rend.hitRadius, 2.5D, rend.hitRadius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, hitArea,
                l -> l.isAlive() && (l instanceof Enemy || l instanceof net.minecraft.world.entity.player.Player) && !rend.struck.contains(l.getUUID()) && PortFx.canAffect(caster, l))) {
            rend.struck.add(target.getUUID());
            target.hurt(Rules.source(caster, target, false), Rules.amount(target, rend.damage, 0.18F, false));
            target.push(rend.direction.x * 0.38D, rend.launch, rend.direction.z * 0.38D);
            target.hurtMarked = true;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getEyeY(), target.getZ(),
                    22, target.getBbWidth() * 0.45D, target.getBbHeight() * 0.28D, target.getBbWidth() * 0.45D, 0.17D);
            PortFx.sound(level, target.position(), SoundEvents.GENERIC_EXPLODE, 0.55F, 0.72F);
        }

        if (rend.nextStep == 1 || rend.nextStep % 6 == 0) {
            PortFx.sound(level, center, SoundEvents.DEEPSLATE_BREAK, 0.6F, 0.65F + rend.nextStep * 0.008F);
        }
    }

    /** Opens the ground across the crack: 5 wide, deepest (5) in the middle and shallower towards the edges. */
    private static void trench(ServerLevel level, Rend rend, Vec3 sample) {
        if (rend.session == null) return;
        Vec3 side = new Vec3(-rend.direction.z, 0D, rend.direction.x);
        for (int s = -2; s <= 2; s++) {
            Vec3 q = sample.add(side.scale(s));
            BlockPos top = findSurface(level, q.x, rend.origin.y, q.z);
            if (top == null) continue;
            int depth = s == 0 ? 5 : Math.abs(s) == 1 ? 3 : 2;
            for (int d = 0; d < depth; d++) rend.session.remove(top.below(d), 60F);
            if (rend.nextStep % 3 == 0 && s == 0)
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(top.below(depth))), q.x, top.getY() + 1.0, q.z, 10, 0.8, 0.4, 0.8, 0.2);
        }
    }

    private static @Nullable BlockPos findSurface(ServerLevel level, double x, double baseY, double z) {
        BlockPos anchor = BlockPos.containing(x, baseY, z);
        if (!level.hasChunkAt(anchor)) return null;
        for (int offset = 2; offset >= -4; offset--) {
            BlockPos candidate = anchor.offset(0, offset, 0);
            if (!level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                    && level.getBlockState(candidate.above()).getCollisionShape(level, candidate.above()).isEmpty()) {
                return candidate;
            }
        }
        return null;
    }

    private static void renderPersistentCrack(ServerLevel level, Rend rend) {
        DustParticleOptions violet = main(rend), magenta = accent(rend);
        Vec3 side = new Vec3(-rend.direction.z, 0D, rend.direction.x);
        for (int step = 1; step <= rend.nextStep; step++) {
            Vec3 sample = rend.origin.add(rend.direction.scale(step));
            BlockPos surface = findSurface(level, sample.x, rend.origin.y, sample.z);
            if (surface == null) continue;
            Vec3 center = Vec3.atBottomCenterOf(surface.above()).add(side.scale(Math.sin(step * 1.85D) * 0.65D));
            level.sendParticles(step % 2 == 0 ? magenta : violet, center.x, center.y + 0.045D, center.z, 1, 0, 0, 0, 0);
            if (step % 3 == 0) {
                for (int branch : new int[]{-1, 1}) {
                    Vec3 bp = center.add(side.scale(branch * 0.48D)).add(rend.direction.scale(-0.12D));
                    level.sendParticles(violet, bp.x, bp.y + 0.04D, bp.z, 1, 0, 0, 0, 0);
                }
            }
        }
    }
}
