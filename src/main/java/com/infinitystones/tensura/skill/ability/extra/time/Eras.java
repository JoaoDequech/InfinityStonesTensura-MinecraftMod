package com.infinitystones.tensura.skill.ability.extra.time;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Eras: left = age the aimed area, right = rejuvenate it; radius 2–6 with Alt+scroll. A green wave sweeps outwards:
 * aging cracks/moss-covers stone and bricks, oxidises copper, chips anvils, ripens crops, grows saplings and babies,
 * wears dropped tools (never below 1 durability); rejuvenating does the opposite (clean stone, de-oxidise copper,
 * repair anvils, seedlings, adults become babies, dropped tools repaired by 25%). Nothing is destroyed or dropped.
 */
public final class Eras implements Ability {
    public static final Eras INSTANCE = new Eras();
    private static final String MODE = "time.eras";
    private static final Map<Block, Block> AGE = new HashMap<>(), YOUTH = new HashMap<>();

    private static void pair(Block young, Block old) { AGE.put(young, old); YOUTH.put(old, young); }

    static {
        pair(Blocks.STONE, Blocks.COBBLESTONE);
        pair(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE);
        pair(Blocks.COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_STAIRS);
        pair(Blocks.COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_SLAB);
        pair(Blocks.COBBLESTONE_WALL, Blocks.MOSSY_COBBLESTONE_WALL);
        pair(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS);
        YOUTH.put(Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS);
        AGE.put(Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS);
        pair(Blocks.STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_STAIRS);
        pair(Blocks.STONE_BRICK_SLAB, Blocks.MOSSY_STONE_BRICK_SLAB);
        pair(Blocks.STONE_BRICK_WALL, Blocks.MOSSY_STONE_BRICK_WALL);
        pair(Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS);
        pair(Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES);
        pair(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS);
        pair(Blocks.NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS);
        pair(Blocks.INFESTED_STONE_BRICKS, Blocks.INFESTED_CRACKED_STONE_BRICKS);
        pair(Blocks.ANVIL, Blocks.CHIPPED_ANVIL);
        pair(Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL);
        pair(Blocks.DIRT, Blocks.COARSE_DIRT);
    }

