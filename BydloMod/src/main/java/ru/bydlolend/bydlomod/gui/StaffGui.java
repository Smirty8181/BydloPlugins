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
import ru.bydlolend.bydlomod.mod.PrisonHook;
import ru.bydlolend.bydlomod.mod.ReportService;
import ru.bydlolend.bydlomod.mod.SupportService;
import ru.bydlolend.bydlomod.spec.SpecService;
import ru.bydlolend.bydlomod.util.Msg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StaffGui implements Listener {
    private final ReportService reports;
    private final SupportService support;
    private final ReportsGui reportsGui;
    private final SupportGui supportGui;
    private final PlayerCardGui playerCard;
    private final SpecService spec;

    public StaffGui(ReportService reports, SupportService support, ReportsGui reportsGui,
                    SupportGui supportGui, PlayerCardGui playerCard, SpecService spec) {
        this.reports = reports;
        this.support = support;
        this.reportsGui = reportsGui;
        this.supportGui = supportGui;
        this.playerCard = playerCard;
        this.spec = spec;
    }

    public void openMain(Player player) {
        MainHolder holder = new MainHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, "§dСтафф");
        holder.inv = inv;

        int openReports = reports.openReports().size();
        int openTickets = support.openTickets().size();

        inv.setItem(10, button(Material.WRITABLE_BOOK, "§dРепорты §7(" + openReports + ")",
                "reports", "§7Открыть очередь"));
        inv.setItem(11, button(Material.BOOK, "§bТикеты §7(" + openTickets + ")",
                "tickets", "§7Поддержка игроков"));
        inv.setItem(12, button(Material.ENDER_EYE, spec.isWatching(player.getUniqueId())
                        ? "§cСпек: выкл" : "§dСпек",
                spec.isWatching(player.getUniqueId()) ? "spec_off" : "players_spec",
                "§7Тихо следить · цель не видит"));
        inv.setItem(13, button(Material.GHAST_TEAR, "§fВаниш",
                "vanish", "§7/sv — скрыться"));
        inv.setItem(14, button(Material.SPYGLASS, "§eCoreProtect",
                "co", "§7Режим инспектора /co i"));
        inv.setItem(15, button(Material.CHEST, "§6Инвентарь",
                "players_inv", "§7Открыть инвентарь игрока"));
        inv.setItem(16, button(Material.IRON_BARS, "§cТюрьма",
                "players_prison", "§7Посадить в клетку"));

        inv.setItem(19, button(Material.PLAYER_HEAD, "§dКарточка",
                "players_card", "§7Варны / муты / баны"));
        inv.setItem(20, button(Material.ENDER_CHEST, "§5Эндер",
                "players_ender", "§7Эндер-сундук"));

        int slot = 27;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 53) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(online);
            sm.setDisplayName("§f" + online.getName());
            List<String> lore = new ArrayList<>();
            lore.add("§7клик — действия");
            if (PrisonHook.isImprisoned(online.getUniqueId())) lore.add("§cв клетке");
            sm.setLore(lore);
            head.setItemMeta(sm);
            inv.setItem(slot, head);
            holder.slotToPlayer.put(slot, online.getUniqueId());
            slot++;
        }

        inv.setItem(53, button(Material.BARRIER, "§cЗакрыть", "close"));
        player.openInventory(inv);
    }

    public void openPlayers(Player staff, String action) {
        String title = switch (action) {
            case "spec" -> "§dСпек · кого";
            case "inv" -> "§6Инвс · кого";
            case "ender" -> "§5Эндер · кого";
            case "prison" -> "§cТюрьма · кого";
            case "card" -> "§dКарточка · кого";
            default -> "§dИгрок";
        };
        PickHolder holder = new PickHolder(action);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.inv = inv;
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(online);
            sm.setDisplayName("§f" + online.getName());
            sm.setLore(List.of("§7" + actionLabel(action)));
            head.setItemMeta(sm);
            inv.setItem(slot, head);
            holder.slotToPlayer.put(slot, online.getUniqueId());
            slot++;
        }
        inv.setItem(49, button(Material.ARROW, "§7Назад", "back"));
        staff.openInventory(inv);
    }

    public void openPlayer(Player staff, UUID targetId, String targetName) {
        PlayerHolder holder = new PlayerHolder(targetId, targetName);
        Inventory inv = Bukkit.createInventory(holder, 27, "§d" + targetName);
        holder.inv = inv;

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(Bukkit.getOfflinePlayer(targetId));
        sm.setDisplayName("§e" + targetName);
        boolean online = Bukkit.getPlayer(targetId) != null;
        sm.setLore(List.of(online ? "§aонлайн" : "§cоффлайн"));
        head.setItemMeta(sm);
        inv.setItem(4, head);

        inv.setItem(10, button(Material.NAME_TAG, "§dКарточка", "card"));
        inv.setItem(11, button(Material.CHEST, "§6Инвс", "inv"));
        inv.setItem(12, button(Material.ENDER_CHEST, "§5Эндер", "ender"));
        inv.setItem(13, button(Material.ENDER_EYE, "§dСпек", "spec"));
        inv.setItem(14, button(Material.IRON_BARS, "§cТюрьма 1ч", "prison_1h"));
        inv.setItem(15, button(Material.CLOCK, "§cТюрьма 1д", "prison_1d"));
        inv.setItem(16, button(Material.BARRIER, "§4Навсегда", "prison_perm"));
        inv.setItem(22, button(Material.ARROW, "§7Назад", "back"));
        staff.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack cur = e.getCurrentItem();

        if (e.getInventory().getHolder() instanceof MainHolder main) {
            e.setCancelled(true);
            String action = actionOf(cur);
            if (action == null) {
                UUID id = main.slotToPlayer.get(e.getSlot());
                if (id != null) {
                    Player t = Bukkit.getPlayer(id);
                    if (t != null) openPlayer(p, id, t.getName());
                }
                return;
            }
            switch (action) {
                case "close" -> p.closeInventory();
                case "reports" -> reportsGui.openList(p);
                case "tickets" -> supportGui.openStaffList(p);
                case "vanish" -> {
                    p.closeInventory();
                    p.performCommand("sv");
                }
                case "co" -> {
                    p.closeInventory();
                    p.performCommand("co i");
                    Msg.tip(p, "Инспектор CoreProtect. Ещё раз /co i — выкл");
                }
                case "spec_off" -> {
                    p.closeInventory();
                    spec.stop(p, true);
                }
                case "players_spec" -> openPlayers(p, "spec");
                case "players_inv" -> openPlayers(p, "inv");
                case "players_ender" -> openPlayers(p, "ender");
                case "players_prison" -> openPlayers(p, "prison");
                case "players_card" -> openPlayers(p, "card");
                default -> {
                }
            }
            return;
        }

        if (e.getInventory().getHolder() instanceof PickHolder pick) {
            e.setCancelled(true);
            if ("back".equals(actionOf(cur))) {
                openMain(p);
                return;
            }
            UUID id = pick.slotToPlayer.get(e.getSlot());
            if (id == null) return;
            Player t = Bukkit.getPlayer(id);
            if (t == null) {
                Msg.err(p, "Игрок вышел");
                return;
            }
            apply(p, t, pick.action);
            return;
        }

        if (e.getInventory().getHolder() instanceof PlayerHolder ph) {
            e.setCancelled(true);
            String action = actionOf(cur);
            if (action == null) return;
            if ("back".equals(action)) {
                openMain(p);
                return;
            }
            Player t = Bukkit.getPlayer(ph.targetId);
            if (t == null && !action.equals("card")) {
                Msg.err(p, "Игрок оффлайн");
                return;
            }
            if ("card".equals(action)) {
                playerCard.open(p, ph.targetId, ph.targetName);
                return;
            }
            apply(p, t, action);
        }
    }

    private void apply(Player staff, Player target, String action) {
        switch (action) {
            case "spec" -> {
                staff.closeInventory();
                spec.start(staff, target);
            }
            case "inv" -> {
                staff.closeInventory();
                staff.performCommand("invsee " + target.getName());
            }
            case "ender" -> {
                staff.closeInventory();
                staff.performCommand("endersee " + target.getName());
            }
            case "card" -> playerCard.open(staff, target.getUniqueId(), target.getName());
            case "prison", "prison_1h" -> prison(staff, target, "1ч");
            case "prison_1d" -> prison(staff, target, "1д");
            case "prison_perm" -> prison(staff, target, "навсегда");
            default -> {
            }
        }
    }

    private void prison(Player staff, Player target, String duration) {
        staff.closeInventory();
        staff.performCommand("бан " + target.getName() + " " + duration + " стафф-меню");
    }

    private static String actionLabel(String action) {
        return switch (action) {
            case "spec" -> "тихо следить";
            case "inv" -> "открыть инвентарь";
            case "ender" -> "эндер-сундук";
            case "prison" -> "посадить";
            case "card" -> "карточка";
            default -> "выбрать";
        };
    }

    private static ItemStack button(Material mat, String name, String action, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> lines = new ArrayList<>();
        for (String l : lore) lines.add(l);
        lines.add("§8action:" + action);
        meta.setLore(lines);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack button(Material mat, String name, String action) {
        return button(mat, name, action, new String[0]);
    }

    private static String actionOf(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return null;
        for (String line : item.getItemMeta().getLore()) {
            if (line.startsWith("§8action:")) return line.substring("§8action:".length());
        }
        return null;
    }

    static final class MainHolder implements InventoryHolder {
        Inventory inv;
        final Map<Integer, UUID> slotToPlayer = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    static final class PickHolder implements InventoryHolder {
        final String action;
        Inventory inv;
        final Map<Integer, UUID> slotToPlayer = new HashMap<>();

        PickHolder(String action) {
            this.action = action;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    static final class PlayerHolder implements InventoryHolder {
        final UUID targetId;
        final String targetName;
        Inventory inv;

        PlayerHolder(UUID targetId, String targetName) {
            this.targetId = targetId;
            this.targetName = targetName;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }
}
