package ru.bydloland.mage;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TowerGui implements Listener {

    private final BydloMagePlugin plugin;
    private final Map<UUID, Boolean> open = new ConcurrentHashMap<>();

    public TowerGui(BydloMagePlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder(), 27, "§dБашня магии");
        fill(player, inv);
        open.put(player.getUniqueId(), true);
        player.openInventory(inv);
    }

    private void fill(Player player, Inventory inv) {
        inv.clear();
        int lvl = plugin.progress().level(player);
        ItemStack glass = CeItems.named(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        inv.setItem(11, levelIcon(1, lvl, "§fУровень 1 §7— база",
                List.of("§7Посох копания 3×3", "§7КД: 8с / 12с", lvl >= 1 ? "§aОткрыто" : "")));
        inv.setItem(13, levelIcon(2, lvl, "§fУровень 2 §7— ускорение",
                List.of("§7КД посохов −20%", "§eЦена: 8 эфириум. кристаллов",
                        lvl >= 2 ? "§aКуплено" : (lvl == 1 ? "§eЛКМ — купить" : "§7Сначала ур.1"))));
        inv.setItem(15, levelIcon(3, lvl, "§fУровень 3 §7— глубина",
                List.of("§7Копание 3×3×2", "§eЦена: 16 эфириум. кристаллов",
                        lvl >= 3 ? "§aКуплено" : (lvl == 2 ? "§eЛКМ — купить" : "§7Сначала ур.2"))));

        ItemStack info = CeItems.of("bydlo:etherium_crystal");
        if (info == null) info = CeItems.named(Material.AMETHYST_SHARD, "§dЭфириум");
        ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§dТвой уровень: §f" + lvl + "/3");
            meta.setLore(List.of("§7Кристаллы — валюта апгрейда"));
            info.setItemMeta(meta);
        }
        inv.setItem(22, info);
    }

    private ItemStack levelIcon(int want, int have, String name, List<String> lore) {
        Material mat = have >= want ? Material.ENCHANTED_BOOK : Material.BOOK;
        ItemStack s = new ItemStack(mat);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.setDisplayName(name);
            m.setLore(lore);
            s.setItemMeta(m);
        }
        return s;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (!open.containsKey(player.getUniqueId())) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        int lvl = plugin.progress().level(player);
        if (slot == 13 && lvl == 1) {
            tryUpgrade(player, 2);
        } else if (slot == 15 && lvl == 2) {
            tryUpgrade(player, 3);
        }
        fill(player, event.getInventory());
    }

    private void tryUpgrade(Player player, int to) {
        int cost = plugin.progress().upgradeCost(to);
        if (!takeCrystals(player, cost)) {
            player.sendMessage("§cНужно §f" + cost + " §cэфириумовых кристаллов.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        plugin.progress().setLevel(player, to);
        player.sendMessage("§aБашня магии: уровень §f" + to);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
    }

    private boolean takeCrystals(Player player, int amount) {
        int left = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null) continue;
            String id = CeItems.idOf(stack);
            boolean crystal = "bydlo:etherium_crystal".equals(id);
            if (!crystal) {
                var meta = stack.getItemMeta();
                if (meta != null && meta.hasDisplayName() && meta.getDisplayName().contains("Эфириумов")) {
                    crystal = true;
                }
            }
            if (!crystal) continue;
            int take = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            left -= take;
            if (stack.getAmount() <= 0) contents[i] = null;
        }
        player.getInventory().setContents(contents);
        return left <= 0;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player p) {
            open.remove(p.getUniqueId());
        }
    }

    private static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
