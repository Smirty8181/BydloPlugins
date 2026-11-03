package ru.bydlolend.bydlomod.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.bydlolend.bydlomod.BydloMod;
import ru.bydlolend.bydlomod.mod.ReportService;
import ru.bydlolend.bydlomod.mod.ReportService.Report;
import ru.bydlolend.bydlomod.mod.WarnService;
import ru.bydlolend.bydlomod.util.Msg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ReportsGui implements Listener {
    private final BydloMod plugin;
    private final ReportService reports;
    private final WarnService warns;
    private PlayerCardGui playerCard;

    public ReportsGui(BydloMod plugin, ReportService reports, WarnService warns) {
        this.plugin = plugin;
        this.reports = reports;
        this.warns = warns;
    }

    public void setPlayerCard(PlayerCardGui playerCard) {
        this.playerCard = playerCard;
    }

    public void openList(Player player) {
        List<Report> open = reports.openReports();
        ListHolder holder = new ListHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, "§dРепорты §7(" + open.size() + ")");
        holder.inv = inv;
        holder.slotToId = new HashMap<>();

        int slot = 0;
        for (Report r : open) {
            if (slot >= 45) break;
            ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§e#" + r.id + " §f" + r.targetName);
            List<String> lore = new ArrayList<>();
            lore.add("§7От: §f" + r.reporterName);
            lore.add("§7Когда: §f" + WarnService.formatTime(r.createdAt));
            lore.add(Bukkit.getPlayer(r.targetId) != null ? "§7Статус: §aонлайн" : "§7Статус: §cоффлайн");
            lore.add("§7Причина: §f" + r.reason);
            if (r.claimedBy != null) lore.add("§7Взял: §f" + r.claimedBy);
            lore.add("");
            lore.add("§aКлик — открыть");
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            holder.slotToId.put(slot, r.id);
            slot++;
        }

        inv.setItem(53, button(Material.BARRIER, "§cЗакрыть", "close"));
        player.openInventory(inv);
    }

    public void openDetail(Player player, int reportId) {
        Report r = reports.get(reportId);
        if (r == null) {
            Msg.err(player, "Репорт не найден");
            return;
        }
        DetailHolder holder = new DetailHolder(reportId);
        Inventory inv = Bukkit.createInventory(holder, 27, "§dРепорт #" + reportId);
        holder.inv = inv;

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§e#" + r.id + " → §c" + r.targetName);
        boolean online = Bukkit.getPlayer(r.targetId) != null;
        im.setLore(List.of(
                "§7От: §f" + r.reporterName,
                "§7Когда: §f" + WarnService.formatTime(r.createdAt),
                "§7Репорт: §f" + r.status.name(),
                online ? "§7Цель: §aонлайн" : "§7Цель: §cоффлайн",
                r.claimedBy == null ? "§8не взят" : "§7Взял: §f" + r.claimedBy,
                "§7Причина:",
                "§f" + r.reason
        ));
        info.setItemMeta(im);
        inv.setItem(4, info);

        inv.setItem(10, button(Material.ENDER_PEARL, "§aТП к цели", "teleport"));
        inv.setItem(11, button(Material.PLAYER_HEAD, "§dКарточка игрока", "card"));
        inv.setItem(12, button(Material.WRITABLE_BOOK, "§eЧат /рс", "rchat"));
        inv.setItem(14, button(Material.GOLDEN_AXE, "§eВарн + закрыть", "warn_close"));
        inv.setItem(15, button(Material.LIME_DYE, "§aЗакрыть", "close"));
        inv.setItem(16, button(Material.ORANGE_DYE, "§6Закрыть (без варна)", "close_note"));
        inv.setItem(22, button(Material.ARROW, "§7Назад", "back"));

        player.openInventory(inv);
    }

    private static ItemStack button(Material mat, String name, String action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of("§8action:" + action));
        item.setItemMeta(meta);
        return item;
    }

    private static String actionOf(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return null;
        for (String line : item.getItemMeta().getLore()) {
            if (line.startsWith("§8action:")) return line.substring("§8action:".length());
        }
        return null;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack cur = e.getCurrentItem();

        if (e.getInventory().getHolder() instanceof ListHolder list) {
            e.setCancelled(true);
            if (cur == null || !cur.hasItemMeta()) return;
            if ("close".equals(actionOf(cur))) {
                p.closeInventory();
                return;
            }
            Integer id = list.slotToId.get(e.getSlot());
            if (id != null) {
                reports.claim(id, p);
                openDetail(p, id);
            }
            return;
        }

        if (e.getInventory().getHolder() instanceof DetailHolder detail) {
            e.setCancelled(true);
            String action = actionOf(cur);
            if (action == null) return;
            Report r = reports.get(detail.reportId);
            if (r == null) {
                Msg.err(p, "Репорт не найден");
                p.closeInventory();
                return;
            }
            switch (action) {
                case "back" -> openList(p);
                case "close", "close_note" -> {
                    reports.close(detail.reportId, p.getName(),
                            "&7Репорт рассмотрен. Действий по цели не применяли.");
                    Msg.ok(p, "Репорт #" + detail.reportId + " закрыт (репортёр уведомлён)");
                    openList(p);
                }
                case "warn_close" -> {
                    if (!p.hasPermission("bydlo.warn") && !p.hasPermission("bydlo.mod") && !p.isOp()) {
                        Msg.err(p, "Нет прав на варн");
                        return;
                    }
                    var op = Bukkit.getOfflinePlayer(r.targetId);
                    String msg = warns.addWarn(op, p.getName(), "Репорт #" + r.id + ": " + r.reason);
                    p.sendMessage(plugin.prefix() + msg);
                    reports.close(detail.reportId, p.getName(),
                            "&aПо твоему репорту выдан варн цели (&f" + r.targetName + "&a).");
                    warns.notifyStaff("&e" + p.getName() + " &7варнул &c" + r.targetName
                            + " &7из репорта #" + r.id);
                    Msg.ok(p, "Варн выдан, репорт закрыт");
                    openList(p);
                }
                case "card" -> {
                    if (playerCard != null) {
                        playerCard.open(p, r.targetId, r.targetName);
                    }
                }
                case "rchat" -> {
                    Msg.tip(p, "/рс <текст> — чат с репортёром по репорту #" + r.id);
                    p.closeInventory();
                }
                case "teleport" -> {
                    Player target = Bukkit.getPlayer(r.targetId);
                    if (target == null) {
                        Msg.err(p, "Игрок оффлайн");
                        return;
                    }
                    p.teleport(target.getLocation());
                    Msg.ok(p, "ТП к " + target.getName());
                }
                default -> {
                }
            }
        }
    }

    static final class ListHolder implements InventoryHolder {
        Inventory inv;
        Map<Integer, Integer> slotToId = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    static final class DetailHolder implements InventoryHolder {
        final int reportId;
        Inventory inv;

        DetailHolder(int reportId) {
            this.reportId = reportId;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }
}
