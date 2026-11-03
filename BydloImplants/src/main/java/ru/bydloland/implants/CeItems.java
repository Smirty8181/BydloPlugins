package ru.bydloland.implants;

import org.bukkit.inventory.ItemStack;

/** Хелпер выдачи CE-предметов через reflection (как в ClassGui). */
public final class CeItems {

    private CeItems() {
    }

    public static ItemStack build(String ceId) {
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
}
