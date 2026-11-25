package com.infinitystones.tensura.skill;

import com.infinitystones.tensura.InfinityStones;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/** Tensura/ManasCore skill registration (Architectury registrar, same idiom as other Tensura addons). */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class InfinitySkills {
    public static final Map<Stone, RegistrySupplier<StoneSkill>> STONES = new EnumMap<>(Stone.class);
    static {
        for (Stone s : Stone.values()) STONES.put(s, register(s.key + "_stone", () -> new StoneSkill(s)));
    }
    public static final RegistrySupplier<ImmortalitySkill> IMMORTALITY = register("immortality", ImmortalitySkill::new);

    private static <T extends ManasSkill> RegistrySupplier<T> register(String name, Supplier<T> sup) {
        return SkillAPI.getSkillRegistry().register(InfinityStones.id(name), sup);
    }

    public static void init() {}

    /** First time a player carries a stone, they learn its skill (free). Checked once per second. */
    @SubscribeEvent
    public static void grant(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        var skills = SkillAPI.getSkillsFrom(p);
        for (Stone s : Stone.values()) {
            if (skills.getSkill(s.skillId()).isEmpty() && s.ownedBy(p)) skills.learnSkill(STONES.get(s).get());
        }
    }

    private InfinitySkills() {}
}
