package com.infinitystones.tensura.skill.ability.extra.v2c;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.MenuNet;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reality, normal: repaints the sky over a region for two minutes. Purely visual, rendered by SkyClient. */
public final class PaintedSky implements Ability {
    public static final PaintedSky INSTANCE = new PaintedSky();
    static final String NODE = "painted_sky", MODE = "reality.painted_sky", K = "infinity.extra.v2c.sky.";
    private static final int DURATION = 20 * 120;
    private static final String[] THEMES = {"aurora", "galaxy", "sunset", "blood_moon", "cotton"};
    private static final String[] ICONS = {"minecraft:prismarine_crystals", "minecraft:amethyst_shard", "minecraft:orange_dye",
            "minecraft:redstone", "minecraft:pink_dye"};
    private static final int[] COLORS = {0x35E8A0, 0x9A5CFF, 0xFF8A4A, 0xD01818, 0xFF9AD8};

    private static final class Sky {
        final UUID owner; final ServerLevel level; final Vec3 c; final float r; final int id = V2CNet.newId();
        int theme, age;
        Tasks.Handle handle;
        Sky(UUID owner, ServerLevel level, Vec3 c, float r, int theme) { this.owner = owner; this.level = level; this.c = c; this.r = r; this.theme = theme; }
    }

    private static final Map<UUID, Sky> SKIES = new HashMap<>();

    @Override public Stone stone() { return Stone.REALITY; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (alt) return MenuNet.open(p, Stone.REALITY, NODE, this) || Fx.fail(p, K + "no_menu");
        ServerLevel l = p.serverLevel();
        if (!l.dimensionType().hasSkyLight()) return Fx.fail(p, K + "no_sky");
        float r = ExtraSettings.get(p, MODE, 64, 32, 128, 8);
        int theme = Mth.clamp(ExtraSettings.choice(p, MODE, 0), 0, THEMES.length - 1);
        var hit = Aim.block(p, 128);
        Vec3 c = hit.getType() == HitResult.Type.MISS ? p.position() : hit.getLocation();
        paint(p, c, r, theme);
        Fx.bar(p, Stone.REALITY, K + "painted", name(theme), (int) r);
        return true;
    }

    private static net.minecraft.network.chat.Component name(int theme) {
        return net.minecraft.network.chat.Component.translatable(K + THEMES[theme]);
    }

    @Override
    public List<MenuNet.Option> menu(ServerPlayer p) {
        Sky s = SKIES.get(p.getUUID());
        int cur = s != null ? s.theme : Mth.clamp(ExtraSettings.choice(p, MODE, 0), 0, THEMES.length - 1);
        MenuNet.Option[] o = new MenuNet.Option[THEMES.length];
        for (int i = 0; i < o.length; i++) o[i] = MenuNet.opt(K + THEMES[i], ICONS[i], i == cur);
        return List.of(o);
    }

    @Override
    public void pick(ServerPlayer p, int index) {
        if (index < 0 || index >= THEMES.length) return;
        ExtraSettings.setChoice(p, MODE, index);
        Sky s = SKIES.get(p.getUUID());
        if (s != null && s.level == p.serverLevel()) {
            s.theme = index;
            s.age = Math.min(s.age, 40);
            send(s);
            FxNet.flashAround(s.level, s.c, Math.min(s.r, 64), COLORS[index], 0.3F, 16);
            Fx.sound(s.level, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 0.8F);
        }
        Fx.bar(p, Stone.REALITY, K + "chosen", name(index));
    }

    private static void paint(ServerPlayer p, Vec3 c, float r, int theme) {
        Sky old = SKIES.remove(p.getUUID());
        if (old != null) end(old);
        ServerLevel l = p.serverLevel();
        Sky s = new Sky(p.getUUID(), l, c, r, theme);
        SKIES.put(p.getUUID(), s);
        int col = COLORS[theme];
        FxNet.pillar(l, 0, c, 1.4F, 36, col, 50);
        FxNet.runes(l, 0, c.add(0, 0.1, 0), 7F, col, 70, 0.6F);
        FxNet.runes(l, 0, c.add(0, 0.12, 0), 4F, 0xFFFFFF, 70, -0.9F);
        FxNet.shockwave(l, c.add(0, 0.2, 0), Math.min(r, 48), col, 40);
        FxNet.flashAround(l, c, Math.min(r, 64), col, 0.25F, 18);
        Fx.sound(l, c, SoundEvents.BEACON_POWER_SELECT, 0.6F, 0.7F);
        Fx.sound(l, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.5F);
        Fx.at(l, ParticleTypes.END_ROD, c.add(0, 1, 0), 40, 0.6, 0.12);
        send(s);
        s.handle = Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int t) {
                if (SKIES.get(s.owner) != s) return false;
                ServerPlayer owner = V2CNet.player(s.level.getServer(), s.owner);
                if (owner == null || ++s.age > DURATION) return false;
                if (s.age % 20 == 0) send(s);
                if (s.age == DURATION - 200) Fx.bar(owner, Stone.REALITY, K + "ending");
                if (s.age < 40 && s.age % 4 == 0) Fx.at(s.level, ParticleTypes.END_ROD, s.c.add(0, 2 + s.age * 0.8, 0), 3, 0.5, 0.02);
                return true;
            }
            @Override public void end() { finish(s); }
        });
    }

    private static void send(Sky s) {
        int left = Math.max(10, Math.min(100, DURATION - s.age + 20));
        V2CNet.near(s.level, s.c, s.r + 96, V2CNet.msg(V2CNet.SKY, s.id, s.theme, left, s.age, s.c, s.r));
    }

    private static void end(Sky s) {
        if (s.handle != null) s.handle.cancel();
        finish(s);
    }

    private static void finish(Sky s) {
        SKIES.remove(s.owner, s);
        V2CNet.near(s.level, s.c, s.r + 256, V2CNet.msg(V2CNet.SKY_END, s.id, 0, 0, 0, s.c, s.r));
    }

    private PaintedSky() {}
}
