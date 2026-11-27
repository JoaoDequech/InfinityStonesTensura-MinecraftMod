package com.infinitystones.tensura.skill.ability.extra.fear;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import net.minecraft.server.level.ServerPlayer;

/** "Sair da Dimensão do Medo": the only power that works while the caster is inside as his own victim. */
public final class FearExit implements Ability {
    public static final FearExit INSTANCE = new FearExit();
    public static final String NODE = "fear_exit";

    @Override public Stone stone() { return Stone.MIND; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) { return FearSessions.selfExit(p); }

    private FearExit() {}
}
