package ru.bydloland.prefixes;

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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PrefixGui implements Listener {

    private final BydloPrefixesPlugin plugin;
    private final PrefixCreateListener createListener;

    public PrefixGui(BydloPrefixesPlugin plugin, PrefixCreateListener createListener) {
        this.plugin = plugin;
        this.createListener = createListener;
    }

    public void openPlayerList(Player admin) {
        PlayerListHolder holder = new PlayerListHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, "§dПрефиксы · игроки");
        holder.inv = inv;

        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(online);
            meta.setDisplayName("§f" + online.getName());
            PrefixDefinition cur = plugin.service().getPlayerPrefix(online);
            List<String> lore = new ArrayList<>();
            lore.add("§7Сейчас: " + (cur == null ? "§7нет" : ColorUtil.legacy(cur.display())));
            lore.add("§eЛКМ §7— выдать префикс");
            lore.add("§7UUID:" + online.getUniqueId());
            meta.setLore(lore);
            skull.setItemMeta(meta);
            inv.setItem(slot++, skull);
        }

        ItemStack manage = new ItemStack(Material.ANVIL);
        ItemMeta mm = manage.getItemMeta();
        mm.setDisplayName("§a✎ Управление префиксами");
        mm.setLore(List.of("§7Создать / удалить префиксы", "§7ID: §7__manage__"));
        manage.setItemMeta(mm);
        inv.setItem(49, manage);

        ItemStack help = new ItemStack(Material.BOOK);
        ItemMeta hm = help.getItemMeta();
        hm.setDisplayName("§eПодсказка");
        hm.setLore(List.of(
                "§7Создать: §f/префикс create <id> <текст>",
                "§7Или кнопка §aУправление",
                "§7Цвета: §d&d &l <#F5D0FE>"
        ));
        help.setItemMeta(hm);
        inv.setItem(53, help);

        admin.openInventory(inv);
    }

    public void openManage(Player admin) {
        ManageHolder holder = new ManageHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, "§dУправление префиксами");
        holder.inv = inv;

        int slot = 0;
        for (PrefixDefinition def : plugin.service().prefixes().values()) {
            if (slot >= 45) break;
            ItemStack item = new ItemStack(def.icon());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ColorUtil.legacy(def.display()));
            List<String> lore = new ArrayList<>();
            lore.add("§7ID: §7" + def.id());
            lore.add("§cShift+ЛКМ §7— удалить");
            lore.add("§7ЛКМ §7— ничего");
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }

        ItemStack create = new ItemStack(Material.EMERALD);
        ItemMeta cm = create.getItemMeta();
        cm.setDisplayName("§a＋ Создать префикс");
        cm.setLore(List.of(
                "§7Дальше пишешь в чат ID и текст",
                "§7ID: §7__create__"
        ));
        create.setItemMeta(cm);
        inv.setItem(49, create);

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§7← Назад");
        bm.setLore(List.of("§7ID: §7__back_players__"));
        back.setItemMeta(bm);
        inv.setItem(45, back);

        admin.openInventory(inv);
    }

    public void openPrefixList(Player admin, UUID targetId, String targetName) {
        PrefixListHolder holder = new PrefixListHolder(targetId, targetName);
        Inventory inv = Bukkit.createInventory(holder, 54, "§dВыдать → §f" + targetName);
        holder.inv = inv;

        int slot = 0;
        for (PrefixDefinition def : plugin.service().prefixes().values()) {
            if (slot >= 45) break;
            ItemStack item = new ItemStack(def.icon());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ColorUtil.legacy(def.display()));
            List<String> lore = new ArrayList<>();
            for (String line : def.lore()) lore.add(ColorUtil.legacy(line));
            lore.add("");
            lore.add("§7ID: §7" + def.id());
            if (def.luckPermsGroup() != null) {
                lore.add("§cПрава: §fгруппа §e" + def.luckPermsGroup());
            }
            lore.add("§eЛКМ §7— выдать");
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }

        ItemStack clear = new ItemStack(Material.BARRIER);
        ItemMeta cm = clear.getItemMeta();
        cm.setDisplayName("§cСнять префикс");
        cm.setLore(List.of("§7Убрать тег у игрока", "§7ID: §7__clear__"));
        clear.setItemMeta(cm);
        inv.setItem(49, clear);

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§7← Назад к игрокам");
        bm.setLore(List.of("§7ID: §7__back__"));
        back.setItemMeta(bm);
        inv.setItem(45, back);

        admin.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        InventoryHolder h = event.getInventory().getHolder();
        if (!(h instanceof PlayerListHolder) && !(h instanceof PrefixListHolder) && !(h instanceof ManageHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!admin.hasPermission("bydloprefixes.admin")) {
            admin.sendMessage("§cНет прав");
            return;
        }
        ItemStack cur = event.getCurrentItem();
        if (cur == null || !cur.hasItemMeta()) return;
        List<String> lore = cur.getItemMeta().getLore();
        String id = idFromLore(lore);

        if (h instanceof PlayerListHolder) {
            if ("__manage__".equals(id)) {
                openManage(admin);
                return;
            }
            if (cur.getType() == Material.BOOK) return;
            if (!(cur.getItemMeta() instanceof SkullMeta skull)) return;
            OfflinePlayer op = skull.getOwningPlayer();
            UUID uuid = op != null ? op.getUniqueId() : uuidFromLore(lore);
            if (uuid == null) return;
            String name = op != null && op.getName() != null
                    ? op.getName()
                    : ColorUtil.strip(cur.getItemMeta().getDisplayName());
            openPrefixList(admin, uuid, name);
            return;
        }

        if (h instanceof ManageHolder) {
            if ("__create__".equals(id)) {
                admin.closeInventory();
                createListener.start(admin);
                return;
            }
            if ("__back_players__".equals(id)) {
                openPlayerList(admin);
                return;
            }
            if (id != null && event.isShiftClick()) {
                if (plugin.service().deletePrefix(id)) {
                    admin.sendMessage("§cУдалён префикс §f" + id);
                    openManage(admin);
                } else {
                    admin.sendMessage("§cНе удалось удалить");
                }
            }
            return;
        }

        if (h instanceof PrefixListHolder holder) {
            if (id == null) return;
            if ("__back__".equals(id)) {
                openPlayerList(admin);
                return;
            }
            if ("__clear__".equals(id)) {
                plugin.service().clearPrefix(holder.targetId);
                notify(admin, holder, null);
                admin.closeInventory();
                return;
            }
            if (!plugin.service().setPrefix(holder.targetId, id)) {
                admin.sendMessage("§cНеизвестный префикс");
                return;
            }
            notify(admin, holder, plugin.service().get(id));
            admin.closeInventory();
        }
    }

    private void notify(Player admin, PrefixListHolder holder, PrefixDefinition def) {
        Player online = PrefixService.onlineByName(holder.targetName);
        if (online == null) online = Bukkit.getPlayer(holder.targetId);
        if (def == null) {
            admin.sendMessage("§aПрефикс снят у §f" + holder.targetName);
            if (online != null) online.sendMessage("§d[БыдлоЛенд] §7Префикс снят");
        } else {
            admin.sendMessage("§aВыдан §f" + ColorUtil.legacy(def.display()) + " §a→ §f" + holder.targetName);
            if (online != null) {
                online.sendMessage("§d[БыдлоЛенд] §fТебе выдали префикс: " + ColorUtil.legacy(def.display()));
            }
        }
    }

    private static String idFromLore(List<String> lore) {
        if (lore == null) return null;
        for (String line : lore) {
            String p = ColorUtil.strip(line);
            if (p.startsWith("ID: ")) return p.substring(4).trim();
        }
        return null;
    }

    private static UUID uuidFromLore(List<String> lore) {
        if (lore == null) return null;
        for (String line : lore) {
            String p = ColorUtil.strip(line);
            if (p.startsWith("UUID:")) {
                try {
                    return UUID.fromString(p.substring(5).trim());
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }

    public static final class PlayerListHolder implements InventoryHolder {
        Inventory inv;
        @Override
        public Inventory getInventory() { return inv; }
    }

    public static final class ManageHolder implements InventoryHolder {
        Inventory inv;
        @Override
        public Inventory getInventory() { return inv; }
    }

    public static final class PrefixListHolder implements InventoryHolder {
        final UUID targetId;
        final String targetName;
        Inventory inv;

        PrefixListHolder(UUID targetId, String targetName) {
            this.targetId = targetId;
            this.targetName = targetName;
        }

        @Override
        public Inventory getInventory() { return inv; }
    }
}
