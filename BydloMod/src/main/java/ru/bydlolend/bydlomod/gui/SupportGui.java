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
import ru.bydlolend.bydlomod.mod.SupportService;
import ru.bydlolend.bydlomod.mod.SupportService.Ticket;
import ru.bydlolend.bydlomod.mod.WarnService;
import ru.bydlolend.bydlomod.util.Msg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SupportGui implements Listener {
    private final SupportService support;

    public SupportGui(SupportService support) {
        this.support = support;
    }

    public void openStaffList(Player player) {
        List<Ticket> open = support.openTickets();
        ListHolder holder = new ListHolder(true);
        Inventory inv = Bukkit.createInventory(holder, 54, "§bПоддержка §7(" + open.size() + ")");
        holder.inv = inv;
        int slot = 0;
        for (Ticket t : open) {
            if (slot >= 45) break;
            ItemStack item = new ItemStack(Material.BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§b#" + t.id + " §f" + t.playerName);
            List<String> lore = new ArrayList<>();
            lore.add("§7" + WarnService.formatTime(t.createdAt));
            lore.add("§7" + t.body);
            if (t.claimedBy != null) lore.add("§7Взял: §f" + t.claimedBy);
            lore.add("");
            lore.add("§aКлик — взять / открыть");
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            holder.slotToId.put(slot, t.id);
            slot++;
        }
        inv.setItem(53, actionButton(Material.BARRIER, "§cЗакрыть", "close"));
        player.openInventory(inv);
    }

    public void openMine(Player player) {
        List<Ticket> mine = support.ticketsOf(player.getUniqueId());
        ListHolder holder = new ListHolder(false);
        Inventory inv = Bukkit.createInventory(holder, 27, "§bМои тикеты");
        holder.inv = inv;
        int slot = 0;
        for (Ticket t : mine) {
            if (slot >= 18) break;
            ItemStack item = new ItemStack(t.status == SupportService.Status.OPEN
                    ? Material.WRITABLE_BOOK : Material.BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§b#" + t.id + " §7" + t.status.name());
            List<String> lore = new ArrayList<>();
            lore.add("§7" + WarnService.formatTime(t.createdAt));
            lore.add("§7" + t.body);
            if (t.claimedBy != null) lore.add("§7Отвечает: §f" + t.claimedBy);
            if (t.status == SupportService.Status.OPEN && t.claimedByUuid != null) {
                lore.add("§aПиши: /пс <текст>");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        inv.setItem(22, actionButton(Material.BARRIER, "§cЗакрыть", "close"));
        player.openInventory(inv);
    }

    public void openDetail(Player staff, int ticketId) {
        Ticket t = support.get(ticketId);
        if (t == null) {
            Msg.err(staff, "Тикет не найден");
            return;
        }
        DetailHolder holder = new DetailHolder(ticketId);
        Inventory inv = Bukkit.createInventory(holder, 27, "§bТикет #" + ticketId);
        holder.inv = inv;

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§b#" + t.id + " §f" + t.playerName);
        im.setLore(List.of(
                "§7" + WarnService.formatTime(t.createdAt),
                "§7" + t.body,
                t.claimedBy == null ? "§8не взят" : "§7Взял: §f" + t.claimedBy
        ));
        info.setItemMeta(im);
        inv.setItem(4, info);

        inv.setItem(11, actionButton(Material.ENDER_PEARL, "§aТП к игроку", "teleport"));
        inv.setItem(13, actionButton(Material.WRITABLE_BOOK, "§eНаписать /пс", "hint"));
        inv.setItem(15, actionButton(Material.LIME_DYE, "§aЗакрыть тикет", "close_ticket"));
        inv.setItem(22, actionButton(Material.ARROW, "§7Назад", "back"));
        staff.openInventory(inv);
    }

    private static ItemStack actionButton(Material mat, String name, String action) {
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
            String action = actionOf(cur);
            if ("close".equals(action)) {
                p.closeInventory();
                return;
            }
            if (!list.staff) return;
            Integer id = list.slotToId.get(e.getSlot());
            if (id != null) {
                support.claim(id, p);
                openDetail(p, id);
            }
            return;
        }

        if (e.getInventory().getHolder() instanceof DetailHolder detail) {
            e.setCancelled(true);
            String action = actionOf(cur);
            if (action == null) return;
            Ticket t = support.get(detail.ticketId);
            if (t == null) {
                Msg.err(p, "Тикет не найден");
                p.closeInventory();
                return;
            }
            switch (action) {
                case "back" -> openStaffList(p);
                case "close_ticket" -> {
                    support.close(detail.ticketId, p.getName());
                    Msg.ok(p, "Тикет #" + detail.ticketId + " закрыт");
                    openStaffList(p);
                }
                case "teleport" -> {
                    Player target = Bukkit.getPlayer(t.playerId);
                    if (target == null) {
                        Msg.err(p, "Игрок оффлайн");
                        return;
                    }
                    p.teleport(target.getLocation());
                    Msg.ok(p, "ТП к " + target.getName());
                }
                case "hint" -> {
                    Msg.tip(p, "/пс <текст> — ответ по тикету #" + t.id);
                    p.closeInventory();
                }
                default -> {
                }
            }
        }
    }

    static final class ListHolder implements InventoryHolder {
        final boolean staff;
        Inventory inv;
        Map<Integer, Integer> slotToId = new HashMap<>();

        ListHolder(boolean staff) {
            this.staff = staff;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    static final class DetailHolder implements InventoryHolder {
        final int ticketId;
        Inventory inv;

        DetailHolder(int ticketId) {
            this.ticketId = ticketId;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }
}
