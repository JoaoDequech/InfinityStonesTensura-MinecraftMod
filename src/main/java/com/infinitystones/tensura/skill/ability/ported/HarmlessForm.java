package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneTree;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Port of palladium:harmless_form (Reality). The aimed creature (any non-boss mob, or any player) becomes a harmless
 * chicken for the activation window (160 ticks).
 *
 * <p>Mobs are really replaced: their full NBT (UUID, health, equipment, effects, owner...) is stored on a chicken
 * stand-in (scaled to the mob's size, no AI, invulnerable) and the original is put back, identical, when the spell ends,
 * the caster dies/leaves or the world reloads (the NBT lives on the chicken, so a restart cannot lose the mob).
 *
 * <p>Players stay themselves but are drawn as a chicken to everyone (a tiny synced flag plus a client-only render swap,
 * {@code client.port.HarmlessFormRenderer}; name hidden), walk slower and can neither attack, use items nor edit blocks.
 * Bosses (Dragon, Wither, Warden, 300+ max health), riders and mounts resist.</p>
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class HarmlessForm implements Ported {
    public static final String FORM_TAG = "infinity_harmless_form";
    private static final String SAVED = "infinity_harmless_form_mob";
    private static final ResourceLocation SLOW = InfinityStones.id("harmless_form_slow");
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    /** Client-side copy: ids of players currently drawn as chickens (filled by the payload handler only). */
    public static final Set<Integer> CLIENT_FORMS = ConcurrentHashMap.newKeySet();

    private record Session(ServerLevel level, UUID caster, long expiresAt, int rgb, boolean player) {}

    public HarmlessForm() {}

    @Override
    public void start(ServerPlayer p, StoneTree.Node n) {
        ServerLevel level = p.serverLevel();
        float maxDistance = PortFx.f(n, "max_distance", 32F);
        LivingEntity aimed = PortFx.aimed(p, maxDistance, HarmlessForm::eligible);
        if (aimed == null || !PortFx.canAffect(p, aimed) || SESSIONS.containsKey(aimed.getUUID())) return;
        int rgb = n.stone().color() & 0xFFFFFF;
        long until = level.getGameTime() + 160L;
        if (aimed instanceof ServerPlayer victim) {
            SESSIONS.put(victim.getUUID(), new Session(victim.serverLevel(), p.getUUID(), until, rgb, true));
            AttributeInstance speed = victim.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && speed.getModifier(SLOW) == null)
                speed.addTransientModifier(new AttributeModifier(SLOW, -0.45D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            victim.stopUsingItem();
            victim.setSprinting(false);
            syncPlayer(victim, true);
            burst(victim.serverLevel(), victim, rgb);
        } else if (aimed instanceof Mob mob) {
            Chicken chicken = EntityType.CHICKEN.create(level);
            CompoundTag saved = new CompoundTag();
            if (chicken == null || !mob.save(saved)) return;
            chicken.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
            chicken.yHeadRot = mob.yHeadRot; chicken.yBodyRot = mob.yBodyRot;
            chicken.setNoAi(true);
            chicken.setPersistenceRequired();
            chicken.setInvulnerable(true);
            chicken.eggTime = Integer.MAX_VALUE;
            chicken.addTag(FORM_TAG);
            chicken.getPersistentData().put(SAVED, saved);
            AttributeInstance scale = chicken.getAttribute(Attributes.SCALE);
            if (scale != null) scale.setBaseValue(Mth.clamp(mob.getBbHeight() / 0.7F, 0.9F, 3F));
            if (mob.hasCustomName()) { chicken.setCustomName(mob.getCustomName()); chicken.setCustomNameVisible(mob.isCustomNameVisible()); }
            burst(level, mob, rgb);
            mob.discard();
            level.addFreshEntity(chicken);
            SESSIONS.put(chicken.getUUID(), new Session(level, p.getUUID(), until, rgb, false));
        } else return;
        PortFx.sound(level, aimed.position(), n, "infinity:reality_stone", 0.7F, 1.3F);
        PortFx.sound(level, aimed.position(), SoundEvents.CHICKEN_HURT, 0.8F, 1.1F);
        PortFx.sound(level, aimed.position(), SoundEvents.CHICKEN_AMBIENT, 0.7F, 0.85F);
    }

    @Override
    public void stop(ServerPlayer p, StoneTree.Node n) { releaseAllOf(p.getUUID()); }

    private static boolean eligible(LivingEntity e) {
        if (e instanceof ServerPlayer sp) return sp.isAlive() && !sp.isSpectator();
        return e instanceof Mob m && m.isAlive() && !m.getTags().contains(FORM_TAG) && !(m instanceof EnderDragon)
                && !(m instanceof WitherBoss) && !(m instanceof Warden) && m.getMaxHealth() < 300F
                && !m.isPassenger() && !m.isVehicle() && !Rules.isBoss(m);
    }

    private static void releaseAllOf(UUID caster) {
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            Session s = SESSIONS.get(id);
            if (s != null && s.caster.equals(caster)) end(id, s);
        }
    }

    /** Puts one session back to normal. */
    private static void end(UUID id, Session s) {
        SESSIONS.remove(id);
        Entity e = s.level.getEntity(id);
        if (s.player) { if (e instanceof ServerPlayer sp) restorePlayer(sp); }
        else if (e instanceof Chicken c) restoreMob(c);
    }

    public static boolean isTransformed(@Nullable Entity entity) {
        if (entity == null) return false;
        if (entity instanceof ServerPlayer) { Session s = SESSIONS.get(entity.getUUID()); return s != null && s.player; }
        return entity.getTags().contains(FORM_TAG);
    }

    // ---------------- hooks ----------------

    /** Keeps the transformation running; ends it when time is up, the caster is gone, or (after a restart) nothing remembers it. */
    @SubscribeEvent
    public static void tick(EntityTickEvent.Post e) {
        if (!(e.getEntity().level() instanceof ServerLevel level)) return;
        Entity ent = e.getEntity();
        boolean proxy = ent instanceof Chicken && ent.getTags().contains(FORM_TAG);
        boolean victim = ent instanceof ServerPlayer sp && SESSIONS.containsKey(sp.getUUID());
        if (!proxy && !victim) return;
        Session s = SESSIONS.get(ent.getUUID());
        Entity caster = s == null ? null : level.getEntity(s.caster);
        boolean over = s == null || s.level != level || level.getGameTime() >= s.expiresAt
                || !(caster instanceof LivingEntity living) || !living.isAlive() || !ent.isAlive()
                || (victim && !((ServerPlayer) ent).isAlive());
        if (over) {
            if (s != null) SESSIONS.remove(ent.getUUID());
            if (proxy) restoreMob((Chicken) ent);
            else if (ent instanceof ServerPlayer sp) restorePlayer(sp);
            return;
        }
        if (proxy) {
            Chicken c = (Chicken) ent;
            c.setNoAi(true);
            c.eggTime = Integer.MAX_VALUE;
        } else {
            ServerPlayer sp = (ServerPlayer) ent;
            sp.setSprinting(false);
            if (sp.isUsingItem()) sp.stopUsingItem();
            if (level.getGameTime() % 20L == 0L) syncPlayer(sp, true);
        }
        if (level.getGameTime() % 5L == 0L) {
            DustParticleOptions dust = PortFx.dust(s.rgb, 0.75F);
            double phase = level.getGameTime() * 0.12D;
            for (int i = 0; i < 8; i++) {
                double a = phase + i * Math.PI / 4D;
                level.sendParticles(dust, ent.getX() + Math.cos(a) * 0.55D, ent.getY() + 0.35D + Math.sin(a * 2D) * 0.15D,
                        ent.getZ() + Math.sin(a) * 0.55D, 1, 0D, 0D, 0D, 0D);
            }
        }
    }

    /** A transformed creature cannot hurt anything. */
    @SubscribeEvent
    public static void incomingDamage(LivingIncomingDamageEvent e) {
        Entity src = e.getSource().getEntity();
        if (src == null) src = e.getSource().getDirectEntity();
        if (isTransformed(src)) e.setCanceled(true);
    }

    @SubscribeEvent public static void attack(AttackEntityEvent e) { if (isTransformed(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void useItem(LivingEntityUseItemEvent.Start e) { if (isTransformed(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void rightItem(PlayerInteractEvent.RightClickItem e) { if (isTransformed(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void rightBlock(PlayerInteractEvent.RightClickBlock e) { if (isTransformed(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void leftBlock(PlayerInteractEvent.LeftClickBlock e) { if (isTransformed(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void entityUse(PlayerInteractEvent.EntityInteract e) { if (isTransformed(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void entityUseAt(PlayerInteractEvent.EntityInteractSpecific e) { if (isTransformed(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent e) { if (isTransformed(e.getPlayer())) e.setCanceled(true); }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking e) {
        if (e.getEntity() instanceof ServerPlayer viewer && e.getTarget() instanceof ServerPlayer t && isTransformed(t))
            PacketDistributor.sendToPlayer(viewer, new Sync(t.getId(), true));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        Session own = SESSIONS.remove(sp.getUUID());
        if (own != null) restorePlayer(sp);
        releaseAllOf(sp.getUUID());
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) { Session s = SESSIONS.get(id); if (s != null) end(id, s); }
        SESSIONS.clear();
    }

    // ---------------- helpers ----------------

    private static void restoreMob(Chicken chicken) {
        if (!(chicken.level() instanceof ServerLevel level)) return;
        CompoundTag saved = chicken.getPersistentData().contains(SAVED) ? chicken.getPersistentData().getCompound(SAVED) : null;
        chicken.removeTag(FORM_TAG);
        chicken.getPersistentData().remove(SAVED);
        chicken.discard();
        if (saved == null) return;
        Entity mob = EntityType.loadEntityRecursive(saved, level, x -> x);
        if (mob == null) return;
        mob.moveTo(chicken.getX(), chicken.getY(), chicken.getZ(), chicken.getYRot(), chicken.getXRot());
        mob.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(mob);
        burst(level, mob, Stone.REALITY.color() & 0xFFFFFF);
        PortFx.sound(level, mob.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.65F);
    }

    private static void restorePlayer(ServerPlayer p) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SLOW);
        syncPlayer(p, false);
        burst(p.serverLevel(), p, Stone.REALITY.color() & 0xFFFFFF);
        PortFx.sound(p.serverLevel(), p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.65F);
    }

    private static void syncPlayer(ServerPlayer p, boolean active) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new Sync(p.getId(), active));
    }

    private static void burst(ServerLevel level, Entity at, int rgb) {
        double y = at.getY() + at.getBbHeight() * 0.5D;
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.FEATHER)), at.getX(), y, at.getZ(), 40, 0.5D, 0.5D, 0.5D, 0.12D);
        level.sendParticles(ParticleTypes.POOF, at.getX(), y, at.getZ(), 24, 0.45D, 0.5D, 0.45D, 0.05D);
        level.sendParticles(ParticleTypes.CLOUD, at.getX(), y, at.getZ(), 10, 0.4D, 0.4D, 0.4D, 0.03D);
        level.sendParticles(PortFx.dust(rgb, 0.75F), at.getX(), y, at.getZ(), 30, 0.5D, 0.6D, 0.5D, 0.02D);
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
