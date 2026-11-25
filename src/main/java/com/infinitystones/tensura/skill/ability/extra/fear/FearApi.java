package com.infinitystones.tensura.skill.ability.extra.fear;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Seams between the three parts of the fear realm: the core (dimension, sessions, director, control room), the scenes
 * (structures built in a victim's arena) and the creatures. Each part registers its provider at mod setup; the core only
 * talks to these interfaces, so the parts compile and work independently.
 */
public final class FearApi {
    /** Builds the scenes of a victim's arena. */
    public interface Scenes {
        /** Scene ids in menu order (e.g. "mansion", "hospital", "church", "fair", "well", "graveyard", "backrooms", "mountain", "forest", "home", "claims"). */
        List<String> ids();

        /**
         * Starts building {@code sceneId} for {@code victim} with its origin at {@code origin} in the fear dimension, batched over ticks.
         * {@code onReady} receives the spawn point once the area around it can be stood on (the rest may still be building).
         */
        void build(ServerLevel fear, ServerPlayer victim, String sceneId, BlockPos origin, Consumer<BlockPos> onReady);

        /** Removes everything built for this victim (when the session ends). */
        void clear(ServerLevel fear, UUID victim);
    }

    /** The creatures that haunt or hunt a victim. */
    public interface Creatures {
        /** Creature ids in menu order; "wendigo" is always first. */
        List<String> ids();

        /** The creature themed to a scene (falls back to "wendigo"). */
        String forScene(String sceneId);

        /** Spawns a creature bound to the victim; {@code mode} is {@link #SCARE} or {@link #HUNT}. */
        Entity spawn(ServerLevel fear, String creatureId, ServerPlayer victim, BlockPos at, int mode);

        /** Switches every creature bound to the victim (or only {@code creatureId} when not null) to {@code mode}. */
        void setMode(UUID victim, String creatureId, int mode);

        /** Short-lived apparition (rush, scream, vanish) of the given kind near the victim. */
        void apparition(ServerLevel fear, ServerPlayer victim, String kind);

        /** Apparition kinds for the control room. */
        List<String> apparitionKinds();

        /** Removes every creature and apparition bound to the victim. */
        void clear(UUID victim);
    }

    public static final int SCARE = 0, HUNT = 1;

    private static Scenes scenes;
    private static Creatures creatures;

    public static void scenes(Scenes s) { scenes = s; }

    public static void creatures(Creatures c) { creatures = c; }

    public static Scenes scenes() { return scenes; }

    public static Creatures creatures() { return creatures; }

    private FearApi() {}
}
