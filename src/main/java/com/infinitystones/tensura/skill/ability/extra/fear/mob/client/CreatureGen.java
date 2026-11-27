package com.infinitystones.tensura.skill.ability.extra.fear.mob.client;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import java.util.HashMap;
import java.util.Map;

/** Base generator for creature models from compact specs. */
final class CreatureGen {
    static class PartSpec {
        final String name, parent;
        final float x, y, z, w, h, d;
        final int texU, texV;
        final float rotX, rotY, rotZ;
        PartSpec(String name, String parent, float x, float y, float z, float w, float h, float d, int texU, int texV, float rotX, float rotY, float rotZ) {
            this.name = name; this.parent = parent; this.x = x; this.y = y; this.z = z;
            this.w = w; this.h = h; this.d = d; this.texU = texU; this.texV = texV;
            this.rotX = rotX; this.rotY = rotY; this.rotZ = rotZ;
        }
    }

    static class CreatureSpec {
        final String id;
        final int texW, texH;
        final PartSpec[] parts;
        final String[] bones, clips, halos;
        final int head;
        final float lookYaw, lookPitch, scale, shadow;

        CreatureSpec(String id, int texW, int texH, PartSpec[] parts, String[] bones, String[] clips, String[] halos, int head, float lookYaw, float lookPitch, float scale, float shadow) {
            this.id = id; this.texW = texW; this.texH = texH; this.parts = parts; this.bones = bones;
            this.clips = clips; this.halos = halos; this.head = head; this.lookYaw = lookYaw;
            this.lookPitch = lookPitch; this.scale = scale; this.shadow = shadow;
        }
    }

    static LayerDefinition buildLayer(CreatureSpec spec) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> map = new HashMap<>();
        map.put("", root);
        for (PartSpec p : spec.parts) {
            PartDefinition parent = map.getOrDefault(p.parent, root);
            CubeListBuilder builder = CubeListBuilder.create().texOffs(p.texU, p.texV).addBox(-p.w/2, -p.h/2, -p.d/2, p.w, p.h, p.d);
            PartPose pose = PartPose.offsetAndRotation(p.x, p.y, p.z, p.rotX, p.rotY, p.rotZ);
            map.put(p.name, parent.addOrReplaceChild(p.name, builder, pose));
        }
        return LayerDefinition.create(mesh, spec.texW, spec.texH);
    }

    static Spec toSpec(CreatureSpec spec) {
        ModelLayerLocation layer = new ModelLayerLocation(InfinityStones.id("fear_" + spec.id), "main");
        return new Spec(spec.id, layer, spec.bones, spec.clips, spec.halos, spec.head, spec.lookYaw, spec.lookPitch, spec.scale, spec.shadow);
    }
}