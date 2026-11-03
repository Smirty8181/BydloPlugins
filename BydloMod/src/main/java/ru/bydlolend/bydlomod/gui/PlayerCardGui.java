package ru.bydlolend.bydlomod.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import ru.bydlolend.bydlomod.BydloMod;
import ru.bydlolend.bydlomod.mod.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayerCardGui implements Listener {
    private final WarnService warns;
    private final ReportService reports;
    private final ModerationService moderation;
    private final HistoryService history;
    private final BlackMarkService blackMarks;

    public PlayerCardGui(BydloMod plugin, WarnService warns, ReportService reports,
                         ModerationService moderation, HistoryService history, BlackMarkService blackMarks) {
        this.warns = warns;
        this.reports = reports;
        this.moderation = moderation;
        this.history = history;
        this.blackMarks = blackMarks;
    }

    public void open(Player viewer, UUID targetId, String targetName) {
        Holder holder = new Holder(targetId, targetName);
        Inventory inv = Bukkit.createInventory(holder, 54, "§dКарточка §f" + targetName);
        holder.inv = inv;

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
        sm.setOwningPlayer(op);
        sm.setDisplayName("§e" + targetName);
        List<String> headLore = new ArrayList<>();
        headLore.add(Bukkit.getPlayer(targetId) != null ? "§aонлайн" : "§cоффлайн");
        headLore.add("§7Варнов: §f" + warns.count(targetId));
        headLore.add("§7Репортов на него: §f" + reports.reportsForTarget(targetId).size());
        blackMarks.get(targetId).ifPresent(m -> headLore.add("§8Чёрная метка: §f"
                + ModerationService.formatLeft(m.expiresAt - System.currentTimeMillis())));
        sm.setLore(headLore);
        head.setItemMeta(sm);
        inv.setItem(4, head);

        // Active punishments row
        moderation.getChatMute(targetId).ifPresentOrElse(p -> inv.setItem(19, item(Material.PAPER,
                "§cМут чата (активен)",
                "§7До: §f" + (p.expiresAt == 0 ? "навсегда" : ModerationService.formatLeft(p.expiresAt - System.currentTimeMillis())),
                "§7Кем: §f" + p.staff,
                "§7" + p.reason)),
                () -> inv.setItem(19, item(Material.GRAY_DYE, "§7Мут чата: нет", "§8активных нет")));

        moderation.getVmute(targetId).ifPresentOrElse(p -> inv.setItem(20, item(Material.NOTE_BLOCK,
                "§cМут войса (активен)",
                "§7До: §f" + (p.expiresAt == 0 ? "навсегда" : ModerationService.formatLeft(p.expiresAt - System.currentTimeMillis())),
                "§7Кем: §f" + p.staff,
                "§7" + p.reason)),
                () -> inv.setItem(20, item(Material.GRAY_DYE, "§7Мут войса: нет", "§8активных нет")));

        PrisonHook.BanInfo ban = PrisonHook.getBan(targetId);
        if (ban != null) {
            String left = ban.expiresAt < 0 ? "навсегда"
                    : ModerationService.formatLeft(ban.expiresAt - System.currentTimeMillis());
            inv.setItem(21, item(Material.IRON_BARS, "§cБан/тюрьма (активен)",
                    "§7До: §f" + left, "§7Кем: §f" + ban.bannedBy, "§7" + ban.reason));
        } else {
            inv.setItem(21, item(Material.GRAY_DYE, "§7Бан: нет", "§8активных нет"));
        }

        int slot = 27;
        for (WarnService.Warn w : warns.getWarns(targetId)) {
            if (slot >= 36) break;
            inv.setItem(slot++, item(Material.YELLOW_DYE, "§eВарн #" + w.id,
                    "§7" + WarnService.formatTime(w.createdAt),
                    "§7Кем: §f" + w.staff,
                    "§7" + w.reason));
        }

        slot = 36;
        for (ReportService.Report r : reports.reportsForTarget(targetId)) {
            if (slot >= 45) break;
            inv.setItem(slot++, item(Material.WRITABLE_BOOK,
                    "§eРепорт #" + r.id + " §7" + r.status.name(),
                    "§7От: §f" + r.reporterName,
                    "§7" + WarnService.formatTime(r.createdAt),
                    "§7" + r.reason));
        }

        slot = 45;
        for (HistoryService.Entry h : history.get(targetId)) {
            if (slot >= 53) break;
            Material mat = h.action.equals("APPLY") ? Material.RED_DYE : Material.LIME_DYE;
            inv.setItem(slot++, item(mat, "§f" + h.type + " §7" + h.action,
                    "§7" + WarnService.formatTime(h.createdAt),
                    "§7Кем: §f" + h.staff,
                    "§7" + h.reason));
        }

        inv.setItem(53, item(Material.BARRIER, "§cЗакрыть", "§8закрыть"));
        viewer.openInventory(inv);
    }

    private static ItemStack item(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack cur = e.getCurrentItem();
        if (cur != null && cur.hasItemMeta() && "§cЗакрыть".equals(cur.getItemMeta().getDisplayName())) {
            p.closeInventory();
        }
    }

    static final class Holder implements InventoryHolder {
        final UUID targetId;
        final String targetName;
        Inventory inv;

        Holder(UUID targetId, String targetName) {
            this.targetId = targetId;
            this.targetName = targetName;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }
}
