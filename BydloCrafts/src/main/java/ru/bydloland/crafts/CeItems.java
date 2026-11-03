package ru.bydloland.crafts;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Build CraftEngine items via reflection; fallback to vanilla Material. */
public final class CeItems {

    private CeItems() {
    }

    public static ItemStack of(String id) {
        if (id == null || id.isBlank()) return null;
        if (id.startsWith("minecraft:")) {
            try {
                Material mat = Material.matchMaterial(id.substring("minecraft:".length()));
                if (mat != null && mat.isItem()) return new ItemStack(mat);
            } catch (Throwable ignored) {
            }
            return null;
        }
        ItemStack ce = buildCe(id);
        if (ce != null) return ce;
        // fallback: last path segment as material
        String plain = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        Material mat = Material.matchMaterial(plain.toUpperCase());
        if (mat != null && mat.isItem()) return new ItemStack(mat);
        return new ItemStack(Material.BARRIER);
    }

    private static ItemStack buildCe(String ceId) {
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
}
