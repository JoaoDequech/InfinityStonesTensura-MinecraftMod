package com.infinitystones.tensura.skill.ability.extra.fear.mob.client;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

final class DrownedGen {
 static final ModelLayerLocation LAYER = new ModelLayerLocation(InfinityStones.id("fear_drowned"), "main");
 static LayerDefinition layer() {
 MeshDefinition mesh = new MeshDefinition();
 PartDefinition root = mesh.getRoot();
 PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -5F, -2F, 6F, 6F, 4F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
 PartDefinition hair = body.addOrReplaceChild("hair", CubeListBuilder.create().texOffs(0, 12).addBox(-3.5F, -5F, -2.5F, 7F, 8F, 5F), PartPose.offsetAndRotation(0F, -3F, 0F, 0.3F, 0F, 0F));
 PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(18, 0).addBox(-2.5F, -3F, -2.5F, 5F, 3F, 5F), PartPose.offsetAndRotation(0F, -5F, 0F, 0F, 0F, 0F));
 PartDefinition armL = body.addOrReplaceChild("armL", CubeListBuilder.create().texOffs(30, 0).addBox(-1F, 0F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(4F, -4F, 0F, 0F, 0F, 0.2F));
 PartDefinition foreL = armL.addOrReplaceChild("foreL", CubeListBuilder.create().texOffs(36, 0).addBox(-1F, 0F, -1F, 2F, 5F, 2F), PartPose.offsetAndRotation(0F, 6F, 0F, 0F, 0F, 0F));
 PartDefinition handL = foreL.addOrReplaceChild("handL", CubeListBuilder.create().texOffs(42, 0).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 5F, 0F, 0F, 0F, 0F));
 PartDefinition armR = body.addOrReplaceChild("armR", CubeListBuilder.create().texOffs(30, 8).addBox(-1F, 0F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(-4F, -4F, 0F, 0F, 0F, -0.2F));
 PartDefinition foreR = armR.addOrReplaceChild("foreR", CubeListBuilder.create().texOffs(36, 8).addBox(-1F, 0F, -1F, 2F, 5F, 2F), PartPose.offsetAndRotation(0F, 6F, 0F, 0F, 0F, 0F));
 PartDefinition handR = foreR.addOrReplaceChild("handR", CubeListBuilder.create().texOffs(42, 8).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 5F, 0F, 0F, 0F, 0F));
 return LayerDefinition.create(mesh, 64, 32);
 }
 static final String[] BONES = {"body", "body/hair", "body/head", "body/armL", "body/armL/foreL", "body/armL/foreL/handL", "body/armR", "body/armR/foreR", "body/armR/foreR/handR"};
 static final String[] CLIPS = {
 "idle;60;1;0;4\nbody;0,1,0,0,0,0,0,0;30,1,0,0,0,0,0,3;60,1,0,0,0,0,0,0\nhead;0,-3,0,0,0,0,0,0;60,-3,0,0,0,0,0,0\narmL;0,2,0,4,0,0,0,0;60,2,0,4,0,0,0,0\narmR;0,2,0,-4,0,0,0,0;60,2,0,-4,0,0,0,0",
 "walk;20;1;7;3\nbody;0,0,-3,0,0,0,0,0;10,0,3,0,0,0,0,7;20,0,-3,0,0,0,0,0\nhead;0,-4,-4,2,0,0,0,0;10,-4,4,-2,0,0,0,7;20,-4,-4,2,0,0,0,0\narmL;0,-14,0,3,0,0,0,0;10,14,0,-3,0,0,0,7;20,-14,0,3,0,0,0,0\narmR;0,14,0,-3,0,0,0,0;10,-14,0,3,0,0,0,7;20,14,0,-3,0,0,0,0",
 "emerge;40;0;0;2\nbody;0,0,0,0,0,0,0,0;10,-20,0,0,0,0,0,7;30,-20,0,0,0,0,0,0;40,0,0,0,0,0,0,0\nhead;0,-3,0,0,0,0,0,0;10,-25,0,0,0,0,0,7;30,-25,0,0,0,0,0,0;40,-3,0,0,0,0,0,0\narmL;0,2,0,4,0,0,0,0;10,-30,0,15,0,0,0,7;30,-30,0,15,0,0,0,0;40,2,0,4,0,0,0,0\narmR;0,2,0,-4,0,0,0,0;10,-30,0,-15,0,0,0,7;30,-30,0,-15,0,0,0,0;40,2,0,-4,0,0,0,0",
 "scream;40;0;0;2\nbody;0,1,0,0,0,0,0,0;10,-8,0,0,0,0,0,7;30,-8,0,0,0,0,0,0;40,1,0,0,0,0,0,0\nhead;0,-3,0,0,0,0,0,0;10,-14,0,0,0,0,0,7;30,-14,0,0,0,0,0,0;40,-3,0,0,0,0,0,0\narmL;0,2,0,4,0,0,0,0;10,-30,0,15,0,0,0,7;30,-30,0,15,0,0,0,0;40,2,0,4,0,0,0,0\narmR;0,2,0,-4,0,0,0,0;10,-30,0,-15,0,0,0,7;30,-30,0,-15,0,0,0,0;40,2,0,-4,0,0,0,0",
 "vanish;24;0;0;2\nbody;0,0,0,0,0,0,0,0;12,15,0,0,0,-4,0,5;24,25,0,0,0,-6,0,0\nhead;0,-3,0,0,0,0,0,0;12,-15,0,0,0,0,0,5;24,-20,0,0,0,0,0,0\narmL;0,2,0,4,0,0,0,0;12,-40,0,10,0,0,0,5;24,-50,0,15,0,0,0,0\narmR;0,2,0,-4,0,0,0,0;12,-40,0,-10,0,0,0,5;24,-50,0,-15,0,0,0,0",
 "appear;16;0;0;1\nbody;0,15,0,0,0,-4,0,0;8,-2,0,0,0,1,0,7;16,0,0,0,0,0,0,0\nhead;0,-15,0,0,0,0,0,0;8,4,0,0,0,0,0,7;16,-3,0,0,0,0,0,0\narmL;0,-40,0,10,0,0,0,0;8,6,0,5,0,0,0,7;16,2,0,4,0,0,0,0\narmR;0,-40,0,-10,0,0,0,0;8,6,0,-5,0,0,0,7;16,2,0,-4,0,0,0,0"
 };
 static final String[] HALOS = {"2,0,-3,-2,4,150,200,220,0.6,0.1"};
 static final int HEAD = 2;
 static final float LOOK_YAW = 40F, LOOK_PITCH = 25F, SCALE = 0.9F, SHADOW = 0.6F;
}