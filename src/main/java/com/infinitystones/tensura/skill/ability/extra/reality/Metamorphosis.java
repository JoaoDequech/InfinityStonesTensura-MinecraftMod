package com.infinitystones.tensura.skill.ability.extra.reality;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneTree;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.MenuNet;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import com.infinitystones.tensura.wheel.AbilitySettings;
import com.infinitystones.tensura.wheel.WheelService;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Metamorphosis (replaces the reality_shatter node). Left (aim a mob): it bursts into red fragments and becomes the
 * chosen species, keeping its name and % of health. Right: species menu. Alt+scroll on the wheel also changes species.
 * Mobs only (never players or bosses). Your own pets are spared. Broken Mode: mobs right next to the target change too.
 */
public final class Metamorphosis implements Ability {
    public static final Metamorphosis INSTANCE = new Metamorphosis();
    static final String NODE = "reality_shatter", MODE = "reality.reality_shatter";
    private static final String K = "infinity.extra.metamorphosis.";
    static final String[] SPECIES = {"chicken", "sheep", "rabbit", "wolf", "pig", "cow", "goat", "fox", "bee", "snow_golem"};
    private static final String[] ICONS = {"minecraft:feather", "minecraft:white_wool", "minecraft:rabbit_foot", "minecraft:bone",
            "minecraft:porkchop", "minecraft:leather", "minecraft:goat_horn", "minecraft:sweet_berries", "minecraft:honeycomb", "minecraft:snowball"};

    @Override public Stone stone() { return Stone.REALITY; }

    private static EntityType<? extends Mob> type(int i) {
        return switch (i) {
            case 0 -> EntityType.CHICKEN; case 1 -> EntityType.SHEEP; case 2 -> EntityType.RABBIT; case 3 -> EntityType.WOLF;
            case 4 -> EntityType.PIG; case 5 -> EntityType.COW; case 6 -> EntityType.GOAT; case 7 -> EntityType.FOX;
            case 8 -> EntityType.BEE; default -> EntityType.SNOW_GOLEM;
        };
    }

    /** Species index 0..9 (wheel setting 1..10, shared with the menu). */
    static int species(ServerPlayer p) {
        int v = AbilitySettings.get(p, MODE);
        return v <= 0 ? 0 : Math.min(SPECIES.length, v) - 1;
    }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (alt) return openMenu(p);
        LivingEntity t = Aim.living(p, 32, e -> e instanceof Mob);
        if (!(t instanceof Mob m)) return Fx.fail(p, K + "no_mob");
        if (Rules.isBoss(m)) return Fx.fail(p, K + "boss");
        if (p.getUUID().equals(RealityPet.ownerOf(m)) || !Rules.canControl(p, m)) return Fx.fail(p, K + "no_mob");
        int sp = species(p);
        EntityType<? extends Mob> to = type(sp);
        if (m.getType() == to) return Fx.fail(p, K + "same");

