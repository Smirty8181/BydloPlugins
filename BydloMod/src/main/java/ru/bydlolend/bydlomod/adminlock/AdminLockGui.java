package ru.bydlolend.bydlomod.adminlock;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.bydlolend.bydlomod.BydloMod;
import ru.bydlolend.bydlomod.adminlock.AdminLockService.CommandSource;
import ru.bydlolend.bydlomod.adminlock.AdminLockService.Feature;
import ru.bydlolend.bydlomod.util.Msg;

import java.util.ArrayList;
import java.util.List;

public final class AdminLockGui implements Listener {
    private final BydloMod plugin;
    private final AdminLockService service;

    public AdminLockGui(BydloMod plugin, AdminLockService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void open(Player player) {
        LockGuiHolder holder = new LockGuiHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, "§dУправление сервером");
        holder.inventory = inv;

        fill(inv, 10, featureItem(Feature.END, Material.END_PORTAL_FRAME, "§5✦ Энд"));
        fill(inv, 11, timerItem(Feature.END));

        fill(inv, 13, featureItem(Feature.NETHER, Material.NETHERRACK, "§c✦ Ад"));
        fill(inv, 14, timerItem(Feature.NETHER));

        fill(inv, 16, featureItem(Feature.VILLAGER_TRADES, Material.EMERALD, "§a✦ Торги"));
        fill(inv, 17, timerItem(Feature.VILLAGER_TRADES));

        fill(inv, 22, infoItem());
        fill(inv, 26, closeItem());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof LockGuiHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!player.hasPermission("bydlo.adminlock")) {
            Msg.err(player, "Нет прав");
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || !current.hasItemMeta()) {
            return;
        }
        String id = actionId(current);
        if (id == null) {
            return;
        }
        if (id.equals("__close__")) {
            player.closeInventory();
            return;
        }
        if (id.startsWith("toggle:")) {
            Feature feature = Feature.valueOf(id.substring("toggle:".length()));
            boolean newValue = !service.isLocked(feature);
            service.setLocked(feature, newValue, new CommandSource(player));
            Msg.ok(player, feature.title() + (newValue ? " закрыт" : " открыт"));
            open(player);
            return;
        }
        if (id.startsWith("timer:")) {
            Feature feature = Feature.valueOf(id.substring("timer:".length()));
            if (event.isRightClick()) {
                service.clearUnlockAt(feature, new CommandSource(player));
                open(player);
                return;
            }
            service.promptTimerInput(player, feature);
        }
    }

    private ItemStack featureItem(Feature feature, Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        lore.add(service.statusLine(feature));
        lore.add(service.remainingLine(feature));
        lore.add("");
        lore.add("§eЛКМ §7— " + (service.isLocked(feature) ? "открыть" : "закрыть"));
        lore.add("§8ID: toggle:" + feature.name());
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack timerItem(Feature feature) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Таймер: " + feature.title());
        List<String> lore = new ArrayList<>();
        lore.add(service.remainingLine(feature));
        lore.add("§7До: §f" + AdminLockService.formatDateTime(service.unlockAt(feature)));
        lore.add("");
        lore.add("§eЛКМ §7— поставить таймер");
        lore.add("§eПКМ §7— убрать таймер");
        lore.add("§8ID: timer:" + feature.name());
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack infoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§eКак пользоваться");
        meta.setLore(List.of(
                "§7ЛКМ по миру — открыть / закрыть",
                "§7ЛКМ по часам — ввести время в чат",
                "§7ПКМ по часам — убрать таймер",
                "§7Форматы: §f30m§7, §f2h§7, §f1d",
                "§7Или: §f23:30 §7/ §f21.07 18:00"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack closeItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§cЗакрыть");
        meta.setLore(List.of("§8ID: __close__"));
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inventory, int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    private String actionId(ItemStack item) {
        if (!item.hasItemMeta()) {
            return null;
        }
        List<String> lore = item.getItemMeta().getLore();
        if (lore == null) {
            return null;
        }
        for (String line : lore) {
            String stripped = org.bukkit.ChatColor.stripColor(line);
            if (stripped.startsWith("ID: ")) {
                return stripped.substring(4).trim();
            }
        }
        return null;
    }

    public static final class LockGuiHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
