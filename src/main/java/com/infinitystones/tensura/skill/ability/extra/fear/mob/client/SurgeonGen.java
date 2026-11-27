package com.infinitystones.tensura.skill.ability.extra.fear.mob.client;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

final class SurgeonGen {
 static final ModelLayerLocation LAYER = new ModelLayerLocation(InfinityStones.id("fear_surgeon"), "main");
 static LayerDefinition layer() {
 MeshDefinition mesh = new MeshDefinition();
 PartDefinition root = mesh.getRoot();
 PartDefinition hips = root.addOrReplaceChild("hips", CubeListBuilder.create().texOffs(0, 36).addBox(-3F, -2F, -2F, 6F, 5F, 4F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
 PartDefinition torso = hips.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -11F, -2.5F, 9F, 11F, 5F).texOffs(22, 0).addBox(-5F, -9F, -3F, 10F, 2F, 1F), PartPose.offsetAndRotation(0F, -2F, 0F, 0F, 0F, 0F));
 PartDefinition neck = torso.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(18, 18).addBox(-1.5F, -3F, -1.5F, 3F, 3F, 3F), PartPose.offsetAndRotation(0F, -11F, 0F, 0F, 0F, 0F));
 PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 18).addBox(-3F, -5F, -3F, 6F, 5F, 6F).texOffs(32, 18).addBox(-2.5F, -3.5F, -4F, 5F, 1.5F, 1F), PartPose.offsetAndRotation(0F, -3F, 0F, 0F, 0F, 0F));
 PartDefinition armL1 = torso.addOrReplaceChild("armL1", CubeListBuilder.create().texOffs(44, 0).addBox(-1F, 0F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(5.5F, -10F, 0F, 0F, 0F, 0.3F));
 PartDefinition foreL1 = armL1.addOrReplaceChild("foreL1", CubeListBuilder.create().texOffs(52, 0).addBox(-1F, 0F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(0F, 7F, 0F, 0F, 0F, 0F));
 PartDefinition handL1 = foreL1.addOrReplaceChild("handL1", CubeListBuilder.create().texOffs(60, 0).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 6F, 0F, 0F, 0F, 0F));
 PartDefinition armL2 = torso.addOrReplaceChild("armL2", CubeListBuilder.create().texOffs(44, 10).addBox(-1F, 0F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(5.5F, -8F, 0F, 0F, 0F, 0.2F));
 PartDefinition foreL2 = armL2.addOrReplaceChild("foreL2", CubeListBuilder.create().texOffs(52, 10).addBox(-1F, 0F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(0F, 7F, 0F, 0F, 0F, 0F));
 PartDefinition handL2 = foreL2.addOrReplaceChild("handL2", CubeListBuilder.create().texOffs(60, 10).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 6F, 0F, 0F, 0F, 0F));
 PartDefinition armR1 = torso.addOrReplaceChild("armR1", CubeListBuilder.create().texOffs(44, 20).addBox(-1F, 0F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(-5.5F, -10F, 0F, 0F, 0F, -0.3F));
 PartDefinition foreR1 = armR1.addOrReplaceChild("foreR1", CubeListBuilder.create().texOffs(52, 20).addBox(-1F, 0F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(0F, 7F, 0F, 0F, 0F, 0F));
 PartDefinition handR1 = foreR1.addOrReplaceChild("handR1", CubeListBuilder.create().texOffs(60, 20).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 6F, 0F, 0F, 0F, 0F));
 PartDefinition armR2 = torso.addOrReplaceChild("armR2", CubeListBuilder.create().texOffs(44, 30).addBox(-1F, 0F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(-5.5F, -8F, 0F, 0F, 0F, -0.2F));
 PartDefinition foreR2 = armR2.addOrReplaceChild("foreR2", CubeListBuilder.create().texOffs(52, 30).addBox(-1F, 0F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(0F, 7F, 0F, 0F, 0F, 0F));
 PartDefinition handR2 = foreR2.addOrReplaceChild("handR2", CubeListBuilder.create().texOffs(60, 30).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 6F, 0F, 0F, 0F, 0F));
 return LayerDefinition.create(mesh, 64, 64);
 }
 static final String[] BONES = {"hips", "hips/torso", "hips/torso/neck", "hips/torso/neck/head", "hips/torso/armL1", "hips/torso/armL1/foreL1", "hips/torso/armL1/foreL1/handL1", "hips/torso/armL2", "hips/torso/armL2/foreL2", "hips/torso/armL2/foreL2/handL2", "hips/torso/armR1", "hips/torso/armR1/foreR1", "hips/torso/armR1/foreR1/handR1", "hips/torso/armR2", "hips/torso/armR2/foreR2", "hips/torso/armR2/foreR2/handR2"};
 static final String[] CLIPS = {
 "idle;60;1;0;4\ntorso;0,2,0,0,0,0,0,0;30,2,0,0,0,0,0,3;60,2,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;60,-4,0,0,0,0,0,0\narmL1;0,4,0,6,0,0,0,0;60,4,0,6,0,0,0,0\narmR1;0,4,0,-6,0,0,0,0;60,4,0,-6,0,0,0,0",
 "walk;20;1;7;3\nhips;0,0,-4,0,0,0,0,0;10,0,4,0,0,0,0,7;20,0,-4,0,0,0,0,0\ntorso;0,3,4,0,0,0,0,0;10,3,-4,0,0,0,0,7;20,3,4,0,0,0,0,0\nhead;0,-6,-6,2,0,0,0,0;10,-6,6,-2,0,0,0,7;20,-6,-6,2,0,0,0,0\narmL1;0,-16,0,4,0,0,0,0;10,16,0,-4,0,0,0,7;20,-16,0,4,0,0,0,0\narmR1;0,16,0,-4,0,0,0,0;10,-16,0,4,0,0,0,7;20,16,0,-4,0,0,0,0",
 "scream;40;0;0;2\ntorso;0,2,0,0,0,0,0,0;10,-10,0,0,0,0,0,7;30,-10,0,0,0,0,0,0;40,2,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;10,-18,0,0,0,0,0,7;30,-18,0,0,0,0,0,0;40,-4,0,0,0,0,0,0\narmL1;0,4,0,6,0,0,0,0;10,-35,0,18,0,0,0,7;30,-35,0,18,0,0,0,0;40,4,0,6,0,0,0,0\narmR1;0,4,0,-6,0,0,0,0;10,-35,0,-18,0,0,0,7;30,-35,0,-18,0,0,0,0;40,4,0,-6,0,0,0,0",
 "attack;28;0;0;2\ntorso;0,2,0,0,0,0,0,0;8,8,0,0,0,0,0,7;20,8,0,0,0,0,0,0;28,2,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;8,10,0,0,0,0,0,7;20,10,0,0,0,0,0,0;28,-4,0,0,0,0,0,0\narmL1;0,4,0,6,0,0,0,0;8,-55,0,-8,0,0,0,7;20,-55,0,-8,0,0,0,0;28,4,0,6,0,0,0,0\narmR1;0,4,0,-6,0,0,0,0;8,-55,0,8,0,0,0,7;20,-55,0,8,0,0,0,0;28,4,0,-6,0,0,0,0",
 "vanish;24;0;0;2\nhips;0,0,0,0,0,0,0,0;12,18,0,0,0,-5,0,5;24,28,0,0,0,-7,0,0\ntorso;0,2,0,0,0,0,0,0;12,28,0,0,0,0,0,5;24,33,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;12,-18,0,0,0,0,0,5;24,-23,0,0,0,0,0,0\narmL1;0,4,0,6,0,0,0,0;12,-45,0,12,0,0,0,5;24,-55,0,18,0,0,0,0\narmR1;0,4,0,-6,0,0,0,0;12,-45,0,-12,0,0,0,5;24,-55,0,-18,0,0,0,0",
 "appear;16;0;0;1\nhips;0,18,0,0,0,-5,0,0;8,-3,0,0,0,1,0,7;16,0,0,0,0,0,0,0\ntorso;0,28,0,0,0,0,0,0;8,-5,0,0,0,0,0,7;16,2,0,0,0,0,0,0\nhead;0,-18,0,0,0,0,0,0;8,5,0,0,0,0,0,7;16,-4,0,0,0,0,0,0\narmL1;0,-45,0,12,0,0,0,0;8,7,0,7,0,0,0,7;16,4,0,6,0,0,0,0\narmR1;0,-45,0,-12,0,0,0,0;8,7,0,-7,0,0,0,7;16,4,0,-6,0,0,0,0"
 };
 static final String[] HALOS = {"3,0,-4,-3,4,180,220,200,0.8,0.09"};
 static final int HEAD = 3;
 static final float LOOK_YAW = 50F, LOOK_PITCH = 35F, SCALE = 1F, SHADOW = 0.8F;
}