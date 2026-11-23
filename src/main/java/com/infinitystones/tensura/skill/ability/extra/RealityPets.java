package com.infinitystones.tensura.skill.ability.extra;

import com.infinitystones.tensura.skill.ability.extra.reality.RealityPet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** Compatibility facade for the Gauntlet powers (Snap, Universal Dominion): delegates to the v2 Reality pet. */
public final class RealityPets {
    public static @Nullable UUID ownerOf(@Nullable Entity e) { return RealityPet.ownerOf(e); }
    public static List<Mob> petsOf(UUID owner) { return RealityPet.petsOf(owner); }
    /** Makes m a pet of p without the per-player cap (Gauntlet). */
    public static void makePet(ServerPlayer p, Mob m) { RealityPet.makePet(p, m); }
    private RealityPets() {}
}
