package com.infinitystones.tensura.skill.ability.extra.reality.client;

import com.infinitystones.tensura.skill.ability.extra.reality.ChestNet;
import net.minecraft.client.Minecraft;

/** Client entry point of the Creation Chest payloads. */
public final class ChestClient {
    public static void open(ChestNet.Open msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        mc.setScreen(new CreationChestScreen(msg.last(), msg.lastCount()));
    }

    private ChestClient() {}
}
