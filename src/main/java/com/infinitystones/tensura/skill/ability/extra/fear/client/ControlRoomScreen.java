package com.infinitystones.tensura.skill.ability.extra.fear.client;

import com.infinitystones.tensura.skill.ability.extra.fear.FearApi;
import com.infinitystones.tensura.skill.ability.extra.fear.FearNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.infinitystones.tensura.skill.ability.extra.fear.client.FearGfx.argb;

/**
 * The control room: a full-screen, mouse + keyboard admin panel. Left: the victims with live vitals. Right: tabs for the
 * view (POV / orbit / replays / audience / groups), scenes, creatures, apparitions, effects, atmosphere, god actions and
 * self-experience presets. While watching, the middle is left free so the live view stays visible.
 */
final class ControlRoomScreen extends FearUi {
    private static final String[] TABS = {"view", "scenes", "creatures", "apparitions", "effects", "env", "god", "preset"};
    private static final int[] DURATIONS = {0, 300, 600, 1200, 1800, 3600};

    private FearNet.Panel p;
    private int tab, selIdx, victimScroll, killArm;
    private EditBox voice, presetName;
    private final CompoundTag draft = freshDraft();
    private int presetSel = -1;

    ControlRoomScreen(FearNet.Panel p) {
        super(Component.translatable("infinity.fear.ui.title"), 0xC01830);
        this.p = p;
    }

    private static CompoundTag freshDraft() {
        CompoundTag t = new CompoundTag();
        t.putString("name", "");
        t.put("scenes", new ListTag()); t.put("creatures", new ListTag()); t.put("invites", new ListTag());
        t.putInt("cats", 15); t.putInt("intensity", 60); t.putInt("freq", 100); t.putInt("hud", FearNet.F_METER); t.putInt("battery", 100); t.putInt("duration", 0);
        t.putInt("fog", 50); t.putInt("dark", 85); t.putInt("sky", 0); t.putInt("weather", 0);
        return t;
    }

    void update(FearNet.Panel np) {
        p = np;
        if (selIdx >= p.victims().size()) selIdx = Math.max(0, p.victims().size() - 1);
        boolean editing = (voice != null && voice.isFocused()) || (presetName != null && presetName.isFocused());
        if (!editing && drag == null) rebuild();
    }

    private FearNet.VInfo cur() { return p.victims().isEmpty() ? null : p.victims().get(Mth.clamp(selIdx, 0, p.victims().size() - 1)); }

    private static Component t(String k, Object... a) { return Component.translatable("infinity.fear.ui." + k, a); }

    private static Component idName(String prefix, String id) { return Component.translatableWithFallback("infinity.fear." + prefix + "." + id, id); }

    private boolean watching() { return FearState.watchMode != 0; }

    private int cx() { return watching() ? width - 318 : 222; }

