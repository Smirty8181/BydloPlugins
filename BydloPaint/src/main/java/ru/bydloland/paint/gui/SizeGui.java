package ru.bydloland.paint.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.bydloland.paint.BydloPaintPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SizeGui {

    public static final Component TITLE = Component.text("Размер полотна", NamedTextColor.LIGHT_PURPLE);
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private SizeGui() {}

    public static void open(BydloPaintPlugin plugin, Player player, ItemStack canvas) {
        int max = plugin.canvases().maxSize();
        Inventory inv = Bukkit.createInventory(player, 27, TITLE);
        int[] presets = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        NamespacedKey sizeKey = new NamespacedKey(plugin, "gui_size");
        int slot = 10;
        for (int size : presets) {
            if (size > max) break;
            if (slot == 17) slot = 19;
            inv.setItem(slot++, sizeItem(size, sizeKey));
        }
        SESSIONS.put(player.getUniqueId(), new Session(canvas.clone(), canvas.getAmount()));
        player.openInventory(inv);
    }

    private static ItemStack sizeItem(int size, NamespacedKey sizeKey) {
        ItemStack item = new ItemStack(Material.PAINTING);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(size + "×" + size, NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Квадратное полотно", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(sizeKey, PersistentDataType.INTEGER, size);
        item.setItemMeta(meta);
        return item;
    }

    public static Integer readSize(BydloPaintPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "gui_size"), PersistentDataType.INTEGER);
    }

    public static Session take(Player player) {
        return SESSIONS.remove(player.getUniqueId());
    }

    public static Session peek(Player player) {
        return SESSIONS.get(player.getUniqueId());
    }

    public static boolean isSizeGui(Component title) {
        return TITLE.equals(title);
    }

    public record Session(ItemStack canvasTemplate, int amount) {}
}
