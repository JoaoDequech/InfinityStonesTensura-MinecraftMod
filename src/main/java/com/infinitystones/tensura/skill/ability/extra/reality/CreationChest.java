package com.infinitystones.tensura.skill.ability.extra.reality;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneProgress;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reality — Creation Chest. A holographic chest unfolds in front of you and opens; a creative-style picker (any item of
 * the game, any mod) appears. Pick the item and the amount: an energy beam from the sky drops it into the chest, the lid
 * closes and glowing orbs carry it to you. Left: open the picker. Right: repeat the last request.
 * The server re-checks stone + unlock, clamps the amount and only accepts components found in its own creative contents.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class CreationChest implements Ability {
    public static final CreationChest INSTANCE = new CreationChest();
    public static final String NODE = "creation_chest";
    private static final String K = "infinity.extra.chest.";
    private static final int RED = 0xE22B3B, HOT = 0xFF6A78, WHITE = 0xFFE8EA;
    private static final int MAX_STACKS = 27, MENU_TIMEOUT = 20 * 90;
    private static final int FALL = 30, ORBS = 5;

    private record Last(ItemStack stack, int count) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Last> LAST = new HashMap<>();

    @Override public Stone stone() { return Stone.REALITY; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        Session s = SESSIONS.get(p.getUUID());
        if (s != null) {
            if (!alt && s.phase == Session.MENU) { ChestNet.open(p, lastStack(p), lastCount(p)); return true; }
            return Fx.fail(p, K + "busy");
        }
        Last last = LAST.get(p.getUUID());
        Last direct = null;
        if (alt) {
            if (last == null) return Fx.fail(p, K + "none");
            direct = last;
        }
        Session ns = new Session(p, direct);
        SESSIONS.put(p.getUUID(), ns);
        Tasks.add(ns);
        return true;
    }

    private static ItemStack lastStack(ServerPlayer p) { Last l = LAST.get(p.getUUID()); return l == null ? ItemStack.EMPTY : l.stack; }
    private static int lastCount(ServerPlayer p) { Last l = LAST.get(p.getUUID()); return l == null ? 0 : l.count; }

    /** Client → server: the picker's answer (count 0 = closed without choosing). */
    static void request(ServerPlayer p, ChestNet.Request msg) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.phase != Session.MENU || s.req != null) return;
        if (msg.count() <= 0 || !allowed(p)) { s.cancel = true; return; }
        ItemStack want = ChestCatalog.resolve(p.server, msg.stack());
        if (want.isEmpty()) { Fx.bar(p, Stone.REALITY, K + "invalid"); s.cancel = true; return; }
        int count = Mth.clamp(msg.count(), 1, MAX_STACKS * Math.max(1, want.getMaxStackSize()));
        s.req = new Last(want, count);
        LAST.put(p.getUUID(), s.req);
    }

    private static boolean allowed(ServerPlayer p) {
        return Stone.REALITY.isHeldBy(p) && StoneProgress.isUnlocked(p, Stone.REALITY, NODE);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent e) { SESSIONS.clear(); LAST.clear(); }

    private static final class Session implements Tasks.Task {
        static final int SPAWN = 0, MENU = 1, DELIVER = 2, FOLDING = 3;

        final UUID owner;
        final MinecraftServer server;
        final ServerLevel level;
        final int id = FxNet.newId();
        final int[] orbIds = new int[ORBS];
        final Last direct;
        Vec3 pos = Vec3.ZERO;
        float yaw;
        int phase = SPAWN, menuAt, deliverAt, foldAt;
        boolean cancel, folded;
        volatile Last req;

        Session(ServerPlayer p, Last direct) {
            this.owner = p.getUUID();
            this.server = p.server;
            this.level = p.serverLevel();
            this.direct = direct;
            for (int i = 0; i < ORBS; i++) orbIds[i] = FxNet.newId();
            place(p);
        }

        private void place(ServerPlayer p) {
            Vec3 eye = p.getEyePosition();
            Vec3 look = p.getLookAngle();
            double hl = Math.sqrt(look.x * look.x + look.z * look.z);
            double dx = hl < 1e-4 ? 0 : look.x / hl, dz = hl < 1e-4 ? 1 : look.z / hl;
            double a = Math.toRadians(18), c = Math.cos(a), s = Math.sin(a);
            double lx = dz, lz = -dx;
            double ox = dx * c + lx * s, oz = dz * c + lz * s;
            double dist = 2.6;
            Vec3 from = new Vec3(eye.x, eye.y - 0.5, eye.z);
            Vec3 to = from.add(ox * dist, 0, oz * dist);
            HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (hit.getType() != HitResult.Type.MISS) {
                double d = Math.max(1.0, hit.getLocation().distanceTo(from) - 0.5);
                to = from.add(ox * d, 0, oz * d);
            }
            double base = eye.y - 1.1;
            BlockPos start = BlockPos.containing(to.x, Math.floor(eye.y + 0.5), to.z);
            for (int i = 0; i < 7; i++) {
                BlockPos bp = start.below(i);
                if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty() && level.getBlockState(bp.above()).getCollisionShape(level, bp.above()).isEmpty()) {
                    base = bp.getY() + level.getBlockState(bp).getCollisionShape(level, bp).max(net.minecraft.core.Direction.Axis.Y) + 0.55;
                    break;
                }
            }
            pos = new Vec3(to.x, base, to.z);
            double fx = eye.x - pos.x, fz = eye.z - pos.z;
            yaw = (float) Math.toDegrees(Math.atan2(-fx, fz));
        }

        private void holo(int op, ItemStack stack, int count, int ticks) {
            ChestNet.holo(level, pos, new ChestNet.Holo(id, op, pos.x, pos.y, pos.z, yaw, stack, count, ticks));
        }

        private void fold(int age) {
            if (folded) return;
            folded = true;
            phase = FOLDING;
            foldAt = age;
            holo(ChestNet.FOLD, ItemStack.EMPTY, 0, 12);
            for (int oid : orbIds) FxNet.kill(level, pos, oid);
            FxNet.flare(level, pos.add(0, 0.5, 0), 1.2F, HOT, 8);
            Fx.sound(level, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.45F, 1.7F);
        }

        @Override
        public boolean tick(int age) {
            ServerPlayer p = server.getPlayerList().getPlayer(owner);
            if (phase != FOLDING && (p == null || !p.isAlive() || p.level() != level || p.position().distanceToSqr(pos) > 48 * 48)) fold(age);
            if (phase == FOLDING) return age < foldAt + 12;
            switch (phase) {
                case SPAWN -> {
                    if (age == 0) spawnFx(p);
                    if (age == 8) openFx();
                    if (age == 18) {
                        if (direct != null) {
                            ItemStack st = ChestCatalog.resolve(server, direct.stack);
                            if (st.isEmpty() || !allowed(p)) { fold(age); return true; }
                            req = new Last(st, direct.count);
                            startDeliver(age);
                        } else {
                            ChestNet.open(p, lastStack(p), lastCount(p));
                            phase = MENU;
                            menuAt = age;
                        }
                    }
                }
                case MENU -> {
                    if (cancel || age - menuAt > MENU_TIMEOUT) { closeFx(); fold(age + 4); }
                    else if (req != null) startDeliver(age);
                }
                case DELIVER -> deliver(p, age - deliverAt, age);
                default -> { }
            }
            return true;
        }

        private void spawnFx(ServerPlayer p) {
            holo(ChestNet.SPAWN, ItemStack.EMPTY, 0, 0);
            Vec3 floor = pos.add(0, -0.5, 0);
            FxNet.shockwave(level, floor.add(0, 0.05, 0), 3.4F, RED, 14);
            FxNet.flare(level, pos.add(0, 0.5, 0), 1.6F, HOT, 10);
            Fx.sound(level, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 0.7F);
            Fx.sound(level, pos, SoundEvents.EVOKER_PREPARE_SUMMON, 0.35F, 1.7F);
            Fx.far(level, Fx.dust(RED, 1.0F), pos.add(0, 0.5, 0), 40, 0.7, 0.02, 48);
        }

        private void openFx() {
            holo(ChestNet.OPEN, ItemStack.EMPTY, 0, 10);
            Fx.sound(level, pos, SoundEvents.CHEST_OPEN, 0.5F, 1.3F);
            Fx.sound(level, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.4F, 1.2F);
            Fx.far(level, ParticleTypes.END_ROD, pos.add(0, 0.8, 0), 14, 0.35, 0.05, 48);
        }

        private void closeFx() {
            holo(ChestNet.CLOSE, ItemStack.EMPTY, 0, 8);
            Fx.sound(level, pos, SoundEvents.CHEST_CLOSE, 0.45F, 1.2F);
        }

        private void startDeliver(int age) {
            phase = DELIVER;
            deliverAt = age;
            ItemStack st = req.stack;
            holo(ChestNet.ITEM, st, req.count, FALL);
            Vec3 top = pos.add(0, 64, 0);
            FxNet.beam(level, top, pos.add(0, 0.5, 0), RED, 0.55F, FALL + 8);
            FxNet.spiral(level, pos.add(0, 0.2, 0), 1.1F, -8F, HOT, FALL + 4, -1);
            FxNet.runes(level, FxNet.newId(), pos.add(0, -0.45, 0), 2.7F, RED, 70, 2F);
            FxNet.runes(level, FxNet.newId(), pos.add(0, -0.42, 0), 1.7F, HOT, 70, -3F);
            Fx.sound(level, pos, SoundEvents.BEACON_ACTIVATE, 0.5F, 1.6F);
            Fx.sound(level, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, 0.6F);
        }

        private void deliver(ServerPlayer p, int t, int age) {
            if (t > 1 && t < FALL && t % 2 == 0) {
                Fx.far(level, Fx.dust(RED, 1.1F), pos.add(0, 1 + level.random.nextDouble() * 10, 0), 3, 0.25, 0, 64);
                Fx.far(level, ParticleTypes.END_ROD, pos.add(0, 1 + level.random.nextDouble() * 10, 0), 1, 0.2, 0, 64);
            }
            if (t == FALL) {
                FxNet.flare(level, pos.add(0, 0.6, 0), 1.8F, WHITE, 10);
                FxNet.shockwave(level, pos.add(0, -0.4, 0), 3.2F, RED, 12);
                FxNet.shake(level, pos, 10, 0.12F, 6);
                Fx.sound(level, pos, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 0.5F, 1.5F);
                Fx.far(level, ParticleTypes.END_ROD, pos.add(0, 0.7, 0), 24, 0.4, 0.12, 48);
            }
            if (t == FALL + 3) closeFx();
            int orbStart = FALL + 6;
            int stacks = (req.count + Math.max(1, req.stack.getMaxStackSize()) - 1) / Math.max(1, req.stack.getMaxStackSize());
            int n = Math.min(ORBS, stacks);
            int travel = 12;
            Vec3 from = pos.add(0, 0.6, 0), to = p.position().add(0, 1.0, 0);
            for (int k = 0; k < n; k++) {
                int s0 = orbStart + k * 2;
                if (t < s0 || t > s0 + travel) continue;
                double u = Mth.clamp((t - s0) / (double) travel, 0, 1);
                double e = u * u * (3 - 2 * u);
                double ang = k * Math.PI * 2 / n + t * 0.1;
                Vec3 ctrl = from.add(Math.cos(ang) * 1.5, 2.2, Math.sin(ang) * 1.5);
                double w0 = (1 - e) * (1 - e), w1 = 2 * (1 - e) * e, w2 = e * e;
                Vec3 at = from.scale(w0).add(ctrl.scale(w1)).add(to.scale(w2));
                FxNet.orb(level, orbIds[k], at, 0.28F, k % 2 == 0 ? WHITE : HOT, 3, -1, Vec3.ZERO);
                Fx.far(level, Fx.dust(HOT, 0.8F), at, 2, 0.08, 0, 48);
            }
            if (t == orbStart + 2 * (n - 1) + travel + 1) grant(p, age);
        }

        private void grant(ServerPlayer p, int age) {
            if (allowed(p)) {
                ItemStack base = req.stack;
                int left = req.count, max = Math.max(1, base.getMaxStackSize());
                boolean dropped = false;
                while (left > 0) {
                    int c = Math.min(left, max);
                    left -= c;
                    ItemStack st = base.copyWithCount(c);
                    p.getInventory().add(st);
                    if (!st.isEmpty()) {
                        ItemEntity it = new ItemEntity(level, p.getX(), p.getY() + 0.3, p.getZ(), st);
                        it.setTarget(owner);
                        it.setPickUpDelay(10);
                        it.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.15, 0.2, (level.random.nextDouble() - 0.5) * 0.15);
                        level.addFreshEntity(it);
                        dropped = true;
                    }
                }
                p.containerMenu.broadcastChanges();
                Fx.bar(p, Stone.REALITY, K + "given", req.count, base.getHoverName());
                if (dropped) Fx.barGray(p, K + "overflow");
                Vec3 at = p.position().add(0, 1.0, 0);
                FxNet.flare(level, at, 1.1F, WHITE, 8);
                FxNet.spiral(level, p.position(), 0.8F, 2F, HOT, 14, p.getId());
                Fx.sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.4F);
                Fx.sound(level, at, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4F, 1.3F);
            }
            fold(age);
        }

        @Override
        public void end() { SESSIONS.remove(owner, this); }
    }

    private CreationChest() {}
}
