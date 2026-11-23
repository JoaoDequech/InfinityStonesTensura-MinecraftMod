package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.registry.ModSounds;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * palladium:attribute_modifier — a transient attribute modifier while the node is on (passive: while unlocked and the
 * stone is carried; toggle: while toggled on). Original attribute/amount/operation. The Space "Force Field" toggle
 * also carries its hidden companions: armour toughness (shield_toughness), "stuck" (cannot move) and the shield visual.
 */
public final class AttributeModifier implements Ported {

    public static ResourceLocation modifierId(StoneTree.Node n, String suffix) {
        return ResourceLocation.fromNamespaceAndPath("infinity", ("port/" + n.stone().key + "/" + n.id() + suffix)
                .toLowerCase().replaceAll("[^a-z0-9/._-]", "_"));
    }

    static @Nullable Holder<Attribute> attribute(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : BuiltInRegistries.ATTRIBUTE.getHolder(rl).map(h -> (Holder<Attribute>) h).orElse(null);
    }

    static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation op(String s) {
        return switch (s) {
            case "add_multiplied_base", "multiply_base" -> net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total", "multiply_total" -> net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE;
        };
    }

    /** Adds (or replaces) a transient modifier. */
    public static void apply(ServerPlayer p, Holder<Attribute> attr, ResourceLocation id, double amount,
                             net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        var existing = inst.getModifier(id);
        if (existing != null && existing.amount() == amount && existing.operation() == op) return;
        inst.removeModifier(id);
        inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, amount, op));
    }

    public static void remove(ServerPlayer p, Holder<Attribute> attr, ResourceLocation id) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst != null) inst.removeModifier(id);
        if (attr.is(Attributes.MAX_HEALTH) && p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    @Override
    public void start(ServerPlayer p, StoneTree.Node n) {
        tick(p, n, 0);
        if ("space.force_field".equals(n.modeId())) {
            PortFx.sound(p.serverLevel(), p.position(), ModSounds.SPACE_STONE_FORCEFIELD, 0.6F, 1F);
            p.setDeltaMovement(Vec3.ZERO);
            p.hurtMarked = true;
            PolishFx.fieldStart(p);
        }
    }

    @Override
    public void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
        Holder<Attribute> attr = attribute(PortFx.s(n, "attribute", ""));
        if (attr != null) apply(p, attr, modifierId(n, ""), PortFx.f(n, "amount", 0), op(PortFx.s(n, "operation", "add_value")));
        if ("space.force_field".equals(n.modeId())) {
            apply(p, Attributes.ARMOR_TOUGHNESS, modifierId(n, "_toughness"), Integer.MAX_VALUE,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
            // original "stuck": movement speed base -0.15 and tp to self every tick → cannot move
            apply(p, Attributes.MOVEMENT_SPEED, modifierId(n, "_stuck"), -1,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            apply(p, Attributes.JUMP_STRENGTH, modifierId(n, "_stuck_jump"), -1,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            if (ticks % 3 == 0) shield(p.serverLevel(), p, ticks);
            PolishFx.fieldTick(p, ticks);
        }
    }

    @Override
    public void stop(ServerPlayer p, StoneTree.Node n) {
        Holder<Attribute> attr = attribute(PortFx.s(n, "attribute", ""));
        if (attr != null) remove(p, attr, modifierId(n, ""));
        if ("space.force_field".equals(n.modeId())) {
            remove(p, Attributes.ARMOR_TOUGHNESS, modifierId(n, "_toughness"));
            remove(p, Attributes.MOVEMENT_SPEED, modifierId(n, "_stuck"));
            remove(p, Attributes.JUMP_STRENGTH, modifierId(n, "_stuck_jump"));
            PortFx.sound(p.serverLevel(), p.position(), ModSounds.SPACE_STONE_FORCEFIELD, 0.4F, 1.4F);
            PolishFx.fieldStop(p);
        }
    }

    /** Blue hex-ish bubble around the caster (replaces the original shield render layer). */
    private static void shield(ServerLevel level, ServerPlayer p, int ticks) {
        Vec3 c = p.position().add(0, p.getBbHeight() / 2, 0);
        double r = 1.6;
        var dust = PortFx.dust(n0(), 1.0F);
        for (int k = 0; k < 26; k++) {
            double u = p.getRandom().nextDouble() * 2 - 1, th = p.getRandom().nextDouble() * Math.PI * 2;
            double s = Math.sqrt(1 - u * u);
            level.sendParticles(dust, c.x + s * Math.cos(th) * r, c.y + u * r, c.z + s * Math.sin(th) * r, 1, 0, 0, 0, 0);
        }
        if (ticks % 12 == 0) level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 3, r * 0.6, r * 0.6, r * 0.6, 0.01);
    }

    private static int n0() { return com.infinitystones.tensura.skill.Stone.SPACE.color(); }
}
