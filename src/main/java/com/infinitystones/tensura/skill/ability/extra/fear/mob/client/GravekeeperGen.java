package com.infinitystones.tensura.skill.ability.extra.fear.mob.client;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

final class GravekeeperGen {
 static final ModelLayerLocation LAYER = new ModelLayerLocation(InfinityStones.id("fear_gravekeeper"), "main");
 static LayerDefinition layer() {
 MeshDefinition mesh = new MeshDefinition();
 PartDefinition root = mesh.getRoot();
 PartDefinition hips = root.addOrReplaceChild("hips", CubeListBuilder.create().texOffs(0, 34).addBox(-3F, -2F, -2F, 6F, 5F, 4F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
 PartDefinition coat = hips.addOrReplaceChild("coat", CubeListBuilder.create().texOffs(0, 44).addBox(-4F, 0F, -3F, 8F, 12F, 6F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
 PartDefinition torso = hips.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -10F, -2.5F, 8F, 10F, 5F), PartPose.offsetAndRotation(0F, -2F, 0F, 0F, 0F, 0F));
 PartDefinition neck = torso.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(18, 18).addBox(-1.5F, -3F, -1.5F, 3F, 3F, 3F), PartPose.offsetAndRotation(0F, -10F, 0F, 0F, 0F, 0F));
 PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 18).addBox(-3F, -5F, -3F, 6F, 5F, 6F), PartPose.offsetAndRotation(0F, -3F, 0F, 0F, 0F, 0F));
 PartDefinition hat = head.addOrReplaceChild("hat", CubeListBuilder.create().texOffs(20, 18).addBox(-4F, -2F, -4F, 8F, 2F, 8F).texOffs(28, 22).addBox(-3F, -5F, -3F, 6F, 3F, 6F), PartPose.offsetAndRotation(0F, -5F, 0F, 0F, 0F, 0F));
 PartDefinition lantern = head.addOrReplaceChild("lantern", CubeListBuilder.create().texOffs(40, 18).addBox(-1.5F, -2F, -1.5F, 3F, 3F, 3F), PartPose.offsetAndRotation(0F, -3F, -4F, 0F, 0F, 0F));
 PartDefinition armL = torso.addOrReplaceChild("armL", CubeListBuilder.create().texOffs(42, 0).addBox(-1.5F, 0F, -1.5F, 3F, 9F, 3F), PartPose.offsetAndRotation(5F, -9F, 0F, 0F, 0F, 0F));
 PartDefinition foreL = armL.addOrReplaceChild("foreL", CubeListBuilder.create().texOffs(52, 0).addBox(-1F, 0F, -1F, 2F, 8F, 2F), PartPose.offsetAndRotation(0F, 9F, 0F, 0F, 0F, 0F));
 PartDefinition handL = foreL.addOrReplaceChild("handL", CubeListBuilder.create().texOffs(60, 0).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 8F, 0F, 0F, 0F, 0F));
 PartDefinition armR = torso.addOrReplaceChild("armR", CubeListBuilder.create().texOffs(42, 12).addBox(-1.5F, 0F, -1.5F, 3F, 9F, 3F), PartPose.offsetAndRotation(-5F, -9F, 0F, 0F, 0F, 0F));
 PartDefinition foreR = armR.addOrReplaceChild("foreR", CubeListBuilder.create().texOffs(52, 12).addBox(-1F, 0F, -1F, 2F, 8F, 2F), PartPose.offsetAndRotation(0F, 9F, 0F, 0F, 0F, 0F));
 PartDefinition handR = foreR.addOrReplaceChild("handR", CubeListBuilder.create().texOffs(60, 12).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 8F, 0F, 0F, 0F, 0F));
 return LayerDefinition.create(mesh, 64, 64);
 }
 static final String[] BONES = {"hips", "hips/coat", "hips/torso", "hips/torso/neck", "hips/torso/neck/head", "hips/torso/neck/head/hat", "hips/torso/neck/head/lantern", "hips/torso/armL", "hips/torso/armL/foreL", "hips/torso/armL/foreL/handL", "hips/torso/armR", "hips/torso/armR/foreR", "hips/torso/armR/foreR/handR"};
 static final String[] CLIPS = {
 "idle;60;1;0;4\ntorso;0,2,0,0,0,0,0,0;30,2,0,0,0,0,0,3;60,2,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;60,-4,0,0,0,0,0,0\narmL;0,3,0,5,0,0,0,0;60,3,0,5,0,0,0,0\narmR;0,3,0,-5,0,0,0,0;60,3,0,-5,0,0,0,0",
 "walk;20;1;7;3\nhips;0,0,-4,0,0,0,0,0;10,0,4,0,0,0,0,7;20,0,-4,0,0,0,0,0\ntorso;0,3,4,0,0,0,0,0;10,3,-4,0,0,0,0,7;20,3,4,0,0,0,0,0\nhead;0,-6,-6,2,0,0,0,0;10,-6,6,-2,0,0,0,7;20,-6,-6,2,0,0,0,0\narmL;0,-16,0,4,0,0,0,0;10,16,0,-4,0,0,0,7;20,-16,0,4,0,0,0,0\narmR;0,16,0,-4,0,0,0,0;10,-16,0,4,0,0,0,7;20,16,0,-4,0,0,0,0",
 "scream;40;0;0;2\ntorso;0,2,0,0,0,0,0,0;10,-10,0,0,0,0,0,7;30,-10,0,0,0,0,0,0;40,2,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;10,-16,0,0,0,0,0,7;30,-16,0,0,0,0,0,0;40,-4,0,0,0,0,0,0\narmL;0,3,0,5,0,0,0,0;10,-35,0,18,0,0,0,7;30,-35,0,18,0,0,0,0;40,3,0,5,0,0,0,0\narmR;0,3,0,-5,0,0,0,0;10,-35,0,-18,0,0,0,7;30,-35,0,-18,0,0,0,0;40,3,0,-5,0,0,0,0",
 "attack;28;0;0;2\ntorso;0,2,0,0,0,0,0,0;8,8,0,0,0,0,0,7;20,8,0,0,0,0,0,0;28,2,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;8,10,0,0,0,0,0,7;20,10,0,0,0,0,0,0;28,-4,0,0,0,0,0,0\narmL;0,3,0,5,0,0,0,0;8,-50,0,-8,0,0,0,7;20,-50,0,-8,0,0,0,0;28,3,0,5,0,0,0,0\narmR;0,3,0,-5,0,0,0,0;8,-50,0,8,0,0,0,7;20,-50,0,8,0,0,0,0;28,3,0,-5,0,0,0,0",
 "vanish;24;0;0;2\nhips;0,0,0,0,0,0,0,0;12,18,0,0,0,-5,0,5;24,28,0,0,0,-7,0,0\ntorso;0,2,0,0,0,0,0,0;12,28,0,0,0,0,0,5;24,33,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;12,-18,0,0,0,0,0,5;24,-23,0,0,0,0,0,0\narmL;0,3,0,5,0,0,0,0;12,-45,0,12,0,0,0,5;24,-55,0,18,0,0,0,0\narmR;0,3,0,-5,0,0,0,0;12,-45,0,-12,0,0,0,5;24,-55,0,-18,0,0,0,0",
 "appear;16;0;0;1\nhips;0,18,0,0,0,-5,0,0;8,-3,0,0,0,1,0,7;16,0,0,0,0,0,0,0\ntorso;0,28,0,0,0,0,0,0;8,-5,0,0,0,0,0,7;16,2,0,0,0,0,0,0\nhead;0,-18,0,0,0,0,0,0;8,5,0,0,0,0,0,7;16,-4,0,0,0,0,0,0\narmL;0,-45,0,12,0,0,0,0;8,7,0,7,0,0,0,7;16,3,0,5,0,0,0,0\narmR;0,-45,0,-12,0,0,0,0;8,7,0,-7,0,0,0,7;16,3,0,-5,0,0,0,0"
 };
 static final String[] HALOS = {"3,0,-5,-3,5,220,180,100,0.8,0.1"};
 static final int HEAD = 4;
 static final float LOOK_YAW = 50F, LOOK_PITCH = 35F, SCALE = 1F, SHADOW = 0.8F;
}