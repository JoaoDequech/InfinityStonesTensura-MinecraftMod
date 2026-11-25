package com.infinitystones.tensura.skill.ability.extra.fear;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** In-memory state of one nightmare in progress (the persistent part is {@link FearData}). */
final class FearLive {
    static final int ENTERING = 0, ACTIVE = 1;

    final UUID victim, caster;
    final int slot;
    int state = ENTERING, age, enterAge, total;
    String scene = "";
    BlockPos spawn;
    boolean auto = true, flash = true, frozen, hunt, firstEntry = true;
    float fear;
    int fog = 50, dark = 85, sky = 0, weather = 0;

    // director
    int next = 0, lastHeavy = -9999, fakeDeadUntil = -1;
    int fallLeft = -1; double fallX, fallY, fallZ;
    final List<Timed> timers = new ArrayList<>();

    static final class Timed {
        int at; final Consumer<ServerPlayer> run;
        Timed(int at, Consumer<ServerPlayer> run) { this.at = at; this.run = run; }
    }

    FearLive(UUID victim, UUID caster, int slot) { this.victim = victim; this.caster = caster; this.slot = slot; }

    void after(int ticks, Consumer<ServerPlayer> run) { timers.add(new Timed(age + ticks, run)); }

    boolean busy() { return fallLeft >= 0 || fakeDeadUntil >= 0; }

    BlockPos origin() { return new BlockPos(FearRealm.centerX(slot), FearTerrain.BASE, FearRealm.centerZ(slot)); }

    void scare(float amount) { fear = Math.min(100F, fear + amount); }
}
