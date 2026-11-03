package ru.bydloland.fishing;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class CeItems {
    private CeItems() {
    }

    public static ItemStack of(String id) {
        if (id == null || id.isBlank()) return null;
        if (id.startsWith("minecraft:")) {
            Material mat = Material.matchMaterial(id.substring("minecraft:".length()));
            return mat != null && mat.isItem() ? new ItemStack(mat) : null;
        }
        ItemStack ce = buildCe(id);
        if (ce != null) return ce;
        String plain = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        Material mat = Material.matchMaterial(plain.toUpperCase());
        return mat != null && mat.isItem() ? new ItemStack(mat) : null;
    }

    public static String ceId(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        try {
            Class<?> items = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
            Object key = items.getMethod("getCustomItemId", ItemStack.class).invoke(null, item);
            return key == null ? null : key.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ItemStack buildCe(String ceId) {
        try {
            Class<?> items = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
            Class<?> keyCl = Class.forName("net.momirealms.craftengine.core.util.Key");
            Object key;
            try {
                key = keyCl.getMethod("of", String.class).invoke(null, ceId);
            } catch (NoSuchMethodException e) {
                int i = ceId.indexOf(':');
                String ns = i > 0 ? ceId.substring(0, i) : "bydlo";
                String val = i > 0 ? ceId.substring(i + 1) : ceId;
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
}
