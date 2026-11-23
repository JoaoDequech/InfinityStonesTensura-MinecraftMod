package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * One hand-ported (or new) ability. PortedActions maps nodes to an implementation and drives it:
 * <ul>
 *   <li>activation "press": {@link #press} — null = not handled, TRUE = worked (cooldown), FALSE = fizzled.</li>
 *   <li>activation "hold": {@link #start} / {@link #tick} / {@link #stop}. Nodes whose original enabling condition was
 *       {@code palladium:activation} (press once, stays on N ticks) run as a timed session (see {@link Sessions}),
 *       otherwise for as long as the wheel key is held (original {@code palladium:held}).</li>
 *   <li>activation "passive"/"toggle": {@link #start} when it turns on, {@link #tick} every tick, {@link #stop}.</li>
 * </ul>
 * Everything runs on the server thread.
 */
public interface Ported {
    default @Nullable Boolean press(ServerPlayer p, StoneTree.Node n, boolean alt) { return null; }
    default void start(ServerPlayer p, StoneTree.Node n) {}
    default void tick(ServerPlayer p, StoneTree.Node n, int ticks) {}
    default void stop(ServerPlayer p, StoneTree.Node n) {}
}
