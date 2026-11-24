package com.infinitystones.tensura.client.wheel;

import com.infinitystones.tensura.wheel.WheelPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Optional Xaero's World Map link of the destination menu. The map is opened by reflection (nothing breaks without the
 * mod) and, while it is on screen because of us, a right click on it does not open Xaero's own menu: the clicked block is
 * sent as a normal destination request instead (see mixin XaeroMapMixin). The server validates the request like any other.
 */
public final class XaeroPick {
    private static final String MAP_SCREEN = "xaero.map.gui.GuiMap";
    private static int stone = -1;
    private static String node = "";
    private static boolean movesTarget;

    public static boolean available() {
        ModList mods = ModList.get();
        return mods != null && mods.isLoaded("xaeroworldmap");
    }

    /** Opens the world map for picking a destination; false if it could not be opened. */
    public static boolean open(int stoneOrdinal, String nodeId, boolean movesTargetMode) {
        Minecraft mc = Minecraft.getInstance();
        if (!available() || mc.player == null) return false;
        try {
            Object session = Class.forName("xaero.map.WorldMapSession").getMethod("getCurrentSession").invoke(null);
            if (session == null || !(Boolean) session.getClass().getMethod("isUsable").invoke(session)) return false;
            Object processor = session.getClass().getMethod("getMapProcessor").invoke(session);
            Class<?> screenClass = Class.forName(MAP_SCREEN);
            Constructor<?> ctor = null;
            for (Constructor<?> c : screenClass.getConstructors()) if (c.getParameterCount() == 4) ctor = c;
            if (ctor == null) return false;
            Entity camera = mc.getCameraEntity();
            Screen map = (Screen) ctor.newInstance(null, null, processor, camera);
            stone = stoneOrdinal;
            node = nodeId;
            movesTarget = movesTargetMode;
            mc.setScreen(map);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            stone = -1;
            return false;
        }
    }

    /** True while a Xaero map opened by {@link #open} is the current screen. */
    public static boolean active() {
        if (stone < 0) return false;
        Screen s = Minecraft.getInstance().screen;
        if (s != null && s.getClass().getName().equals(MAP_SCREEN)) return true;
        stone = -1;
        return false;
    }

    /** Called by the mixin on a right click of the map: x / z are in the dimension shown by the map. */
    public static void picked(int x, int z, ResourceKey<Level> dim) {
        if (!active() || dim == null) return;
        WheelPayloads.Destination dest = new WheelPayloads.Destination(stone, node, dim.location().toString(), false, x + 0.5, 0, z + 0.5, true);
        boolean aim = movesTarget;
        stone = -1;
        Minecraft.getInstance().setScreen(null);
        if (aim) AimMode.startDestination(dest); else PacketDistributor.sendToServer(dest);
    }

    private XaeroPick() {}
}
