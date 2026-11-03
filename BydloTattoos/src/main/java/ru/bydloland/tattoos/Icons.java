package ru.bydloland.tattoos;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class Icons {
    private Icons() {
    }

    public static ItemStack design(TattooDef def) {
        ItemStack item = tryCe("bydlo:tattoo_icon_" + def.id());
        if (item == null) {
            item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                try {
                    meta.setItemModel(new NamespacedKey("minecraft", "item/custom/tattoo_" + def.id()));
                } catch (Throwable ignored) {
                }
                item.setItemMeta(meta);
            }
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.text(def.name()));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(Msg.text("&7Куда:"));
            for (TattooSlot slot : def.slots()) {
                lore.add(Msg.text("&8• &f" + slot.title));
            }
            lore.add(Msg.text("&eЛКМ — выбрать"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack body(TattooSlot slot) {
        String id = slot.name().toLowerCase(java.util.Locale.ROOT);
        ItemStack item = tryCe("bydlo:tattoo_icon_slot_" + id);
        if (item == null) {
            item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                try {
                    meta.setItemModel(new NamespacedKey("minecraft", "item/custom/tattoo_slot_" + id));
                } catch (Throwable ignored) {
                }
                item.setItemMeta(meta);
            }
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.text("&f" + slot.title));
            meta.lore(List.of(Msg.text("&7ЛКМ — выбрать")));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack tryCe(String ceId) {
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
