package ru.bydloland.tattoos;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.awt.image.BufferedImage;
import java.util.List;

public final class DrawGui implements InventoryHolder, Listener {
    private static final int VIEW_ROWS = 4;

    private final BydloTattoosPlugin plugin;
    private Inventory inventory;
    private String clientName;
    private TattooSlot slot;
    private int cols;
    private int rows;
    private int[][] pixels;
    private int page;
    private int color = InkPalette.BLACK.argb();

    public DrawGui(BydloTattoosPlugin plugin) {
        this.plugin = plugin;
    }

    public static void open(BydloTattoosPlugin plugin, Player artist, Player client, TattooSlot slot) {
        DrawGui holder = new DrawGui(plugin);
        holder.clientName = client.getName();
        holder.slot = slot;
        boolean slim = "SLIM".equalsIgnoreCase(plugin.storage().get(client.getUniqueId()).variant);
        holder.cols = slot.width(slim);
        holder.rows = slot.h;
        holder.pixels = new int[holder.rows][holder.cols];
        holder.page = 0;
        Inventory inv = Bukkit.createInventory(holder, 54, "§5Рисунок §8· §f" + slot.title
                + " §8" + holder.cols + "×" + holder.rows);
        holder.inventory = inv;
        holder.redraw();
        artist.openInventory(inv);
    }

    private int pages() {
        return Math.max(1, (rows + VIEW_ROWS - 1) / VIEW_ROWS);
    }

    private void redraw() {
        ItemStack empty = pane(Material.BLACK_STAINED_GLASS_PANE, "&8");
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, empty);
        int y0 = page * VIEW_ROWS;
        for (int vy = 0; vy < VIEW_ROWS; vy++) {
            int y = y0 + vy;
            for (int x = 0; x < 8; x++) {
                paintCell(vy, x, y);
            }
        }
        inventory.setItem(8, named(Material.ARROW, "&fВыше",
                "&7Строки " + y0 + "–" + Math.min(rows, y0 + VIEW_ROWS),
                page > 0 ? "&eЛКМ — страница вверх" : "&8это верх"));
        inventory.setItem(17, named(Material.MAP, "&d" + slot.title + " &f" + cols + "×" + rows,
                "&7Стр. &f" + (page + 1) + "&7/&f" + pages(),
                "&7Каждая клетка — один пиксель скина",
                "&7Рисуй мелко, без растягивания"));
        inventory.setItem(26, named(Material.ARROW, "&fНиже",
                page + 1 < pages() ? "&eЛКМ — страница вниз" : "&8это низ"));
        inventory.setItem(35, named(InkPalette.byArgb(color).mat(), "&dКисть: &f" + InkPalette.byArgb(color).name(),
                "&7Любые цвета на одном рисунке"));

        List<InkPalette.Color> all = InkPalette.ALL;
        for (int i = 0; i < all.size(); i++) {
            InkPalette.Color ink = all.get(i);
            boolean sel = ink.argb() == color;
            ItemStack swatch = named(ink.mat(), (sel ? "&a▶ " : "&f") + ink.name(), "&7ЛКМ — кисть");
            if (sel) {
                ItemMeta meta = swatch.getItemMeta();
                if (meta != null) {
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    swatch.setItemMeta(meta);
                }
            }
            inventory.setItem(i < 9 ? 36 + i : 45 + (i - 9), swatch);
        }
        inventory.setItem(48, named(Material.BARRIER, "&cОчистить", "&7Стереть холст"));
        inventory.setItem(50, named(Material.PAPER, "&f" + clientName, "&7Куда: &f" + slot.title));
        inventory.setItem(53, named(Material.INK_SAC, "&aНабить", "&7" + slot.title + " · 1:1 пиксели"));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof DrawGui holder)) return;
        if (holder.plugin != this.plugin) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player artist)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != holder.inventory) return;
        int raw = event.getRawSlot();
        if (raw < 0) return;

        if (raw == 8 && holder.page > 0) {
            holder.page--;
            holder.redraw();
            return;
        }
        if (raw == 26 && holder.page + 1 < holder.pages()) {
            holder.page++;
            holder.redraw();
            return;
        }
        if (raw == 48) {
            for (int y = 0; y < holder.rows; y++) {
                for (int x = 0; x < holder.cols; x++) holder.pixels[y][x] = 0;
            }
            holder.redraw();
            return;
        }
        if (raw == 53) {
            holder.apply(artist);
            return;
        }
        if (raw >= 36 && raw <= 44) {
            holder.color = InkPalette.ALL.get(raw - 36).argb();
            holder.redraw();
            return;
        }
        if (raw >= 45 && raw <= 47) {
            holder.color = InkPalette.ALL.get(9 + (raw - 45)).argb();
            holder.redraw();
            return;
        }
        int col = raw % 9;
        int row = raw / 9;
        if (col >= holder.cols || row >= VIEW_ROWS) return;
        int y = holder.page * VIEW_ROWS + row;
        if (y >= holder.rows) return;
        if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
            holder.pixels[y][col] = 0;
        } else {
            holder.pixels[y][col] = holder.color;
        }
        holder.paintCell(row, col, y);
    }

    private void paintCell(int vy, int x, int y) {
        int idx = vy * 9 + x;
        if (y >= rows || x >= cols) {
            inventory.setItem(idx, pane(Material.GRAY_STAINED_GLASS_PANE, "&8вне слота"));
            return;
        }
        int argb = pixels[y][x];
        Material mat = argb == 0 ? Material.WHITE_STAINED_GLASS_PANE : InkPalette.byArgb(argb).mat();
        inventory.setItem(idx, named(mat,
                argb == 0 ? "&7пусто &8" + x + "," + y : "&fпиксель &8" + x + "," + y,
                "&81 клетка = 1 пиксель скина",
                "&8ЛКМ краска · ПКМ стереть"));
    }

    private void apply(Player artist) {
        Player client = Players.byName(clientName);
        if (client == null) {
            artist.closeInventory();
            Msg.send(plugin, artist, "&cКлиент вышел.");
            return;
        }
        if (!hasInk()) {
            Msg.send(plugin, artist, "&cНарисуй хоть одну точку.");
            return;
        }
        BufferedImage stamp = new BufferedImage(cols, rows, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int argb = pixels[y][x];
                if (argb != 0) stamp.setRGB(x, y, argb);
            }
        }
        artist.closeInventory();
        artist.playSound(artist.getLocation(), Sound.ITEM_HONEYCOMB_WAX_ON, 1f, 0.6f);
        Msg.send(plugin, artist, "&7Бью свой рисунок на &f" + slot.title + "&7...");
        boolean self = artist.getName().equalsIgnoreCase(client.getName());
        plugin.service().applyCustom(artist, client, slot, stamp, err -> {
            if (err != null) {
                Msg.send(plugin, artist, "&c" + err);
                return;
            }
            Msg.send(plugin, artist, "&aНабил свой рисунок на &f" + slot.title);
            if (!self) {
                Msg.send(plugin, client, "&dМастер набил тебе свой рисунок: &f" + slot.title);
            }
            client.playSound(client.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.7f, 0.5f);
        });
    }

    private boolean hasInk() {
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (pixels[y][x] != 0) return true;
            }
        }
        return false;
    }

    private static ItemStack named(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.text(name));
            java.util.ArrayList<net.kyori.adventure.text.Component> lines = new java.util.ArrayList<>();
            for (String line : lore) lines.add(Msg.text(line));
            meta.lore(lines);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack pane(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.text(name));
            try {
                meta.setHideTooltip(true);
            } catch (Throwable ignored) {
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
