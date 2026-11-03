package ru.bydloland.mage;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** CE item helper via reflection. */
public final class CeItems {

    private CeItems() {}

    public static ItemStack of(String ceId) {
        if (ceId == null || ceId.isBlank()) return null;
        try {
            Class<?> items = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
            Class<?> keyCl = Class.forName("net.momirealms.craftengine.core.util.Key");
            Object key;
            try {
                key = keyCl.getMethod("of", String.class).invoke(null, ceId);
            } catch (NoSuchMethodException e) {
                String ns = "bydlo";
                String val = ceId;
                int i = ceId.indexOf(':');
                if (i > 0) {
                    ns = ceId.substring(0, i);
                    val = ceId.substring(i + 1);
                }
                key = keyCl.getMethod("of", String.class, String.class).invoke(null, ns, val);
            }
            Object def = items.getMethod("byId", keyCl).invoke(null, key);
            if (def == null) return null;
            Object built = def.getClass().getMethod("buildBukkitItem").invoke(def);
            if (built instanceof ItemStack stack && !stack.getType().isAir()) {
                return stack.clone();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static String idOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        try {
            Class<?> items = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
            Object key = items.getMethod("getCustomItemId", ItemStack.class).invoke(null, stack);
            if (key == null) return null;
            return key.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    public static String blockIdAt(org.bukkit.block.Block block) {
        if (block == null) return null;
        try {
            Class<?> blocks = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineBlocks");
            Object state = blocks.getMethod("getCustomBlockState", org.bukkit.block.Block.class).invoke(null, block);
            if (state == null) return null;
            Boolean empty = (Boolean) state.getClass().getMethod("isEmpty").invoke(state);
            if (Boolean.TRUE.equals(empty)) return null;
            Object owner = state.getClass().getMethod("owner").invoke(state);
            Object value = owner.getClass().getMethod("value").invoke(owner);
            Object id = value.getClass().getMethod("id").invoke(value);
            return id == null ? null : id.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    public static ItemStack named(Material mat, String name) {
        ItemStack s = new ItemStack(mat);
        var meta = s.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            s.setItemMeta(meta);
        }
        return s;
    }
}
