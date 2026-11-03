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
import org.bukkit.inventory.meta.SkullMeta;
import ru.bydlolend.bydlomod.BydloMod;
import ru.bydlolend.bydlomod.mod.WarnService;
import ru.bydlolend.bydlomod.mod.WarnService.Warn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WarnsGui implements Listener {
    private final BydloMod plugin;
    private final WarnService warns;

    public WarnsGui(BydloMod plugin, WarnService warns) {
        this.plugin = plugin;
        this.warns = warns;
    }

    public void open(Player viewer, UUID targetId, String targetName) {
        List<Warn> list = warns.getWarns(targetId);
        Holder holder = new Holder(targetId, targetName);
        Inventory inv = Bukkit.createInventory(holder, 54, "§dВарны: §f" + targetName + " §7(" + list.size() + ")");
        holder.inv = inv;

        int slot = 0;
        for (Warn w : list) {
            if (slot >= 45) break;
            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta meta = paper.getItemMeta();
            meta.setDisplayName("§eВарн #" + w.id);
            List<String> lore = new ArrayList<>();
            lore.add("§7Кто: §f" + w.staff);
            lore.add("§7Когда: §f" + WarnService.formatTime(w.createdAt));
            lore.add("§7Причина:");
            lore.add("§f" + w.reason);
            lore.add("");
            lore.add("§8ID: " + w.id);
            meta.setLore(lore);
            paper.setItemMeta(meta);
            inv.setItem(slot++, paper);
        }

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setDisplayName("§d" + targetName);
        sm.setLore(List.of("§7Всего варнов: §f" + list.size(),
                "§7Автомут с: §f" + plugin.getConfig().getInt("warns.auto-mute-count", 3)));
        sm.setOwningPlayer(Bukkit.getOfflinePlayer(targetId));
        head.setItemMeta(sm);
        inv.setItem(49, head);

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName("§cЗакрыть");
        close.setItemMeta(cm);
        inv.setItem(53, close);

        viewer.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack cur = e.getCurrentItem();
        if (cur == null || !cur.hasItemMeta()) return;
        String name = cur.getItemMeta().getDisplayName();
        if ("§cЗакрыть".equals(name)) {
            p.closeInventory();
            return;
        }
        if (cur.getType() == Material.PAPER && cur.getItemMeta().hasLore()) {
            for (String line : cur.getItemMeta().getLore()) {
                p.sendMessage(BydloMod.color("&7" + line.replace('§', '&')));
            }
        }
    }

    public static final class Holder implements InventoryHolder {
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
