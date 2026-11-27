package com.infinitystones.tensura.skill.ability.extra.fear.mob.client;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

final class MascotGen {
 static final ModelLayerLocation LAYER = new ModelLayerLocation(InfinityStones.id("fear_mascot"), "main");
 static LayerDefinition layer() {
 MeshDefinition mesh = new MeshDefinition();
 PartDefinition root = mesh.getRoot();
 PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-5F, -10F, -3F, 10F, 12F, 6F).texOffs(24, 0).addBox(-5.5F, -8F, -3.5F, 11F, 3F, 1F), PartPose.offsetAndRotation(0F, -2F, 0F, 0F, 0F, 0F));
 PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 18).addBox(-4F, -6F, -4F, 8F, 6F, 8F).texOffs(24, 18).addBox(-3.5F, -4F, -5F, 7F, 2F, 1F), PartPose.offsetAndRotation(0F, -10F, 0F, 0F, 0F, 0F));
 PartDefinition snout = head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(40, 18).addBox(-2F, -2F, -6F, 4F, 3F, 2F), PartPose.offsetAndRotation(0F, -2F, -4F, 0F, 0F, 0F));
 PartDefinition earL = head.addOrReplaceChild("earL", CubeListBuilder.create().texOffs(48, 18).addBox(-1F, -3F, -1F, 2F, 3F, 2F), PartPose.offsetAndRotation(3F, -5F, 0F, 0F, 0F, 0.3F));
 PartDefinition earR = head.addOrReplaceChild("earR", CubeListBuilder.create().texOffs(56, 18).addBox(-1F, -3F, -1F, 2F, 3F, 2F), PartPose.offsetAndRotation(-3F, -5F, 0F, 0F, 0F, -0.3F));
 PartDefinition armL = body.addOrReplaceChild("armL", CubeListBuilder.create().texOffs(40, 0).addBox(-1.5F, 0F, -1.5F, 3F, 8F, 3F), PartPose.offsetAndRotation(6F, -9F, 0F, 0F, 0F, 0F));
 PartDefinition handL = armL.addOrReplaceChild("handL", CubeListBuilder.create().texOffs(52, 0).addBox(-1.5F, 0F, -1.5F, 3F, 3F, 3F), PartPose.offsetAndRotation(0F, 8F, 0F, 0F, 0F, 0F));
 PartDefinition armR = body.addOrReplaceChild("armR", CubeListBuilder.create().texOffs(40, 10).addBox(-1.5F, 0F, -1.5F, 3F, 8F, 3F), PartPose.offsetAndRotation(-6F, -9F, 0F, 0F, 0F, 0F));
 PartDefinition handR = armR.addOrReplaceChild("handR", CubeListBuilder.create().texOffs(52, 10).addBox(-1.5F, 0F, -1.5F, 3F, 3F, 3F), PartPose.offsetAndRotation(0F, 8F, 0F, 0F, 0F, 0F));
 PartDefinition legL = root.addOrReplaceChild("legL", CubeListBuilder.create().texOffs(0, 32).addBox(-2F, 0F, -2F, 4F, 8F, 4F), PartPose.offsetAndRotation(2.5F, 0F, 0F, 0F, 0F, 0F));
 PartDefinition footL = legL.addOrReplaceChild("footL", CubeListBuilder.create().texOffs(12, 32).addBox(-2F, 0F, -3F, 4F, 2F, 5F), PartPose.offsetAndRotation(0F, 8F, 0F, 0F, 0F, 0F));
 PartDefinition legR = root.addOrReplaceChild("legR", CubeListBuilder.create().texOffs(0, 42).addBox(-2F, 0F, -2F, 4F, 8F, 4F), PartPose.offsetAndRotation(-2.5F, 0F, 0F, 0F, 0F, 0F));
 PartDefinition footR = legR.addOrReplaceChild("footR", CubeListBuilder.create().texOffs(12, 42).addBox(-2F, 0F, -3F, 4F, 2F, 5F), PartPose.offsetAndRotation(0F, 8F, 0F, 0F, 0F, 0F));
 return LayerDefinition.create(mesh, 64, 64);
 }
 static final String[] BONES = {"body", "body/head", "body/head/snout", "body/head/earL", "body/head/earR", "body/armL", "body/armL/handL", "body/armR", "body/armR/handR", "legL", "legL/footL", "legR", "legR/footR"};
 static final String[] CLIPS = {
 "idle;60;1;0;3\nbody;0,2,0,0,0,0,0,0;30,2,0,0,0,0,0,3;60,2,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;60,-4,0,0,0,0,0,0\narmL;0,3,0,5,0,0,0,0;60,3,0,5,0,0,0,0\narmR;0,3,0,-5,0,0,0,0;60,3,0,-5,0,0,0,0",
 "walk;20;1;7;3\nbody;0,0,-3,0,0,0,0,0;10,0,3,0,0,0,0,7;20,0,-3,0,0,0,0,0\nhead;0,-5,-5,2,0,0,0,0;10,-5,5,-2,0,0,0,7;20,-5,-5,2,0,0,0,0\narmL;0,-16,0,4,0,0,0,0;10,16,0,-4,0,0,0,7;20,-16,0,4,0,0,0,0\narmR;0,16,0,-4,0,0,0,0;10,-16,0,4,0,0,0,7;20,16,0,-4,0,0,0,0\nlegL;0,-20,0,0,0,0,0,0;10,20,0,0,0,0,0,7;20,-20,0,0,0,0,0,0\nlegR;0,20,0,0,0,0,0,0;10,-20,0,0,0,0,0,7;20,20,0,0,0,0,0,0",
 "scream;40;0;0;2\nbody;0,2,0,0,0,0,0,0;10,-10,0,0,0,0,0,7;30,-10,0,0,0,0,0,0;40,2,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;10,-16,0,0,0,0,0,7;30,-16,0,0,0,0,0,0;40,-4,0,0,0,0,0,0\narmL;0,3,0,5,0,0,0,0;10,-35,0,18,0,0,0,7;30,-35,0,18,0,0,0,0;40,3,0,5,0,0,0,0\narmR;0,3,0,-5,0,0,0,0;10,-35,0,-18,0,0,0,7;30,-35,0,-18,0,0,0,0;40,3,0,-5,0,0,0,0",
 "attack;28;0;0;2\nbody;0,2,0,0,0,0,0,0;8,8,0,0,0,0,0,7;20,8,0,0,0,0,0,0;28,2,0,0,0,0,0,0\nhead;0,-4,0,0,0,0,0,0;8,10,0,0,0,0,0,7;20,10,0,0,0,0,0,0;28,-4,0,0,0,0,0,0\narmL;0,3,0,5,0,0,0,0;8,-50,0,-8,0,0,0,7;20,-50,0,-8,0,0,0,0;28,3,0,5,0,0,0,0\narmR;0,3,0,-5,0,0,0,0;8,-50,0,8,0,0,0,7;20,-50,0,8,0,0,0,0;28,3,0,-5,0,0,0,0",
 "vanish;24;0;0;2\nbody;0,0,0,0,0,0,0,0;12,18,0,0,0,-5,0,5;24,28,0,0,0,-7,0,0\nhead;0,-4,0,0,0,0,0,0;12,-18,0,0,0,0,0,5;24,-23,0,0,0,0,0,0\narmL;0,3,0,5,0,0,0,0;12,-45,0,12,0,0,0,5;24,-55,0,18,0,0,0,0\narmR;0,3,0,-5,0,0,0,0;12,-45,0,-12,0,0,0,5;24,-55,0,-18,0,0,0,0",
 "appear;16;0;0;1\nbody;0,18,0,0,0,-5,0,0;8,-3,0,0,0,1,0,7;16,0,0,0,0,0,0,0\nhead;0,-18,0,0,0,0,0,0;8,5,0,0,0,0,0,7;16,-4,0,0,0,0,0,0\narmL;0,-45,0,12,0,0,0,0;8,7,0,7,0,0,0,7;16,3,0,5,0,0,0,0\narmR;0,-45,0,-12,0,0,0,0;8,7,0,-7,0,0,0,7;16,3,0,-5,0,0,0,0"
 };
 static final String[] HALOS = {"3,0,-5,-4,6,255,100,100,0.85,0.12"};
 static final int HEAD = 1;
 static final float LOOK_YAW = 55F, LOOK_PITCH = 40F, SCALE = 1.1F, SHADOW = 1.0F;
}