package com.infinitystones.tensura.skill.ability.extra.time;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Displays;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Restoration (Time + Reality): left = undo the explosions of the last 5 minutes around you (radius 8–32, Alt+scroll).
 * Green-red waves sweep outwards and every block an explosion destroyed flies back into place with its original state
 * (only where the spot is still empty / liquid / replaceable — later builds are never overwritten). Block items the
 * explosion dropped and that are still lying around are taken back (no duplication). Containers return empty.
 */
public final class Restoration implements Ability {
    public static final Restoration INSTANCE = new Restoration();
    private static final String MODE = "time.restoration";
    private static final int FLIGHT = 7, MAX_DISPLAYS_PER_TICK = 10, MAX_PER_TICK = 300;

    private record Flying(ExplosionLog.Entry e, Display.BlockDisplay d, int at) {}

    @Override public Stone stone() { return Stone.TIME; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (!Stone.REALITY.isHeldBy(p)) return Fx.fail(p, "infinity.extra.time.restore.need_reality");
        int r = ExtraSettings.get(p, MODE, 16, 8, 32, 1);
        ServerLevel l = p.serverLevel();
        Vec3 c = p.position();
        List<ExplosionLog.Entry> list = ExplosionLog.take(l, c, r);
        list.removeIf(en -> !free(l, en));
        if (list.isEmpty()) return Fx.fail(p, "infinity.extra.time.restore.nothing");
        list.sort(Comparator.<ExplosionLog.Entry>comparingInt(en -> (int) (Math.sqrt(en.pos().distToCenterSqr(c)) / 2)).thenComparingInt(en -> en.pos().getY()));
        // opening
        TimeNet.restore(l, c, r, 50);
        FxNet.shockwave(l, c.add(0, 0.1, 0), r, Stone.TIME.color(), 30);
        FxNet.shockwave(l, c.add(0, 0.2, 0), r * 0.9F, Stone.REALITY.color(), 36);
        FxNet.pillar(l, 0, c, 0.5F, 14, Stone.TIME.color(), 40);
        FxNet.flashAround(l, c, r + 8, 0xD8FFE8, 0.3F, 10);
        Fx.sound(l, c, SoundEvents.BEACON_ACTIVATE, 0.7F, 0.8F);
        Fx.sound(l, c, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.6F, 0.7F);
        Fx.sound(l, c, SoundEvents.BELL_RESONATE, 0.6F, 1.2F);
        Map<Item, Integer> owed = new HashMap<>();
        int total = list.size();
        int[] cursor = {0};
        List<Flying> flying = new ArrayList<>();
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                double front = 1 + age * 1.2;
                int done = 0, shown = 0;
                while (cursor[0] < list.size() && done < MAX_PER_TICK) {
                    ExplosionLog.Entry en = list.get(cursor[0]);
                    if (Math.sqrt(en.pos().distToCenterSqr(c)) > front) break;
                    cursor[0]++; done++;
                    Display.BlockDisplay d = null;
                    if (shown < MAX_DISPLAYS_PER_TICK && l.random.nextInt(3) == 0) {
                        Vec3 from = Vec3.atCenterOf(en.pos()).add((l.random.nextDouble() - 0.5) * 4, 2.5 + l.random.nextDouble() * 3, (l.random.nextDouble() - 0.5) * 4);
                        d = Displays.block(l, en.state(), from, 0.9F, l.random.nextBoolean() ? Stone.TIME.color() : Stone.REALITY.color(), FLIGHT);
                        shown++;
                    }
                    flying.add(new Flying(en, d, age + 1));
                }
                for (int i = 0; i < flying.size(); i++) {
                    Flying f = flying.get(i);
                    if (f.d != null && age == f.at) f.d.setPos(Vec3.atCenterOf(f.e.pos()));
                }
                flying.removeIf(f -> {
                    if (age < f.at + (f.d == null ? 0 : FLIGHT)) return false;
                    Displays.remove(f.d);
                    if (free(l, f.e)) {
                        l.setBlock(f.e.pos(), f.e.state(), 2 | 16);
                        Item it = f.e.state().getBlock().asItem();
                        if (it != Items.AIR) owed.merge(it, 1, Integer::sum);
                        Vec3 v = Vec3.atCenterOf(f.e.pos());
                        if (l.random.nextInt(3) == 0) Fx.at(l, l.random.nextBoolean() ? Fx.dust(Stone.TIME, 1F) : Fx.dust(Stone.REALITY, 1F), v, 3, 0.4, 0);
                    }
                    return true;
                });
                if (age % 8 == 0) {
                    float rr = (float) Math.min(r, front);
                    FxNet.shockwave(l, c.add(0, 0.15, 0), rr, (age / 8) % 2 == 0 ? Stone.TIME.color() : Stone.REALITY.color(), 18);
                    Fx.sound(l, c, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 0.8F + Math.min(1F, age * 0.02F));
                }
                return cursor[0] < list.size() || !flying.isEmpty();
            }
            @Override public void end() {
                for (Flying f : flying) { Displays.remove(f.d); if (free(l, f.e)) l.setBlock(f.e.pos(), f.e.state(), 2 | 16); }
                reclaim(l, c, r + 8, owed);
                Fx.sound(l, c, SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.3F);
                Fx.at(l, ParticleTypes.END_ROD, c.add(0, 2, 0), 30, 1.5, 0.1);
            }
        });
        Fx.bar(p, Stone.TIME, "infinity.extra.time.restore.on", total);
        return true;
    }

    private static boolean free(ServerLevel l, ExplosionLog.Entry en) {
        if (!l.isLoaded(en.pos())) return false;
        BlockState cur = l.getBlockState(en.pos());
        return cur.isAir() || cur.canBeReplaced() || !cur.getFluidState().isEmpty() && cur.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock;
    }

    /** Takes back the explosion's own drops of the restored blocks (tagged item entities still on the ground). */
    private static void reclaim(ServerLevel l, Vec3 c, double r, Map<Item, Integer> owed) {
        if (owed.isEmpty()) return;
        for (ItemEntity it : l.getEntitiesOfClass(ItemEntity.class, new AABB(c, c).inflate(r, 24, r), e -> e.isAlive() && e.getTags().contains(ExplosionLog.DROP_TAG))) {
            ItemStack st = it.getItem();
            Integer need = owed.get(st.getItem());
            if (need == null || need <= 0) continue;
            int take = Math.min(need, st.getCount());
            owed.put(st.getItem(), need - take);
            if (take >= st.getCount()) it.discard();
            else { ItemStack copy = st.copy(); copy.shrink(take); it.setItem(copy); }
            Fx.at(l, Fx.light(Stone.TIME, 0.8F), it.position().add(0, 0.2, 0), 4, 0.2, 0);
        }
    }

}
