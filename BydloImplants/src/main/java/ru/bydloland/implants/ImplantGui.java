package ru.bydloland.implants;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
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
import java.util.UUID;

public final class ImplantGui implements InventoryHolder, Listener {

    private static final int[] CAT_SLOTS = {10, 11, 12, 13, 14, 15};
    private static final int INFO_SLOT = 40;
    private static final int CLOSE_SLOT = 49;
    private static final int REMOVER_SLOT = 53;

    private final BydloImplantsPlugin plugin;
    private Inventory inventory;
    private final Map<Integer, String> slotToCategory = new HashMap<>();
    private UUID viewer;

    public ImplantGui(BydloImplantsPlugin plugin) {
        this.plugin = plugin;
    }

    public static void open(BydloImplantsPlugin plugin, Player player) {
        ImplantGui holder = new ImplantGui(plugin);
        holder.viewer = player.getUniqueId();
        Inventory inv = Bukkit.createInventory(holder, 54, "§d§l✦ §fИмпланты §d§l✦");
        holder.inventory = inv;

        ItemStack border = pane(Material.PINK_STAINED_GLASS_PANE);
        ItemStack fill = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack accent = pane(Material.MAGENTA_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) {
            boolean edge = i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8;
            boolean corner = i == 0 || i == 8 || i == 45 || i == 53;
            inv.setItem(i, corner ? accent.clone() : (edge ? border.clone() : fill.clone()));
        }

        ImplantService svc = plugin.service();
        for (int i = 0; i < ImplantService.CATEGORIES.length && i < CAT_SLOTS.length; i++) {
            String cat = ImplantService.CATEGORIES[i];
            int slot = CAT_SLOTS[i];
            holder.slotToCategory.put(slot, cat);
            inv.setItem(slot, categoryIcon(svc, player, cat));
        }

        ItemStack info = named(Material.BOOK, "§e§lИнфо");
        ItemMeta im = info.getItemMeta();
        if (im != null) {
            List<String> lore = new ArrayList<>();
            lore.add("§7Максимум сразу: §f" + svc.maxInstalled());
            lore.add("§7Стоимость L1/L2/L3: §f"
                    + svc.costForLevel(1) + "§7/§f" + svc.costForLevel(2) + "§7/§f" + svc.costForLevel(3));
            lore.add("§7Титановых слитков: §f" + svc.countTitan(player));
            lore.add("");
            lore.add("§eЛКМ §7— установить / апгрейд");
            lore.add("§eПКМ §7— сменить вариант импланта");
            lore.add("§eShift+съёмник §7— снять");
            im.setLore(lore);
            hideFlags(im);
            info.setItemMeta(im);
        }
        inv.setItem(INFO_SLOT, info);

        inv.setItem(CLOSE_SLOT, named(Material.BARRIER, "§cЗакрыть"));

        ItemStack rem = named(Material.SHEARS, "§bСъёмник имплантов");
        ItemMeta rm = rem.getItemMeta();
        if (rm != null) {
            rm.setDisplayName("§bСъёмник имплантов");
            rm.setLore(List.of(
                    "§7Держи в руке §f§oСъёмник§7",
                    "§7затем §eShift+клик§7 по категории."
            ));
            hideFlags(rm);
            rem.setItemMeta(rm);
        }
        inv.setItem(REMOVER_SLOT, rem);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 1.4f);
    }

    private static ItemStack categoryIcon(ImplantService svc, Player player, String cat) {
        int lvl = svc.getLevel(player.getUniqueId(), cat);
        String variant = svc.selectedOrDefault(player.getUniqueId(), cat);

        ItemStack icon = null;
        String ceId = svc.iconCe(cat);
        if (ceId != null && !ceId.isBlank()) {
            icon = CeItems.build(ceId);
        }
        if (icon == null) {
            icon = named(svc.iconMaterial(cat), svc.displayName(cat));
        } else {
            ItemMeta nameMeta = icon.getItemMeta();
            if (nameMeta != null) {
                nameMeta.setDisplayName(svc.displayName(cat));
                icon.setItemMeta(nameMeta);
            }
        }
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>(svc.categoryLore(cat));
            lore.add("");
            lore.add("§7Вариант: " + svc.variantDisplay(cat, variant));
            List<String> levels = svc.variantLevels(cat, variant);
            if (!levels.isEmpty()) {
                lore.add("§7—— эффекты ——");
                for (int i = 0; i < levels.size(); i++) {
                    String line = levels.get(i);
                    if (lvl > 0 && i + 1 == lvl) {
                        lore.add("§a▶ " + stripLeading(line));
                    } else if (lvl > 0 && i + 1 < lvl) {
                        lore.add("§2✓ " + stripLeading(line));
                    } else {
                        lore.add(line);
                    }
                }
            }
            lore.add("");
            if (lvl <= 0) {
                lore.add("§7Статус: §cне установлен");
                lore.add("§7Установить L1 за §f" + svc.costForLevel(1) + " §7титана");
                lore.add("§eПКМ §7— другой вариант");
            } else if (lvl >= 3) {
                lore.add("§7Уровень: §a§lIII §7(макс)");
                lore.add("§7Вариант зафиксирован: " + svc.variantDisplay(cat, variant));
            } else {
                lore.add("§7Уровень: §a" + toRoman(lvl));
                lore.add("§7Апгрейд до L" + (lvl + 1) + " за §f" + svc.costForLevel(lvl + 1) + " §7титана");
            }
            lore.add("§7Shift+съёмник — снять");
            meta.setLore(lore);
            hideFlags(meta);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private static String stripLeading(String line) {
        return line.replaceFirst("^§[0-9a-fk-or]", "").trim();
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(n);
        };
    }

    private static ItemStack named(Material mat, String name) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of());
            hideFlags(meta);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack pane(Material mat) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.setLore(List.of());
            hideFlags(meta);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static void hideFlags(ItemMeta meta) {
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_DESTROYS,
                ItemFlag.HIDE_PLACED_ON,
                ItemFlag.HIDE_DYE,
                ItemFlag.HIDE_ARMOR_TRIM
        );
        try {
            meta.addItemFlags(ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP"));
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ImplantGui gui)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (gui.viewer != null && !gui.viewer.equals(player.getUniqueId())) return;

        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == INFO_SLOT || slot == REMOVER_SLOT) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.2f);
            return;
        }

        String cat = gui.slotToCategory.get(slot);
        if (cat == null) return;

        if (!player.hasPermission("bydloimplants.use") && !player.hasPermission("bydloimplants.admin")) {
            player.sendMessage("§cНет прав");
            return;
        }

        ImplantService svc = plugin.service();
        boolean shift = event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT;
        boolean right = event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT;

        if (shift && svc.isHoldingRemover(player)) {
            String err = svc.tryUninstall(player, cat);
            if (err != null) {
                player.sendMessage(err);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.45f, 0.9f);
            } else {
                player.sendMessage("§bИмплант §f" + svc.displayName(cat) + " §bснят.");
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.35f, 1.6f);
                open(plugin, player);
            }
            return;
        }

        if (shift && !svc.isHoldingRemover(player) && !right) {
            player.sendMessage("§cДля снятия держи съёмник в руке (Shift+клик).");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.4f, 1.0f);
            return;
        }

        // ПКМ — смена варианта (только если ещё не установлен)
        if (right && !shift) {
            if (svc.getLevel(player.getUniqueId(), cat) > 0) {
                player.sendMessage("§cВариант меняется только до установки. Сними имплант съёмником.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.4f, 1.0f);
                return;
            }
            String next = svc.cyclePendingVariant(player.getUniqueId(), cat);
            player.sendMessage("§bВариант: " + svc.variantDisplay(cat, next));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.45f, 1.35f);
            open(plugin, player);
            return;
        }

        int before = svc.getLevel(player.getUniqueId(), cat);
        String err = svc.tryInstall(player, cat);
        if (err != null) {
            player.sendMessage(err);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.45f, 0.9f);
            return;
        }
        int after = svc.getLevel(player.getUniqueId(), cat);
        String var = svc.getVariant(player.getUniqueId(), cat);
        if (before == 0) {
            player.sendMessage("§aУстановлен §f" + svc.displayName(cat) + " §7("
                    + svc.variantDisplay(cat, var) + "§7) §aL" + after);
        } else {
            player.sendMessage("§aАпгрейд §f" + svc.displayName(cat) + " §a→ L" + after);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.5f);
        open(plugin, player);
    }
}
