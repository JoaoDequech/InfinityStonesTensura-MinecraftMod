package com.infinitystones.tensura.skill.ability.extra.fear.mob.client;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Runtime description of one generated model: bones, clips, glow billboards, look limits. */
final class Spec {
    final String id;
    final ModelLayerLocation layer;
    final String[] bones;
    final Map<String, Clip> clips = new HashMap<>();
    final float[][] halos;
    final int head;
    final float lookYaw, lookPitch, scale, shadow;
    final ResourceLocation tex, glow;

    Spec(String id, ModelLayerLocation layer, String[] bones, String[] clipData, String[] halos, int head, float lookYaw, float lookPitch, float scale, float shadow) {
        this.id = id; this.layer = layer; this.bones = bones; this.head = head;
        this.lookYaw = lookYaw; this.lookPitch = lookPitch; this.scale = scale; this.shadow = shadow;
        for (String c : clipData) { Clip k = Clip.parse(c, bones); clips.put(k.name, k); }
        this.halos = new float[halos.length][];
        for (int i = 0; i < halos.length; i++) {
            String[] f = halos[i].split(",");
            this.halos[i] = new float[f.length];
            for (int j = 0; j < f.length; j++) this.halos[i][j] = Float.parseFloat(f[j]);
        }
        this.tex = InfinityStones.id("textures/entity/fear/" + id + ".png");
        this.glow = InfinityStones.id("textures/entity/fear/" + id + "_glow.png");
    }
}
