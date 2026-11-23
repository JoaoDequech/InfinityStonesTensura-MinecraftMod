package com.infinitystones.tensura.skill.ability.extra.power.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Additive glow (lightning shader) WITHOUT depth write and without culling, so layered translucent geometry never cuts itself. */
final class PowerRT extends RenderType {
    static final RenderType GLOW = create("infinity_power_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1 << 18,
            false, false, CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setOutputState(WEATHER_TARGET)
                    .createCompositeState(false));

    private PowerRT(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean a, boolean b, Runnable r1, Runnable r2) {
        super(n, f, m, s, a, b, r1, r2);
    }
}
