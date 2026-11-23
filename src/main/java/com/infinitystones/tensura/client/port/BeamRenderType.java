package com.infinitystones.tensura.client.port;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Additive, double-sided, no depth write: glowing energy that stacks into bloom (like Palladium's beam renderer). */
final class BeamRenderType extends RenderType {
    static final RenderType BEAM = create("infinity_energy_beam", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 262144,
            false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setOutputState(WEATHER_TARGET)
                    .createCompositeState(false));

    private BeamRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }
}
