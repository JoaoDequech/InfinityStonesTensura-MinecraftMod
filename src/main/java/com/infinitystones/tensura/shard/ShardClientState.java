package com.infinitystones.tensura.shard;

import java.util.List;

/** Latest mini-gem data received from the server (plain data: safe to load on a dedicated server). */
public final class ShardClientState {
    private static volatile List<ShardNet.Page> pages = List.of();
    private static volatile ShardNet.ListMsg list = new ShardNet.ListMsg(0L, List.of());
    private static volatile long listAt;

    public static void clear() {
        pages = List.of();
        list = new ShardNet.ListMsg(0L, List.of());
    }

    static void pages(ShardNet.WheelMsg m) { pages = m.pages(); }

    static void list(ShardNet.ListMsg m) {
        list = m;
        listAt = System.currentTimeMillis();
    }

    /** Wheel pages of the gems the player carries. */
    public static List<ShardNet.Page> pages() { return pages; }

    public static ShardNet.ListMsg list() { return list; }

    /** Milliseconds since the list arrived (the menu animates new rows with it). */
    public static long listAge() { return System.currentTimeMillis() - listAt; }

    private ShardClientState() {}
}
