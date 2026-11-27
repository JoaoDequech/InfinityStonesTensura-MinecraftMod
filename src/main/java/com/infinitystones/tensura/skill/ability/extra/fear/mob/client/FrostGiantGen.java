package com.infinitystones.tensura.skill.ability.extra.fear.mob.client;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

final class FrostGiantGen {
 static final ModelLayerLocation LAYER = new ModelLayerLocation(InfinityStones.id("fear_frostgiant"), "main");
 static LayerDefinition layer() {
 MeshDefinition mesh = new MeshDefinition();
 PartDefinition root = mesh.getRoot();
 PartDefinition hips = root.addOrReplaceChild("hips", CubeListBuilder.create().texOffs(0, 40).addBox(-5F, -3F, -3F, 10F, 7F, 6F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
 PartDefinition torso = hips.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).addBox(-6F, -14F, -3.5F, 12F, 14F, 7F).texOffs(28, 0).addBox(-6.5F, -12F, -4F, 13F, 3F, 1F), PartPose.offsetAndRotation(0F, -3F, 0F, 0F, 0F, 0F));
 PartDefinition neck = torso.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(20, 20).addBox(-2F, -4F, -2F, 4F, 4F, 4F), PartPose.offsetAndRotation(0F, -14F, 0F, 0F, 0F, 0F));
 PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 20).addBox(-4F, -7F, -4F, 8F, 7F, 8F), PartPose.offsetAndRotation(0F, -4F, 0F, 0F, 0F, 0F));
 PartDefinition hornL = head.addOrReplaceChild("hornL", CubeListBuilder.create().texOffs(32, 20).addBox(-1F, -4F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(3F, -6F, 0F, 0F, 0F, 0.4F));
 PartDefinition hornR = head.addOrReplaceChild("hornR", CubeListBuilder.create().texOffs(40, 20).addBox(-1F, -4F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(-3F, -6F, 0F, 0F, 0F, -0.4F));
 PartDefinition armL = torso.addOrReplaceChild("armL", CubeListBuilder.create().texOffs(44, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F), PartPose.offsetAndRotation(7.5F, -13F, 0F, 0F, 0F, 0F));
 PartDefinition foreL = armL.addOrReplaceChild("foreL", CubeListBuilder.create().texOffs(56, 0).addBox(-1.5F, 0F, -1.5F, 3F, 11F, 3F), PartPose.offsetAndRotation(0F, 12F, 0F, 0F, 0F, 0F));
 PartDefinition handL = foreL.addOrReplaceChild("handL", CubeListBuilder.create().texOffs(60, 12).addBox(-2F, 0F, -2F, 4F, 3F, 4F), PartPose.offsetAndRotation(0F, 11F, 0F, 0F, 0F, 0F));
 PartDefinition armR = torso.addOrReplaceChild("armR", CubeListBuilder.create().texOffs(44, 16).addBox(-2F, 0F, -2F, 4F, 12F, 4F), PartPose.offsetAndRotation(-7.5F, -13F, 0F, 0F, 0F, 0F));
 PartDefinition foreR = armR.addOrReplaceChild("foreR", CubeListBuilder.create().texOffs(56, 16).addBox(-1.5F, 0F, -1.5F, 3F, 11F, 3F), PartPose.offsetAndRotation(0F, 12F, 0F, 0F, 0F, 0F));
 PartDefinition handR = foreR.addOrReplaceChild("handR", CubeListBuilder.create().texOffs(60, 28).addBox(-2F, 0F, -2F, 4F, 3F, 4F), PartPose.offsetAndRotation(0F, 11F, 0F, 0F, 0F, 0F));
 PartDefinition legL = hips.addOrReplaceChild("legL", CubeListBuilder.create().texOffs(0, 54).addBox(-2.5F, 0F, -2.5F, 5F, 14F, 5F), PartPose.offsetAndRotation(3F, 0F, 0F, 0F, 0F, 0F));
 PartDefinition footL = legL.addOrReplaceChild("footL", CubeListBuilder.create().texOffs(16, 54).addBox(-2.5F, 0F, -3F, 5F, 3F, 6F), PartPose.offsetAndRotation(0F, 14F, 0F, 0F, 0F, 0F));
 PartDefinition legR = hips.addOrReplaceChild("legR", CubeListBuilder.create().texOffs(0, 68).addBox(-2.5F, 0F, -2.5F, 5F, 14F, 5F), PartPose.offsetAndRotation(-3F, 0F, 0F, 0F, 0F, 0F));
 PartDefinition footR = legR.addOrReplaceChild("footR", CubeListBuilder.create().texOffs(16, 68).addBox(-2.5F, 0F, -3F, 5F, 3F, 6F), PartPose.offsetAndRotation(0F, 14F, 0F, 0F, 0F, 0F));
 return LayerDefinition.create(mesh, 64, 128);
 }
 static final String[] BONES = {"hips", "hips/torso", "hips/torso/neck", "hips/torso/neck/head", "hips/torso/neck/head/hornL", "hips/torso/neck/head/hornR", "hips/torso/armL", "hips/torso/armL/foreL", "hips/torso/armL/foreL/handL", "hips/torso/armR", "hips/torso/armR/foreR", "hips/torso/armR/foreR/handR", "hips/legL", "hips/legL/footL", "hips/legR", "hips/legR/footR"};
 static final String[] CLIPS = {
 "idle;60;1;0;4\ntorso;0,2,0,0,0,0,0,0;30,2,0,0,0,0,0,3;60,2,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;60,-4,0,0,0,0,0,0\narmL;0,3,0,5,0,0,0,0;60,3,0,5,0,0,0,0\narmR;0,3,0,-5,0,0,0,0;60,3,0,-5,0,0,0,0",
 "walk;20;1;7;3\nhips;0,0,-4,0,0,0,0,0;10,0,4,0,0,0,0,7;20,0,-4,0,0,0,0,0\ntorso;0,3,4,0,0,0,0,0;10,3,-4,0,0,0,0,7;20,3,4,0,0,0,0,0\nhead;0,-6,-6,2,0,0,0,0;10,-6,6,-2,0,0,0,7;20,-6,-6,2,0,0,0,0\narmL;0,-14,0,4,0,0,0,0;10,14,0,-4,0,0,0,7;20,-14,0,4,0,0,0,0\narmR;0,14,0,-4,0,0,0,0;10,-14,0,4,0,0,0,7;20,14,0,-4,0,0,0,0\nlegL;0,-18,0,0,0,0,0,0;10,18,0,0,0,0,0,7;20,-18,0,0,0,0,0,0\nlegR;0,18,0,0,0,0,0,0;10,-18,0,0,0,0,0,7;20,18,0,0,0,0,0,0",
 "scream;40;0;0;2\ntorso;0,2,0,0,0,0,0,0;10,-10,0,0,0,0,0,7;30,-10,0,0,0,0,0,0;40,2,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;10,-16,0,0,0,0,0,7;30,-16,0,0,0,0,0,0;40,-4,0,0,0,0,0,0\narmL;0,3,0,5,0,0,0,0;10,-35,0,18,0,0,0,7;30,-35,0,18,0,0,0,0;40,3,0,5,0,0,0,0\narmR;0,3,0,-5,0,0,0,0;10,-35,0,-18,0,0,0,7;30,-35,0,-18,0,0,0,0;40,3,0,-5,0,0,0,0",
 "attack;28;0;0;2\ntorso;0,2,0,0,0,0,0,0;8,8,0,0,0,0,0,7;20,8,0,0,0,0,0,0;28,2,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;8,10,0,0,0,0,0,7;20,10,0,0,0,0,0,0;28,-4,0,0,0,0,0,0\narmL;0,3,0,5,0,0,0,0;8,-50,0,-8,0,0,0,7;20,-50,0,-8,0,0,0,0;28,3,0,5,0,0,0,0\narmR;0,3,0,-5,0,0,0,0;8,-50,0,8,0,0,0,7;20,-50,0,8,0,0,0,0;28,3,0,-5,0,0,0,0",
 "vanish;24;0;0;2\nhips;0,0,0,0,0,0,0,0;12,18,0,0,0,-5,0,5;24,28,0,0,0,-7,0,0\ntorso;0,2,0,0,0,0,0,0;12,28,0,0,0,0,0,5;24,33,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;12,-18,0,0,0,0,0,5;24,-23,0,0,0,0,0,0\narmL;0,3,0,5,0,0,0,0;12,-45,0,12,0,0,0,5;24,-55,0,18,0,0,0,0\narmR;0,3,0,-5,0,0,0,0;12,-45,0,-12,0,0,0,5;24,-55,0,-18,0,0,0,0",
 "appear;16;0;0;1\nhips;0,18,0,0,0,-5,0,0;8,-3,0,0,0,1,0,7;16,0,0,0,0,0,0,0\ntorso;0,28,0,0,0,0,0,0;8,-5,0,0,0,0,0,7;16,2,0,0,0,0,0,0\nhead;0,-18,0,0,0,0,0,0;8,5,0,0,0,0,0,7;16,-4,0,0,0,0,0,0\narmL;0,-45,0,12,0,0,0,0;8,7,0,7,0,0,0,7;16,3,0,5,0,0,0,0\narmR;0,-45,0,-12,0,0,0,0;8,7,0,-7,0,0,0,7;16,3,0,-5,0,0,0,0"
 };
 static final String[] HALOS = {"3,0,-5,-4,7,180,220,255,0.85,0.1"};
 static final int HEAD = 3;
 static final float LOOK_YAW = 55F, LOOK_PITCH = 40F, SCALE = 1.5F, SHADOW = 1.5F;
}