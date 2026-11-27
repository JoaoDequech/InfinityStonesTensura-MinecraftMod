package com.infinitystones.tensura.skill.ability.extra.fear.mob.client;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

final class LadyGen {
    static final ModelLayerLocation LAYER = new ModelLayerLocation(InfinityStones.id("fear_lady"), "main");
    static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition hips = root.addOrReplaceChild("hips", CubeListBuilder.create().texOffs(0, 32).addBox(-3F, -2F, -2F, 6F, 5F, 4F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition torso = hips.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -10F, -2.5F, 8F, 10F, 5F).texOffs(20, 0).addBox(-5F, -8F, -3F, 10F, 3F, 1F), PartPose.offsetAndRotation(0F, -2F, 0F, 0F, 0F, 0F));
        PartDefinition neck = torso.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(16, 16).addBox(-1.5F, -3F, -1.5F, 3F, 3F, 3F), PartPose.offsetAndRotation(0F, -10F, 0F, 0F, 0F, 0F));
        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 16).addBox(-3F, -5F, -3F, 6F, 5F, 6F).texOffs(30, 16).addBox(-2.5F, -3F, -4F, 5F, 1F, 1F), PartPose.offsetAndRotation(0F, -3F, 0F, 0F, 0F, 0F));
        PartDefinition dressL = hips.addOrReplaceChild("dressL", CubeListBuilder.create().texOffs(0, 42).addBox(-4F, 0F, -3F, 8F, 12F, 6F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition armL = torso.addOrReplaceChild("armL", CubeListBuilder.create().texOffs(40, 0).addBox(-1F, 0F, -1F, 2F, 8F, 2F), PartPose.offsetAndRotation(5F, -9F, 0F, 0F, 0F, 0F));
        PartDefinition foreL = armL.addOrReplaceChild("foreL", CubeListBuilder.create().texOffs(48, 0).addBox(-1F, 0F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(0F, 8F, 0F, 0F, 0F, 0F));
        PartDefinition handL = foreL.addOrReplaceChild("handL", CubeListBuilder.create().texOffs(56, 0).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 7F, 0F, 0F, 0F, 0F));
        PartDefinition armR = torso.addOrReplaceChild("armR", CubeListBuilder.create().texOffs(40, 10).addBox(-1F, 0F, -1F, 2F, 8F, 2F), PartPose.offsetAndRotation(-5F, -9F, 0F, 0F, 0F, 0F));
        PartDefinition foreR = armR.addOrReplaceChild("foreR", CubeListBuilder.create().texOffs(48, 10).addBox(-1F, 0F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(0F, 8F, 0F, 0F, 0F, 0F));
        PartDefinition handR = foreR.addOrReplaceChild("handR", CubeListBuilder.create().texOffs(56, 10).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 7F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    static final String[] BONES = {"hips", "hips/torso", "hips/torso/neck", "hips/torso/neck/head", "hips/dressL", "hips/torso/armL", "hips/torso/armL/foreL", "hips/torso/armL/foreL/handL", "hips/torso/armR", "hips/torso/armR/foreR", "hips/torso/armR/foreR/handR"};
    static final String[] CLIPS = {
        "idle;60;1;0;4\ntorso;0,2,0,0,0,0,0,0;30,2,0,0,0,0,0,3;60,2,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;60,-4,0,0,0,0,0,0\narmL;0,3,0,5,0,0,0,0;60,3,0,5,0,0,0,0\narmR;0,3,0,-5,0,0,0,0;60,3,0,-5,0,0,0,0",
        "walk;20;1;7;3\nhips;0,0,-4,0,0,0,0,0;10,0,4,0,0,0,0,7;20,0,-4,0,0,0,0,0\ntorso;0,3,4,0,0,0,0,0;10,3,-4,0,0,0,0,7;20,3,4,0,0,0,0,0\nhead;0,-6,-6,2,0,0,0,0;10,-6,6,-2,0,0,0,7;20,-6,-6,2,0,0,0,0\narmL;0,-18,0,4,0,0,0,0;10,18,0,-4,0,0,0,7;20,-18,0,4,0,0,0,0\narmR;0,18,0,-4,0,0,0,0;10,-18,0,4,0,0,0,7;20,18,0,-4,0,0,0,0",
        "scream;40;0;0;2\ntorso;0,2,0,0,0,0,0,0;10,-12,0,0,0,0,0,7;30,-12,0,0,0,0,0,0;40,2,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;10,-20,0,0,0,0,0,7;30,-20,0,0,0,0,0,0;40,-4,0,0,0,0,0,0\narmL;0,3,0,5,0,0,0,0;10,-40,0,20,0,0,0,7;30,-40,0,20,0,0,0,0;40,3,0,5,0,0,0,0\narmR;0,3,0,-5,0,0,0,0;10,-40,0,-20,0,0,0,7;30,-40,0,-20,0,0,0,0;40,3,0,-5,0,0,0,0",
        "attack;28;0;0;2\ntorso;0,2,0,0,0,0,0,0;8,10,0,0,0,0,0,7;20,10,0,0,0,0,0,0;28,2,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;8,12,0,0,0,0,0,7;20,12,0,0,0,0,0,0;28,-4,0,0,0,0,0,0\narmL;0,3,0,5,0,0,0,0;8,-60,0,-10,0,0,0,7;20,-60,0,-10,0,0,0,0;28,3,0,5,0,0,0,0\narmR;0,3,0,-5,0,0,0,0;8,-60,0,10,0,0,0,7;20,-60,0,10,0,0,0,0;28,3,0,-5,0,0,0,0",
        "vanish;24;0;0;2\nhips;0,0,0,0,0,0,0,0;12,20,0,0,0,-6,0,5;24,30,0,0,0,-8,0,0\ntorso;0,2,0,0,0,0,0,0;12,30,0,0,0,0,0,5;24,35,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;12,-20,0,0,0,0,0,5;24,-25,0,0,0,0,0,0\narmL;0,3,0,5,0,0,0,0;12,-50,0,15,0,0,0,5;24,-60,0,20,0,0,0,0\narmR;0,3,0,-5,0,0,0,0;12,-50,0,-15,0,0,0,5;24,-60,0,-20,0,0,0,0",
        "appear;16;0;0;1\nhips;0,20,0,0,0,-6,0,0;8,-4,0,0,0,1,0,7;16,0,0,0,0,0,0,0\ntorso;0,30,0,0,0,0,0,0;8,-6,0,0,0,0,0,7;16,2,0,0,0,0,0,0\nhead;0,-20,0,0,0,0,0,0;8,6,0,0,0,0,0,7;16,-4,0,0,0,0,0,0\narmL;0,-50,0,15,0,0,0,0;8,8,0,8,0,0,0,7;16,3,0,5,0,0,0,0\narmR;0,-50,0,-15,0,0,0,0;8,8,0,-8,0,0,0,7;16,3,0,-5,0,0,0,0"
    };
    static final String[] HALOS = {"3,0,-4,-3,5,200,180,220,0.7,0.1"};
    static final int HEAD = 3;
    static final float LOOK_YAW = 50F, LOOK_PITCH = 35F, SCALE = 1F, SHADOW = 0.7F;
}