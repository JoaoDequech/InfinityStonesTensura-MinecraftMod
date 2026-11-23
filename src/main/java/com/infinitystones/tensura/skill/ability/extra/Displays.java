package com.infinitystones.tensura.skill.ability.extra;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
 * Purely visual block/text displays (vanilla display entities): flying debris, glowing copies of blocks, floating
 * labels. They never touch real blocks. Tagged so leftovers (crash, unload) are removed when they load again.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class Displays {
    public static final String TAG = "infinity_extra_fx";
    private static final Set<Entity> LIVE = Collections.newSetFromMap(new WeakHashMap<>());

    /** A block copy centred on pos (scale s), optional glow colour (0 = none), smooth movement over moveTicks. */
    public static @Nullable Display.BlockDisplay block(ServerLevel l, BlockState state, Vec3 pos, float s, int glowRgb, int moveTicks) {
        Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(l);
        if (d == null) return null;
        CompoundTag t = new CompoundTag();
        d.saveWithoutId(t);
        t.put("block_state", NbtUtils.writeBlockState(state));
        t.put("transformation", transform(s, 0, 0, 0));
        t.putInt("teleport_duration", Math.max(0, Math.min(59, moveTicks)));
        t.putInt("interpolation_duration", 3);
        t.putFloat("view_range", 2F);
        if (glowRgb != 0) { t.putBoolean("Glowing", true); t.putInt("glow_color_override", glowRgb & 0xFFFFFF); }
        CompoundTag br = new CompoundTag(); br.putInt("block", 15); br.putInt("sky", 15); t.put("brightness", br);
        d.load(t);
        d.moveTo(pos.x, pos.y, pos.z, 0, 0);
        d.addTag(TAG);
        LIVE.add(d);
        l.addFreshEntity(d);
        return d;
    }

    /** Floating text facing everyone (billboard centre). */
    public static @Nullable Display.TextDisplay text(ServerLevel l, net.minecraft.network.chat.Component text, Vec3 pos, int bgArgb, float scale) {
        Display.TextDisplay d = EntityType.TEXT_DISPLAY.create(l);
        if (d == null) return null;
        CompoundTag t = new CompoundTag();
        d.saveWithoutId(t);
        t.putString("text", net.minecraft.network.chat.Component.Serializer.toJson(text, l.registryAccess()));
        t.putString("billboard", "center");
        t.putInt("background", bgArgb);
        t.putBoolean("shadow", true);
        t.put("transformation", transform(scale, 0, 0, 0));
        CompoundTag br = new CompoundTag(); br.putInt("block", 15); br.putInt("sky", 15); t.put("brightness", br);
        d.load(t);
        d.moveTo(pos.x, pos.y, pos.z, 0, 0);
        d.addTag(TAG);
        LIVE.add(d);
        l.addFreshEntity(d);
        return d;
    }

    /**
     * Changes scale and rotation (radians, around Y then X) of a block display, interpolated over ticks.
     * The block is centred: translation keeps the cube around the entity position.
     */
    public static void pose(Display d, float s, float yaw, float pitch, int ticks) {
        CompoundTag t = new CompoundTag();
        d.saveWithoutId(t);
        t.put("transformation", transform(s, yaw, pitch, 0));
        t.putInt("interpolation_duration", ticks);
        t.putInt("start_interpolation", 0);
        d.load(t);
    }

    private static CompoundTag transform(float s, float yaw, float pitch, float roll) {
        org.joml.Quaternionf q = new org.joml.Quaternionf().rotateY(yaw).rotateX(pitch).rotateZ(roll);
        // centre the unit cube: translation = -R * (s/2, s/2, s/2)
        org.joml.Vector3f off = q.transform(new org.joml.Vector3f(-s / 2, -s / 2, -s / 2));
        CompoundTag tr = new CompoundTag();
        tr.put("left_rotation", quat(q));
        tr.put("right_rotation", quat(new org.joml.Quaternionf()));
        tr.put("translation", vec(off.x, off.y, off.z));
        tr.put("scale", vec(s, s, s));
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

    /** Leftovers from a previous session (saved in a chunk) are removed as soon as they load. */
    @SubscribeEvent
    public static void join(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && e.getEntity().getTags().contains(TAG) && !LIVE.contains(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        for (Entity d : LIVE.toArray(new Entity[0])) d.discard();
        LIVE.clear();
    }

    private Displays() {}
}
