// Made with Blockbench 5.2.2
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports


public class stone_hand<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "stone_hand"), "main");
	private final ModelPart hand;
	private final ModelPart gem_mind;
	private final ModelPart gem_power;
	private final ModelPart gem_space;
	private final ModelPart gem_reality;
	private final ModelPart gem_soul;
	private final ModelPart gem_time;
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
		this.gem_mind = this.hand.getChild("gem_mind");
		this.gem_power = this.hand.getChild("gem_power");
		this.gem_space = this.hand.getChild("gem_space");
		this.gem_reality = this.hand.getChild("gem_reality");
		this.gem_soul = this.hand.getChild("gem_soul");
		this.gem_time = this.hand.getChild("gem_time");
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

		PartDefinition hand = partdefinition.addOrReplaceChild("hand", CubeListBuilder.create().texOffs(0, 166).addBox(-16.0F, -11.0F, -6.0F, 32.0F, 8.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(372, 88).addBox(-13.0F, -14.0F, -6.0F, 26.0F, 14.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(0, 117).addBox(-13.0F, -11.0F, -9.0F, 26.0F, 8.0F, 18.0F, new CubeDeformation(0.0F))
		.texOffs(88, 166).addBox(-15.0F, -23.0F, -6.0F, 30.0F, 8.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(88, 117).addBox(-12.0F, -26.0F, -6.0F, 24.0F, 14.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(160, 117).addBox(-12.0F, -23.0F, -9.0F, 24.0F, 8.0F, 18.0F, new CubeDeformation(0.0F))
		.texOffs(220, 166).addBox(-13.0F, -33.0F, -6.0F, 26.0F, 7.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(0, 143).addBox(-11.0F, -35.0F, -6.0F, 22.0F, 11.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(68, 143).addBox(-11.0F, -33.0F, -8.0F, 22.0F, 7.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(464, 202).addBox(9.0F, -6.0F, 9.0F, 7.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(482, 202).addBox(1.0F, -6.0F, 9.0F, 7.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 214).addBox(-7.0F, -6.0F, 9.0F, 7.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(18, 214).addBox(-15.0F, -6.0F, 9.0F, 7.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(384, 186).addBox(-16.0F, -21.0F, 10.0F, 8.0F, 12.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(88, 186).addBox(-15.0F, -22.0F, 10.0F, 6.0F, 14.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(102, 186).addBox(-15.0F, -21.0F, 9.0F, 6.0F, 12.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(418, 166).addBox(8.0F, -24.0F, 9.0F, 8.0F, 14.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(368, 117).addBox(-19.0F, -20.0F, -4.0F, 6.0F, 15.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(484, 58).addBox(-17.0F, -22.0F, -4.0F, 2.0F, 19.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(0, 88).addBox(-17.0F, -20.0F, -6.0F, 2.0F, 15.0F, 14.0F, new CubeDeformation(0.0F))
		.texOffs(408, 0).addBox(-14.0F, -33.0F, -12.0F, 28.0F, 30.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(212, 214).addBox(-17.0F, -1.0F, -10.0F, 34.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(82, 202).addBox(-15.0F, -3.0F, -10.0F, 30.0F, 6.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(152, 202).addBox(-15.0F, -1.0F, -12.0F, 30.0F, 2.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(42, 222).addBox(10.0F, -2.0F, -14.0F, 5.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(290, 222).addBox(11.0F, -1.0F, -16.0F, 3.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(56, 222).addBox(2.0F, -2.0F, -14.0F, 5.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(300, 222).addBox(3.0F, -1.0F, -16.0F, 3.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(70, 222).addBox(-6.0F, -2.0F, -14.0F, 5.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(310, 222).addBox(-5.0F, -1.0F, -16.0F, 3.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(84, 222).addBox(-14.0F, -2.0F, -14.0F, 5.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(320, 222).addBox(-13.0F, -1.0F, -16.0F, 3.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(98, 222).addBox(-6.0F, -34.0F, -14.0F, 12.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(230, 202).addBox(7.0F, -14.0F, -13.0F, 10.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(252, 202).addBox(-1.0F, -14.0F, -13.0F, 10.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(274, 202).addBox(-9.0F, -14.0F, -13.0F, 10.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(296, 202).addBox(-17.0F, -14.0F, -13.0F, 10.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(402, 186).addBox(-6.0F, -30.0F, -13.0F, 12.0F, 12.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(318, 202).addBox(-23.0F, -21.0F, -8.0F, 10.0F, 10.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition gem_mind = hand.addOrReplaceChild("gem_mind", CubeListBuilder.create().texOffs(36, 214).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(126, 222).addBox(-2.0F, -2.0F, -5.0F, 4.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(12.0F, -9.0F, -12.0F));

		PartDefinition gem_power = hand.addOrReplaceChild("gem_power", CubeListBuilder.create().texOffs(52, 214).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(138, 222).addBox(-2.0F, -2.0F, -5.0F, 4.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(4.0F, -9.0F, -12.0F));

		PartDefinition gem_space = hand.addOrReplaceChild("gem_space", CubeListBuilder.create().texOffs(68, 214).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(150, 222).addBox(-2.0F, -2.0F, -5.0F, 4.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-4.0F, -9.0F, -12.0F));

		PartDefinition gem_reality = hand.addOrReplaceChild("gem_reality", CubeListBuilder.create().texOffs(84, 214).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(162, 222).addBox(-2.0F, -2.0F, -5.0F, 4.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-12.0F, -9.0F, -12.0F));

		PartDefinition gem_soul = hand.addOrReplaceChild("gem_soul", CubeListBuilder.create().texOffs(364, 202).addBox(-4.0F, -4.0F, -3.0F, 8.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(100, 214).addBox(-3.0F, -3.0F, -5.0F, 6.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -24.0F, -12.0F));

		PartDefinition gem_time = hand.addOrReplaceChild("gem_time", CubeListBuilder.create().texOffs(116, 214).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(174, 222).addBox(-2.0F, -2.0F, -5.0F, 4.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-18.0F, -16.0F, -7.0F));

		PartDefinition finger0 = hand.addOrReplaceChild("finger0", CubeListBuilder.create().texOffs(296, 166).addBox(-3.5F, 1.0F, -3.0F, 7.0F, 12.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(172, 166).addBox(-2.5F, 0.0F, -3.0F, 5.0F, 14.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(194, 166).addBox(-2.5F, 1.0F, -4.0F, 5.0F, 12.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(464, 186).addBox(-3.5F, 1.0F, -5.0F, 7.0F, 11.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(12.5F, 0.0F, 1.0F));

		PartDefinition finger0_mid = finger0.addOrReplaceChild("finger0_mid", CubeListBuilder.create().texOffs(214, 222).addBox(-3.5F, -2.0F, -5.0F, 7.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(290, 214).addBox(-2.5F, -3.0F, -5.0F, 5.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(302, 214).addBox(-2.5F, -2.0F, -6.0F, 5.0F, 4.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(428, 186).addBox(-2.5F, 1.0F, -2.0F, 5.0F, 9.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(120, 186).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 11.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(134, 186).addBox(-1.5F, 1.0F, -3.0F, 3.0F, 9.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(452, 202).addBox(-2.5F, 1.0F, -4.0F, 5.0F, 8.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 14.0F, 0.0F));

		PartDefinition finger0_tip = finger0_mid.addOrReplaceChild("finger0_tip", CubeListBuilder.create().texOffs(318, 214).addBox(-2.5F, -2.0F, -5.0F, 5.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(384, 202).addBox(-2.5F, 1.0F, -2.0F, 5.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(480, 186).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 8.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(494, 186).addBox(-1.5F, 1.0F, -3.0F, 3.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(230, 222).addBox(-2.5F, 1.0F, -4.0F, 5.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(332, 214).addBox(-1.5F, 8.0F, -2.0F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 11.0F, 0.0F));

		PartDefinition finger1 = hand.addOrReplaceChild("finger1", CubeListBuilder.create().texOffs(406, 143).addBox(-3.5F, 1.0F, -3.0F, 7.0F, 15.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(144, 143).addBox(-2.5F, 0.0F, -3.0F, 5.0F, 17.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(166, 143).addBox(-2.5F, 1.0F, -4.0F, 5.0F, 15.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(152, 186).addBox(-3.5F, 1.0F, -5.0F, 7.0F, 14.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(4.5F, 0.0F, 1.0F));

		PartDefinition finger1_mid = finger1.addOrReplaceChild("finger1_mid", CubeListBuilder.create().texOffs(242, 222).addBox(-3.5F, -2.0F, -5.0F, 7.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(346, 214).addBox(-2.5F, -3.0F, -5.0F, 5.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(358, 214).addBox(-2.5F, -2.0F, -6.0F, 5.0F, 4.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(168, 186).addBox(-2.5F, 1.0F, -2.0F, 5.0F, 11.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(354, 166).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 13.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(368, 166).addBox(-1.5F, 1.0F, -3.0F, 3.0F, 11.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(340, 202).addBox(-2.5F, 1.0F, -4.0F, 5.0F, 10.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 17.0F, 0.0F));

		PartDefinition finger1_tip = finger1_mid.addOrReplaceChild("finger1_tip", CubeListBuilder.create().texOffs(374, 214).addBox(-2.5F, -2.0F, -5.0F, 5.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 202).addBox(-2.5F, 1.0F, -2.0F, 5.0F, 8.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(252, 186).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(266, 186).addBox(-1.5F, 1.0F, -3.0F, 3.0F, 8.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(388, 214).addBox(-2.5F, 1.0F, -4.0F, 5.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(400, 214).addBox(-1.5F, 10.0F, -2.0F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 13.0F, 0.0F));

		PartDefinition finger2 = hand.addOrReplaceChild("finger2", CubeListBuilder.create().texOffs(352, 143).addBox(-3.5F, 1.0F, -3.0F, 7.0F, 16.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(400, 117).addBox(-2.5F, 0.0F, -3.0F, 5.0F, 18.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(422, 117).addBox(-2.5F, 1.0F, -4.0F, 5.0F, 16.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(438, 166).addBox(-3.5F, 1.0F, -5.0F, 7.0F, 15.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-3.5F, 0.0F, 1.0F));

		PartDefinition finger2_mid = finger2.addOrReplaceChild("finger2_mid", CubeListBuilder.create().texOffs(258, 222).addBox(-3.5F, -2.0F, -5.0F, 7.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(414, 214).addBox(-2.5F, -3.0F, -5.0F, 5.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(426, 214).addBox(-2.5F, -2.0F, -6.0F, 5.0F, 4.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(454, 166).addBox(-2.5F, 1.0F, -2.0F, 5.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(322, 166).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 14.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(336, 166).addBox(-1.5F, 1.0F, -3.0F, 3.0F, 12.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(18, 202).addBox(-2.5F, 1.0F, -4.0F, 5.0F, 11.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 18.0F, 0.0F));

		PartDefinition finger2_tip = finger2_mid.addOrReplaceChild("finger2_tip", CubeListBuilder.create().texOffs(442, 214).addBox(-2.5F, -2.0F, -5.0F, 5.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(446, 186).addBox(-2.5F, 1.0F, -2.0F, 5.0F, 9.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(186, 186).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 11.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(200, 186).addBox(-1.5F, 1.0F, -3.0F, 3.0F, 9.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(132, 214).addBox(-2.5F, 1.0F, -4.0F, 5.0F, 7.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(456, 214).addBox(-1.5F, 11.0F, -2.0F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 14.0F, 0.0F));

		PartDefinition finger3 = hand.addOrReplaceChild("finger3", CubeListBuilder.create().texOffs(432, 143).addBox(-3.5F, 1.0F, -3.0F, 7.0F, 15.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(192, 143).addBox(-2.5F, 0.0F, -3.0F, 5.0F, 17.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(214, 143).addBox(-2.5F, 1.0F, -4.0F, 5.0F, 15.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(218, 186).addBox(-3.5F, 1.0F, -5.0F, 7.0F, 14.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-11.5F, 0.0F, 1.0F));

		PartDefinition finger3_mid = finger3.addOrReplaceChild("finger3_mid", CubeListBuilder.create().texOffs(274, 222).addBox(-3.5F, -2.0F, -5.0F, 7.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(470, 214).addBox(-2.5F, -3.0F, -5.0F, 5.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(482, 214).addBox(-2.5F, -2.0F, -6.0F, 5.0F, 4.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(234, 186).addBox(-2.5F, 1.0F, -2.0F, 5.0F, 11.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(386, 166).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 13.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(400, 166).addBox(-1.5F, 1.0F, -3.0F, 3.0F, 11.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(352, 202).addBox(-2.5F, 1.0F, -4.0F, 5.0F, 10.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 17.0F, 0.0F));

		PartDefinition finger3_tip = finger3_mid.addOrReplaceChild("finger3_tip", CubeListBuilder.create().texOffs(498, 214).addBox(-2.5F, -2.0F, -5.0F, 5.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(30, 202).addBox(-2.5F, 1.0F, -2.0F, 5.0F, 8.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(284, 186).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(298, 186).addBox(-1.5F, 1.0F, -3.0F, 3.0F, 8.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(0, 222).addBox(-2.5F, 1.0F, -4.0F, 5.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(12, 222).addBox(-1.5F, 10.0F, -2.0F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 13.0F, 0.0F));

		PartDefinition thumb = hand.addOrReplaceChild("thumb", CubeListBuilder.create().texOffs(378, 143).addBox(-4.0F, 1.0F, -3.0F, 8.0F, 16.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(448, 117).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 18.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(472, 117).addBox(-3.0F, 1.0F, -4.0F, 6.0F, 16.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(48, 202).addBox(-4.0F, 1.0F, -5.0F, 8.0F, 3.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(144, 214).addBox(-4.0F, 15.0F, -6.0F, 8.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-14.0F, -12.0F, 1.0F, 0.0F, 0.0F, 0.4887F));

		PartDefinition thumb_tip = thumb.addOrReplaceChild("thumb_tip", CubeListBuilder.create().texOffs(316, 186).addBox(-3.0F, 1.0F, -2.0F, 6.0F, 10.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(472, 166).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(488, 166).addBox(-2.0F, 1.0F, -3.0F, 4.0F, 10.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(402, 202).addBox(-3.0F, 1.0F, -4.0F, 6.0F, 9.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(26, 222).addBox(-2.0F, 12.0F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 18.0F, 0.0F));

		PartDefinition wrist = partdefinition.addOrReplaceChild("wrist", CubeListBuilder.create().texOffs(244, 117).addBox(-19.0F, -4.0F, -12.0F, 38.0F, 2.0F, 24.0F, new CubeDeformation(0.0F))
		.texOffs(0, 58).addBox(-17.0F, -6.0F, -12.0F, 34.0F, 6.0F, 24.0F, new CubeDeformation(0.0F))
		.texOffs(116, 58).addBox(-17.0F, -4.0F, -14.0F, 34.0F, 2.0F, 28.0F, new CubeDeformation(0.0F))
		.texOffs(240, 143).addBox(-17.0F, -9.0F, -11.0F, 34.0F, 1.0F, 22.0F, new CubeDeformation(0.0F))
		.texOffs(156, 88).addBox(-15.0F, -11.0F, -11.0F, 30.0F, 5.0F, 22.0F, new CubeDeformation(0.0F))
		.texOffs(260, 88).addBox(-15.0F, -9.0F, -13.0F, 30.0F, 1.0F, 26.0F, new CubeDeformation(0.0F))
		.texOffs(336, 186).addBox(19.0F, -9.0F, -4.0F, 4.0F, 6.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(0, 186).addBox(20.0F, -10.0F, -4.0F, 2.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(20, 186).addBox(20.0F, -9.0F, -5.0F, 2.0F, 6.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(186, 222).addBox(23.0F, -7.0F, -2.0F, 3.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(164, 214).addBox(24.0F, -8.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(174, 214).addBox(24.0F, -7.0F, -3.0F, 1.0F, 2.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(360, 186).addBox(-23.0F, -9.0F, -4.0F, 4.0F, 6.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(44, 186).addBox(-22.0F, -10.0F, -4.0F, 2.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(64, 186).addBox(-22.0F, -9.0F, -5.0F, 2.0F, 6.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(200, 222).addBox(-26.0F, -7.0F, -2.0F, 3.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(188, 214).addBox(-25.0F, -8.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(198, 214).addBox(-25.0F, -7.0F, -3.0F, 1.0F, 2.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(416, 202).addBox(-8.0F, -9.0F, 14.0F, 16.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -35.0F, 0.0F));

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