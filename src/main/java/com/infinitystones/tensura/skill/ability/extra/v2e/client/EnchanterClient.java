package com.infinitystones.tensura.skill.ability.extra.v2e.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.v2e.V2eNet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The Enchanter show, drawn on the client only (no real blocks, no world change): a rune circle, a small cauldron and an
 * anvil appear in front of the caster. The item floats out of the hand, dips into the cauldron, rests on the anvil; an
 * enchanted book dives into the cauldron; a flask scoops the glowing liquid and pours it over the item; flash; the item
 * flies back. Timeline in client ticks (the server changes the item at 54): 0-9 appear, 7-19 item to the cauldron,
 * 19-25 dip, 22-40 book, 26-35 item to the anvil, 40-58 flask, 54 flash, 58-67 item home and everything fades.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class EnchanterClient {
    private static final int RED = 0xE02848, PURPLE = 0xA050FF, HOT = 0xFF7A9A, WHITE = 0xFFFFFF;
    private static final int TOTAL = 68;
    private static final int[] SPLASHES = {24, 40, 46};
    private static final BlockState CAULDRON = Blocks.CAULDRON.defaultBlockState();
    private static final BlockState ANVIL = Blocks.ANVIL.defaultBlockState();

    private static final class Inst {
        final V2eNet.Anim m;
        final Vec3 a, f, r;
        final ItemStack plain, glint, book, empty, filled;
        final boolean remove;
        int age;

        Inst(V2eNet.Anim m) {
            this.m = m;
            this.a = new Vec3(m.x(), m.y(), m.z());
            float y = m.yaw() * Mth.DEG_TO_RAD;
            this.f = new Vec3(-Mth.sin(y), 0, Mth.cos(y));
            this.r = new Vec3(-Mth.cos(y), 0, -Mth.sin(y));
            this.remove = (m.flags() & V2eNet.FLAG_REMOVE) != 0;
            this.plain = m.stack().copy();
            this.glint = m.stack().copy();
            if (!glint.isEmpty()) glint.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            this.book = new ItemStack(Items.ENCHANTED_BOOK);
            this.empty = new ItemStack(Items.GLASS_BOTTLE);
            this.filled = new ItemStack(Items.EXPERIENCE_BOTTLE);
        }
    }

    private record Pose(Vec3 p, float scale, float spin, float tilt, float roll, boolean filled) {}

    private static final Map<Integer, Inst> RUN = new HashMap<>();

    public static void open() { Minecraft.getInstance().setScreen(new EnchanterScreen()); }

    public static void accept(V2eNet.Anim m) {
        Minecraft mc = Minecraft.getInstance();
        if ((m.flags() & V2eNet.FLAG_CANCEL) != 0) {
            Inst i = RUN.remove(m.caster());
            if (i != null && mc.level != null) {
                Vec3 c = i.a.add(0, 0.6, 0);
                burst(mc, c, ParticleTypes.POOF, 14, 0.05, 0.05);
                burst(mc, c, Fx.dust(PURPLE, 1F), 14, 0.08, 0.05);
                snd(mc, c, SoundEvents.FIRE_EXTINGUISH, 0.35F, 1.2F);
            }
            return;
        }
        if (m.stack().isEmpty()) return;
        RUN.put(m.caster(), new Inst(m));
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { RUN.clear(); }

    @SubscribeEvent
    public static void hand(RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || RUN.isEmpty()) return;
        Inst i = RUN.get(mc.player.getId());
        if (i == null || i.age < 7 || i.age > 66) return;
        int s = i.m.slot();
        if ((s == 0 && e.getHand() == InteractionHand.MAIN_HAND) || (s == 1 && e.getHand() == InteractionHand.OFF_HAND)) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ timeline

    private static float seg(float a, float t0, float t1) { return Mth.clamp((a - t0) / (t1 - t0), 0F, 1F); }

    private static float ease(float x) { return x * x * (3 - 2 * x); }

    private static float back(float x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        float c1 = 1.70158F, c3 = c1 + 1, k = x - 1;
        return 1 + c3 * k * k * k + c1 * k * k;
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255), g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    private static Vec3 cauldron(Inst i) { return i.a.add(i.r.scale(-0.95)); }

    private static Vec3 anvil(Inst i) { return i.a.add(i.r.scale(0.95)); }

    private static Vec3 above(Inst i) { return cauldron(i).add(0, 1.55, 0); }

    private static Vec3 sunk(Inst i) { return cauldron(i).add(0, 0.5, 0); }

    private static Vec3 land(Inst i) { return anvil(i).add(0, 0.72, 0); }

    private static Vec3 liquid(Inst i) { return cauldron(i).add(0, 0.64 * 0.85, 0); }

    private static float structScale(float a) { return back(seg(a, 1, 9)) * (1F - ease(seg(a, 59, 67))); }

    private static Vec3 hand(Minecraft mc, Inst i, float pt) {
        Entity e = mc.level.getEntity(i.m.caster());
        if (!(e instanceof LivingEntity le)) return i.a.subtract(i.f.scale(2.4)).add(0, 1.1, 0);
        Vec3 pos = le.getPosition(pt);
        float by = Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 f = new Vec3(-Mth.sin(by), 0, Mth.cos(by)), r = new Vec3(-Mth.cos(by), 0, -Mth.sin(by));
        int s = i.m.slot();
        double h = le.getBbHeight();
        if (s <= 1) {
            boolean right = (s == 0) == (le.getMainArm() == HumanoidArm.RIGHT);
            return pos.add(0, h * 0.6, 0).add(f.scale(0.45)).add(r.scale(right ? 0.32 : -0.32));
        }
        double[] hs = {0, 0, 0.88, 0.7, 0.45, 0.08};
        return pos.add(0, h * hs[s], 0).add(f.scale(0.3));
    }

    /** The stack going around: null while it is not visible. */
    private static Pose itemPose(Minecraft mc, Inst i, float a, float pt) {
        if (a < 7 || a > 67.5F) return null;
        Vec3 hand = hand(mc, i, pt);
        float spin = a * 14F;
        if (a < 19) {
            float u = ease(seg(a, 7, 19));
            Vec3 p = hand.lerp(above(i), u).add(0, Math.sin(Math.PI * u) * 0.5, 0);
            return new Pose(p, 0.35F + 0.65F * seg(a, 7, 11), spin, 0, 0, false);
        }
        if (a < 26) {
            float u = seg(a, 19, 26);
            return new Pose(above(i).lerp(sunk(i), u * u * u), 1F - 0.45F * u, spin, 0, 0, false);
        }
        if (a < 35) {
            float u = ease(seg(a, 26, 35));
            Vec3 p = sunk(i).lerp(land(i), u).add(0, Math.sin(Math.PI * u) * 1.0, 0);
            float k = ease(seg(a, 31, 36));
            return new Pose(p, 0.55F + 0.45F * u, Mth.lerp(k, spin, 720F), -90F * k, 0, false);
        }
        if (a < 58) return new Pose(land(i).add(0, Math.sin(a * 0.25) * 0.015, 0), 1F, 720F, -90F, 0, false);
        float u = ease(seg(a, 58, 67));
        Vec3 p = land(i).lerp(hand, u).add(0, Math.sin(Math.PI * u) * 0.6, 0);
        return new Pose(p, 1F, Mth.lerp(u, 720F, 1440F), -90F * (1F - ease(seg(a, 58, 63))), 0, false);
    }

    private static Pose bookPose(Inst i, float a) {
        if (a < 22 || a > 40.5F) return null;
        Vec3 hover = cauldron(i).add(0, 1.75 + Math.sin(a * 0.3) * 0.05, 0);
        if (a < 34) return new Pose(hover, back(seg(a, 22, 27)), a * 9F, -20F, 0, false);
        float u = seg(a, 34, 40);
        float e = u * u;
        Vec3 p = hover.lerp(cauldron(i).add(0, 0.55, 0), e).add(Math.cos(a * 1.3) * 0.2 * (1 - u), 0, Math.sin(a * 1.3) * 0.2 * (1 - u));
        return new Pose(p, 1F - 0.6F * e, a * 12F, -20F, 0, false);
    }

    private static Pose flaskPose(Inst i, float a) {
        if (a < 40 || a >= 58) return null;
        Vec3 s0 = cauldron(i).add(i.r.scale(-0.55)).add(0, 1.5, 0);
        Vec3 dip = cauldron(i).add(0, 0.8, 0);
        Vec3 pour = land(i).add(i.r.scale(-0.18)).add(0, 1.0, 0);
        boolean filled = a >= 46;
        if (a < 43) return new Pose(s0.add(0, Math.sin(a * 0.4) * 0.03, 0), back(seg(a, 40, 43)), 0, 0, 0, false);
        if (a < 46) return new Pose(s0.lerp(dip, ease(seg(a, 43, 46))), 1F, 0, 0, 0.25F * seg(a, 43, 46), false);
        if (a < 52) {
            float u = ease(seg(a, 46, 52));
            return new Pose(dip.lerp(pour, u).add(0, Math.sin(Math.PI * u) * 0.4, 0), 1F, 0, 0, 0.25F * (1 - u), filled);
        }
        return new Pose(pour, 1F, 0, 0, 105F * Mth.DEG_TO_RAD * ease(seg(a, 52, 56)), true);
    }

    private static Vec3 lip(Inst i, Pose f) {
        double th = f.roll();
        return f.p().add(i.r.scale(Math.sin(th) * 0.38)).add(0, Math.cos(th) * 0.38, 0);
    }

    // ------------------------------------------------------------------ ticks: particles and sounds

    private static float quality(Minecraft mc) {
        ParticleStatus s = mc.options.particles().get();
        return s == ParticleStatus.ALL ? 1F : s == ParticleStatus.DECREASED ? 0.6F : 0.3F;
    }

    private static void snd(Minecraft mc, Vec3 p, SoundEvent s, float vol, float pitch) {
        mc.level.playLocalSound(p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch, false);
    }

    private static void add(Minecraft mc, ParticleOptions o, Vec3 p, double vx, double vy, double vz) {
        mc.level.addParticle(o, p.x, p.y, p.z, vx, vy, vz);
    }

    private static void burst(Minecraft mc, Vec3 c, ParticleOptions o, int n, double speed, double lift) {
        var rnd = mc.level.random;
        int count = Math.max(1, (int) (n * quality(mc)));
        for (int k = 0; k < count; k++) {
            double u = rnd.nextDouble() * 2 - 1, t = rnd.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
            add(mc, o, c, s * Math.cos(t) * speed, u * speed + lift, s * Math.sin(t) * speed);
        }
    }

    private static void tickInst(Minecraft mc, Inst i) {
        int a = i.age;
        float q = quality(mc);
        var rnd = mc.level.random;
        Vec3 cauldron = cauldron(i), anvil = anvil(i), liq = liquid(i), land = land(i);

        if (a == 1) {
            snd(mc, i.a, SoundEvents.ENCHANTMENT_TABLE_USE, 0.6F, 0.9F);
            int n = Math.max(6, (int) (44 * q));
            for (int k = 0; k < n; k++) {
                double an = Math.PI * 2 * k / n;
                add(mc, Fx.dust(k % 2 == 0 ? RED : PURPLE, 1F), i.a.add(Math.cos(an) * 0.4, 0.1, Math.sin(an) * 0.4), Math.cos(an) * 0.16, 0.04, Math.sin(an) * 0.16);
            }
        }
        if (a == 3) snd(mc, i.a, SoundEvents.BREWING_STAND_BREW, 0.45F, 0.8F);
        if (a >= 1 && a <= 58) {
            int n = Math.max(1, (int) (2 * q));
            for (int k = 0; k < n; k++) {
                double an = rnd.nextDouble() * Math.PI * 2, rad = 1.6 + rnd.nextDouble() * 0.5;
                add(mc, Fx.dust(rnd.nextBoolean() ? RED : PURPLE, 0.8F), i.a.add(Math.cos(an) * rad, 0.06, Math.sin(an) * rad), 0, 0.03 + rnd.nextDouble() * 0.03, 0);
            }
        }
        if (a >= 12 && a <= 58) {
            float boil = ease(seg(a, 40, 48));
            int n = Math.max(1, (int) ((1 + boil * 2.5F) * q));
            for (int k = 0; k < n; k++) {
                double an = rnd.nextDouble() * Math.PI * 2, rad = Math.sqrt(rnd.nextDouble()) * 0.26;
                Vec3 p = liq.add(Math.cos(an) * rad, 0.02, Math.sin(an) * rad);
                add(mc, ParticleTypes.BUBBLE_POP, p, 0, 0.02, 0);
                if (rnd.nextFloat() < 0.5F + boil * 0.4F) add(mc, Fx.dust(rnd.nextBoolean() ? PURPLE : HOT, 0.7F), p, (rnd.nextDouble() - 0.5) * 0.02, 0.05 + 0.07 * boil, (rnd.nextDouble() - 0.5) * 0.02);
            }
        }
        if (a == 7) {
            Vec3 h = hand(mc, i, 0);
            snd(mc, h, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.25F, 1.6F);
            burst(mc, h, ParticleTypes.ENCHANTED_HIT, 8, 0.08, 0.02);
        }
        Pose ip = itemPose(mc, i, a, 0);
        if (ip != null && ((a >= 7 && a <= 35) || a >= 58) && a < 67) {
            int n = Math.max(1, (int) (2 * q));
            for (int k = 0; k < n; k++)
                add(mc, Fx.dust(k % 2 == 0 ? RED : PURPLE, 0.8F), ip.p().add((rnd.nextDouble() - 0.5) * 0.25, (rnd.nextDouble() - 0.5) * 0.25, (rnd.nextDouble() - 0.5) * 0.25),
                        (rnd.nextDouble() - 0.5) * 0.02, -0.01, (rnd.nextDouble() - 0.5) * 0.02);
        }
        if (a == 22) {
            Vec3 b = cauldron.add(0, 1.75, 0);
            snd(mc, b, SoundEvents.BOOK_PAGE_TURN, 0.6F, 1.0F);
            snd(mc, b, SoundEvents.ENCHANTMENT_TABLE_USE, 0.5F, 1.3F);
            burst(mc, b, ParticleTypes.END_ROD, 14, 0.08, 0);
        }
        if (a >= 22 && a <= 40) {
            Pose bp = bookPose(i, a);
            if (bp != null) {
                int n = Math.max(1, (int) (3 * q));
                for (int k = 0; k < n; k++) {
                    Vec3 target = liq.add(0, 0.25, 0);
                    Vec3 d = bp.p().subtract(target).add(rnd.nextGaussian() * 0.15, rnd.nextGaussian() * 0.1, rnd.nextGaussian() * 0.15);
                    add(mc, ParticleTypes.ENCHANT, target, d.x, d.y, d.z);
                }
            }
        }
        if (a == 24) {
            snd(mc, liq, SoundEvents.PLAYER_SPLASH, 0.45F, 1.3F);
            snd(mc, liq, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 0.4F, 1.0F);
            splash(mc, liq, 22);
        }
        if (a == 35) {
            snd(mc, land, SoundEvents.ANVIL_LAND, 0.35F, 1.3F);
            burst(mc, land, ParticleTypes.CLOUD, 10, 0.06, 0.0);
            burst(mc, land, ParticleTypes.END_ROD, 12, 0.1, 0.02);
        }
        if (a == 40) {
            snd(mc, liq, SoundEvents.PLAYER_SPLASH, 0.5F, 0.9F);
            snd(mc, liq, SoundEvents.BREWING_STAND_BREW, 0.5F, 0.7F);
            splash(mc, liq, 30);
            burst(mc, liq, Fx.dust(HOT, 1.2F), 20, 0.12, 0.1);
            Pose fp = flaskPose(i, 40.5F);
            if (fp != null) {
                snd(mc, fp.p(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 1.5F);
                burst(mc, fp.p(), ParticleTypes.END_ROD, 10, 0.06, 0);
            }
        }
        if (a == 46) {
            snd(mc, liq, SoundEvents.BOTTLE_FILL, 0.6F, 1.0F);
            splash(mc, liq, 12);
        }
        if (a == 53) snd(mc, land.add(0, 0.8, 0), SoundEvents.BOTTLE_EMPTY, 0.6F, 0.9F);
        if (a >= 54 && a < 58 && a % 2 == 0) snd(mc, land, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON, 0.4F, 1.2F);
        Pose fl = flaskPose(i, a);
        if (fl != null && a >= 53 && a < 58) {
            Vec3 lp = lip(i, fl), end = land.add(0, 0.12, 0);
            int n = Math.max(2, (int) (5 * q));
            for (int k = 0; k < n; k++) {
                Vec3 p = lp.lerp(end, rnd.nextDouble()).add((rnd.nextDouble() - 0.5) * 0.06, 0, (rnd.nextDouble() - 0.5) * 0.06);
                add(mc, Fx.dust(rnd.nextBoolean() ? PURPLE : HOT, 0.9F), p, 0, -0.05, 0);
            }
            add(mc, ParticleTypes.END_ROD, lp, 0, -0.03, 0);
            add(mc, ParticleTypes.ENCHANT, land.add(0, 0.15, 0), (rnd.nextDouble() - 0.5) * 0.4, 0.8 + rnd.nextDouble() * 0.4, (rnd.nextDouble() - 0.5) * 0.4);
        }
        if (a == 54) {
            Vec3 c = land.add(0, 0.25, 0);
            snd(mc, c, SoundEvents.PLAYER_LEVELUP, 0.45F, 1.1F);
            snd(mc, c, SoundEvents.ANVIL_USE, 0.2F, 1.7F);
            snd(mc, c, SoundEvents.ENCHANTMENT_TABLE_USE, 0.7F, 0.8F);
            add(mc, ParticleTypes.FLASH, c, 0, 0, 0);
            burst(mc, c, ParticleTypes.ENCHANTED_HIT, 30, 0.25, 0.04);
            burst(mc, c, ParticleTypes.END_ROD, 30, 0.2, 0.04);
            burst(mc, c, Fx.dust(i.remove ? RED : PURPLE, 1.4F), 30, 0.25, 0.04);
        }
        if (a >= 55 && a <= 64) {
            int n = Math.max(1, (int) (3 * q));
            for (int k = 0; k < n; k++) {
                double an = rnd.nextDouble() * Math.PI * 2, rad = 0.2 + rnd.nextDouble() * 0.5;
                add(mc, ParticleTypes.ENCHANTED_HIT, anvil.add(Math.cos(an) * rad, 0.65, Math.sin(an) * rad), 0, 0.06 + rnd.nextDouble() * 0.04, 0);
            }
        }
        if (a == 58) {
            Pose f = flaskPose(i, 57.5F);
            Vec3 c = f != null ? f.p() : land.add(0, 1, 0);
            burst(mc, c, ParticleTypes.POOF, 10, 0.04, 0.02);
            burst(mc, c, ParticleTypes.END_ROD, 12, 0.08, 0);
            snd(mc, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.4F, 1.2F);
        }
        if (a == 60) {
            burst(mc, cauldron.add(0, 0.5, 0), ParticleTypes.POOF, 8, 0.05, 0.03);
            burst(mc, anvil.add(0, 0.5, 0), ParticleTypes.POOF, 8, 0.05, 0.03);
        }
        if (a == 67) {
            Vec3 h = hand(mc, i, 0);
            snd(mc, h, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4F, 1.2F);
            burst(mc, h, ParticleTypes.ENCHANTED_HIT, 12, 0.1, 0.03);
        }
    }

    private static void splash(Minecraft mc, Vec3 at, int n) {
        var rnd = mc.level.random;
        int count = Math.max(2, (int) (n * quality(mc)));
        for (int k = 0; k < count; k++) {
            double an = rnd.nextDouble() * Math.PI * 2, s = 0.03 + rnd.nextDouble() * 0.1;
            add(mc, ParticleTypes.SPLASH, at.add(Math.cos(an) * 0.1, 0.05, Math.sin(an) * 0.1), Math.cos(an) * s, 0.22 + rnd.nextDouble() * 0.2, Math.sin(an) * s);
            if (k % 2 == 0) add(mc, Fx.dust(rnd.nextBoolean() ? PURPLE : RED, 1F), at.add(0, 0.05, 0), Math.cos(an) * s, 0.2 + rnd.nextDouble() * 0.15, Math.sin(an) * s);
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { RUN.clear(); return; }
        if (mc.isPaused() || RUN.isEmpty()) return;
        for (Iterator<Inst> it = RUN.values().iterator(); it.hasNext(); ) {
            Inst i = it.next();
            i.age++;
            tickInst(mc, i);
            if (i.age > TOTAL) it.remove();
        }
    }

    // ------------------------------------------------------------------ drawing

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || RUN.isEmpty()) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 cp = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cp.x, -cp.y, -cp.z);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        for (Inst i : RUN.values()) solids(mc, i, Math.min(TOTAL, i.age + pt), pt, ps, buf);
        buf.endBatch();
        EnchGlow gl = new EnchGlow(buf.getBuffer(RenderType.lightning()), ps.last().pose(), cp);
        float time = (mc.level.getGameTime() % 100000L + pt) / 20F;
        for (Inst i : RUN.values()) glow(mc, gl, i, Math.min(TOTAL, i.age + pt), pt, time);
        buf.endBatch(RenderType.lightning());
        ps.popPose();
    }

    private static void block(Minecraft mc, PoseStack ps, MultiBufferSource buf, BlockState st, Vec3 base, float scale, float yawDeg, float wobble) {
        if (scale <= 0.01F) return;
        int l = LevelRenderer.getLightColor(mc.level, BlockPos.containing(base.add(0, 0.5, 0)));
        l = LightTexture.pack(Math.max(LightTexture.block(l), 11), LightTexture.sky(l));
        ps.pushPose();
        ps.translate(base.x, base.y, base.z);
        ps.mulPose(Axis.YP.rotationDegrees(yawDeg));
        ps.scale(scale, scale * (1F + wobble), scale);
        ps.translate(-0.5, 0, -0.5);
        mc.getBlockRenderer().renderSingleBlock(st, ps, buf, l, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    private static void item(Minecraft mc, PoseStack ps, MultiBufferSource buf, ItemStack st, Pose p, Vec3 rollAxis, float size, int seed) {
        if (p == null || p.scale() <= 0.02F || st.isEmpty()) return;
        ps.pushPose();
        ps.translate(p.p().x, p.p().y, p.p().z);
        if (p.roll() != 0) ps.mulPose(new Quaternionf().fromAxisAngleRad((float) rollAxis.x, (float) rollAxis.y, (float) rollAxis.z, p.roll()));
        ps.mulPose(Axis.YP.rotationDegrees(p.spin()));
        if (p.tilt() != 0) ps.mulPose(Axis.XP.rotationDegrees(p.tilt()));
        float s = size * p.scale();
        ps.scale(s, s, s);
        mc.getItemRenderer().renderStatic(st, ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, seed);
        ps.popPose();
    }

    private static void solids(Minecraft mc, Inst i, float a, float pt, PoseStack ps, MultiBufferSource buf) {
        float sc = structScale(a);
        float wob = 0;
        for (int t : SPLASHES) if (a > t) wob += (float) (Math.exp(-(a - t) * 0.25) * Math.sin((a - t) * 1.6) * 0.04);
        float yaw = 180F - i.m.yaw();
        block(mc, ps, buf, CAULDRON, cauldron(i), 0.85F * sc, yaw, wob);
        block(mc, ps, buf, ANVIL, anvil(i), 0.9F * sc, yaw, 0);

        Pose ip = itemPose(mc, i, a, pt);
        item(mc, ps, buf, a >= 55 && !i.remove ? i.glint : i.plain, ip, i.f, 1.5F, i.m.caster());
        item(mc, ps, buf, i.book, bookPose(i, a), i.f, 1.4F, 3);
        Pose fp = flaskPose(i, a);
        item(mc, ps, buf, fp != null && fp.filled() ? i.filled : i.empty, fp, i.f, 1.5F, 5);
    }

    private static void runeCircle(EnchGlow gl, Vec3 c, float k, float alpha, float t) {
        if (k <= 0.01F) return;
        float a = alpha * Mth.clamp(k * 1.4F, 0, 1);
        gl.color(RED).flatDisc(c, 2.0 * k, 40, 0.14F * a, 0.0F);
        gl.color(RED);
        gl.circle(c, 2.1 * k, 90, t * 0.5, 0.022F, 0.9F * a, false);
        gl.circle(c, 1.75 * k, 60, -t * 0.8, 0.016F, 0.8F * a, true);
        gl.circle(c, 1.0 * k, 40, t * 1.1, 0.014F, 0.7F * a, false);
        gl.color(PURPLE);
        gl.ticks(c, 1.78 * k, 2.05 * k, 40, t * 0.5, 0.012F, a);
        gl.star(c, 1.7 * k, 6, 2, -t * 0.8, 0.014F, 0.8F * a);
        gl.star(c, 1.7 * k, 6, 1, -t * 0.8, 0.012F, 0.5F * a);
        gl.color(WHITE);
        for (int i = 0; i < 6; i++) {
            double an = -t * 0.8 + Math.PI * 2 * i / 6;
            gl.spark(c.add(Math.cos(an) * 1.7 * k, 0.02, Math.sin(an) * 1.7 * k), 0.08F * k, 0.8F * a);
        }
    }

    private static void smallCircle(EnchGlow gl, Vec3 c, float k, float alpha, float t, boolean star) {
        if (k <= 0.01F) return;
        float a = alpha * Mth.clamp(k, 0, 1);
        gl.color(PURPLE);
        gl.circle(c, 0.8 * k, 36, t, 0.014F, 0.8F * a, false);
        gl.circle(c, 0.65 * k, 28, -t * 1.3, 0.01F, 0.7F * a, true);
        if (star) gl.star(c, 0.62 * k, 5, 2, t * 0.7, 0.01F, 0.6F * a);
        gl.color(RED);
        gl.ticks(c, 0.8 * k, 0.92 * k, 24, t, 0.01F, a);
    }

    private static void glow(Minecraft mc, EnchGlow gl, Inst i, float a, float pt, float time) {
        float sc = structScale(a);
        float vis = Mth.clamp(sc, 0, 1);
        Vec3 ground = i.a.add(0, 0.04, 0);
        runeCircle(gl, ground, ease(seg(a, 0.5F, 9F)) * (1F - ease(seg(a, 59, 67))), vis, time);
        smallCircle(gl, cauldron(i).add(0, 0.04, 0), sc, vis, time, false);
        smallCircle(gl, anvil(i).add(0, 0.04, 0), sc, vis, -time, true);
        // light columns when it all rises out of the circle
        if (a < 12) {
            float h = 1.4F * seg(a, 1, 8), fade = 1F - seg(a, 6, 12);
            gl.color(PURPLE);
            for (int k = 0; k < 10; k++) {
                double an = Math.PI * 2 * k / 10 + time * 0.3;
                Vec3 b = i.a.add(Math.cos(an) * 1.9, 0.04, Math.sin(an) * 1.9);
                gl.line(b, b.add(0, h, 0), 0.012F, 0.7F * fade);
            }
        }
        // glowing liquid
        float fill = ease(seg(a, 4, 14)) * (1F - ease(seg(a, 59, 66)));
        if (fill > 0.01F) {
            float boil = ease(seg(a, 40, 48));
            Vec3 liq = cauldron(i).add(0, 0.64 * 0.85 * sc, 0);
            gl.color(mix(RED, PURPLE, 0.5F + 0.5F * Mth.sin(time * 2F)));
            Vec3 u = i.r.scale(0.3 * sc), w = i.f.scale(0.3 * sc);
            gl.flatSquare(liq, u, w, fill * (0.45F + 0.35F * boil + 0.08F * Mth.sin(time * 9F)));
            gl.color(HOT);
            gl.flatSquare(liq.add(0, 0.003, 0), u.scale(0.55), w.scale(0.55), fill * (0.3F + 0.45F * boil));
            for (int t : SPLASHES) if (a > t && a < t + 14) {
                float k = (a - t) / 14F;
                gl.color(WHITE);
                gl.circle(liq.add(0, 0.01, 0), 0.05 + 0.3 * k, 24, 0, 0.012F, (1 - k) * 0.9F, false);
            }
            gl.color(HOT);
            for (int k = 0; k < 5; k++) {
                float rise = (time * 0.8F + k * 0.37F) % 1F;
                gl.spark(liq.add(Math.sin(k * 2.3) * 0.18, rise * 0.7 * (0.2 + boil), Math.cos(k * 1.7) * 0.18), 0.05F, boil * (1F - rise));
            }
        }
        // halos on the flying pieces
        Pose ip = itemPose(mc, i, a, pt);
        if (ip != null && (a < 35 || a >= 58)) {
            gl.color(Mth.sin(time * 8F) > 0 ? RED : PURPLE);
            gl.spark(ip.p(), 0.3F * ip.scale(), 0.6F);
        }
        Pose bp = bookPose(i, a);
        if (bp != null) {
            gl.color(PURPLE);
            gl.ringFacing(bp.p(), 0.42 * bp.scale(), 28, time * 2, 0.012F, 0.8F);
            gl.color(WHITE);
            gl.spark(bp.p(), 0.25F * bp.scale(), 0.5F);
        }
        Pose fp = flaskPose(i, a);
        if (fp != null) {
            gl.color(fp.filled() ? HOT : WHITE);
            gl.spark(fp.p(), 0.28F * fp.scale(), fp.filled() ? 0.7F : 0.4F);
            if (a >= 53.5F) {
                Vec3 lp = lip(i, fp), end = land(i).add(0, 0.12, 0);
                float shimmer = 0.8F + 0.2F * Mth.sin(time * 40F);
                gl.color(PURPLE);
                gl.line(lp, end, 0.03F * shimmer, 0.9F);
                gl.color(WHITE);
                gl.line(lp, end, 0.012F, 0.8F);
            }
        }
        // the flash of the pour
        if (a >= 54 && a < 61) {
            float u = seg(a, 54, 61);
            Vec3 c = land(i).add(0, 0.2, 0);
            gl.color(i.remove ? HOT : WHITE);
            gl.discFacing(c, 0.4 + 2.0 * u, 28, (1 - u) * 0.9F, 0F);
            gl.color(PURPLE);
            gl.ringFacing(c, 0.2 + 3.2 * ease(u), 48, 0, 0.03F * (1 - u) + 0.005F, 1F - u);
            gl.color(WHITE);
            gl.rays(c, 14, 0.2 + u, 1.2 + 3 * u, u * 2, 0.02F, 1F - u);
        }
        if (a >= 55 && a < 64) {
            float fade = 1F - seg(a, 58, 64);
            gl.color(HOT);
            for (int k = 0; k < 6; k++) {
                double an = time * 2 + Math.PI * 2 * k / 6, rise = (time * 1.2 + k * 0.17) % 1.0;
                gl.spark(anvil(i).add(Math.cos(an) * 0.45, 0.7 + rise * 0.8, Math.sin(an) * 0.45), 0.06F, fade * (1F - (float) rise));
            }
        }
    }
}
