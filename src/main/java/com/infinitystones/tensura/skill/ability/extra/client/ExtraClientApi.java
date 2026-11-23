package com.infinitystones.tensura.skill.ability.extra.client;

import com.infinitystones.tensura.skill.ability.extra.MenuNet;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Set;

/**
 * Client entry points for the wheel (wheel-ux): powers with a choice menu, opened with the middle click.
 * {@code if (ExtraClientApi.hasMenu(modeId)) ExtraClientApi.requestMenu(stoneOrdinal, nodeId);}
 */
public final class ExtraClientApi {
    /** Mode ids ("stone.node") whose middle click should open our menu. */
    public static final Set<String> MENU_POWERS = Set.of(
            "mind.suggestion", "reality.reality_pet", "reality.local_decree", "reality.reality_shatter", "time.climate_zone", "space.area_heist");

    public static boolean hasMenu(String modeId) { return MENU_POWERS.contains(modeId); }

    public static void requestMenu(int stone, String node) { PacketDistributor.sendToServer(new MenuNet.Pick(stone, node, -1)); }

    public static void open(MenuNet.Open msg) { Minecraft.getInstance().setScreen(new MenuScreen(msg)); }

    private ExtraClientApi() {}
}
