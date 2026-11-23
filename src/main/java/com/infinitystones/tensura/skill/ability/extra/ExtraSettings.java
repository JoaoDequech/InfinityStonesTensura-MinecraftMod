package com.infinitystones.tensura.skill.ability.extra;

import com.infinitystones.tensura.InfinityStones;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.lang.reflect.Method;

/**
 * Adapter for the "Alt + scroll" per-power setting. When the wheel-ux branch is merged, its
 * {@code com.infinitystones.tensura.wheel.AbilitySettings.get(ServerPlayer, String modeId)} is read (the value of the node JSON "settings" range;
 * 0 = the node declares none) and clamped. Until then the value is stored on the player
 * (also changed from our menus, or with {@code /infinity_setting <stone.node> <value>}, permission 0).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class ExtraSettings {
    private static final String KEY = "infinity_extra_settings";
    private static Method wheelGet;
    private static boolean looked;

    public static int get(ServerPlayer p, String modeId, int def, int min, int max, int step) {
        int v = com.infinitystones.tensura.wheel.VariableAbility.get(p, modeId);
        if (v != 0) return clamp(v, min, max);
        CompoundTag t = Rules.data(p).getCompound(KEY);
        return t.contains(modeId) ? clamp(t.getInt(modeId), min, max) : def;
    }

    public static void set(ServerPlayer p, String modeId, int value) {
        CompoundTag d = Rules.data(p);
        CompoundTag t = d.getCompound(KEY);
        t.putInt(modeId, value);
        d.put(KEY, t);
    }

    /** Integer "choice" of a menu (law, order, species...), stored on the player. */
    public static int choice(ServerPlayer p, String modeId, int def) {
        CompoundTag t = Rules.data(p).getCompound(KEY);
        return t.contains(modeId + "#choice") ? t.getInt(modeId + "#choice") : def;
    }

    public static void setChoice(ServerPlayer p, String modeId, int v) { set(p, modeId + "#choice", v); }

    private static Integer wheel(ServerPlayer p, String modeId) {
        if (!looked) {
            looked = true;
            try {
                Class<?> c = Class.forName("com.infinitystones.tensura.wheel.AbilitySettings");
                wheelGet = c.getMethod("get", net.minecraft.world.entity.player.Player.class, String.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                try {
                    Class<?> c = Class.forName("com.infinitystones.tensura.wheel.AbilitySettings");
                    wheelGet = c.getMethod("get", ServerPlayer.class, String.class);
                } catch (ReflectiveOperationException | LinkageError ignored) { wheelGet = null; }
            }
        }
        if (wheelGet == null) return null;
        try {
            Object v = wheelGet.invoke(null, p, modeId);
            return v instanceof Number n ? n.intValue() : null;
        } catch (ReflectiveOperationException e) { return null; }
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("infinity_setting")
                .then(Commands.argument("power", StringArgumentType.string())
                        .then(Commands.argument("value", IntegerArgumentType.integer(-1000, 1000))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    String id = StringArgumentType.getString(ctx, "power");
                                    int v = IntegerArgumentType.getInteger(ctx, "value");
                                    set(p, id, v);
                                    p.displayClientMessage(Component.translatable("infinity.extra.setting", id, v), true);
                                    return 1;
                                }))));
    }

    private ExtraSettings() {}
}
