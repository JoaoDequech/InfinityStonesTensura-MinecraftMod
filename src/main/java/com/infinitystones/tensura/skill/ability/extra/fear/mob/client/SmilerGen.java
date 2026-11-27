package com.infinitystones.tensura.skill.ability.extra.fear.mob.client;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

final class SmilerGen {
 static final ModelLayerLocation LAYER = new ModelLayerLocation(InfinityStones.id("fear_smiler"), "main");
 static LayerDefinition layer() {
 MeshDefinition mesh = new MeshDefinition();
 PartDefinition root = mesh.getRoot();
 PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -10F, -2.5F, 7F, 12F, 5F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
 PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 18).addBox(-3F, -5F, -3F, 6F, 5F, 6F), PartPose.offsetAndRotation(0F, -10F, 0F, 0F, 0F, 0F));
 PartDefinition mouth = head.addOrReplaceChild("mouth", CubeListBuilder.create().texOffs(18, 18).addBox(-2.5F, -1F, -3.5F, 5F, 2F, 1F), PartPose.offsetAndRotation(0F, -2F, 0F, 0F, 0F, 0F));
 PartDefinition armL = body.addOrReplaceChild("armL", CubeListBuilder.create().texOffs(24, 0).addBox(-1F, 0F, -1F, 2F, 10F, 2F), PartPose.offsetAndRotation(4.5F, -9F, 0F, 0F, 0F, 0F));
 PartDefinition foreL = armL.addOrReplaceChild("foreL", CubeListBuilder.create().texOffs(30, 0).addBox(-1F, 0F, -1F, 2F, 9F, 2F), PartPose.offsetAndRotation(0F, 10F, 0F, 0F, 0F, 0F));
 PartDefinition handL = foreL.addOrReplaceChild("handL", CubeListBuilder.create().texOffs(36, 0).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 9F, 0F, 0F, 0F, 0F));
 PartDefinition armR = body.addOrReplaceChild("armR", CubeListBuilder.create().texOffs(24, 12).addBox(-1F, 0F, -1F, 2F, 10F, 2F), PartPose.offsetAndRotation(-4.5F, -9F, 0F, 0F, 0F, 0F));
 PartDefinition foreR = armR.addOrReplaceChild("foreR", CubeListBuilder.create().texOffs(30, 12).addBox(-1F, 0F, -1F, 2F, 9F, 2F), PartPose.offsetAndRotation(0F, 10F, 0F, 0F, 0F, 0F));
 PartDefinition handR = foreR.addOrReplaceChild("handR", CubeListBuilder.create().texOffs(36, 12).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 9F, 0F, 0F, 0F, 0F));
 PartDefinition legL = root.addOrReplaceChild("legL", CubeListBuilder.create().texOffs(0, 30).addBox(-1.5F, 0F, -1.5F, 3F, 10F, 3F), PartPose.offsetAndRotation(2F, 0F, 0F, 0F, 0F, 0F));
 PartDefinition footL = legL.addOrReplaceChild("footL", CubeListBuilder.create().texOffs(10, 30).addBox(-1.5F, 0F, -2F, 3F, 2F, 4F), PartPose.offsetAndRotation(0F, 10F, 0F, 0F, 0F, 0F));
 PartDefinition legR = root.addOrReplaceChild("legR", CubeListBuilder.create().texOffs(0, 42).addBox(-1.5F, 0F, -1.5F, 3F, 10F, 3F), PartPose.offsetAndRotation(-2F, 0F, 0F, 0F, 0F, 0F));
 PartDefinition footR = legR.addOrReplaceChild("footR", CubeListBuilder.create().texOffs(10, 42).addBox(-1.5F, 0F, -2F, 3F, 2F, 4F), PartPose.offsetAndRotation(0F, 10F, 0F, 0F, 0F, 0F));
 return LayerDefinition.create(mesh, 64, 64);
 }
 static final String[] BONES = {"body", "body/head", "body/head/mouth", "body/armL", "body/armL/foreL", "body/armL/foreL/handL", "body/armR", "body/armR/foreR", "body/armR/foreR/handR", "legL", "legL/footL", "legR", "legR/footR"};
 static final String[] CLIPS = {
 "idle;60;1;0;4\nbody;0,2,0,0,0,0,0,0;30,2,0,0,0,0,0,3;60,2,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;60,-4,0,0,0,0,0,0\narmL;0,3,0,5,0,0,0,0;60,3,0,5,0,0,0,0\narmR;0,3,0,-5,0,0,0,0;60,3,0,-5,0,0,0,0",
 "walk;20;1;7;3\nbody;0,0,-3,0,0,0,0,0;10,0,3,0,0,0,0,7;20,0,-3,0,0,0,0,0\nhead;0,-5,-5,2,0,0,0,0;10,-5,5,-2,0,0,0,7;20,-5,-5,2,0,0,0,0\narmL;0,-16,0,4,0,0,0,0;10,16,0,-4,0,0,0,7;20,-16,0,4,0,0,0,0\narmR;0,16,0,-4,0,0,0,0;10,-16,0,4,0,0,0,7;20,16,0,-4,0,0,0,0\nlegL;0,-20,0,0,0,0,0,0;10,20,0,0,0,0,0,7;20,-20,0,0,0,0,0,0\nlegR;0,20,0,0,0,0,0,0;10,-20,0,0,0,0,0,7;20,20,0,0,0,0,0,0",
 "scream;40;0;0;2\nbody;0,2,0,0,0,0,0,0;10,-10,0,0,0,0,0,7;30,-10,0,0,0,0,0,0;40,2,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;10,-16,0,0,0,0,0,7;30,-16,0,0,0,0,0,0;40,-4,0,0,0,0,0,0\nmouth;0,2,0,0,0,0,0,0;10,10,0,0,0,0,0,7;30,10,0,0,0,0,0,0;40,2,0,0,0,0,0,0\narmL;0,3,0,5,0,0,0,0;10,-35,0,18,0,0,0,7;30,-35,0,18,0,0,0,0;40,3,0,5,0,0,0,0\narmR;0,3,0,-5,0,0,0,0;10,-35,0,-18,0,0,0,7;30,-35,0,-18,0,0,0,0;40,3,0,-5,0,0,0,0",
 "attack;28;0;0;2\nbody;0,2,0,0,0,0,0,0;8,8,0,0,0,0,0,7;20,8,0,0,0,0,0,0;28,2,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;8,10,0,0,0,0,0,7;20,10,0,0,0,0,0,0;28,-4,0,0,0,0,0,0\narmL;0,3,0,5,0,0,0,0;8,-50,0,-8,0,0,0,7;20,-50,0,-8,0,0,0,0;28,3,0,5,0,0,0,0\narmR;0,3,0,-5,0,0,0,0;8,-50,0,8,0,0,0,7;20,-50,0,8,0,0,0,0;28,3,0,-5,0,0,0,0",
 "vanish;24;0;0;2\nbody;0,0,0,0,0,0,0,0;12,18,0,0,0,-5,0,5;24,28,0,0,0,-7,0,0\nhead;0,-4,0,0,0,0,0,0;12,-18,0,0,0,0,0,5;24,-23,0,0,0,0,0,0\narmL;0,3,0,5,0,0,0,0;12,-45,0,12,0,0,0,5;24,-55,0,18,0,0,0,0\narmR;0,3,0,-5,0,0,0,0;12,-45,0,-12,0,0,0,5;24,-55,0,-18,0,0,0,0",
 "appear;16;0;0;1\nbody;0,18,0,0,0,-5,0,0;8,-3,0,0,0,1,0,7;16,0,0,0,0,0,0,0\nhead;0,-18,0,0,0,0,0,0;8,5,0,0,0,0,0,7;16,-4,0,0,0,0,0,0\narmL;0,-45,0,12,0,0,0,0;8,7,0,7,0,0,0,7;16,3,0,5,0,0,0,0\narmR;0,-45,0,-12,0,0,0,0;8,7,0,-7,0,0,0,7;16,3,0,-5,0,0,0,0"
 };
 static final String[] HALOS = {"2,0,-4,-3,6,255,255,255,0.9,0.15"};
 static final int HEAD = 1;
 static final float LOOK_YAW = 50F, LOOK_PITCH = 35F, SCALE = 1F, SHADOW = 0.8F;
}