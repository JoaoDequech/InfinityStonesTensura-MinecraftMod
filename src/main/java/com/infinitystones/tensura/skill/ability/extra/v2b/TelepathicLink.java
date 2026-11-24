package com.infinitystones.tensura.skill.ability.extra.v2b;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.MenuNet;
import com.infinitystones.tensura.skill.ability.extra.Priv;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Telepathic Link (Mind, normal). Left: pings the aimed spot with a golden marker beam that you and your party (OPAC party,
 * else players within 128 blocks) see through walls for 20 s. Right: menu of thought bubbles (Danger!, Come here, Loot,
 * Help, Wait, Nice) that float over your head for 5 s for nearby players. Scroll: marker colour. No damage.
 */
public final class TelepathicLink implements Ability {
    public static final TelepathicLink INSTANCE = new TelepathicLink();
    public static final String MODE = "mind.telepathic_link";
    private static final String NODE = "telepathic_link", K = "infinity.extra.v2b.link.";
    private static final int[] COLORS = {0xFFD34A, 0xFF5A4A, 0x6BE07A, 0x6AB8FF, 0xF4F4F4};
    private static final String[] BUBBLES = {"danger", "come", "loot", "help", "wait", "nice"};
    private static final String[] ICONS = {"minecraft:fire_charge", "minecraft:ender_pearl", "minecraft:diamond", "minecraft:bell", "minecraft:clock", "minecraft:golden_apple"};
    private static final double RANGE = 128, BUBBLE_RANGE = 64;
    public static final int PING_TICKS = 400, BUBBLE_TICKS = 100;

    @Override public Stone stone() { return Stone.MIND; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (alt) return MenuNet.open(p, Stone.MIND, NODE, this) || Fx.fail(p, K + "no_menu");
        Vec3 at;
        LivingEntity e = Aim.living(p, RANGE, t -> true);
        if (e != null) at = e.position().add(0, e.getBbHeight() + 0.2, 0);
        else {
            BlockHitResult b = Aim.block(p, RANGE);
            if (b.getType() == HitResult.Type.MISS) return Fx.fail(p, K + "nothing");
            at = b.getLocation().add(Vec3.atLowerCornerOf(b.getDirection().getNormal()).scale(0.05));
        }
        int type = ExtraSettings.get(p, MODE, 1, 1, COLORS.length, 1) - 1;
        V2BNet.Msg m = V2BNet.msg(V2BNet.PING, p.getId(), COLORS[type], PING_TICKS, type, at.x, at.y, at.z);
        int seen = 0;
        for (ServerPlayer sp : recipients(p)) {
            V2BNet.to(sp, m);
            if (sp != p) {
                Priv.sound(sp, SoundEvents.AMETHYST_BLOCK_CHIME, sp.getEyePosition(), 0.45F, 1.3F);
                Priv.sound(sp, SoundEvents.NOTE_BLOCK_BELL, sp.getEyePosition(), 0.25F, 1.6F);
                seen++;
            }
        }
        Priv.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, p.getEyePosition(), 0.45F, 1.5F);
        Fx.bar(p, Stone.MIND, K + "pinged", seen);
        return true;
    }

    /** The caster plus their party members in the same dimension; without a party, everyone within 128 blocks. */
    private static List<ServerPlayer> recipients(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        boolean party = V2BClaims.hasParty(p);
        List<ServerPlayer> out = new ArrayList<>();
        out.add(p);
        for (ServerPlayer sp : l.players()) {
            if (sp == p || sp.isSpectator()) continue;
            if (party ? V2BClaims.sameParty(p, sp) : sp.distanceToSqr(p) <= RANGE * RANGE) out.add(sp);
        }
        return out;
    }

    @Override
    public List<MenuNet.Option> menu(ServerPlayer p) {
        List<MenuNet.Option> out = new ArrayList<>();
        for (int i = 0; i < BUBBLES.length; i++) out.add(MenuNet.opt(K + "bubble." + BUBBLES[i], ICONS[i], false));
        return out;
    }

    @Override
    public void pick(ServerPlayer p, int index) {
        if (index < 0 || index >= BUBBLES.length) return;
        ServerLevel l = p.serverLevel();
        V2BNet.Msg m = V2BNet.msg(V2BNet.BUBBLE, p.getId(), 0xFFFFFF, BUBBLE_TICKS, index);
        for (ServerPlayer sp : l.players())
            if (sp == p || sp.distanceToSqr(p) <= BUBBLE_RANGE * BUBBLE_RANGE) V2BNet.to(sp, m);
        Fx.sound(l, p.getEyePosition(), SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.5F, 1.2F + 0.08F * index);
        Fx.sound(l, p.getEyePosition(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.3F, 1.0F + 0.1F * index);
        Fx.bar(p, Stone.MIND, K + "said", Component.translatable(K + "bubble." + BUBBLES[index]));
    }

    private TelepathicLink() {}
}