    @Override public Stone stone() { return Stone.TIME; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        BlockHitResult hit = Aim.block(p, 40);
        if (hit.getType() == HitResult.Type.MISS) return Fx.fail(p, "infinity.extra.time.eras.no_target");
        boolean aging = !alt;
        int r = ExtraSettings.get(p, MODE, 3, 2, 6, 1);
        ServerLevel l = p.serverLevel();
        BlockPos center = hit.getBlockPos();
        Vec3 c = Vec3.atCenterOf(center);
        List<BlockPos> all = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r)))
            if (pos.distSqr(center) <= (r + 0.5) * (r + 0.5)) all.add(pos.immutable());
        all.sort(Comparator.comparingDouble(b -> b.distSqr(center)));
        int ticks = r * 3 + 4;
        Vec3 floor = new Vec3(c.x, center.getY() + 1.02, c.z);
        TimeNet.era(l, floor, r + 1.5F, aging, ticks + 25);
        FxNet.clock(l, 0, floor, r + 1F, Stone.TIME.color(), ticks + 20, aging ? 10F : -10F);
        FxNet.flare(l, c.add(0, 1, 0), 3F, Stone.TIME.color(), 12);
        Fx.sound(l, c, SoundEvents.BELL_RESONATE, 0.6F, aging ? 0.7F : 1.7F);
        Fx.sound(l, c, aging ? SoundEvents.SCULK_CATALYST_BLOOM : SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, aging ? 0.6F : 1.4F);
        int[] cursor = {0};
        int[] changed = {0};
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                double front = (age + 1) / 3.0 + 0.5;
                int done = 0;
                while (cursor[0] < all.size() && done < 400) {
                    BlockPos pos = all.get(cursor[0]);
                    if (Math.sqrt(pos.distSqr(center)) > front) break;
                    if (transform(l, pos, aging)) changed[0]++;
                    cursor[0]++; done++;
                }
                if (age % 3 == 0) Fx.sound(l, c, aging ? SoundEvents.TUFF_BREAK : SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, aging ? 0.6F + age * 0.02F : 1.8F - age * 0.03F);
                if (cursor[0] >= all.size()) { entities(l, c, r + 1, aging); return false; }
                return true;
            }
        });
        Fx.bar(p, Stone.TIME, aging ? "infinity.extra.time.eras.age" : "infinity.extra.time.eras.youth", r);
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState copyProps(BlockState from, Block to) {
        BlockState s = to.defaultBlockState();
        for (Property prop : from.getProperties()) if (s.hasProperty(prop)) s = s.setValue(prop, from.getValue(prop));
        return s;
    }

    private static boolean transform(ServerLevel l, BlockPos pos, boolean aging) {
        BlockState s = l.getBlockState(pos);
        if (s.isAir()) return false;
        Block b = s.getBlock();
        BlockState next = null;
        Block map = (aging ? AGE : YOUTH).get(b);
        if (map != null) {
            if (aging && b == Blocks.STONE_BRICKS && l.random.nextBoolean()) map = Blocks.MOSSY_STONE_BRICKS;
            next = copyProps(s, map);
        } else if (b instanceof WeatheringCopper) {
            next = aging ? WeatheringCopper.getNext(b).map(n -> copyProps(s, n)).orElse(null) : WeatheringCopper.getPrevious(s).orElse(null);
        } else if (b instanceof CropBlock crop) {
            next = aging ? crop.getStateForAge(crop.getMaxAge()) : crop.getStateForAge(0);
        } else if (b instanceof StemBlock || b instanceof SweetBerryBushBlock || b instanceof CocoaBlock || b instanceof NetherWartBlock) {
            for (Property<?> prop : s.getProperties()) if (prop instanceof IntegerProperty ip && "age".equals(prop.getName())) {
                int v = aging ? ip.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0) : ip.getPossibleValues().stream().mapToInt(Integer::intValue).min().orElse(0);
                next = s.setValue(ip, v);
            }
        } else if (b instanceof SaplingBlock sap && aging) {
            sap.advanceTree(l, pos, s, l.random);
            BlockState s2 = l.getBlockState(pos);
            if (s2.is(b)) sap.advanceTree(l, pos, s2, l.random);
            Fx.at(l, ParticleTypes.HAPPY_VILLAGER, Vec3.atCenterOf(pos), 8, 0.5, 0);
            return true;
        }
        if (next == null || next == s) return false;
        l.setBlock(pos, next, 3);
        Vec3 v = Vec3.atCenterOf(pos);
        if (l.random.nextInt(3) == 0) Fx.at(l, new BlockParticleOption(ParticleTypes.BLOCK, next), v, 4, 0.4, 0.05);
        Fx.at(l, aging ? Fx.dust(0xC8A060, 0.9F) : Fx.light(Stone.TIME, 0.9F), v, 2, 0.45, 0);
        return true;
    }

    private static void entities(ServerLevel l, Vec3 c, double r, boolean aging) {
        AABB box = new AABB(c, c).inflate(r);
        for (AgeableMob m : l.getEntitiesOfClass(AgeableMob.class, box, e -> e.isAlive() && e.position().distanceToSqr(c) <= r * r)) {
            if (aging && m.getAge() < 0) m.setAge(0);
            else if (!aging && m.getAge() >= 0) m.setAge(-24000);
            else continue;
            Fx.at(l, aging ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.END_ROD, m.getBoundingBox().getCenter(), 8, 0.4, 0.02);
        }
        for (ItemEntity it : l.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && e.position().distanceToSqr(c) <= r * r)) {
            ItemStack st = it.getItem();
            if (!st.isDamageableItem()) continue;
            int max = st.getMaxDamage(), q = Math.max(1, max / 4);
            ItemStack copy = st.copy();
            copy.setDamageValue(aging ? Math.min(max - 1, st.getDamageValue() + q) : Math.max(0, st.getDamageValue() - q));
            it.setItem(copy);
            Fx.at(l, aging ? Fx.dust(0xC8A060, 0.8F) : Fx.light(Stone.TIME, 0.8F), it.position().add(0, 0.3, 0), 6, 0.2, 0);
        }
    }
}
