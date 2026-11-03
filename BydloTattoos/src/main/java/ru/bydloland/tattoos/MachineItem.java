package ru.bydloland.tattoos;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class MachineItem {
    public static final String CE_ID = "bydlo:tattoo_machine";

    private MachineItem() {
    }

    public static NamespacedKey key(BydloTattoosPlugin plugin) {
        return new NamespacedKey(plugin, "tattoo_machine");
    }

    public static ItemStack create(BydloTattoosPlugin plugin) {
        ItemStack item = tryCraftEngine();
        if (item == null) {
            String matName = plugin.getConfig().getString("machine.material", "SHEARS");
            Material mat;
            try {
                mat = Material.valueOf(matName);
            } catch (IllegalArgumentException ex) {
                mat = Material.SHEARS;
            }
            item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Msg.text(plugin.getConfig().getString("machine.name", "&5Тату-машинка")));
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                for (String line : plugin.getConfig().getStringList("machine.lore")) {
                    lore.add(Msg.text(line));
                }
                meta.lore(lore);
                item.setItemMeta(meta);
            }
        }
        stamp(plugin, item);
        return item;
    }

    public static boolean isMachine(BydloTattoosPlugin plugin, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        if (item.getItemMeta().getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE)) {
            return true;
        }
        String ce = craftEngineId(item);
        return CE_ID.equalsIgnoreCase(ce);
    }

    private static void stamp(BydloTattoosPlugin plugin, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    private static ItemStack tryCraftEngine() {
        try {
            Class<?> items = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
            Class<?> keyCl = Class.forName("net.momirealms.craftengine.core.util.Key");
            Object key;
            try {
                key = keyCl.getMethod("of", String.class).invoke(null, CE_ID);
            } catch (NoSuchMethodException e) {
                key = keyCl.getMethod("of", String.class, String.class).invoke(null, "bydlo", "tattoo_machine");
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

    private static String craftEngineId(ItemStack item) {
        try {
            Class<?> items = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
            Object key = items.getMethod("getCustomItemId", ItemStack.class).invoke(null, item);
            return key == null ? null : key.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
