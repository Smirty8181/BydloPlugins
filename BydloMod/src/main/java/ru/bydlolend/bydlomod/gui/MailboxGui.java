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
import ru.bydlolend.bydlomod.mail.LetterService;
import ru.bydlolend.bydlomod.mail.LetterService.Letter;
import ru.bydlolend.bydlomod.mod.WarnService;
import ru.bydlolend.bydlomod.util.Msg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MailboxGui implements Listener {
    private final LetterService letters;

    public MailboxGui(LetterService letters) {
        this.letters = letters;
    }

    public void open(Player player) {
        List<Letter> inbox = letters.inbox(player.getUniqueId());
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 54, "§dПочта §7(" + inbox.size() + ")");
        holder.inv = inv;

        int slot = 0;
        for (Letter l : inbox) {
            if (slot >= 45) break;
            ItemStack item = new ItemStack(l.delivered ? Material.WRITTEN_BOOK : Material.WRITABLE_BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§dПисьмо от §f" + l.fromName);
            List<String> lore = new ArrayList<>();
            lore.add("§7" + WarnService.formatTime(l.createdAt));
            lore.add(l.delivered ? "§8уже выдано книгой" : "§eждёт выдачи");
            String preview = l.body.length() > 40 ? l.body.substring(0, 40) + "…" : l.body;
            lore.add("§f" + preview.replace('\n', ' '));
            lore.add("");
            lore.add("§aКлик — открыть письмо");
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            holder.slotToId.put(slot, l.id);
            slot++;
        }

        ItemStack help = new ItemStack(Material.FEATHER);
        ItemMeta hm = help.getItemMeta();
        hm.setDisplayName("§dНаписать");
        hm.setLore(List.of("§7/почта <ник> <текст>", "§8придёт настоящей книгой"));
        help.setItemMeta(hm);
        inv.setItem(49, help);
        inv.setItem(53, button(Material.BARRIER, "§cЗакрыть"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        e.setCancelled(true);
        if (e.getSlot() == 53) {
            p.closeInventory();
            return;
        }
        if (e.getSlot() == 49) {
            p.closeInventory();
            Msg.tip(p, "/почта <ник> <текст>");
            return;
        }
        Integer id = holder.slotToId.get(e.getSlot());
        if (id == null) return;
        Letter letter = letters.get(id);
        if (letter == null || !letter.toId.equals(p.getUniqueId())) return;
        p.closeInventory();
        letters.openBook(p, letter);
    }

    private static ItemStack button(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    static final class Holder implements InventoryHolder {
        Inventory inv;
        final Map<Integer, Integer> slotToId = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }
}
