package com.infinitystones.tensura.skill.ability.extra;

import com.infinitystones.tensura.skill.Stone;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One new ability. Registered in {@link Abilities} by its node {@code original_type} ("infinity:&lt;name&gt;").
 * All methods run on the server thread after the wheel validated stone + unlock.
 */
public interface Ability {
    /** The stone whose colour/package this ability belongs to. */
    Stone stone();

    /** Wheel use. alt = right click (2nd variant) or Shift. TRUE = done, FALSE = fizzled (message shown). */
    default boolean press(ServerPlayer p, boolean alt) { return false; }

    /** Toggle nodes: turned on / every tick while on / turned off (also when the stone is dropped or on logout). */
    default void start(ServerPlayer p) {}
    default void tick(ServerPlayer p) {}
    default void stop(ServerPlayer p) {}

    /** Menu entries (middle click / right click on menu powers); null = no menu. */
    default @Nullable List<MenuNet.Option> menu(ServerPlayer p) { return null; }
    /** A menu entry was picked. */
    default void pick(ServerPlayer p, int index) {}
}
