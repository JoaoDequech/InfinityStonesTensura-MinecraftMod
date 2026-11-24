// Made with Blockbench 5.2.2
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports


public class stone_hand<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "stone_hand"), "main");
	private final ModelPart hand;
	private final ModelPart finger0;
	private final ModelPart finger0_mid;
	private final ModelPart finger0_tip;
	private final ModelPart finger1;
	private final ModelPart finger1_mid;
	private final ModelPart finger1_tip;
	private final ModelPart finger2;
	private final ModelPart finger2_mid;
	private final ModelPart finger2_tip;
	private final ModelPart finger3;
	private final ModelPart finger3_mid;
	private final ModelPart finger3_tip;
	private final ModelPart thumb;
	private final ModelPart thumb_tip;
	private final ModelPart wrist;
	private final ModelPart arm;

	public stone_hand(ModelPart root) {
		this.hand = root.getChild("hand");
		this.finger0 = this.hand.getChild("finger0");
		this.finger0_mid = this.finger0.getChild("finger0_mid");
		this.finger0_tip = this.finger0_mid.getChild("finger0_tip");
		this.finger1 = this.hand.getChild("finger1");
		this.finger1_mid = this.finger1.getChild("finger1_mid");
		this.finger1_tip = this.finger1_mid.getChild("finger1_tip");
		this.finger2 = this.hand.getChild("finger2");
		this.finger2_mid = this.finger2.getChild("finger2_mid");
		this.finger2_tip = this.finger2_mid.getChild("finger2_tip");
		this.finger3 = this.hand.getChild("finger3");
		this.finger3_mid = this.finger3.getChild("finger3_mid");
		this.finger3_tip = this.finger3_mid.getChild("finger3_tip");
		this.thumb = this.hand.getChild("thumb");
		this.thumb_tip = this.thumb.getChild("thumb_tip");
		this.wrist = root.getChild("wrist");
		this.arm = root.getChild("arm");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition hand = partdefinition.addOrReplaceChild("hand", CubeListBuilder.create().texOffs(0, 167).addBox(-16.0F, -11.0F, -6.0F, 32.0F, 8.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(372, 88).addBox(-13.0F, -14.0F, -6.0F, 26.0F, 14.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(0, 117).addBox(-13.0F, -11.0F, -9.0F, 26.0F, 8.0F, 18.0F, new CubeDeformation(0.0F))
		.texOffs(88, 167).addBox(-15.0F, -23.0F, -6.0F, 30.0F, 8.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(88, 117).addBox(-12.0F, -26.0F, -6.0F, 24.0F, 14.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(160, 117).addBox(-12.0F, -23.0F, -9.0F, 24.0F, 8.0F, 18.0F, new CubeDeformation(0.0F))
		.texOffs(220, 167).addBox(-13.0F, -33.0F, -6.0F, 26.0F, 7.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(52, 143).addBox(-11.0F, -35.0F, -6.0F, 22.0F, 11.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(120, 143).addBox(-11.0F, -33.0F, -8.0F, 22.0F, 7.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(234, 202).addBox(9.0F, -6.0F, 9.0F, 7.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(334, 202).addBox(9.0F, -3.0F, -6.0F, 7.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 202).addBox(10.0F, -4.0F, -6.0F, 5.0F, 7.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(14, 202).addBox(10.0F, -3.0F, -7.0F, 5.0F, 5.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(252, 202).addBox(1.0F, -6.0F, 9.0F, 7.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(352, 202).addBox(1.0F, -3.0F, -6.0F, 7.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(32, 202).addBox(2.0F, -4.0F, -6.0F, 5.0F, 7.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(46, 202).addBox(2.0F, -3.0F, -7.0F, 5.0F, 5.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(270, 202).addBox(-7.0F, -6.0F, 9.0F, 7.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(370, 202).addBox(-7.0F, -3.0F, -6.0F, 7.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(64, 202).addBox(-6.0F, -4.0F, -6.0F, 5.0F, 7.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(78, 202).addBox(-6.0F, -3.0F, -7.0F, 5.0F, 5.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(288, 202).addBox(-15.0F, -6.0F, 9.0F, 7.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(388, 202).addBox(-15.0F, -3.0F, -6.0F, 7.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(96, 202).addBox(-14.0F, -4.0F, -6.0F, 5.0F, 7.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(110, 202).addBox(-14.0F, -3.0F, -7.0F, 5.0F, 5.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(202, 187).addBox(-16.0F, -21.0F, 10.0F, 8.0F, 12.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(492, 167).addBox(-15.0F, -22.0F, 10.0F, 6.0F, 14.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 187).addBox(-15.0F, -21.0F, 9.0F, 6.0F, 12.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(418, 167).addBox(8.0F, -24.0F, 9.0F, 8.0F, 14.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(420, 117).addBox(-19.0F, -20.0F, -4.0F, 6.0F, 15.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(484, 58).addBox(-17.0F, -22.0F, -4.0F, 2.0F, 19.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(0, 88).addBox(-17.0F, -20.0F, -6.0F, 2.0F, 15.0F, 14.0F, new CubeDeformation(0.0F))
		.texOffs(244, 117).addBox(-12.0F, -31.0F, -11.0F, 24.0F, 24.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(408, 0).addBox(12.0F, -32.0F, -12.0F, 2.0F, 27.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(418, 0).addBox(-14.0F, -32.0F, -12.0F, 2.0F, 27.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(214, 211).addBox(-14.0F, -32.0F, -12.0F, 28.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(80, 211).addBox(-14.0F, -7.0F, -12.0F, 28.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition finger0 = hand.addOrReplaceChild("finger0", CubeListBuilder.create().texOffs(296, 167).addBox(-3.5F, 1.0F, -3.0F, 7.0F, 12.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(172, 167).addBox(-2.5F, 0.0F, -3.0F, 5.0F, 14.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(194, 167).addBox(-2.5F, 1.0F, -4.0F, 5.0F, 12.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(358, 187).addBox(-3.5F, 1.0F, -5.0F, 7.0F, 2.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(12.5F, 0.0F, 1.0F));

		PartDefinition finger0_mid = finger0.addOrReplaceChild("finger0_mid", CubeListBuilder.create().texOffs(142, 211).addBox(-3.5F, -2.0F, -5.0F, 7.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(406, 202).addBox(-2.5F, -3.0F, -5.0F, 5.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(418, 202).addBox(-2.5F, -2.0F, -6.0F, 5.0F, 4.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(220, 187).addBox(-2.5F, 1.0F, -2.0F, 5.0F, 9.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(18, 187).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 11.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(32, 187).addBox(-1.5F, 1.0F, -3.0F, 3.0F, 9.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(128, 202).addBox(-2.5F, 0.0F, -4.0F, 5.0F, 2.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 14.0F, 0.0F));

		PartDefinition finger0_tip = finger0_mid.addOrReplaceChild("finger0_tip", CubeListBuilder.create().texOffs(434, 202).addBox(-2.5F, -2.0F, -5.0F, 5.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(486, 187).addBox(-2.5F, 1.0F, -2.0F, 5.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(256, 187).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 8.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(270, 187).addBox(-1.5F, 1.0F, -3.0F, 3.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(158, 211).addBox(-1.5F, 3.0F, -4.0F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 11.0F, 0.0F));

		PartDefinition finger1 = hand.addOrReplaceChild("finger1", CubeListBuilder.create().texOffs(458, 143).addBox(-3.5F, 1.0F, -3.0F, 7.0F, 15.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(196, 143).addBox(-2.5F, 0.0F, -3.0F, 5.0F, 17.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(218, 143).addBox(-2.5F, 1.0F, -4.0F, 5.0F, 15.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(390, 187).addBox(-3.5F, 1.0F, -5.0F, 7.0F, 2.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(4.5F, 0.0F, 1.0F));

		PartDefinition finger1_mid = finger1.addOrReplaceChild("finger1_mid", CubeListBuilder.create().texOffs(166, 211).addBox(-3.5F, -2.0F, -5.0F, 7.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(448, 202).addBox(-2.5F, -3.0F, -5.0F, 5.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(460, 202).addBox(-2.5F, -2.0F, -6.0F, 5.0F, 4.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(50, 187).addBox(-2.5F, 1.0F, -2.0F, 5.0F, 11.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(354, 167).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 13.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(368, 167).addBox(-1.5F, 1.0F, -3.0F, 3.0F, 11.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(152, 202).addBox(-2.5F, 0.0F, -4.0F, 5.0F, 2.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 17.0F, 0.0F));

		PartDefinition finger1_tip = finger1_mid.addOrReplaceChild("finger1_tip", CubeListBuilder.create().texOffs(476, 202).addBox(-2.5F, -2.0F, -5.0F, 5.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(288, 187).addBox(-2.5F, 1.0F, -2.0F, 5.0F, 8.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(118, 187).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(132, 187).addBox(-1.5F, 1.0F, -3.0F, 3.0F, 8.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(490, 202).addBox(-1.5F, 3.0F, -4.0F, 3.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 13.0F, 0.0F));

		PartDefinition finger2 = hand.addOrReplaceChild("finger2", CubeListBuilder.create().texOffs(404, 143).addBox(-3.5F, 1.0F, -3.0F, 7.0F, 16.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(452, 117).addBox(-2.5F, 0.0F, -3.0F, 5.0F, 18.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(474, 117).addBox(-2.5F, 1.0F, -4.0F, 5.0F, 16.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(422, 187).addBox(-3.5F, 1.0F, -5.0F, 7.0F, 2.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(-3.5F, 0.0F, 1.0F));

		PartDefinition finger2_mid = finger2.addOrReplaceChild("finger2_mid", CubeListBuilder.create().texOffs(182, 211).addBox(-3.5F, -2.0F, -5.0F, 7.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(498, 202).addBox(-2.5F, -3.0F, -5.0F, 5.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 211).addBox(-2.5F, -2.0F, -6.0F, 5.0F, 4.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(438, 167).addBox(-2.5F, 1.0F, -2.0F, 5.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(322, 167).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 14.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(336, 167).addBox(-1.5F, 1.0F, -3.0F, 3.0F, 12.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(176, 202).addBox(-2.5F, 0.0F, -4.0F, 5.0F, 2.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 18.0F, 0.0F));

		PartDefinition finger2_tip = finger2_mid.addOrReplaceChild("finger2_tip", CubeListBuilder.create().texOffs(16, 211).addBox(-2.5F, -2.0F, -5.0F, 5.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(238, 187).addBox(-2.5F, 1.0F, -2.0F, 5.0F, 9.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(68, 187).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 11.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(82, 187).addBox(-1.5F, 1.0F, -3.0F, 3.0F, 9.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(306, 202).addBox(-1.5F, 3.0F, -4.0F, 3.0F, 7.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 14.0F, 0.0F));

		PartDefinition finger3 = hand.addOrReplaceChild("finger3", CubeListBuilder.create().texOffs(484, 143).addBox(-3.5F, 1.0F, -3.0F, 7.0F, 15.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(244, 143).addBox(-2.5F, 0.0F, -3.0F, 5.0F, 17.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(266, 143).addBox(-2.5F, 1.0F, -4.0F, 5.0F, 15.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(454, 187).addBox(-3.5F, 1.0F, -5.0F, 7.0F, 2.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(-11.5F, 0.0F, 1.0F));

		PartDefinition finger3_mid = finger3.addOrReplaceChild("finger3_mid", CubeListBuilder.create().texOffs(198, 211).addBox(-3.5F, -2.0F, -5.0F, 7.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(30, 211).addBox(-2.5F, -3.0F, -5.0F, 5.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(42, 211).addBox(-2.5F, -2.0F, -6.0F, 5.0F, 4.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(100, 187).addBox(-2.5F, 1.0F, -2.0F, 5.0F, 11.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(386, 167).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 13.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(400, 167).addBox(-1.5F, 1.0F, -3.0F, 3.0F, 11.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(200, 202).addBox(-2.5F, 0.0F, -4.0F, 5.0F, 2.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 17.0F, 0.0F));

		PartDefinition finger3_tip = finger3_mid.addOrReplaceChild("finger3_tip", CubeListBuilder.create().texOffs(58, 211).addBox(-2.5F, -2.0F, -5.0F, 5.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(306, 187).addBox(-2.5F, 1.0F, -2.0F, 5.0F, 8.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(150, 187).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(164, 187).addBox(-1.5F, 1.0F, -3.0F, 3.0F, 8.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(72, 211).addBox(-1.5F, 3.0F, -4.0F, 3.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 13.0F, 0.0F));

		PartDefinition thumb = hand.addOrReplaceChild("thumb", CubeListBuilder.create().texOffs(430, 143).addBox(-4.0F, 1.0F, -3.0F, 8.0F, 16.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(0, 143).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 18.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(24, 143).addBox(-3.0F, 1.0F, -4.0F, 6.0F, 16.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(324, 187).addBox(-4.0F, 1.0F, -5.0F, 8.0F, 3.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(314, 202).addBox(-4.0F, 15.0F, -6.0F, 8.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-14.0F, -12.0F, 1.0F, 0.0F, 0.0F, 0.4887F));

		PartDefinition thumb_tip = thumb.addOrReplaceChild("thumb_tip", CubeListBuilder.create().texOffs(182, 187).addBox(-3.0F, 1.0F, -2.0F, 6.0F, 10.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(456, 167).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(472, 167).addBox(-2.0F, 1.0F, -3.0F, 4.0F, 10.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(224, 202).addBox(-2.0F, 3.0F, -4.0F, 4.0F, 8.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 18.0F, 0.0F));

		PartDefinition wrist = partdefinition.addOrReplaceChild("wrist", CubeListBuilder.create().texOffs(296, 117).addBox(-19.0F, -4.0F, -12.0F, 38.0F, 2.0F, 24.0F, new CubeDeformation(0.0F))
		.texOffs(0, 58).addBox(-17.0F, -6.0F, -12.0F, 34.0F, 6.0F, 24.0F, new CubeDeformation(0.0F))
		.texOffs(116, 58).addBox(-17.0F, -4.0F, -14.0F, 34.0F, 2.0F, 28.0F, new CubeDeformation(0.0F))
		.texOffs(292, 143).addBox(-17.0F, -9.0F, -11.0F, 34.0F, 1.0F, 22.0F, new CubeDeformation(0.0F))
		.texOffs(156, 88).addBox(-15.0F, -11.0F, -11.0F, 30.0F, 5.0F, 22.0F, new CubeDeformation(0.0F))
		.texOffs(260, 88).addBox(-15.0F, -9.0F, -13.0F, 30.0F, 1.0F, 26.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -35.0F, 0.0F));

		PartDefinition arm = partdefinition.addOrReplaceChild("arm", CubeListBuilder.create().texOffs(184, 0).addBox(-15.0F, -38.0F, -9.0F, 30.0F, 36.0F, 18.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-13.0F, -40.0F, -9.0F, 26.0F, 40.0F, 18.0F, new CubeDeformation(0.0F))
		.texOffs(88, 0).addBox(-13.0F, -38.0F, -11.0F, 26.0F, 36.0F, 22.0F, new CubeDeformation(0.0F))
		.texOffs(360, 0).addBox(13.0F, -39.0F, -12.0F, 4.0F, 38.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(280, 0).addBox(14.0F, -40.0F, -12.0F, 2.0F, 40.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(288, 0).addBox(14.0F, -39.0F, -13.0F, 2.0F, 38.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(372, 0).addBox(13.0F, -39.0F, 10.0F, 4.0F, 38.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(300, 0).addBox(14.0F, -40.0F, 10.0F, 2.0F, 40.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(308, 0).addBox(14.0F, -39.0F, 9.0F, 2.0F, 38.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(384, 0).addBox(-17.0F, -39.0F, -12.0F, 4.0F, 38.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(320, 0).addBox(-16.0F, -40.0F, -12.0F, 2.0F, 40.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(328, 0).addBox(-16.0F, -39.0F, -13.0F, 2.0F, 38.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(396, 0).addBox(-17.0F, -39.0F, 10.0F, 4.0F, 38.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(340, 0).addBox(-16.0F, -40.0F, 10.0F, 2.0F, 40.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(348, 0).addBox(-16.0F, -39.0F, 9.0F, 2.0F, 38.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(32, 88).addBox(-18.0F, -21.0F, -13.0F, 36.0F, 2.0F, 26.0F, new CubeDeformation(0.0F))
		.texOffs(240, 58).addBox(-17.0F, -22.0F, -13.0F, 34.0F, 4.0F, 26.0F, new CubeDeformation(0.0F))
		.texOffs(360, 58).addBox(-17.0F, -21.0F, -14.0F, 34.0F, 2.0F, 28.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -46.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 512, 256);
	}

	@Override
	public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		hand.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		wrist.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		arm.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}