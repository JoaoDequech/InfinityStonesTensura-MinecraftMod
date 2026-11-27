package com.infinitystones.tensura.skill.ability.extra.fear.mob.client;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

final class StalkerGen {
 static final ModelLayerLocation LAYER = new ModelLayerLocation(InfinityStones.id("fear_stalker"), "main");
 static LayerDefinition layer() {
 MeshDefinition mesh = new MeshDefinition();
 PartDefinition root = mesh.getRoot();
 PartDefinition trunk = root.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -14F, -3F, 6F, 16F, 6F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
 PartDefinition bark1 = trunk.addOrReplaceChild("bark1", CubeListBuilder.create().texOffs(18, 0).addBox(-3.5F, -12F, -3.5F, 7F, 2F, 7F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
 PartDefinition bark2 = trunk.addOrReplaceChild("bark2", CubeListBuilder.create().texOffs(18, 10).addBox(-3.5F, -8F, -3.5F, 7F, 2F, 7F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
 PartDefinition head = trunk.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 22).addBox(-2.5F, -5F, -2.5F, 5F, 5F, 5F), PartPose.offsetAndRotation(0F, -14F, 0F, 0F, 0F, 0F));
 PartDefinition branchL1 = trunk.addOrReplaceChild("branchL1", CubeListBuilder.create().texOffs(24, 22).addBox(-1F, 0F, -1F, 2F, 8F, 2F), PartPose.offsetAndRotation(4F, -10F, 0F, 0F, 0F, 0.5F));
 PartDefinition branchL2 = branchL1.addOrReplaceChild("branchL2", CubeListBuilder.create().texOffs(30, 22).addBox(-1F, 0F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(0F, 8F, 0F, 0F, 0F, 0.3F));
 PartDefinition branchR1 = trunk.addOrReplaceChild("branchR1", CubeListBuilder.create().texOffs(24, 32).addBox(-1F, 0F, -1F, 2F, 8F, 2F), PartPose.offsetAndRotation(-4F, -10F, 0F, 0F, 0F, -0.5F));
 PartDefinition branchR2 = branchR1.addOrReplaceChild("branchR2", CubeListBuilder.create().texOffs(30, 32).addBox(-1F, 0F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(0F, 8F, 0F, 0F, 0F, -0.3F));
 PartDefinition legL = root.addOrReplaceChild("legL", CubeListBuilder.create().texOffs(0, 32).addBox(-2F, 0F, -2F, 4F, 10F, 4F), PartPose.offsetAndRotation(2F, 0F, 0F, 0F, 0F, 0F));
 PartDefinition footL = legL.addOrReplaceChild("footL", CubeListBuilder.create().texOffs(12, 32).addBox(-2F, 0F, -3F, 4F, 2F, 5F), PartPose.offsetAndRotation(0F, 10F, 0F, 0F, 0F, 0F));
 PartDefinition legR = root.addOrReplaceChild("legR", CubeListBuilder.create().texOffs(0, 42).addBox(-2F, 0F, -2F, 4F, 10F, 4F), PartPose.offsetAndRotation(-2F, 0F, 0F, 0F, 0F, 0F));
 PartDefinition footR = legR.addOrReplaceChild("footR", CubeListBuilder.create().texOffs(12, 42).addBox(-2F, 0F, -3F, 4F, 2F, 5F), PartPose.offsetAndRotation(0F, 10F, 0F, 0F, 0F, 0F));
 return LayerDefinition.create(mesh, 64, 64);
 }
 static final String[] BONES = {"trunk", "trunk/bark1", "trunk/bark2", "trunk/head", "trunk/branchL1", "trunk/branchL1/branchL2", "trunk/branchR1", "trunk/branchR1/branchR2", "legL", "legL/footL", "legR", "legR/footR"};
 static final String[] CLIPS = {
 "idle;60;1;0;4\ntrunk;0,2,0,0,0,0,0,0;30,2,0,0,0,0,0,3;60,2,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;60,-4,0,0,0,0,0,0\nbranchL1;0,3,0,5,0,0,0,0;60,3,0,5,0,0,0,0\nbranchR1;0,3,0,-5,0,0,0,0;60,3,0,-5,0,0,0,0",
 "walk;20;1;7;3\ntrunk;0,0,-3,0,0,0,0,0;10,0,3,0,0,0,0,7;20,0,-3,0,0,0,0,0\nhead;0,-5,-5,2,0,0,0,0;10,-5,5,-2,0,0,0,7;20,-5,-5,2,0,0,0,0\nbranchL1;0,-14,0,4,0,0,0,0;10,14,0,-4,0,0,0,7;20,-14,0,4,0,0,0,0\nbranchR1;0,14,0,-4,0,0,0,0;10,-14,0,4,0,0,0,7;20,14,0,-4,0,0,0,0\nlegL;0,-18,0,0,0,0,0,0;10,18,0,0,0,0,0,7;20,-18,0,0,0,0,0,0\nlegR;0,18,0,0,0,0,0,0;10,-18,0,0,0,0,0,7;20,18,0,0,0,0,0,0",
 "scream;40;0;0;2\ntrunk;0,2,0,0,0,0,0,0;10,-10,0,0,0,0,0,7;30,-10,0,0,0,0,0,0;40,2,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;10,-16,0,0,0,0,0,7;30,-16,0,0,0,0,0,0;40,-4,0,0,0,0,0,0\nbranchL1;0,3,0,5,0,0,0,0;10,-35,0,18,0,0,0,7;30,-35,0,18,0,0,0,0;40,3,0,5,0,0,0,0\nbranchR1;0,3,0,-5,0,0,0,0;10,-35,0,-18,0,0,0,7;30,-35,0,-18,0,0,0,0;40,3,0,-5,0,0,0,0",
 "attack;28;0;0;2\ntrunk;0,2,0,0,0,0,0,0;8,8,0,0,0,0,0,7;20,8,0,0,0,0,0,0;28,2,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;8,10,0,0,0,0,0,7;20,10,0,0,0,0,0,0;28,-4,0,0,0,0,0,0\nbranchL1;0,3,0,5,0,0,0,0;8,-50,0,-8,0,0,0,7;20,-50,0,-8,0,0,0,0;28,3,0,5,0,0,0,0\nbranchR1;0,3,0,-5,0,0,0,0;8,-50,0,8,0,0,0,7;20,-50,0,8,0,0,0,0;28,3,0,-5,0,0,0,0",
 "vanish;24;0;0;2\ntrunk;0,0,0,0,0,0,0,0;12,18,0,0,0,-5,0,5;24,28,0,0,0,-7,0,0\nhead;0,-4,0,0,0,0,0,0;12,-18,0,0,0,0,0,5;24,-23,0,0,0,0,0,0\nbranchL1;0,3,0,5,0,0,0,0;12,-45,0,12,0,0,0,5;24,-55,0,18,0,0,0,0\nbranchR1;0,3,0,-5,0,0,0,0;12,-45,0,-12,0,0,0,5;24,-55,0,-18,0,0,0,0",
 "appear;16;0;0;1\ntrunk;0,18,0,0,0,-5,0,0;8,-3,0,0,0,1,0,7;16,0,0,0,0,0,0,0\nhead;0,-18,0,0,0,0,0,0;8,5,0,0,0,0,0,7;16,-4,0,0,0,0,0,0\nbranchL1;0,-45,0,12,0,0,0,0;8,7,0,7,0,0,0,7;16,3,0,5,0,0,0,0\nbranchR1;0,-45,0,-12,0,0,0,0;8,7,0,-7,0,0,0,7;16,3,0,-5,0,0,0,0"
 };
 static final String[] HALOS = {"2,0,-4,-3,5,100,200,100,0.7,0.08"};
 static final int HEAD = 3;
 static final float LOOK_YAW = 45F, LOOK_PITCH = 30F, SCALE = 1.1F, SHADOW = 0.9F;
}