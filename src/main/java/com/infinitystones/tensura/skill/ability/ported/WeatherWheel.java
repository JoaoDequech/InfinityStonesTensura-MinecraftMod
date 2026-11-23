package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;

/**
 * palladium:ability_wheel (Reality "Weather Wheel": clear / rain / thunder). The original opened a sub-wheel; here one
 * use steps to the next weather (clear → rain → thunder → clear), sneaking steps backwards. Same effects as the original
 * "weather ..." commands (vanilla random durations) + cloud and red dust burst. No OP needed.
 */
public final class WeatherWheel implements Ported {
    private static final IntProvider RAIN = UniformInt.of(12_000, 24_000), CLEAR = UniformInt.of(12_000, 180_000);

    @Override
    public void start(ServerPlayer p, StoneTree.Node n) {
        ServerLevel level = p.serverLevel();
        int cur = level.isThundering() ? 2 : level.isRaining() ? 1 : 0;
        int next = Math.floorMod(cur + (p.isShiftKeyDown() ? -1 : 1), 3);
        switch (next) {
            case 0 -> level.setWeatherParameters(CLEAR.sample(level.getRandom()), 0, false, false);
            case 1 -> level.setWeatherParameters(0, RAIN.sample(level.getRandom()), true, false);
            default -> level.setWeatherParameters(0, RAIN.sample(level.getRandom()), true, true);
        }
        level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 150, 1.5, 1.5, 1.5, 0.4);
        level.sendParticles(PortFx.dust(n.stone(), 2F), p.getX(), p.getY() + 1, p.getZ(), 150, 1.5, 1.5, 1.5, 0);
        PolishFx.weather(p, next);
        p.displayClientMessage(Component.translatable("ability.infinity.port.weather." + (next == 0 ? "clear" : next == 1 ? "rain" : "thunder"))
                .withStyle(ChatFormatting.RED), true);
    }
}
