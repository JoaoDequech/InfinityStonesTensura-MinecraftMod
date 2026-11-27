package com.infinitystones.tensura.skill.ability.extra.fear.mob;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Synthesised sound events of the creatures (assets/infinity/sounds/fear_*.ogg). */
public final class FearSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, InfinityStones.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SCREAM_A = reg("fear_scream_a");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCREAM_B = reg("fear_scream_b");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCREAM_LOW = reg("fear_scream_low");
    public static final DeferredHolder<SoundEvent, SoundEvent> BREATH = reg("fear_breath");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHISPER = reg("fear_whisper");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLICK = reg("fear_click");
    public static final DeferredHolder<SoundEvent, SoundEvent> SERVO = reg("fear_servo");
    public static final DeferredHolder<SoundEvent, SoundEvent> SQUELCH = reg("fear_squelch");
    public static final DeferredHolder<SoundEvent, SoundEvent> STINGER = reg("fear_stinger");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE = reg("fear_drone");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHANT = reg("fear_chant");
    public static final DeferredHolder<SoundEvent, SoundEvent> RATTLE = reg("fear_rattle");

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(InfinityStones.id(name)));
    }

    private FearSounds() {}
}
