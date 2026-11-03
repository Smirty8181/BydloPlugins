package ru.bydloland.paint.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.bydloland.paint.BydloPaintPlugin;
import ru.bydloland.paint.Keys;

import java.util.List;
import java.util.UUID;

public final class CanvasService {

    private final BydloPaintPlugin plugin;
    private final Keys keys;

    public CanvasService(BydloPaintPlugin plugin) {
        this.plugin = plugin;
        this.keys = new Keys(plugin);
    }

    public Keys keys() {
        return keys;
    }

    public int maxSize() {
        return Math.max(1, Math.min(10, plugin.getConfig().getInt("max-size", 10)));
    }

    public ItemStack createBlankCanvas() {
        ItemStack item = new ItemStack(Material.WHITE_WOOL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Полотно", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("ПКМ — выбрать размер", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Потом ПКМ по стене — повесить", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Макс. " + maxSize() + "×" + maxSize(), NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(keys.canvas, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isCanvas(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keys.canvas, PersistentDataType.BYTE);
    }

    public boolean isSized(ItemStack item) {
        return isCanvas(item)
                && item.getItemMeta().getPersistentDataContainer().has(keys.sized, PersistentDataType.BYTE);
    }

    public boolean isPaintingTile(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(keys.paintingId, PersistentDataType.STRING);
    }

    public void setSize(ItemStack item, int w, int h) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(keys.width, PersistentDataType.INTEGER, w);
        meta.getPersistentDataContainer().set(keys.height, PersistentDataType.INTEGER, h);
        meta.getPersistentDataContainer().set(keys.sized, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(keys.paintingId, PersistentDataType.STRING, UUID.randomUUID().toString());
        meta.displayName(Component.text("Полотно " + w + "×" + h, NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("ПКМ по стене — повесить", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Рисовать: краситель + ПКМ", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
    }

    public int width(ItemStack item) {
        Integer v = item.getItemMeta().getPersistentDataContainer().get(keys.width, PersistentDataType.INTEGER);
        return v == null ? 1 : v;
    }

    public int height(ItemStack item) {
        Integer v = item.getItemMeta().getPersistentDataContainer().get(keys.height, PersistentDataType.INTEGER);
        return v == null ? 1 : v;
    }

    public String paintingId(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().get(keys.paintingId, PersistentDataType.STRING);
    }
}
