package com.infinitystones.tensura.skill.ability.extra.fear.mob;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.fear.FearApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Registry of the realm's creatures and the {@link FearApi.Creatures} implementation. */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class FearMobs {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, InfinityStones.MOD_ID);
    public static final Map<Kind, DeferredHolder<EntityType<?>, EntityType<FearMob>>> MOBS = new EnumMap<>(Kind.class);
    public static final Map<Apparition.Type, DeferredHolder<EntityType<?>, EntityType<Apparition>>> APPS = new EnumMap<>(Apparition.Type.class);
    private static final Map<EntityType<?>, Kind> KINDS = new IdentityHashMap<>();
    private static final Map<EntityType<?>, Apparition.Type> APP_TYPES = new IdentityHashMap<>();
    private static final Map<UUID, List<FearEntity>> TRACKED = new ConcurrentHashMap<>();

    static {
        for (Kind k : Kind.values()) {
            String id = "fear_" + k.id();
            MOBS.put(k, ENTITIES.register(id, () -> EntityType.Builder.<FearMob>of(FearMob::new, MobCategory.MISC)
                    .sized(k.width, k.height).clientTrackingRange(12).updateInterval(1).fireImmune().noSummon().noSave()
                    .build(InfinityStones.MOD_ID + ":" + id)));
        }
        for (Apparition.Type t : Apparition.Type.values()) {
            String id = "fear_ap_" + t.id();
            APPS.put(t, ENTITIES.register(id, () -> EntityType.Builder.<Apparition>of(Apparition::new, MobCategory.MISC)
                    .sized(t.width, t.height).clientTrackingRange(12).updateInterval(1).fireImmune().noSummon().noSave()
                    .build(InfinityStones.MOD_ID + ":" + id)));
        }
    }

    /** Called once from the mod constructor. */
    public static void init(IEventBus bus) {
        ENTITIES.register(bus);
        FearSounds.SOUNDS.register(bus);
        bus.addListener(FearMobs::attributes);
        FearApi.creatures(new Impl());
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        MOBS.forEach((k, h) -> e.put(h.get(), FearEntity.attributes(k.walk).build()));
        APPS.forEach((t, h) -> e.put(h.get(), FearEntity.attributes(.3).build()));
    }

    public static Kind kindOf(EntityType<?> type) {
        Kind k = KINDS.get(type);
        if (k == null) {
            for (var en : MOBS.entrySet()) if (en.getValue().isBound() && en.getValue().get() == type) { k = en.getKey(); KINDS.put(type, k); break; }
        }
        return k == null ? Kind.WENDIGO : k;
    }

    public static Apparition.Type appTypeOf(EntityType<?> type) {
        Apparition.Type t = APP_TYPES.get(type);
        if (t == null) {
            for (var en : APPS.entrySet()) if (en.getValue().isBound() && en.getValue().get() == type) { t = en.getKey(); APP_TYPES.put(type, t); break; }
        }
        return t == null ? Apparition.Type.RUSHER : t;
    }

    static void track(UUID victim, FearEntity e) {
        if (victim != null) TRACKED.computeIfAbsent(victim, k -> new CopyOnWriteArrayList<>()).add(e);
    }

    static void untrack(UUID victim, FearEntity e) {
        if (victim == null) return;
        List<FearEntity> l = TRACKED.get(victim);
        if (l != null) { l.remove(e); if (l.isEmpty()) TRACKED.remove(victim); }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent e) { TRACKED.clear(); }

    /** A standing spot with room for the given size near (x, y0, z): scans a few blocks up and down. */
    static Vec3 ground(ServerLevel l, Entity ctx, double x, double y0, double z, float w, float h) {
        if (!l.hasChunkAt(BlockPos.containing(x, y0, z))) return null;
        int hh = (int) Math.ceil(h);
        for (int dy = 5; dy >= -10; dy--) {
            double y = Math.floor(y0) + dy;
            BlockPos below = BlockPos.containing(x, y - 1, z);
            if (!l.getBlockState(below).isFaceSturdy(l, below, Direction.UP)) continue;
            AABB box = new AABB(x - w / 2, y, z - w / 2, x + w / 2, y + hh, z + w / 2);
            if (l.noCollision(ctx, box)) return new Vec3(x, y, z);
        }
        return null;
    }

    private static final class Impl implements FearApi.Creatures {
        @Override
        public List<String> ids() {
            List<String> out = new ArrayList<>();
            for (Kind k : Kind.values()) out.add(k.id());
            return out;
        }

        @Override
        public String forScene(String sceneId) { return Kind.forScene(sceneId).id(); }

        @Override
        public Entity spawn(ServerLevel fear, String creatureId, ServerPlayer victim, BlockPos at, int mode) {
            Kind k = Kind.byId(creatureId);
            FearMob e = MOBS.get(k).get().create(fear);
            if (e == null) return null;
            e.moveTo(at.getX() + .5, at.getY(), at.getZ() + .5, victim.getYRot() + 180F, 0F);
            e.bind(victim);
            e.begin(mode);
            fear.addFreshEntity(e);
            return e;
        }

        @Override
        public void setMode(UUID victim, String creatureId, int mode) {
            List<FearEntity> l = TRACKED.get(victim);
            if (l == null) return;
            for (FearEntity e : l) if (e instanceof FearMob m && (creatureId == null || m.kind.id().equals(creatureId))) m.setMode(mode);
        }

        @Override
        public void apparition(ServerLevel fear, ServerPlayer victim, String kind) {
            Apparition.Type t = Apparition.Type.byId(kind);
            if (t == null) t = Apparition.Type.values()[fear.random.nextInt(Apparition.Type.values().length)];
            Apparition a = APPS.get(t).get().create(fear);
            if (a == null) return;
            a.bind(victim);
            if (a.begin(victim)) fear.addFreshEntity(a);
            else FearMobs.untrack(victim.getUUID(), a);
        }

        @Override
        public List<String> apparitionKinds() {
            List<String> out = new ArrayList<>();
            for (Apparition.Type t : Apparition.Type.values()) out.add(t.id());
            return out;
        }

        @Override
        public void clear(UUID victim) {
            List<FearEntity> l = TRACKED.remove(victim);
            if (l != null) for (FearEntity e : l) e.discard();
        }
    }

    private FearMobs() {}
}
