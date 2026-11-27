package com.infinitystones.tensura.skill.ability.extra.fear.mob.client;

import com.infinitystones.tensura.skill.ability.extra.fear.mob.FearEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

import java.util.Arrays;

/** Generic clip-driven model: crossfades the clip chosen from the entity's action or gait, then adds procedural touches. */
final class FearModel extends EntityModel<FearEntity> {
    final ModelPart root;
    final ModelPart[] parts;
    final Spec spec;
    private final float[] pose;
    private static final float DEG = (float) (Math.PI / 180.0);

    FearModel(ModelPart root, Spec spec) {
        super(RenderType::entityCutoutNoCull);
        this.root = root; this.spec = spec;
        this.parts = new ModelPart[spec.bones.length];
        for (int i = 0; i < parts.length; i++) {
            ModelPart p = root;
            for (String seg : spec.bones[i].split("/")) p = p.getChild(seg);
            parts[i] = p;
        }
        this.pose = new float[parts.length * 6];
    }

    ModelPart part(String name) {
        for (int i = 0; i < parts.length; i++) if (spec.bones[i].equals(name) || spec.bones[i].endsWith("/" + name)) return parts[i];
        return null;
    }

    private String choose(FearEntity e, float limbAmount) {
        int a = e.action();
        if (a > 0 && a < FearEntity.ACTION_CLIPS.length) {
            String n = FearEntity.ACTION_CLIPS[a];
            if (spec.clips.containsKey(n)) return n;
        }
        if (limbAmount > .04F) {
            if (e.climbing() && spec.clips.containsKey("climb")) return "climb";
            if (e.fast() && spec.clips.containsKey("sprint")) return "sprint";
            if (spec.clips.containsKey("walk")) return "walk";
        }
        return "idle";
    }

    private float clipTime(Clip c, FearEntity.Anim an, float start, float age, float limbSwing) {
        if (c.period > 0) return (limbSwing / c.period % 1F + 1F) % 1F * c.length;
        float t = age - start;
        float q = Procedurals.quantum(spec.id);
        return q > 0 ? (float) Math.floor(t / q) * q : t;
    }

    @Override
    public void setupAnim(FearEntity e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        Arrays.fill(pose, 0F);
        FearEntity.Anim an = e.anim;
        String want = choose(e, limbAmount);
        if (!want.equals(an.cur)) {
            an.prev = an.cur; an.prevStart = an.curStart;
            an.cur = want; an.curStart = age; an.blendStart = age;
            Clip nc = spec.clips.get(want);
            an.blendLen = nc != null ? nc.blend : 4F;
            if (an.prev == null) an.blendLen = 0F;
        }
        float bw = an.blendLen <= 0F ? 1F : Mth.clamp((age - an.blendStart) / an.blendLen, 0F, 1F);
        bw = bw * bw * (3 - 2 * bw);
        Clip cur = spec.clips.get(an.cur);
        Clip prev = an.prev != null ? spec.clips.get(an.prev) : null;
        if (prev != null && bw < 1F) prev.sample(clipTime(prev, an, an.prevStart, age, limbSwing), pose, 1F - bw);
        if (cur != null) cur.sample(clipTime(cur, an, an.curStart, age, limbSwing), pose, prev != null ? bw : 1F);

        Procedurals.apply(spec, this, e, age, pose);

        int act = e.action();
        float look = act == FearEntity.A_SCREAM || act == FearEntity.A_ATTACK || act == FearEntity.A_KILL || act == FearEntity.A_STARE
                || act == FearEntity.A_FROZEN || act == FearEntity.A_VANISH || act == FearEntity.A_EMERGE ? 0F : 1F;
        for (int i = 0; i < parts.length; i++) {
            ModelPart p = parts[i];
            int o = i * 6;
            p.xRot += pose[o] * DEG; p.yRot += pose[o + 1] * DEG; p.zRot += pose[o + 2] * DEG;
            p.x += pose[o + 3]; p.y -= pose[o + 4]; p.z += pose[o + 5];
        }
        if (spec.head >= 0 && look > 0F) {
            ModelPart h = parts[spec.head];
            h.yRot += Mth.clamp(headYaw, -spec.lookYaw, spec.lookYaw) * DEG * look;
            h.xRot += Mth.clamp(headPitch, -spec.lookPitch, spec.lookPitch) * DEG * look;
        }
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, int color) {
        root.render(pose, vc, light, overlay, color);
    }
}
