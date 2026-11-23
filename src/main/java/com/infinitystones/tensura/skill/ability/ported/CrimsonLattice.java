package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Port of palladium:crimson_lattice (Reality). A selective ruby wall 4 blocks in front of the caster (width x height
 * from the node, 160-tick activation). Hostile mobs cannot cross it (pushed back to their previous position) and
 * projectiles fired by monsters are destroyed on contact; the caster, players and allies pass freely. It never writes
 * world blocks: the visual is a tagged red-glass BlockDisplay plus particle facets.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class CrimsonLattice implements Ported {
    private static final Map<UUID, Barrier> BARRIERS = new HashMap<>();
    private static final String DISPLAY_TAG = "infinity_crimson_lattice_display";

    private record Barrier(ServerLevel level, UUID casterId, Vec3 center, Vec3 normal, Vec3 tangent, double width,
                           double height, long expiresAt, @Nullable UUID displayId, int rgb) {}

    public CrimsonLattice() {}

    private static DustParticleOptions ruby(int rgb) { return PortFx.dust(rgb, 0.72F); }
    private static DustParticleOptions glow() { return PortFx.dust(0xFF8CBA, 0.85F); }

    @Override
    public void start(ServerPlayer p, StoneTree.Node n) {
        ServerLevel level = p.serverLevel();
        float width = PortFx.f(n, "width", 8F), height = PortFx.f(n, "height", 4F);
        Vec3 look = p.getLookAngle();
        Vec3 normal = new Vec3(look.x, 0D, look.z);
        if (normal.lengthSqr() < 0.01D) normal = new Vec3(0D, 0D, 1D);
        normal = normal.normalize();
        Vec3 tangent = new Vec3(-normal.z, 0D, normal.x);
        Vec3 center = p.position().add(normal.scale(4D));
        Display.BlockDisplay display = createDisplay(level, center, normal, tangent, width, height);
        Barrier barrier = new Barrier(level, p.getUUID(), center, normal, tangent, width, height,
                level.getGameTime() + 160L, display == null ? null : display.getUUID(), n.stone().color() & 0xFFFFFF);
        discardDisplay(BARRIERS.put(p.getUUID(), barrier));
        Vec3 top = center.add(0D, 2D, 0D);
        PortFx.sound(level, top, n, "infinity:reality_stone", 0.7F, 0.72F);
        PortFx.sound(level, top, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.68F);
        render(level, barrier, true);
    }

    @Override
    public void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
        ServerLevel level = p.serverLevel();
        Barrier b = BARRIERS.get(p.getUUID());
        if (b == null || b.level != level || !p.isAlive()) return;
        if (level.getGameTime() >= b.expiresAt) discardDisplay(BARRIERS.remove(p.getUUID()));
        else if (level.getGameTime() % 5L == 0L) render(level, b, false);
    }

    @Override
    public void stop(ServerPlayer p, StoneTree.Node n) {
        Barrier b = BARRIERS.remove(p.getUUID());
        discardDisplay(b);
        if (b != null) b.level.sendParticles(ParticleTypes.REVERSE_PORTAL, b.center.x, b.center.y + b.height / 2D,
                b.center.z, 32, b.width / 3D, b.height / 3D, b.width / 3D, 0.03D);
    }

    /** After movement: hostile mobs cannot cross, hostile projectiles shatter; everything else passes. */
    @SubscribeEvent
    public static void entityTick(EntityTickEvent.Post e) {
        Entity entity = e.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (entity instanceof Display.BlockDisplay && entity.getTags().contains(DISPLAY_TAG)) {
            boolean active = false;
            for (Barrier b : BARRIERS.values()) if (entity.getUUID().equals(b.displayId)) { active = true; break; }
            if (!active) entity.discard();
            return;
        }
        if (BARRIERS.isEmpty()) return;
        boolean hostileMob = entity instanceof Mob && entity instanceof Enemy;
        boolean hostileProjectile = entity instanceof Projectile proj && proj.getOwner() instanceof Enemy;
        if (!hostileMob && !hostileProjectile) return;

        Vec3 old = new Vec3(entity.xOld, entity.yOld, entity.zOld);
        Vec3 now = entity.position();
        if (old.distanceToSqr(now) < 0.00001D) return;
        for (Barrier b : BARRIERS.values()) {
            if (b.level != level || level.getGameTime() >= b.expiresAt) continue;
            if (level.getEntity(b.casterId) == null) continue;
            double before = old.subtract(b.center).dot(b.normal);
            double after = now.subtract(b.center).dot(b.normal);
            if (before == 0D || before * after > 0D) continue;
            double travel = before - after;
            if (Math.abs(travel) < 0.0001D) continue;
            double fraction = before / travel;
            if (fraction < 0D || fraction > 1D) continue;
            Vec3 crossing = old.lerp(now, fraction);
            double lateral = Math.abs(crossing.subtract(b.center).dot(b.tangent));
            if (lateral > b.width / 2D + entity.getBbWidth() / 2D) continue;
            if (crossing.y > b.center.y + b.height || crossing.y + entity.getBbHeight() < b.center.y) continue;

            if (hostileProjectile) {
                entity.discard();
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, crossing.x, crossing.y, crossing.z, 10, 0.25D, 0.25D, 0.25D, 0.04D);
                level.sendParticles(glow(), crossing.x, crossing.y, crossing.z, 14, 0.35D, 0.35D, 0.35D, 0.01D);
                PortFx.sound(level, crossing, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 0.7F);
            } else {
                entity.setPos(old.x, old.y, old.z);
                Vec3 v = entity.getDeltaMovement();
                double inward = v.dot(b.normal);
                if (inward * before < 0D) entity.setDeltaMovement(v.subtract(b.normal.scale(inward)));
                entity.hasImpulse = true;
                entity.hurtMarked = true;
                if (entity instanceof Mob mob) mob.getNavigation().stop();
                if (level.getGameTime() % 8L == 0L) level.sendParticles(glow(), crossing.x,
                        crossing.y + entity.getBbHeight() / 2D, crossing.z, 8, 0.3D, 0.5D, 0.3D, 0.01D);
            }
            break;
        }
    }

    /** Failsafe for disconnects / dimension changes that skip stop(). */
    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post e) {
        if (BARRIERS.isEmpty() || e.getServer().getTickCount() % 20 != 0) return;
        BARRIERS.entrySet().removeIf(entry -> {
            Barrier b = entry.getValue();
            boolean expired = b.level.getGameTime() >= b.expiresAt || b.level.getEntity(entry.getKey()) == null;
            if (expired) discardDisplay(b);
            return expired;
        });
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { discardDisplay(BARRIERS.remove(e.getEntity().getUUID())); }

    private static @Nullable Display.BlockDisplay createDisplay(ServerLevel level, Vec3 center, Vec3 normal, Vec3 tangent,
                                                                double width, double height) {
        Display.BlockDisplay display = EntityType.BLOCK_DISPLAY.create(level);
        if (display == null) return null;
        CompoundTag blockState = new CompoundTag();
        blockState.putString("Name", "minecraft:red_stained_glass");
        CompoundTag transform = new CompoundTag();
        transform.put("translation", floats(0F, 0F, 0F));
        transform.put("scale", floats((float) width, (float) height, 0.08F));
        Quaternionf rotation = new Quaternionf().rotationY((float) Math.atan2(normal.x, normal.z));
        transform.put("left_rotation", floats(rotation.x, rotation.y, rotation.z, rotation.w));
        transform.put("right_rotation", floats(0F, 0F, 0F, 1F));
        CompoundTag data = new CompoundTag();
        data.put("block_state", blockState);
        data.put("transformation", transform);
        data.putFloat("width", (float) width + 2F);
        data.putFloat("height", (float) height + 2F);
        display.load(data);
        Vec3 left = center.add(tangent.scale(width / 2D));
        display.setPos(left.x, left.y, left.z);
        display.addTag(DISPLAY_TAG);
        return level.addFreshEntity(display) ? display : null;
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float v : values) list.add(FloatTag.valueOf(v));
        return list;
    }

    private static void discardDisplay(@Nullable Barrier b) {
        if (b == null || b.displayId == null) return;
        Entity display = b.level.getEntity(b.displayId);
        if (display != null) display.discard();
    }

    private static void render(ServerLevel level, Barrier b, boolean opening) {
        DustParticleOptions ruby = ruby(b.rgb), glow = glow();
        int columns = 8, rows = 4;
        for (int row = 0; row < rows; row++) for (int col = 0; col < columns; col++) for (int sample = 0; sample <= 3; sample++) {
            double t = sample / 3D;
            for (int diag = 0; diag < 2; diag++) {
                double side = b.width * ((col + (diag == 0 ? t : 1D - t)) / columns - 0.5D);
                double y = b.height * (row + t) / rows;
                Vec3 pt = b.center.add(b.tangent.scale(side)).add(0D, y, 0D);
                level.sendParticles(sample == 0 || sample == 3 || opening ? glow : ruby, pt.x, pt.y, pt.z, 1, 0D, 0D, 0D, 0D);
            }
        }
    }
}
