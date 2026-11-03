package ru.bydloland.classes;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.inventory.ItemStack;

final class CeIds {

    private CeIds() {}

    static String id(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        try {
            Key key = CraftEngineItems.getCustomItemId(stack);
            return key == null ? null : key.asString();
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean is(ItemStack stack, String... ids) {
        String id = id(stack);
        if (id == null) return false;
        for (String want : ids) {
            if (id.equals(want)) return true;
        }
        return false;
    }
}
