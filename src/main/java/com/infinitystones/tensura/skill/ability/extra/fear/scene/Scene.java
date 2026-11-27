package com.infinitystones.tensura.skill.ability.extra.fear.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/** One horror scene. {@link #plan} queues small generation steps (run a few per tick) that draw into the {@link Arena}. */
public abstract class Scene {
    /** What the generators may know about the victim (captured when the build starts, never the player object itself). */
    public record Ctx(MinecraftServer server, ServerLevel fear, UUID victim, BlockPos origin, ResourceKey<Level> srcDim, BlockPos srcPos) {}

    public abstract String id();

    /** Lowest and highest local y the scene writes (the ground surface is y = 0). */
    public int minY() { return -8; }

    public int maxY() { return 48; }

    public abstract void plan(Arena a, Ctx c, List<Runnable> steps);
}
