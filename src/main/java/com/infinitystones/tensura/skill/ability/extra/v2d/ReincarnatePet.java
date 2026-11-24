package com.infinitystones.tensura.skill.ability.extra.v2d;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.MenuNet;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import com.infinitystones.tensura.skill.ability.extra.reality.RealityPet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reincarnate Companion (Reality + Soul). Every tamed pet that dies while its owner is online is remembered (full entity
 * data without position; name, armour, collar, variant... kept; at most 20 per player). Left: menu of the fallen pets
 * (by name). Right: the same menu, newest first. Picking one plays a soul-to-body animation - an orange soul rises
 * from a red reality circle and the body re-forms - and brings it back next to you. Each pet can be revived once per death.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class ReincarnatePet implements Ability {
    public static final ReincarnatePet INSTANCE = new ReincarnatePet();
    private static final String NODE = "reincarnate_pet", KEY = "infinity_fallen_pets";
    private static final int MAX = 20, RED = 0xE22B3B, ORANGE = 0xF28A1A, MAX_BYTES = 24_000;
    private static final String[] STRIP = {"Pos", "Motion", "Rotation", "UUID", "Health", "DeathTime", "HurtTime", "HurtByTimestamp", "Fire",
            "FallDistance", "Passengers", "Leash", "Brain", "AbsorptionAmount", "active_effects", "SleepingX", "SleepingY", "SleepingZ",
            "Air", "OnGround", "PortalCooldown", "AngryAt", "AngerTime", "attributes_backup"};

    private static final Map<UUID, Boolean> NEWEST_FIRST = new HashMap<>();
    private static final Map<UUID, Long> BUSY = new HashMap<>();

    private record Entry(int index, CompoundTag tag) {
        String type() { return tag.getString("type"); }
        String name() { return tag.getString("name"); }
        long time() { return tag.getLong("time"); }
    }

    @Override public Stone stone() { return Stone.REALITY; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (!Stone.SOUL.isHeldBy(p)) return Fx.fail(p, "infinity.extra.v2d.pet.need_soul");
        NEWEST_FIRST.put(p.getUUID(), alt);
        if (view(p).isEmpty()) return Fx.fail(p, "infinity.extra.v2d.pet.none");
        return MenuNet.open(p, Stone.REALITY, NODE, this) || Fx.fail(p, "infinity.extra.v2d.pet.none");
    }

    @Override
    public List<MenuNet.Option> menu(ServerPlayer p) {
        List<MenuNet.Option> out = new ArrayList<>();
        for (Entry e : view(p)) {
            String label = e.name().isEmpty() ? descriptionId(e.type()) : e.name();
            out.add(new MenuNet.Option(label, "infinity.extra.v2d.pet.desc", icon(e.type()), false));
        }
        return out.isEmpty() ? null : out;
    }

    @Override
    public void pick(ServerPlayer p, int index) {
        if (!Stone.SOUL.isHeldBy(p) || !Stone.REALITY.isHeldBy(p)) { Fx.fail(p, "infinity.extra.v2d.pet.need_soul"); return; }
        List<Entry> v = view(p);
        if (index < 0 || index >= v.size()) return;
        long now = p.serverLevel().getGameTime();
        Long busy = BUSY.get(p.getUUID());
        if (busy != null && now < busy) return;
        Entry e = v.get(index);
        CompoundTag nbt = e.tag.getCompound("nbt").copy();
        nbt.putString("id", e.type());
        ListTag list = list(p);
        list.remove(e.index);
        revive(p, nbt, e.name());
        BUSY.put(p.getUUID(), now + 70);
    }

    // ------------------------------------------------------------------ storage

    private static ListTag list(Player p) {
        CompoundTag d = Rules.data(p);
        if (!d.contains(KEY, Tag.TAG_LIST)) d.put(KEY, new ListTag());
        return d.getList(KEY, Tag.TAG_COMPOUND);
    }

    private static List<Entry> view(ServerPlayer p) {
        ListTag l = list(p);
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < l.size(); i++) out.add(new Entry(i, l.getCompound(i)));
        if (NEWEST_FIRST.getOrDefault(p.getUUID(), false)) out.sort(Comparator.comparingLong(Entry::time).reversed());
        else out.sort(Comparator.comparing((Entry e) -> e.name().isEmpty() ? e.type() : e.name().toLowerCase()).thenComparing(Entry::time, Comparator.reverseOrder()));
        return out;
    }

    private static String descriptionId(String type) {
        ResourceLocation id = ResourceLocation.tryParse(type);
        return id == null ? type : BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(EntityType::getDescriptionId).orElse(type);
    }

    private static String icon(String type) {
        ResourceLocation id = ResourceLocation.tryParse(type);
        if (id != null) {
            Optional<EntityType<?>> t = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
            if (t.isPresent()) {
                SpawnEggItem egg = SpawnEggItem.byId(t.get());
                if (egg != null) return BuiltInRegistries.ITEM.getKey((Item) egg).toString();
            }
        }
        return "minecraft:bone";
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void died(LivingDeathEvent e) {
        LivingEntity pet = e.getEntity();
        if (e.isCanceled() || pet.level().isClientSide || pet instanceof Player || !(pet instanceof OwnableEntity own)) return;
        UUID owner = own.getOwnerUUID();
        if (owner == null || RealityPet.ownerOf(pet) != null) return;
        if (pet instanceof TamableAnimal t && !t.isTame()) return;
        ServerPlayer op = pet.getServer() == null ? null : pet.getServer().getPlayerList().getPlayer(owner);
        if (op == null) return;
        CompoundTag nbt = new CompoundTag();
        pet.saveWithoutId(nbt);
        for (String k : STRIP) nbt.remove(k);
        if (nbt.sizeInBytes() > MAX_BYTES) return;
        CompoundTag entry = new CompoundTag();
        entry.putString("type", BuiltInRegistries.ENTITY_TYPE.getKey(pet.getType()).toString());
        entry.putString("name", pet.hasCustomName() ? pet.getCustomName().getString() : "");
        entry.putLong("time", pet.level().getGameTime());
        entry.put("nbt", nbt);
        ListTag l = list(op);
        l.add(entry);
        while (l.size() > MAX) {
            int oldest = 0;
            for (int i = 1; i < l.size(); i++) if (l.getCompound(i).getLong("time") < l.getCompound(oldest).getLong("time")) oldest = i;
            l.remove(oldest);
        }
    }

    // ------------------------------------------------------------------ the animation

    private static void revive(ServerPlayer p, CompoundTag nbt, String name) {
        ServerLevel l = p.serverLevel();
        Vec3 look = p.getLookAngle();
        Vec3 c = Ground.below(l, p.position().add(look.x * 2.6, 0.5, look.z * 2.6), 6);
        if (c.distanceToSqr(p.position()) > 64) c = p.position();
        Vec3 at = c;
        int circle = FxNet.newId(), soul = FxNet.newId();
        UUID id = p.getUUID();
        FxNet.runes(l, circle, at.add(0, 0.05, 0), 2.4F, RED, 80, 2.5F);
        FxNet.shockwave(l, at.add(0, 0.1, 0), 5F, RED, 20);
        FxNet.pillar(l, 0, at, 0.5F, 3F, RED, 30);
        Fx.sound(l, at, SoundEvents.BEACON_ACTIVATE, 0.6F, 0.7F);
        Fx.sound(l, at, SoundEvents.SOUL_ESCAPE.value(), 0.7F, 0.8F);
        Fx.bar(p, Stone.REALITY, "infinity.extra.v2d.pet.reviving", name.isEmpty() ? net.minecraft.network.chat.Component.translatable("infinity.extra.v2d.pet.unnamed") : net.minecraft.network.chat.Component.literal(name));
        Tasks.add(new Tasks.Task() {
            Entity body;
            @Override public boolean tick(int age) {
                if (age < 36) {
                    double h = 0.2 + 2.6 * (age / 36.0);
                    Vec3 s = at.add(Math.sin(age * 0.4) * 0.25, h, Math.cos(age * 0.4) * 0.25);
                    FxNet.orb(l, soul, s, 0.45F, ORANGE, 6, -1, Vec3.ZERO);
                    Fx.at(l, Fx.dust(ORANGE, 1.3F), s, 3, 0.15, 0);
                    Fx.at(l, ParticleTypes.SOUL_FIRE_FLAME, s, 1, 0.1, 0.01);
                    if (age % 8 == 0) Fx.sound(l, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, 0.6F + age * 0.02F);
                } else if (age < 52) {
                    double k = (age - 36) / 16.0;
                    Vec3 s = at.add(0, 2.8 - 2.0 * k, 0);
                    FxNet.orb(l, soul, s, 0.45F * (float) (1 - k * 0.5), ORANGE, 4, -1, Vec3.ZERO);
                    Fx.shell(l, at.add(0, 0.8, 0), 1.6 * (1 - k) + 0.2, 10, Fx.dust(ORANGE, 1.1F));
                    Fx.shell(l, at.add(0, 0.8, 0), 1.2 * (1 - k) + 0.2, 6, Fx.dust(RED, 1.1F));
                    if (age == 44) Fx.sound(l, at, SoundEvents.SOUL_ESCAPE.value(), 0.6F, 1.6F);
                } else if (age == 52) {
                    spawn(p, l, at, nbt);
                    FxNet.kill(l, at, soul);
                    FxNet.shockwave(l, at.add(0, 0.1, 0), 6F, ORANGE, 18);
                    FxNet.flare(l, at.add(0, 0.9, 0), 3.5F, ORANGE, 12);
                    FxNet.flashAround(l, at, 8, 0xFFB060, 0.25F, 8);
                    Fx.at(l, ParticleTypes.TOTEM_OF_UNDYING, at.add(0, 0.8, 0), 40, 0.4, 0.3);
                    Fx.at(l, ParticleTypes.END_ROD, at.add(0, 0.8, 0), 25, 0.5, 0.15);
                    Fx.sound(l, at, SoundEvents.TOTEM_USE, 0.5F, 1.2F);
                    Fx.sound(l, at, SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.4F);
                } else return age < 70;
                return true;
            }
            @Override public void end() {
                FxNet.kill(l, at, soul);
                FxNet.kill(l, at, circle);
            }
        });
    }

    private static void spawn(ServerPlayer owner, ServerLevel l, Vec3 at, CompoundTag nbt) {
        Optional<Entity> made = EntityType.create(nbt, l);
        if (made.isEmpty()) {
            Fx.fail(owner, "infinity.extra.v2d.pet.failed");
            return;
        }
        Entity e = made.get();
        e.moveTo(at.x, at.y, at.z, owner.getYRot() + 180F, 0F);
        e.setDeltaMovement(Vec3.ZERO);
        if (e instanceof LivingEntity le) { le.setHealth(le.getMaxHealth()); le.deathTime = 0; le.hurtTime = 0; }
        if (e instanceof TamableAnimal t) t.setOrderedToSit(false);
        l.addFreshEntity(e);
    }

    private ReincarnatePet() {}
}
