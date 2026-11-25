package com.infinitystones.tensura.skill.ability.extra.fear.client;

import com.infinitystones.tensura.skill.ability.extra.fear.FearNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Screens shown only to a victim, copies of vanilla ones: the "You died!" screen and the "Connection lost" screen. They never touch the real connection. */
final class FakeScreens {
    private FakeScreens() {}
}

final class FakeDeathScreen extends Screen {
    private int age;

    FakeDeathScreen() { super(Component.translatable("deathScreen.title")); }

    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        age = 0;
        int y = height / 4 + 72;
        Button a = addRenderableWidget(Button.builder(Component.translatable("deathScreen.respawn"), b -> wake()).bounds(width / 2 - 100, y, 200, 20).build());
        Button b2 = addRenderableWidget(Button.builder(Component.translatable("deathScreen.titleScreen"), b -> wake()).bounds(width / 2 - 100, y + 24, 200, 20).build());
        a.active = b2.active = false;
    }

    private void wake() {
        FearState.fakeDeath = false;
        PacketDistributor.sendToServer(new FearNet.Respawn());
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void tick() {
        age++;
        if (age == 20) for (var w : children()) if (w instanceof Button b) b.active = true;
        if (age > 880) wake();
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) { g.fillGradient(0, 0, width, height, 0x60500000, 0xA0803030); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.pose().pushPose();
        g.pose().scale(2F, 2F, 2F);
        g.drawCenteredString(font, title, width / 2 / 2, 30, 0xFFFFFFFF);
        g.pose().popPose();
        Minecraft mc = Minecraft.getInstance();
        g.drawCenteredString(font, Component.translatable("deathScreen.score").append(": ").append(Component.literal(String.valueOf(mc.player == null ? 0 : mc.player.getScore())).withStyle(net.minecraft.ChatFormatting.YELLOW)), width / 2, 85, 0xFFFFFFFF);
    }
}

final class FakeLostScreen extends Screen {
    private int left;

    FakeLostScreen(int ticks) { super(Component.translatable("disconnect.lost")); left = ticks; }

    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.toMultiplayer"), b -> end()).bounds(width / 2 - 100, height / 2 + 40, 200, 20).build());
    }

    private void end() {
        FearState.fakeLost = false;
        FearState.tear.set(40); FearState.shake(3F, 20);
        FearClient.sound(net.minecraft.sounds.SoundEvents.ENDERMAN_STARE, 0.6F, 0.8F);
        Minecraft.getInstance().setScreen(null);
    }

    @Override public void tick() { if (--left <= 0) end(); }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) { g.fill(0, 0, width, height, 0xFF1A120C); renderMenuBackground(g); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, height / 2 - 40, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("disconnect.timeout"), width / 2, height / 2 - 20, 0xFFAAAAAA);
        g.drawCenteredString(font, Component.literal("Internal Exception: io.netty.handler.timeout.ReadTimeoutException"), width / 2, height / 2, 0xFF888888);
    }
}

final class InviteScreen extends Screen {
    private final FearNet.Invite inv;

    InviteScreen(FearNet.Invite inv) { super(Component.translatable("infinity.fear.invite.title")); this.inv = inv; }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("infinity.fear.invite.accept"), b -> answer(FearNet.ACCEPT)).bounds(width / 2 - 104, height / 2 + 10, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("infinity.fear.invite.decline"), b -> answer(FearNet.DECLINE)).bounds(width / 2 + 4, height / 2 + 10, 100, 20).build());
    }

    private void answer(int a) {
        PacketDistributor.sendToServer(new FearNet.Cmd(a, inv.caster(), "", 0));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(width / 2 - 130, height / 2 - 50, width / 2 + 130, height / 2 + 44, 0xE0100608);
        g.fill(width / 2 - 130, height / 2 - 50, width / 2 + 130, height / 2 - 48, 0xFFC01830);
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, height / 2 - 40, 0xFFE0A0A0);
        g.drawCenteredString(font, Component.translatable("infinity.fear.invite.text", inv.casterName(), inv.victimName()), width / 2, height / 2 - 20, 0xFFD0C0B8);
    }
}