    private int cw() { return watching() ? 310 : width - 230; }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        voice = new EditBox(font, 0, 0, 150, 14, Component.empty());
        voice.setMaxLength(40);
        voice.setHint(t("voice.hint"));
        presetName = new EditBox(font, 0, 0, 120, 14, Component.empty());
        presetName.setMaxLength(24);
        presetName.setHint(t("preset.name"));
        presetName.setValue(draft.getString("name"));
        presetName.setResponder(s -> draft.putString("name", s));
        addRenderableWidget(voice); addRenderableWidget(presetName);
        rebuild();
    }

    private void cmd(int action, String arg, int num) {
        FearNet.VInfo v = cur();
        if (v != null) FearClient.cmd(action, v.id(), arg, num);
    }

    @Override
    protected void layout() {
        voice.visible = voice.active = false; presetName.visible = presetName.active = false;
        int x = cx(), y = 40, w = cw();
        for (int i = 0; i < TABS.length; i++) {
            final int k = i;
            add(x + i * (w / TABS.length), 8, w / TABS.length - 3, 18, t("tab." + TABS[i]), tab == i, () -> { tab = k; rebuild(); });
        }
        // left column
        int ly = 40;
        List<FearNet.VInfo> vs = p.victims();
        for (int i = 0; i < vs.size(); i++) {
            final int k = i;
            Btn b = add(8, ly + i * 46, 200, 42, Component.empty(), i == selIdx, () -> { selIdx = k; rebuild(); });
            b.label = Component.literal(vs.get(i).name());
        }
        boolean inRealm = Minecraft.getInstance().level != null && Minecraft.getInstance().level.dimension() == com.infinitystones.tensura.skill.ability.extra.fear.FearRealm.DIM;
        if (inRealm && FearState.watchMode == 0) add(8, height - 54, 200, 16, t("booth.leave"), false, () -> { FearClient.cmd(FearNet.STOP_WATCH, new UUID(0, 0), "", 0); onClose(); });
        else add(8, height - 54, 200, 16, t("booth"), false, () -> { FearClient.cmd(FearNet.BOOTH, new UUID(0, 0), "", 0); onClose(); });
        add(8, height - 36, 200, 16, t("release_all"), false, () -> FearClient.cmd(FearNet.RELEASE_ALL, new UUID(0, 0), "", 0)).danger = true;
        add(8, height - 18, 200, 14, t("close"), false, this::onClose);
        Grid g = new Grid(x, y, w);
        FearNet.VInfo v = cur();
        switch (tab) {
            case 0 -> tabView(g, v);
            case 1 -> { for (String s : p.scenes()) { g.btn(idName("scene", s), v != null && s.equals(v.scene()), () -> cmd(FearNet.SCENE, s, 0)); } }
            case 2 -> tabCreatures(g);
            case 3 -> { for (String s : p.apparitions()) g.btn(idName("apparition", s), false, () -> cmd(FearNet.APPARITION, s, 0)); g.btn(t("random"), false, () -> cmd(FearNet.APPARITION, "", 0)); }
            case 4 -> { for (int k : FearNet.EFFECTS) g.btn(Component.translatable("infinity.fear.fx." + k), false, () -> cmd(FearNet.FX, "", k)); }
            case 5 -> tabEnv(g, v);
            case 6 -> tabGod(g, v);
            default -> tabPreset(g);
        }
    }

    private final class Grid {
        final int x0, w; int x, y, col; static final int COLS = 3;
        Grid(int x, int y, int w) { this.x0 = x; this.x = x; this.y = y; this.w = w; }
        int bw() { return (w - 6 * (COLS - 1)) / COLS; }
        Btn btn(Component label, boolean on, Runnable r) {
            Btn b = add(x0 + col * (bw() + 6), y, bw(), 18, label, on, r);
            if (++col >= COLS) { col = 0; y += 22; }
            return b;
        }
        void row() { if (col != 0) { col = 0; y += 22; } }
        void title(String key) { row(); title = t(key); y += 14; }
        Component title;
    }

    private void tabView(Grid g, FearNet.VInfo v) {
        g.btn(t("pov"), FearState.watchMode == 1, () -> cmd(FearNet.POV, "", 0));
        g.btn(t("orbit"), FearState.watchMode == 2, () -> cmd(FearNet.ORBIT, "", 0));
        g.btn(t("stop_watch"), false, () -> FearClient.cmd(FearNet.STOP_WATCH, new UUID(0, 0), "", 0));
        g.row();
        for (FearNet.Hl h : p.highlights()) {
            if (v == null || !h.victim().equals(v.id())) continue;
            g.btn(Component.translatable(h.label()).append(" · " + h.ago() + "s"), false, () -> FearClient.cmd(FearNet.REPLAY, h.victim(), "", h.index()));
        }
        g.row();
        for (FearNet.Cand c : p.candidates()) g.btn(t("invite", c.name()), false, () -> { if (v != null) FearClient.cmd(FearNet.INVITE, c.id(), v.id().toString(), 0); });
        for (FearNet.Cand c : p.audience()) g.btn(t("kick", c.name()), false, () -> FearClient.cmd(FearNet.KICK_AUDIENCE, c.id(), "", 0)).danger = true;
        g.row();
        for (FearNet.VInfo o : p.victims()) {
            if (v == null || o.id().equals(v.id())) continue;
            boolean same = o.slot() == v.slot();
            g.btn(same ? t("split", o.name()) : t("join", o.name()), same, () -> {
                if (same) FearClient.cmd(FearNet.SPLIT, o.id(), "", 0); else FearClient.cmd(FearNet.JOIN, v.id(), o.id().toString(), 0);
            });
        }
    }

    private void tabCreatures(Grid g) {
        FearNet.VInfo v = cur();
        g.btn(t("all_scare"), false, () -> cmd(FearNet.MODE, "", FearApi.SCARE));
        g.btn(t("all_killer"), v != null && (v.flags() & 4) != 0, () -> cmd(FearNet.MODE, "", FearApi.HUNT)).danger = true;
        g.btn(t("despawn_all"), false, () -> cmd(FearNet.DESPAWN, "", 0));
        g.row();
        for (String id : p.creatures()) {
            g.btn(t("spawn", idName("creature", id)), false, () -> cmd(FearNet.SPAWN, id, FearApi.SCARE));
            g.btn(t("spawn_killer", idName("creature", id)), false, () -> cmd(FearNet.SPAWN, id, FearApi.HUNT)).danger = true;
            g.btn(t("set_mode", idName("creature", id)), false, () -> cmd(FearNet.MODE, id, FearApi.HUNT));
        }
    }

    private void tabEnv(Grid g, FearNet.VInfo v) {
        int bw = g.bw() * 2 + 6;
        slider(g.x0, g.y, bw, t("fog"), 0, 100, p.fog(), val -> cmd(FearNet.ENV, "fog", val));
        slider(g.x0, g.y + 20, bw, t("dark"), 0, 100, p.dark(), val -> cmd(FearNet.ENV, "dark", val));
        g.y += 46;
        for (int i = 0; i < 4; i++) { final int k = i; g.btn(t("sky." + i), p.sky() == i, () -> cmd(FearNet.ENV, "sky", k)); }
        g.row();
        for (int i = 0; i < 4; i++) { final int k = i; g.btn(t("weather." + i), p.weather() == i, () -> cmd(FearNet.ENV, "weather", k)); }
        g.row();
        int hud = v == null ? 0 : v.hud();
        g.btn(t("hud.meter"), (hud & FearNet.F_METER) != 0, () -> cmd(FearNet.HUD, "meter", (hud & FearNet.F_METER) != 0 ? 0 : 1));
        g.btn(t("hud.fake"), (hud & FearNet.F_FAKE) != 0, () -> cmd(FearNet.HUD, "fake", (hud & FearNet.F_FAKE) != 0 ? 0 : 1));
        g.btn(t("hud.real"), (hud & FearNet.F_REAL) != 0, () -> cmd(FearNet.HUD, "real", (hud & FearNet.F_REAL) != 0 ? 0 : 1));
        g.row();
        g.btn(t("battery.kill"), false, () -> cmd(FearNet.BATTERY, "", 0)).danger = true;
        g.btn(t("battery.drain"), false, () -> cmd(FearNet.BATTERY, "", 1));
        g.btn(t("battery.full"), false, () -> cmd(FearNet.BATTERY, "", 2));
        g.row();
        g.btn(t("auto"), p.auto(), () -> cmd(FearNet.AUTO, "", p.auto() ? 0 : 1));
    }

    private void tabGod(Grid g, FearNet.VInfo v) {
        g.btn(killArm > 0 ? t("kill_confirm") : t("kill"), killArm > 0, () -> {
            if (killArm > 0) { cmd(FearNet.KILL, "", 0); killArm = 0; } else killArm = 100;
            rebuild();
        }).danger = true;
        g.btn(t("steal"), false, () -> { cmd(FearNet.STEAL, "", 0); onClose(); });
        g.btn(t("heal"), false, () -> cmd(FearNet.HEAL, "", 0));
        g.btn(t("freeze"), v != null && (v.flags() & 2) != 0, () -> cmd(FearNet.FREEZE, "", 0));
        g.btn(t("pull"), false, () -> cmd(FearNet.PULL, "", 0));
        g.row();
        voice.setX(g.x0); voice.setY(g.y + 2); voice.setWidth(g.w - 70); voice.visible = voice.active = true;
        add(g.x0 + g.w - 66, g.y, 66, 18, t("voice.send"), false, () -> { cmd(FearNet.VOICE, voice.getValue(), 0); voice.setValue(""); });
        g.y += 26;
        for (FearNet.Cand c : p.candidates()) g.btn(t("send", c.name()), false, () -> FearClient.cmd(FearNet.SEND, c.id(), "", 0));
    }

    // ------------------------------------------------------------------ presets (self experience)

    private ListTag list(String k) { return draft.getList(k, Tag.TAG_STRING); }

    private void toggleIn(String key, String value) {
        ListTag l = list(key), out = new ListTag();
        boolean had = false;
        for (Tag e : l) { if (e.getAsString().equals(value)) had = true; else out.add(e); }
        if (!had) out.add(StringTag.valueOf(value));
        draft.put(key, out);
    }

    private void tabPreset(Grid g) {
        presetName.setX(g.x0); presetName.setY(g.y + 2); presetName.setWidth(130); presetName.visible = presetName.active = true;
        add(g.x0 + 136, g.y, 70, 18, t("preset.save"), false, () -> { if (!draft.getString("name").isBlank()) PacketDistributor.sendToServer(new FearNet.PresetSave(draft.copy())); });
        add(g.x0 + 210, g.y, 70, 18, t("preset.delete"), false, () -> { if (!draft.getString("name").isBlank()) FearClient.cmd(FearNet.PRESET_DELETE, new UUID(0, 0), draft.getString("name"), 0); }).danger = true;
        add(g.x0 + 284, g.y, Math.max(90, g.w - 284), 18, t("preset.enter"), false, () -> { FearClient.cmd(FearNet.SELF_ENTER, new UUID(0, 0), draft.getString("name"), 0); onClose(); }).danger = true;
        g.y += 24;
        for (int i = 0; i < FearState.presets.size(); i++) {
            CompoundTag pr = FearState.presets.get(i);
            final int k = i;
            g.btn(Component.literal(pr.getString("name")), i == presetSel, () -> {
                presetSel = k;
                CompoundTag c = FearState.presets.get(k).copy();
                for (String key : c.getAllKeys()) draft.put(key, c.get(key));
                presetName.setValue(draft.getString("name"));
                rebuild();
            });
        }
        g.row();
        for (String s : p.scenes()) {
            int idx = -1, n = 0;
            for (Tag e : list("scenes")) { n++; if (e.getAsString().equals(s)) idx = n; }
            final String id = s;
            g.btn(idx > 0 ? Component.literal(idx + ". ").append(idName("scene", s)) : idName("scene", s), idx > 0, () -> { toggleIn("scenes", id); rebuild(); });
        }
        g.row();
        for (String s : p.creatures()) {
            String mode = "";
            for (Tag e : list("creatures")) if (e.getAsString().startsWith(s + ":")) mode = e.getAsString().substring(s.length() + 1);
            final String id = s, fm = mode;
            g.btn(idName("creature", s).copy().append(fm.isEmpty() ? "" : fm.equals("1") ? " ☠" : " ◌"), !fm.isEmpty(), () -> {
                ListTag out = new ListTag();
                for (Tag e : list("creatures")) if (!e.getAsString().startsWith(id + ":")) out.add(e);
                if (fm.isEmpty()) out.add(StringTag.valueOf(id + ":0")); else if (fm.equals("0")) out.add(StringTag.valueOf(id + ":1"));
                draft.put("creatures", out);
                rebuild();
            });
        }
        g.row();
        int cats = draft.getInt("cats");
        String[] cn = {"sounds", "visual", "apparitions", "heavy"};
        for (int i = 0; i < 4; i++) { final int bit = 1 << i; g.btn(t("cat." + cn[i]), (cats & bit) != 0, () -> { draft.putInt("cats", draft.getInt("cats") ^ bit); rebuild(); }); }
        g.row();
        int hud = draft.getInt("hud");
        g.btn(t("hud.meter"), (hud & FearNet.F_METER) != 0, () -> { draft.putInt("hud", draft.getInt("hud") ^ FearNet.F_METER); rebuild(); });
        g.btn(t("hud.fake"), (hud & FearNet.F_FAKE) != 0, () -> { draft.putInt("hud", draft.getInt("hud") ^ FearNet.F_FAKE); rebuild(); });
        g.btn(t("hud.real"), (hud & FearNet.F_REAL) != 0, () -> { draft.putInt("hud", draft.getInt("hud") ^ FearNet.F_REAL); rebuild(); });
        g.row();
        int bw = g.bw();
        slider(g.x0, g.y, bw, t("intensity"), 0, 100, draft.getInt("intensity"), v -> draft.putInt("intensity", v));
        slider(g.x0 + bw + 6, g.y, bw, t("freq"), 10, 200, draft.getInt("freq"), v -> draft.putInt("freq", v));
        slider(g.x0 + 2 * (bw + 6), g.y, bw, t("battery"), 10, 100, draft.getInt("battery"), v -> draft.putInt("battery", v));
        g.y += 20;
        slider(g.x0, g.y, bw, t("fog"), 0, 100, draft.getInt("fog"), v -> draft.putInt("fog", v));
        slider(g.x0 + bw + 6, g.y, bw, t("dark"), 0, 100, draft.getInt("dark"), v -> draft.putInt("dark", v));
        g.y += 22;
        int di = 0;
        for (int i = 0; i < DURATIONS.length; i++) if (DURATIONS[i] == draft.getInt("duration")) di = i;
        final int cdi = di;
        g.btn(t("duration", DURATIONS[di] == 0 ? t("until_leave") : Component.literal(DURATIONS[di] / 60 + " min")), false, () -> { draft.putInt("duration", DURATIONS[(cdi + 1) % DURATIONS.length]); rebuild(); });
        g.btn(t("sky.cycle", draft.getInt("sky")), false, () -> { draft.putInt("sky", (draft.getInt("sky") + 1) % 4); rebuild(); });
        g.btn(t("weather.cycle", draft.getInt("weather")), false, () -> { draft.putInt("weather", (draft.getInt("weather") + 1) % 4); rebuild(); });
        g.row();
        for (FearNet.Cand c : p.candidates()) {
            boolean inv = false;
            for (Tag e : list("invites")) if (e.getAsString().equals(c.id().toString())) inv = true;
            g.btn(t("invite", c.name()), inv, () -> { toggleIn("invites", c.id().toString()); rebuild(); });
        }
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void tick() {
        super.tick();
        if (killArm > 0 && --killArm == 0) rebuild();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!watching()) g.fill(0, 0, width, height, argb(0.62F, 0x050204));
        else { g.fill(0, 0, 216, height, argb(0.62F, 0x050204)); g.fill(width - 322, 0, width, height, argb(0.62F, 0x050204)); }
        FearGfx.vgrad(g, 0, 0, width, 34, argb(0.8F, 0x300008), argb(0F, 0x300008));
        g.drawString(font, t("title"), 10, 12, 0xFFE0A0A0, true);
        FearNet.VInfo cur = cur();
        // victim cards
        List<FearNet.VInfo> vs = p.victims();
        for (int i = 0; i < vs.size(); i++) {
            FearNet.VInfo v = vs.get(i);
            int y = 40 + i * 46;
            g.fill(10, y + 16, 10 + (int) (196 * Mth.clamp(v.health() / 20F, 0, 1)), y + 19, argb(0.9F, 0xC83030));
            float fk = Mth.clamp(v.fear() / 100F, 0, 1);
            g.fill(10, y + 22, 10 + (int) (196 * fk), y + 25, argb(0.9F, fk > 0.66F ? 0xFF3040 : 0xD0A040));
            g.drawString(font, Component.empty().append(idName("scene", v.scene())).append(" · " + v.secs() / 60 + ":" + String.format("%02d", v.secs() % 60)), 12, y + 28, 0xFFB8A8A0, false);
            String fl = ((v.flags() & 1) != 0 ? "☼ " : "○ ") + ((v.flags() & 2) != 0 ? "❄ " : "") + ((v.flags() & 4) != 0 ? "☠ " : "") + ((v.flags() & 8) != 0 ? "◉" : "") + " " + v.battery() + "%";
            g.drawString(font, fl, 120, y + 28, 0xFFC8B890, false);
            g.drawString(font, v.name(), 12, y + 4, i == selIdx ? 0xFFFFE8D0 : 0xFFD0C0B8, false);
        }
        if (vs.isEmpty()) g.drawString(font, t("no_victims"), 12, 46, 0xFF907878, false);
        int ty = 36 + 18 + (tab == 7 ? 6 : 0);
        scanlines(g, 0, 0, watching() ? 216 : width, height, 0.12F);
        drawBtns(g, mx, my, pt);
        drawToast(g, cx() + 4, height - 20);
        if (cur != null) g.drawString(font, Component.translatable("infinity.fear.ui.selected", cur.name()), cx(), height - 34, 0xFFA89890, false);
        super.render(g, mx, my, pt);
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean typing = (voice != null && voice.isFocused()) || (presetName != null && presetName.isFocused());
        if (!typing) {
            if (key == GLFW.GLFW_KEY_ESCAPE || FearClient.PANEL_KEY.matches(key, scan)) { onClose(); return true; }
            if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_8) { tab = key - GLFW.GLFW_KEY_1; rebuild(); return true; }
            if (key == GLFW.GLFW_KEY_TAB && !p.victims().isEmpty()) { selIdx = (selIdx + 1) % p.victims().size(); rebuild(); return true; }
        } else if (key == GLFW.GLFW_KEY_ESCAPE) { setFocused(null); return true; }
        if (typing && key == GLFW.GLFW_KEY_ENTER && voice.isFocused()) { cmd(FearNet.VOICE, voice.getValue(), 0); voice.setValue(""); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new FearNet.Request(false));
        Minecraft.getInstance().setScreen(null);
    }
}
