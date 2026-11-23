package com.infinitystones.tensura.skill.ability.extra.space;

import com.infinitystones.tensura.skill.ability.extra.Rules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One stored Area Heist: a box of at most 16³ cells with the REAL blocks that were taken out of the world
 * (states + block-entity NBT). Cells are -1 when empty. Saved on the player (PlayerPersisted, survives death).
 * Blocks are moved, never copied: a cell is cleared the moment its block is placed back in the world.
 */
final class HeistData {
    static final String KEY = "infinity_area_heist";
    static final int MAX = 8, MAX_SIDE = 16;

    final long id;
    final int sx, sy, sz;
    final List<BlockState> palette = new ArrayList<>();
    final int[] cells;
    final Map<Integer, CompoundTag> bes = new HashMap<>();
    private final Map<BlockState, Integer> lookup = new HashMap<>();

    HeistData(long id, int sx, int sy, int sz) {
        this.id = id; this.sx = sx; this.sy = sy; this.sz = sz;
        this.cells = new int[sx * sy * sz];
        java.util.Arrays.fill(cells, -1);
    }

    int index(int x, int y, int z) { return (y * sz + z) * sx + x; }

    void set(int i, BlockState s, @Nullable CompoundTag be) {
        Integer p = lookup.get(s);
        if (p == null) { p = palette.size(); palette.add(s); lookup.put(s, p); }
        cells[i] = p;
        if (be != null) bes.put(i, be); else bes.remove(i);
    }

    void clear(int i) { cells[i] = -1; bes.remove(i); }

    @Nullable BlockState get(int i) { return cells[i] < 0 ? null : palette.get(cells[i]); }

    int count() { int n = 0; for (int c : cells) if (c >= 0) n++; return n; }

    /** Global block-state ids for the client ghost (-1 = empty). */
    int[] stateIds() {
        int[] out = new int[cells.length];
        for (int i = 0; i < cells.length; i++) out[i] = cells[i] < 0 ? -1 : Block.getId(palette.get(cells[i]));
        return out;
    }

    /** Item id of the most common block, for the menu icon. */
    String icon() {
        int[] freq = new int[palette.size()];
        for (int c : cells) if (c >= 0) freq[c]++;
        int best = -1;
        for (int i = 0; i < freq.length; i++) {
            if (palette.get(i).getBlock().asItem() == Items.AIR) continue;
            if (best < 0 || freq[i] > freq[best]) best = i;
        }
        return best < 0 ? "minecraft:structure_void" : BuiltInRegistries.ITEM.getKey(palette.get(best).getBlock().asItem()).toString();
    }

    // ------------------------------------------------------------------ transform

    /** Size after rotation (x, z swap on odd rotations). */
    int[] size(int rot) { return (rot & 1) == 0 ? new int[]{sx, sy, sz} : new int[]{sz, sy, sx}; }

    /** Offset of cell (x, y, z) after mirror (flip X) then rot × 90° clockwise. */
    BlockPos offset(int x, int y, int z, int rot, boolean mirror) { return transform(x, y, z, sx, sz, rot, mirror); }

    static BlockPos transform(int x, int y, int z, int sx, int sz, int rot, boolean mirror) {
        if (mirror) x = sx - 1 - x;
        int w = sx, d = sz;
        for (int r = 0; r < (rot & 3); r++) {
            int nx = d - 1 - z, nz = x;
            x = nx; z = nz;
            int t = w; w = d; d = t;
        }
        return new BlockPos(x, y, z);
    }

    static BlockState transform(BlockState s, int rot, boolean mirror) {
        if (mirror) s = s.mirror(Mirror.FRONT_BACK);
        return switch (rot & 3) {
            case 1 -> s.rotate(Rotation.CLOCKWISE_90);
            case 2 -> s.rotate(Rotation.CLOCKWISE_180);
            case 3 -> s.rotate(Rotation.COUNTERCLOCKWISE_90);
            default -> s;
        };
    }

    // ------------------------------------------------------------------ NBT

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("id", id);
        t.putInt("sx", sx); t.putInt("sy", sy); t.putInt("sz", sz);
        ListTag pal = new ListTag();
        for (BlockState s : palette) pal.add(NbtUtils.writeBlockState(s));
        t.put("palette", pal);
        t.putIntArray("cells", cells);
        ListTag be = new ListTag();
        for (var e : bes.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putInt("i", e.getKey());
            c.put("nbt", e.getValue());
            be.add(c);
        }
        t.put("bes", be);
        return t;
    }

    static @Nullable HeistData load(CompoundTag t) {
        int sx = t.getInt("sx"), sy = t.getInt("sy"), sz = t.getInt("sz");
        if (sx < 1 || sy < 1 || sz < 1 || sx > MAX_SIDE || sy > MAX_SIDE || sz > MAX_SIDE) return null;
        HeistData h = new HeistData(t.getLong("id"), sx, sy, sz);
        ListTag pal = t.getList("palette", Tag.TAG_COMPOUND);
        for (int i = 0; i < pal.size(); i++) {
            BlockState s = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), pal.getCompound(i));
            h.palette.add(s);
            h.lookup.putIfAbsent(s, i);
        }
        int[] c = t.getIntArray("cells");
        if (c.length == h.cells.length)
            for (int i = 0; i < c.length; i++) h.cells[i] = c[i] >= 0 && c[i] < h.palette.size() ? c[i] : -1;
        ListTag be = t.getList("bes", Tag.TAG_COMPOUND);
        for (int i = 0; i < be.size(); i++) {
            CompoundTag e = be.getCompound(i);
            int idx = e.getInt("i");
            if (idx >= 0 && idx < h.cells.length && h.cells[idx] >= 0) h.bes.put(idx, e.getCompound("nbt"));
        }
        return h;
    }

    // ------------------------------------------------------------------ player list

    static ListTag list(ServerPlayer p) { return Rules.data(p).getList(KEY, Tag.TAG_COMPOUND); }

    static List<HeistData> all(ServerPlayer p) {
        ListTag l = list(p);
        List<HeistData> out = new ArrayList<>();
        for (int i = 0; i < l.size(); i++) { HeistData h = load(l.getCompound(i)); if (h != null) out.add(h); }
        return out;
    }

    static @Nullable HeistData find(ServerPlayer p, long id) {
        ListTag l = list(p);
        for (int i = 0; i < l.size(); i++) if (l.getCompound(i).getLong("id") == id) return load(l.getCompound(i));
        return null;
    }

    /** Writes (replaces or appends) this heist into the player's list. */
    void store(ServerPlayer p) {
        CompoundTag d = Rules.data(p);
        ListTag l = d.getList(KEY, Tag.TAG_COMPOUND);
        CompoundTag me = save();
        boolean done = false;
        for (int i = 0; i < l.size(); i++) if (l.getCompound(i).getLong("id") == id) { l.set(i, me); done = true; break; }
        if (!done) l.add(me);
        d.put(KEY, l);
    }

    static void remove(ServerPlayer p, long id) {
        CompoundTag d = Rules.data(p);
        ListTag l = d.getList(KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) if (l.getCompound(i).getLong("id") == id) { l.remove(i); break; }
        d.put(KEY, l);
    }
}
