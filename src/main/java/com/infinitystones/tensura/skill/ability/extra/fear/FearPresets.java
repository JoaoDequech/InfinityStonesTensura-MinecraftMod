package com.infinitystones.tensura.skill.ability.extra.fear;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Self-experience mode: presets the caster configures in advance and the entry of the caster himself into the realm.
 * Preset fields: name, scenes (list), creatures (list of "id:mode"), cats (1 sounds, 2 visual, 4 apparitions, 8 heavy),
 * intensity, freq, hud (Env flags), battery, duration (seconds, 0 = until he leaves), fog, dark, sky, weather, invites (uuids).
 */
final class FearPresets {
    static final int MAX = 24;

    static void send(ServerPlayer p) {
        if (!FearSessions.canCast(p)) return;
        PacketDistributor.sendToPlayer(p, new FearNet.Presets(new ArrayList<>(FearData.get(p.server).presets.getOrDefault(p.getUUID(), List.of()))));
    }

    static void save(ServerPlayer p, CompoundTag tag) {
        if (!FearSessions.canCast(p) || FearSessions.isVictim(p)) return;
        String name = tag.getString("name").trim();
        if (name.isEmpty() || name.length() > 24) return;
        CompoundTag clean = sanitize(tag, name);
        FearData d = FearData.get(p.server);
        List<CompoundTag> l = d.presets.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
        l.removeIf(t -> t.getString("name").equals(name));
        if (l.size() >= MAX) return;
        l.add(clean);
        d.setDirty();
        send(p);
    }

    static void delete(ServerPlayer p, String name) {
        FearData d = FearData.get(p.server);
        List<CompoundTag> l = d.presets.get(p.getUUID());
        if (l != null && l.removeIf(t -> t.getString("name").equals(name))) { d.setDirty(); send(p); }
    }

    private static CompoundTag sanitize(CompoundTag in, String name) {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.put("scenes", strings(in, "scenes", 12));
        t.put("creatures", strings(in, "creatures", 12));
        t.put("invites", strings(in, "invites", 12));
        t.putInt("cats", Mth.clamp(in.getInt("cats"), 0, 15));
        t.putInt("intensity", Mth.clamp(in.getInt("intensity"), 0, 100));
        t.putInt("freq", Mth.clamp(in.getInt("freq"), 10, 200));
        t.putInt("hud", in.getInt("hud") & (FearNet.F_METER | FearNet.F_FAKE | FearNet.F_REAL));
        t.putInt("battery", Mth.clamp(in.getInt("battery"), 10, 100));
        t.putInt("duration", Mth.clamp(in.getInt("duration"), 0, 7200));
        t.putInt("fog", Mth.clamp(in.getInt("fog"), 0, 100));
        t.putInt("dark", Mth.clamp(in.getInt("dark"), 0, 100));
        t.putInt("sky", Mth.clamp(in.getInt("sky"), 0, 3));
        t.putInt("weather", Mth.clamp(in.getInt("weather"), 0, 3));
        return t;
    }

    private static ListTag strings(CompoundTag in, String key, int max) {
        ListTag out = new ListTag();
        for (Tag e : in.getList(key, Tag.TAG_STRING)) {
            String s = e.getAsString();
            if (s.length() <= 48 && out.size() < max) out.add(StringTag.valueOf(s));
        }
        return out;
    }

    /** "Entrar eu mesmo": the caster becomes his own victim and the preset drives the AUTO director. */
    static void enter(ServerPlayer c, String presetName) {
        if (!FearSessions.canCast(c) || FearSessions.LIVE.containsKey(c.getUUID()) || c.level().dimension() == FearRealm.DIM || c.isSpectator() || !c.isAlive()) return;
        ServerLevel realm = FearRealm.level(c.server);
        if (realm == null) return;
        FearData d = FearData.get(c.server);
        CompoundTag t = null;
        for (CompoundTag x : d.presets.getOrDefault(c.getUUID(), List.of())) if (x.getString("name").equals(presetName)) t = x;
        if (t == null) t = sanitize(new CompoundTag(), "default");
        if (!t.contains("cats")) t.putInt("cats", 15);
        FearApi.Scenes sc = FearApi.scenes();
        List<String> scenes = new ArrayList<>();
        for (Tag e : t.getList("scenes", Tag.TAG_STRING)) if (sc == null || sc.ids().contains(e.getAsString())) scenes.add(e.getAsString());
        if (scenes.isEmpty() && sc != null && !sc.ids().isEmpty()) scenes.add(sc.ids().get(0));

        int slot = d.slotFor(c.getUUID());
        d.victims.put(c.getUUID(), new FearData.Victim(c.getUUID(), FearSessions.origin(c)));
        d.setDirty();
        FearLive live = new FearLive(c.getUUID(), c.getUUID(), slot);
        live.self = true;
        live.sceneSeq.addAll(scenes);
        for (Tag e : t.getList("creatures", Tag.TAG_STRING)) live.creatureList.add(e.getAsString());
        live.cats = t.getInt("cats");
        live.intensity = t.getInt("intensity");
        live.freq = Math.max(10, t.getInt("freq"));
        live.hud = t.getInt("hud");
        live.battery = Math.max(10, t.getInt("battery"));
        live.duration = t.getInt("duration") * 20;
        live.fog = t.getInt("fog"); live.dark = t.getInt("dark"); live.sky = t.getInt("sky"); live.weather = t.getInt("weather");
        live.auto = true;
        for (String cr : live.creatureList) if (cr.endsWith(":" + FearApi.HUNT)) live.hunt = true;
        for (Tag e : t.getList("invites", Tag.TAG_STRING)) {
            try {
                ServerPlayer who = c.server.getPlayerList().getPlayer(UUID.fromString(e.getAsString()));
                if (who != null) live.pendingInvites.add(who.getUUID());
            } catch (IllegalArgumentException ignored) { }
        }
        FearSessions.LIVE.put(c.getUUID(), live);
        FearNet.send(c, FearNet.ENTER, 1, 0);
        FearSessions.beginScene(live, c, live.sceneSeq.isEmpty() ? "" : live.sceneSeq.get(0));
    }

    private FearPresets() {}
}
