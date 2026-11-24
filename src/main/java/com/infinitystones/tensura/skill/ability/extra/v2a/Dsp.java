package com.infinitystones.tensura.skill.ability.extra.v2a;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Display entities of the v2a abilities (stretched block copies, floating food). Visual only, tagged so leftovers
 * of a crash are removed when they load again, and all removed when the server stops.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class Dsp {
    public static final String TAG = "infinity_v2a_fx";
    private static final Set<Entity> LIVE = Collections.newSetFromMap(new WeakHashMap<>());

    /** Block copy centred on pos, size (sx, sy, sz) in blocks, turned by yaw (radians). */
    public static @Nullable Display.BlockDisplay block(ServerLevel l, BlockState state, Vec3 pos, float sx, float sy, float sz,
                                                       float yaw, int glowRgb, int moveTicks) {
        Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(l);
        if (d == null) return null;
        CompoundTag t = new CompoundTag();
        d.saveWithoutId(t);
        t.put("block_state", NbtUtils.writeBlockState(state));
        t.put("transformation", centred(sx, sy, sz, yaw, 0));
        base(t, moveTicks, glowRgb);
        d.load(t);
        d.moveTo(pos.x, pos.y, pos.z, 0, 0);
        d.addTag(TAG);
        LIVE.add(d);
        l.addFreshEntity(d);
        return d;
    }

    /** Item copy (flat when rotX = -PI/2) centred on pos. */
    public static @Nullable Display.ItemDisplay item(ServerLevel l, ItemStack stack, Vec3 pos, float s, float rotX, float yaw, int moveTicks) {
        Display.ItemDisplay d = EntityType.ITEM_DISPLAY.create(l);
        if (d == null) return null;
        CompoundTag t = new CompoundTag();
        d.saveWithoutId(t);
        t.put("item", stack.save(l.registryAccess()));
        t.putString("item_display", "fixed");
        t.put("transformation", centred(s, s, s, yaw, rotX, true));
        base(t, moveTicks, 0);
        d.load(t);
        d.moveTo(pos.x, pos.y, pos.z, 0, 0);
        d.addTag(TAG);
        LIVE.add(d);
        l.addFreshEntity(d);
        return d;
    }

    private static void base(CompoundTag t, int moveTicks, int glowRgb) {
        t.putInt("teleport_duration", Math.max(0, Math.min(59, moveTicks)));
        t.putInt("interpolation_duration", 4);
        t.putFloat("view_range", 3F);
        if (glowRgb != 0) { t.putBoolean("Glowing", true); t.putInt("glow_color_override", glowRgb & 0xFFFFFF); }
        CompoundTag br = new CompoundTag(); br.putInt("block", 15); br.putInt("sky", 15); t.put("brightness", br);
    }

    /** New size / turn, interpolated over ticks. */
    public static void pose(Display d, float sx, float sy, float sz, float yaw, float rotX, int ticks) {
        CompoundTag t = new CompoundTag();
        d.saveWithoutId(t);
        t.put("transformation", centred(sx, sy, sz, yaw, rotX, d instanceof Display.ItemDisplay));
        t.putInt("interpolation_duration", ticks);
        t.putInt("start_interpolation", 0);
        d.load(t);
    }

    private static CompoundTag centred(float sx, float sy, float sz, float yaw, float rotX) { return centred(sx, sy, sz, yaw, rotX, false); }

    private static CompoundTag centred(float sx, float sy, float sz, float yaw, float rotX, boolean origin) {
        org.joml.Quaternionf q = new org.joml.Quaternionf().rotateY(yaw).rotateX(rotX);
        org.joml.Vector3f off = origin ? new org.joml.Vector3f() : q.transform(new org.joml.Vector3f(-sx / 2, -sy / 2, -sz / 2));
        CompoundTag tr = new CompoundTag();
        tr.put("left_rotation", quat(q));
        tr.put("right_rotation", quat(new org.joml.Quaternionf()));
        tr.put("translation", vec(off.x, off.y, off.z));
        tr.put("scale", vec(sx, sy, sz));
        return tr;
    }

    private static ListTag quat(org.joml.Quaternionf q) {
        ListTag l = new ListTag();
        l.add(FloatTag.valueOf(q.x)); l.add(FloatTag.valueOf(q.y)); l.add(FloatTag.valueOf(q.z)); l.add(FloatTag.valueOf(q.w));
        return l;
    }

    private static ListTag vec(float x, float y, float z) {
        ListTag l = new ListTag();
        l.add(FloatTag.valueOf(x)); l.add(FloatTag.valueOf(y)); l.add(FloatTag.valueOf(z));
        return l;
    }

    public static void remove(@Nullable Entity e) {
        if (e != null) { LIVE.remove(e); e.discard(); }
    }

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && e.getEntity().getTags().contains(TAG) && !LIVE.contains(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        for (Entity d : LIVE.toArray(new Entity[0])) d.discard();
        LIVE.clear();
    }

    private Dsp() {}
}
