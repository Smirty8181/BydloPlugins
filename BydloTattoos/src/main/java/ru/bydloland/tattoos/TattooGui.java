package ru.bydloland.tattoos;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TattooGui implements InventoryHolder, Listener {
    static final String CUSTOM_ID = "__custom__";

    private final BydloTattoosPlugin plugin;
    private Inventory inventory;
    private String clientName;
    private String selectedId;
    private View view = View.DESIGNS;
    private final Map<Integer, String> designSlots = new HashMap<>();
    private final Map<Integer, TattooSlot> bodySlots = new HashMap<>();
    private final Map<Integer, Integer> colorSlots = new HashMap<>();
    private final List<Integer> colors = new ArrayList<>();

    public TattooGui(BydloTattoosPlugin plugin) {
        this.plugin = plugin;
    }

    public static void openDesigns(BydloTattoosPlugin plugin, Player artist, Player client) {
        TattooGui holder = new TattooGui(plugin);
        holder.clientName = client.getName();
        holder.colors.add(InkPalette.BLACK.argb());
        Inventory inv = Bukkit.createInventory(holder, 54, "§5Наколка §8· §f" + client.getName());
        holder.inventory = inv;
        holder.fillDesigns();
        artist.openInventory(inv);
    }

    private void fillDesigns() {
        view = View.DESIGNS;
        designSlots.clear();
        fill(inventory);
        List<TattooDef> all = plugin.catalog().all();
        int[] cells = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < all.size() && i < cells.length; i++) {
            TattooDef def = all.get(i);
            designSlots.put(cells[i], def.id());
            inventory.setItem(cells[i], Icons.design(def));
        }
        designSlots.put(40, CUSTOM_ID);
        inventory.setItem(40, named(Material.MAP, "&dСвой рисунок", "&7Пиксель в пиксель, без растягивания", "&7Стрелки — листать холст", "&eЛКМ — холст"));
    }

    private void openColors(Player artist) {
        view = View.COLORS;
        colorSlots.clear();
        Inventory inv = Bukkit.createInventory(this, 27, "§5Чернила §8· до 3 цветов");
        this.inventory = inv;
        fillSmall(inv);
        int i = 10;
        for (InkPalette.Color c : InkPalette.ALL) {
            if (i == 17) i = 19;
            colorSlots.put(i, c.argb());
            inv.setItem(i, colorIcon(c, colors.contains(c.argb())));
            i++;
        }
        inv.setItem(4, named(Material.INK_SAC, "&fВыбрано: &d" + InkPalette.names(colors), "&7ЛКМ по краске — вкл/выкл", "&8Можно несколько, максимум 3"));
        inv.setItem(26, named(Material.LIME_DYE, "&aДальше", "&7Куда бить"));
        artist.openInventory(inv);
    }

    private void openSlots(Player artist, String id) {
        view = View.SLOTS;
        selectedId = id;
        designSlots.clear();
        bodySlots.clear();
        boolean custom = CUSTOM_ID.equals(id);
        Inventory inv = Bukkit.createInventory(this, 27, custom ? "§5Куда свой рисунок" : "§5Куда бить");
        this.inventory = inv;
        fillSmall(inv);
        TattooSlot[] slots = custom ? TattooSlot.values() : plugin.catalog().get(id).slots().toArray(TattooSlot[]::new);
        int i = 10;
        for (TattooSlot slot : slots) {
            if (i == 17) i = 19;
            bodySlots.put(i, slot);
            inv.setItem(i, Icons.body(slot));
            i++;
        }
        artist.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TattooGui holder)) return;
        if (holder.plugin != this.plugin) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player artist)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != holder.inventory) return;
        Player client = Players.byName(holder.clientName);
        if (client == null) {
            artist.closeInventory();
            Msg.send(plugin, artist, "&cКлиент вышел.");
            return;
        }
        if (holder.view == View.DESIGNS) {
            String id = holder.designSlots.get(event.getRawSlot());
            if (id == null) return;
            if (CUSTOM_ID.equals(id)) {
                holder.openSlots(artist, CUSTOM_ID);
                return;
            }
            if (plugin.catalog().get(id) == null) return;
            holder.selectedId = id;
            holder.openColors(artist);
            return;
        }
        if (holder.view == View.COLORS) {
            if (event.getRawSlot() == 26) {
                if (holder.colors.isEmpty()) {
                    Msg.send(plugin, artist, "&cВыбери хотя бы один цвет.");
                    return;
                }
                holder.openSlots(artist, holder.selectedId);
                return;
            }
            Integer argb = holder.colorSlots.get(event.getRawSlot());
            if (argb == null) return;
            if (holder.colors.contains(argb)) holder.colors.remove(argb);
            else if (holder.colors.size() < 3) holder.colors.add(argb);
            else {
                Msg.send(plugin, artist, "&cМаксимум 3 цвета.");
                return;
            }
            holder.openColors(artist);
            return;
        }
        TattooSlot slot = holder.bodySlots.get(event.getRawSlot());
        if (slot == null) return;
        if (CUSTOM_ID.equals(holder.selectedId)) {
            artist.closeInventory();
            DrawGui.open(plugin, artist, client, slot);
            return;
        }
        TattooDef def = plugin.catalog().get(holder.selectedId);
        if (def == null) return;
        artist.closeInventory();
        artist.playSound(artist.getLocation(), Sound.ITEM_HONEYCOMB_WAX_ON, 1f, 0.6f);
        Msg.send(plugin, artist, "&7Бью &f" + slot.title + " &8· &f" + InkPalette.names(holder.colors));
        boolean self = artist.getName().equalsIgnoreCase(client.getName());
        plugin.service().apply(artist, client, def, slot, List.copyOf(holder.colors), err -> {
            if (err != null) {
                Msg.send(plugin, artist, "&c" + err);
                return;
            }
            Msg.send(plugin, artist, "&aНабил &f" + Msg.legacy(def.name()) + " &7на &f" + slot.title);
            if (!self) {
                Msg.send(plugin, client, "&dМастер набил тебе тату: " + Msg.legacy(def.name()));
            }
            client.playSound(client.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.7f, 0.5f);
        });
    }

    private static ItemStack colorIcon(InkPalette.Color c, boolean on) {
        ItemStack item = new ItemStack(c.mat());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.text((on ? "&a▶ " : "&f") + c.name()));
            meta.lore(List.of(Msg.text(on ? "&aВыбран" : "&7ЛКМ — выбрать")));
            if (on) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void fill(Inventory inv) {
        ItemStack edge = pane(Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack fill = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < inv.getSize(); i++) {
            boolean border = i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8;
            inv.setItem(i, border ? edge : fill);
        }
    }

    private static void fillSmall(Inventory inv) {
        ItemStack edge = pane(Material.PURPLE_STAINED_GLASS_PANE);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, edge);
    }

    private static ItemStack named(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.text(name));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            for (String line : loreLines) lore.add(Msg.text(line));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack pane(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.text("&f"));
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

    private enum View {DESIGNS, COLORS, SLOTS}
}
