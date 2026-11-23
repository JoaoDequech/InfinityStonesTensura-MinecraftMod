package com.infinitystones.tensura.registry;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Sound events from the original assets/infinity/sounds.json (same ids). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, InfinityStones.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SPACE_STONE_PORTAL = reg("space_stone_portal");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPACE_STONE_GRAB = reg("space_stone_grab");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPACE_STONE_RANDOM = reg("space_stone_random");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLACK_HOLE = reg("black_hole");
    public static final DeferredHolder<SoundEvent, SoundEvent> INFINITY_STONE_EQUIP = reg("infinity_stone_equip");
    public static final DeferredHolder<SoundEvent, SoundEvent> USE_INFINITY_GAUNTLET = reg("use_infinity_gauntlet");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIND_STONE_PHASE = reg("mind_stone_phase");
    public static final DeferredHolder<SoundEvent, SoundEvent> NANO_GAUNTLET = reg("nano_gauntlet");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_STONE_BEAM1 = reg("power_stone_beam1");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_STONE_EXPLODE = reg("power_stone_explode");
    public static final DeferredHolder<SoundEvent, SoundEvent> REALITY_STONE_BUBBLE = reg("reality_stone_bubble");
    public static final DeferredHolder<SoundEvent, SoundEvent> REALITY_STONE_INVIS = reg("reality_stone_invis");
    public static final DeferredHolder<SoundEvent, SoundEvent> REALITY_STONE_SIZE = reg("reality_stone_size");
    public static final DeferredHolder<SoundEvent, SoundEvent> REALITY_STONE = reg("reality_stone");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUL_STONE = reg("soul_stone");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPACE_STONE_FORCEFIELD = reg("space_stone_forcefield");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_REMOVE = reg("stone_remove");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_STONE = reg("time_stone");
    public static final DeferredHolder<SoundEvent, SoundEvent> NP52 = reg("np52");

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(InfinityStones.id(name)));
    }

    private ModSounds() {}
}
