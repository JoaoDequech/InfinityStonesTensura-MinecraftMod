package com.infinitystones.tensura.skill.ability.extra.fear;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Network of the fear realm. The server decides what happens and only sends "what to show" ({@link Event}, {@link Env});
 * the control room talks back with one generic {@link Cmd}. Nothing here names the caster to the victim.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class FearNet {
    // event kinds (server -> victim client; also the "Effects" tab of the control room)
    public static final int ENTER = 1, EXIT = 2, LAND = 3, SWAP = 4,
            JUMPSCARE = 13, LOUD = 14, FAKE_DEATH = 15, FAKE_LAVA = 16, VOID_FALL = 17, LIGHTS_OUT = 18, FOOTSTEPS = 19, WHISPER = 20,
            FAKE_CHAT = 21, INV_EMPTY = 22, DOPPEL = 23, CREEPER = 24, FLIP = 25, PULSE = 26, LOST = 27, WAKE_FLASH = 29,
            FLASH_OFF = 30, FLASH_FLICKER = 31, BLACKOUT = 32, LIGHTNING = 33, EYES = 34, SCREEN_TEAR = 35, HEARTBEAT = 36, SHOCK = 37;
    /** Effects tab order. */
    public static final int[] EFFECTS = {FLASH_OFF, FLASH_FLICKER, BLACKOUT, LIGHTNING, WHISPER, FOOTSTEPS, FAKE_CHAT, FAKE_DEATH, FAKE_LAVA, VOID_FALL,
            INV_EMPTY, FLIP, LOST, DOPPEL, HEARTBEAT, SCREEN_TEAR, JUMPSCARE, LOUD, EYES, CREEPER, PULSE, LIGHTS_OUT};

    // control room commands (client -> server)
    public static final int SEND = 0, SCENE = 1, SPAWN = 2, DESPAWN = 3, MODE = 4, APPARITION = 5, FX = 6, ENV = 7, AUTO = 8, POV = 9, ORBIT = 10,
            STOP_WATCH = 11, KILL = 12, STEAL = 13, HEAL = 14, FREEZE = 15, PULL = 16, RELEASE_ALL = 17;

    public record Event(int kind, int seed, int a, int b, double x, double y, double z, String s) implements CustomPacketPayload {
        public static final Type<Event> TYPE = new Type<>(InfinityStones.id("fear_event"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Event> CODEC = StreamCodec.of((buf, m) -> {
            buf.writeVarInt(m.kind); buf.writeInt(m.seed); buf.writeVarInt(m.a); buf.writeVarInt(m.b);
            buf.writeDouble(m.x); buf.writeDouble(m.y); buf.writeDouble(m.z); buf.writeUtf(m.s, 64);
        }, buf -> new Event(buf.readVarInt(), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readUtf(64)));
        @Override public Type<Event> type() { return TYPE; }
    }

    /** Atmosphere of the victim's nightmare. sky: 0 blood moon, 1 black void, 2 eclipse, 3 red storm. weather: 0 clear, 1 blood rain, 2 ash storm, 3 thunderstorm. */
    public record Env(int fog, int dark, int sky, int weather, int fear, boolean frozen) implements CustomPacketPayload {
        public static final Type<Env> TYPE = new Type<>(InfinityStones.id("fear_env"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Env> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Env::fog, ByteBufCodecs.VAR_INT, Env::dark, ByteBufCodecs.VAR_INT, Env::sky, ByteBufCodecs.VAR_INT, Env::weather,
                ByteBufCodecs.VAR_INT, Env::fear, ByteBufCodecs.BOOL, Env::frozen, Env::new);
        @Override public Type<Env> type() { return TYPE; }
    }

    /** flags: 1 flashlight on, 2 frozen, 4 killer creature, 8 watched by me. */
    public record VInfo(UUID id, String name, float health, int fear, String scene, int secs, int flags) {}

    public record Cand(UUID id, String name) {}

    public record Panel(List<VInfo> victims, List<Cand> candidates, boolean auto, List<String> scenes, List<String> creatures, List<String> apparitions, int fog, int dark, int sky, int weather)
            implements CustomPacketPayload {
        public static final Type<Panel> TYPE = new Type<>(InfinityStones.id("fear_panel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Panel> CODEC = StreamCodec.of((buf, m) -> {
            buf.writeVarInt(m.victims.size());
            for (VInfo v : m.victims) { buf.writeUUID(v.id); buf.writeUtf(v.name, 40); buf.writeFloat(v.health); buf.writeVarInt(v.fear); buf.writeUtf(v.scene, 40); buf.writeVarInt(v.secs); buf.writeVarInt(v.flags); }
            buf.writeVarInt(m.candidates.size());
            for (Cand c : m.candidates) { buf.writeUUID(c.id); buf.writeUtf(c.name, 40); }
            buf.writeBoolean(m.auto);
            for (List<String> l : List.of(m.scenes, m.creatures, m.apparitions)) { buf.writeVarInt(l.size()); for (String s : l) buf.writeUtf(s, 40); }
            buf.writeVarInt(m.fog); buf.writeVarInt(m.dark); buf.writeVarInt(m.sky); buf.writeVarInt(m.weather);
        }, buf -> {
            List<VInfo> vs = new ArrayList<>();
            for (int i = 0, n = Math.min(buf.readVarInt(), 64); i < n; i++) vs.add(new VInfo(buf.readUUID(), buf.readUtf(40), buf.readFloat(), buf.readVarInt(), buf.readUtf(40), buf.readVarInt(), buf.readVarInt()));
            List<Cand> cs = new ArrayList<>();
            for (int i = 0, n = Math.min(buf.readVarInt(), 256); i < n; i++) cs.add(new Cand(buf.readUUID(), buf.readUtf(40)));
            boolean auto = buf.readBoolean();
            List<List<String>> ls = new ArrayList<>();
            for (int k = 0; k < 3; k++) { List<String> l = new ArrayList<>(); for (int i = 0, n = Math.min(buf.readVarInt(), 64); i < n; i++) l.add(buf.readUtf(40)); ls.add(l); }
            return new Panel(vs, cs, auto, ls.get(0), ls.get(1), ls.get(2), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        });
        @Override public Type<Panel> type() { return TYPE; }
    }

    /** mode 0 = stopped, 1 = through their eyes, 2 = free orbit. */
    public record Watch(int mode, int entityId, String name) implements CustomPacketPayload {
        public static final Type<Watch> TYPE = new Type<>(InfinityStones.id("fear_watch"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Watch> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Watch::mode, ByteBufCodecs.VAR_INT, Watch::entityId, ByteBufCodecs.STRING_UTF8, Watch::name, Watch::new);
        @Override public Type<Watch> type() { return TYPE; }
    }

    /** The control room opens (true) or closes (false); while open the server streams {@link Panel} updates. */
    public record Request(boolean open) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(InfinityStones.id("fear_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Request::open, Request::new);
        @Override public Type<Request> type() { return TYPE; }
    }

    public record Cmd(int action, UUID target, String arg, int num) implements CustomPacketPayload {
        public static final Type<Cmd> TYPE = new Type<>(InfinityStones.id("fear_cmd"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Cmd> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Cmd::action, UUIDUtil.STREAM_CODEC, Cmd::target, ByteBufCodecs.STRING_UTF8, Cmd::arg, ByteBufCodecs.VAR_INT, Cmd::num, Cmd::new);
        @Override public Type<Cmd> type() { return TYPE; }
    }

    /** Victim -> server: the flashlight was switched. */
    public record Flash(boolean on) implements CustomPacketPayload {
        public static final Type<Flash> TYPE = new Type<>(InfinityStones.id("fear_flash"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Flash> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Flash::on, Flash::new);
        @Override public Type<Flash> type() { return TYPE; }
    }

    /** The victim pressed "Respawn" on the fake death screen. */
    public record Respawn() implements CustomPacketPayload {
        public static final Type<Respawn> TYPE = new Type<>(InfinityStones.id("fear_respawn"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Respawn> CODEC = StreamCodec.unit(new Respawn());
        @Override public Type<Respawn> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1").optional();
        r.playToClient(Event.TYPE, Event.CODEC, (m, ctx) -> ctx.enqueueWork(() -> com.infinitystones.tensura.skill.ability.extra.fear.client.FearClient.event(m)));
        r.playToClient(Env.TYPE, Env.CODEC, (m, ctx) -> ctx.enqueueWork(() -> com.infinitystones.tensura.skill.ability.extra.fear.client.FearClient.env(m)));
        r.playToClient(Panel.TYPE, Panel.CODEC, (m, ctx) -> ctx.enqueueWork(() -> com.infinitystones.tensura.skill.ability.extra.fear.client.FearClient.panel(m)));
        r.playToClient(Watch.TYPE, Watch.CODEC, (m, ctx) -> ctx.enqueueWork(() -> com.infinitystones.tensura.skill.ability.extra.fear.client.FearClient.watch(m)));
        r.playToServer(Request.TYPE, Request.CODEC, (m, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer p) FearSessions.request(p, m.open()); }));
        r.playToServer(Cmd.TYPE, Cmd.CODEC, (m, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer p) FearSessions.cmd(p, m); }));
        r.playToServer(Flash.TYPE, Flash.CODEC, (m, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer p) FearSessions.flash(p, m.on()); }));
        r.playToServer(Respawn.TYPE, Respawn.CODEC, (m, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer p) FearSessions.respawn(p); }));
    }

    public static void send(ServerPlayer to, Event e) { PacketDistributor.sendToPlayer(to, e); }

    public static void send(ServerPlayer to, int kind, int a, int b) { send(to, new Event(kind, to.getRandom().nextInt(), a, b, 0, 0, 0, "")); }

    public static void send(ServerPlayer to, int kind, int a, int b, double x, double y, double z) { send(to, new Event(kind, to.getRandom().nextInt(), a, b, x, y, z, "")); }

    private FearNet() {}
}