        List<Mob> victims = new ArrayList<>();
        victims.add(m);
        if (Rules.mult(p, "reality") > 1F) {
            for (Mob o : p.serverLevel().getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(2.5), o -> o != m && o.getType() != to
                    && !Rules.isBoss(o) && Rules.canControl(p, o) && !p.getUUID().equals(RealityPet.ownerOf(o)))) {
                if (victims.size() >= 4) break;
                victims.add(o);
            }
        }
        ServerLevel l = p.serverLevel();
        FxNet.beam(l, p.getEyePosition().add(0, -0.3, 0), m.position().add(0, m.getBbHeight() / 2, 0), Stone.REALITY.color(), 0.18F, 10);
        Fx.sound(l, p.position(), SoundEvents.EVOKER_CAST_SPELL, 0.5F, 0.7F);
        for (Mob v : victims) transform(l, v, to);
        Fx.bar(p, Stone.REALITY, K + "done", Component.translatable(K + "species." + SPECIES[sp]));
        return true;
    }

    /** 12-tick cocoon (rune circle, spiral, red shell closing in), then the burst and the new body. */
    private static void transform(ServerLevel l, Mob m, EntityType<? extends Mob> to) {
        int c = Stone.REALITY.color();
        float w = Math.max(0.8F, m.getBbWidth());
        Vec3 base = m.position();
        int rune = FxNet.newId(), rune2 = FxNet.newId();
        FxNet.runes(l, rune, base.add(0, 0.05, 0), w * 1.8F, c, 30, 3F);
        FxNet.runes(l, rune2, base.add(0, 0.08, 0), w * 1.1F, 0xFF8FA0, 30, -4F);
        FxNet.spiral(l, base, w, m.getBbHeight() + 0.8F, c, 16, m.getId());
        FxNet.cube(l, FxNet.newId(), base.add(0, m.getBbHeight() / 2, 0), Math.max(w, m.getBbHeight()) * 0.6F, c, 14, m.getId());
        Fx.sound(l, base, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, 0.5F, 1.4F);
        m.getNavigation().stop();
        Tasks.add(age -> {
            if (!m.isAlive() || m.isRemoved()) return false;
            Vec3 mid = m.position().add(0, m.getBbHeight() / 2, 0);
            double r = Math.max(0.3, (w + 0.6) * (1 - age / 12.0));
            Fx.shell(l, mid, r, 14, Fx.dust(Stone.REALITY, 1F));
            m.setDeltaMovement(m.getDeltaMovement().multiply(0.2, 1, 0.2));
            if (age < 12) return true;
            burst(l, m, to);
            return false;
        });
    }

    private static void burst(ServerLevel l, Mob m, EntityType<? extends Mob> to) {
        float pct = m.getHealth() / Math.max(1F, m.getMaxHealth());
        Vec3 mid = m.position().add(0, m.getBbHeight() / 2, 0);
        int c = Stone.REALITY.color();
        // reality pets of somebody else lose the bond with their old body
        if (RealityPet.ownerOf(m) != null) RealityPet.release(m, false);
        Mob n = m.convertTo(to, false);
        if (n == null) return;
        n.setHealth(Math.max(1F, n.getMaxHealth() * pct));
        n.setTarget(null);
        FxNet.flare(l, mid, 2.6F, c, 14);
        FxNet.shockwave(l, n.position().add(0, 0.1, 0), 4F, c, 18);
        FxNet.shockwave(l, mid, 2.5F, 0xFFFFFF, 12);
        FxNet.shake(l, mid, 10, 0.3F, 8);
        Fx.burstRing(l, mid, 36, Fx.dust(Stone.REALITY, 1.4F), 0.35);
        Fx.at(l, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.RED_STAINED_GLASS.defaultBlockState()), mid, 30, 0.4, 0.2);
        Fx.at(l, ParticleTypes.REVERSE_PORTAL, mid, 20, 0.3, 0.1);
        Fx.shell(l, mid, 1.2, 30, Fx.light(Stone.REALITY, 1F));
        Fx.sound(l, mid, SoundEvents.GLASS_BREAK, 0.6F, 0.6F);
        Fx.sound(l, mid, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.6F, 0.6F);
        Fx.sound(l, mid, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.6F, 0.8F);
    }

    // ------------------------------------------------------------------ species menu

    private boolean openMenu(ServerPlayer p) {
        List<MenuNet.Option> opts = menu(p);
        // the node keeps the id reality_shatter: send our own title instead of the old node's
        PacketDistributor.sendToPlayer(p, new MenuNet.Open(Stone.REALITY.ordinal(), NODE, "ability.infinity.reality_stone.metamorphosis.title", opts));
        return true;
    }

    @Override
    public List<MenuNet.Option> menu(ServerPlayer p) {
        int cur = species(p);
        List<MenuNet.Option> o = new ArrayList<>();
        for (int i = 0; i < SPECIES.length; i++) o.add(MenuNet.opt(K + "species." + SPECIES[i], ICONS[i], i == cur));
        return o;
    }

    @Override
    public void pick(ServerPlayer p, int index) {
        if (index < 0 || index >= SPECIES.length) return;
        StoneTree.Node n = StoneTree.of(Stone.REALITY).nodes.get(NODE);
        if (n != null) {
            AbilitySettings.set(p, n, index + 1);
            WheelService.sync(p, true);
        }
        Fx.bar(p, Stone.REALITY, K + "chosen", Component.translatable(K + "species." + SPECIES[index]));
        Fx.sound(p.serverLevel(), p.position(), SoundEvents.BOOK_PAGE_TURN, 0.7F, 1.1F);
    }
}
