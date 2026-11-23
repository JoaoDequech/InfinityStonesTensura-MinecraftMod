package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.StoneTree;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Port of palladium:harmless_form (Reality). Rewrites the common hostile under the caster's aim (within
 * {@code max_distance}) into a harmless chicken for the activation window (160 ticks): its AI is switched off, it
 * forgets its target and any damage it would deal is cancelled. The mob keeps its UUID, health, equipment and NBT,
 * and the original AI returns when the spell ends, the caster dies/leaves, or the mob dies. Bosses (Dragon, Wither,
 * Warden, 300+ max health), riders and mounts resist.
 *
 * <p>Visual: the original used a client mixin. Here the server syncs "who is transformed" with a tiny payload
 * ({@code infinity:port_harmless_form}, sent to trackers on change, on start-tracking and every second) and a
 * client-only {@code RenderLivingEvent.Pre} handler ({@code client.port.HarmlessFormRenderer}) draws a chicken in
 * place of the mob — no mixin. Persistent tags let a reloaded/unloaded mob recover its original AI.</p>
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class HarmlessForm implements Ported {
    public static final String FORM_TAG = "infinity_harmless_form";
    private static final String ORIGINAL_NO_AI = "infinity_harmless_form_original_no_ai";
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    /** Client-side copy: ids of entities currently drawn as chickens (filled by the payload handler only). */
    public static final Set<Integer> CLIENT_FORMS = ConcurrentHashMap.newKeySet();

    private record Session(ServerLevel level, UUID caster, long expiresAt, int rgb) {}

    public HarmlessForm() {}

    @Override
    public void start(ServerPlayer p, StoneTree.Node n) {
        ServerLevel level = p.serverLevel();
        float maxDistance = PortFx.f(n, "max_distance", 32F);
        LivingEntity aimed = PortFx.aimed(p, maxDistance, HarmlessForm::eligible);
        if (!(aimed instanceof Mob target) || !PortFx.canAffect(p, target) || SESSIONS.containsKey(target.getUUID())) return;
        if (target.isNoAi()) target.addTag(ORIGINAL_NO_AI);
        target.addTag(FORM_TAG);
        SESSIONS.put(target.getUUID(), new Session(level, p.getUUID(), level.getGameTime() + 160L, n.stone().color() & 0xFFFFFF));
        target.setNoAi(true);
        target.setTarget(null);
        target.getNavigation().stop();
        target.setDeltaMovement(Vec3.ZERO);
        sync(target, true);
        burst(level, target, n.stone().color() & 0xFFFFFF);
        PortFx.sound(level, target.position(), n, "infinity:reality_stone", 0.7F, 1.3F);
        PortFx.sound(level, target.position(), SoundEvents.CHICKEN_AMBIENT, 0.7F, 0.85F);
    }

    @Override
    public void stop(ServerPlayer p, StoneTree.Node n) { releaseAllOf(p.getUUID()); }

    private static boolean eligible(LivingEntity e) {
        return e instanceof Mob m && m.isAlive() && m instanceof Enemy && !(m instanceof EnderDragon)
                && !(m instanceof WitherBoss) && !(m instanceof Warden) && m.getMaxHealth() < 300F
                && !m.isPassenger() && !m.isVehicle();
    }

    private static void releaseAllOf(UUID caster) {
        SESSIONS.entrySet().removeIf(entry -> {
            if (!entry.getValue().caster.equals(caster)) return false;
            Entity target = entry.getValue().level.getEntity(entry.getKey());
            if (target instanceof Mob mob) restore(mob);
            return true;
        });
    }

    public static boolean isTransformed(@Nullable Entity entity) {
        return entity != null && entity.getTags().contains(FORM_TAG);
    }

    // ---------------- hooks ----------------

    /** Keeps the rewrite applied; restores when the session is gone (also after a restart thanks to the tags). */
    @SubscribeEvent
    public static void mobTick(EntityTickEvent.Post e) {
        if (!(e.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)
                || !mob.getTags().contains(FORM_TAG)) return;
        Session s = SESSIONS.get(mob.getUUID());
        Entity caster = s == null ? null : level.getEntity(s.caster);
        if (s == null || s.level != level || level.getGameTime() >= s.expiresAt
                || !(caster instanceof LivingEntity living) || !living.isAlive() || !mob.isAlive()) {
            SESSIONS.remove(mob.getUUID());
            restore(mob);
            return;
        }
        mob.setNoAi(true);
        mob.setTarget(null);
        mob.getNavigation().stop();
        if (level.getGameTime() % 20L == 0L) sync(mob, true);
        if (level.getGameTime() % 5L == 0L) {
            DustParticleOptions ruby = PortFx.dust(s.rgb, 0.75F);
            double phase = level.getGameTime() * 0.12D;
            for (int i = 0; i < 12; i++) {
                double a = phase + i * Math.PI / 6D;
                level.sendParticles(ruby, mob.getX() + Math.cos(a) * 0.55D, mob.getY() + 0.35D + Math.sin(a * 2D) * 0.15D,
                        mob.getZ() + Math.sin(a) * 0.55D, 1, 0D, 0D, 0D, 0D);
            }
        }
    }

    /** A rewritten creature cannot hurt anything. */
    @SubscribeEvent
    public static void incomingDamage(LivingIncomingDamageEvent e) {
        if (isTransformed(e.getSource().getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking e) {
        if (e.getEntity() instanceof ServerPlayer viewer && isTransformed(e.getTarget()))
            PacketDistributor.sendToPlayer(viewer, new Sync(e.getTarget().getId(), true));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { releaseAllOf(e.getEntity().getUUID()); }

    // ---------------- helpers ----------------

    private static void restore(Mob mob) {
        mob.setNoAi(mob.getTags().contains(ORIGINAL_NO_AI));
        mob.removeTag(FORM_TAG);
        mob.removeTag(ORIGINAL_NO_AI);
        sync(mob, false);
        if (mob.level() instanceof ServerLevel level) {
            burst(level, mob, com.infinitystones.tensura.skill.Stone.REALITY.color() & 0xFFFFFF);
            PortFx.sound(level, mob.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.65F);
        }
    }

    private static void sync(Mob mob, boolean active) {
        if (mob.level() instanceof ServerLevel) PacketDistributor.sendToPlayersTrackingEntity(mob, new Sync(mob.getId(), active));
    }

    private static void burst(ServerLevel level, Mob mob, int rgb) {
        level.sendParticles(ParticleTypes.CHERRY_LEAVES, mob.getX(), mob.getY() + 0.7D, mob.getZ(), 24, 0.6D, 0.7D, 0.6D, 0.02D);
        level.sendParticles(PortFx.dust(rgb, 0.75F), mob.getX(), mob.getY() + 0.7D, mob.getZ(), 35, 0.5D, 0.65D, 0.5D, 0.02D);
    }

    // ---------------- network ----------------

    /** Server → client: entity {@code id} is (or is no longer) drawn in its harmless form. */
    public record Sync(int entityId, boolean active) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(InfinityStones.id("port_harmless_form"));
        public static final StreamCodec<ByteBuf, Sync> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Sync::entityId, ByteBufCodecs.BOOL, Sync::active, Sync::new);
        @Override public Type<Sync> type() { return TYPE; }
    }

    @EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            PayloadRegistrar r = event.registrar("infinity_port");
            r.playToClient(Sync.TYPE, Sync.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
                if (msg.active()) CLIENT_FORMS.add(msg.entityId());
                else CLIENT_FORMS.remove(msg.entityId());
            }));
        }
        private Network() {}
    }
}
