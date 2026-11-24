package com.infinitystones.tensura.skill;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Accessory slots by reflection (no hard dependency): Curios and Accessories (wispforest). Packs may ship both, plus the
 * Accessories compatibility layer that mirrors Curios slots, so both are asked. Safe on client and server.
 */
public final class CuriosCompat {
    private static Method getInventory, findFirst, accOptionally, accIsEquipped;
    private static boolean curiosBroken, accBroken;
    private static final boolean[] CURIOS_LOADED = new boolean[2], ACCESSORIES_LOADED = new boolean[2];

    /** True when an equipped accessory slot (Curios or Accessories) holds a stack matching the test. */
    public static boolean has(LivingEntity entity, Predicate<ItemStack> test) {
        return curios(entity, test) || accessories(entity, test);
    }

    /** Visits every equipped accessory stack (one reflective query per mod instead of one per question). */
    public static void forEach(LivingEntity entity, Consumer<ItemStack> visitor) {
        Predicate<ItemStack> all = s -> { visitor.accept(s); return false; };
        curios(entity, all);
        accessories(entity, all);
    }

    private static boolean loaded(String mod, boolean[] cache) {
        if (cache[0]) return cache[1];
        ModList list = ModList.get();
        if (list == null) return false;
        cache[1] = list.isLoaded(mod);
        cache[0] = true;
        return cache[1];
    }

    private static boolean curios(LivingEntity entity, Predicate<ItemStack> test) {
        if (curiosBroken || !loaded("curios", CURIOS_LOADED)) return false;
        try {
            if (getInventory == null) {
                Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
                findFirst = handler.getMethod("findFirstCurio", Predicate.class);
                getInventory = api.getMethod("getCuriosInventory", LivingEntity.class);
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            curiosBroken = true; // the API is not what we expect: stop asking
            return false;
        }
        try {
            Optional<?> inv = (Optional<?>) getInventory.invoke(null, entity);
            if (inv.isEmpty()) return false;
            return ((Optional<?>) findFirst.invoke(inv.get(), test)).isPresent();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false; // transient (inventory not synced yet, mid-login): answer again next time instead of giving up for good
        }
    }

    private static boolean accessories(LivingEntity entity, Predicate<ItemStack> test) {
        if (accBroken || !loaded("accessories", ACCESSORIES_LOADED)) return false;
        try {
            if (accOptionally == null) {
                Class<?> cap = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
                accIsEquipped = cap.getMethod("isEquipped", Predicate.class);
                accOptionally = cap.getMethod("getOptionally", LivingEntity.class);
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            accBroken = true;
            return false;
        }
        try {
            Optional<?> cap = (Optional<?>) accOptionally.invoke(null, entity);
            return cap.isPresent() && (Boolean) accIsEquipped.invoke(cap.get(), test);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
    }

    private CuriosCompat() {}
}
