package ru.bydloland.classes;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Меню рас. Иконки — уже рабочие CE-предметы (пиво / мифрил-кирка / шлем),
 * только имя меняем на название расы.
 */
public final class ClassGui implements InventoryHolder, Listener {

    private static final int[] RACE_SLOTS = {20, 21, 22, 23, 24};
    private static final int INFO_SLOT = 40;
    private static final int[] MARK_SLOTS = {11, 12, 13, 14, 15};

    /** Проверенные модели / CE-предметы для иконок рас. */
    private static final Map<String, String> RACE_CE_ICONS = Map.of(
            "bomzh", "bydlo:race_icon_bomzh",
            "dwarf", "bydlo:race_icon_dwarf",
            "samurai", "bydlo:race_icon_samurai",
            "netrunner", "bydlo:race_icon_netrunner",
            "mage", "bydlo:race_icon_mage"
    );

    private final BydloClassesPlugin plugin;
    private Inventory inventory;
    private final Map<Integer, String> slotToClass = new HashMap<>();

    public ClassGui(BydloClassesPlugin plugin) {
        this.plugin = plugin;
    }

    public static void open(BydloClassesPlugin plugin, Player player) {
        ClassGui holder = new ClassGui(plugin);
        Inventory inv = Bukkit.createInventory(holder, 54, "§d§l✦ §fРаса §d§l✦");
        holder.inventory = inv;

        ItemStack border = pane(Material.PINK_STAINED_GLASS_PANE);
        ItemStack fill = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack accent = pane(Material.MAGENTA_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) {
            boolean edge = i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8;
            boolean corner = i == 0 || i == 8 || i == 45 || i == 53;
            inv.setItem(i, corner ? accent.clone() : (edge ? border.clone() : fill.clone()));
        }

        boolean admin = player.hasPermission("bydloclasses.admin");
        String current = plugin.service().getPlayerClassId(player.getUniqueId());

        List<ClassDefinition> selectable = new ArrayList<>();
        for (ClassDefinition def : plugin.service().classes().values()) {
            if (!def.selectable() && !admin) continue;
            selectable.add(def);
        }

        for (int i = 0; i < selectable.size() && i < RACE_SLOTS.length; i++) {
            ClassDefinition def = selectable.get(i);
            int slot = RACE_SLOTS[i];
            holder.slotToClass.put(slot, def.id());
            inv.setItem(slot, raceIcon(def));

            if (def.id().equalsIgnoreCase(current)) {
                inv.setItem(MARK_SLOTS[i], pane(Material.LIME_STAINED_GLASS_PANE));
            }
        }

        ItemStack info = ceItem("bydlo:ui_info");
        if (info == null) info = named(Material.BOOK, "§e§lКак сменить расу");
        ItemMeta im = info.getItemMeta();
        if (im != null) {
            im.setDisplayName("§e§lКак сменить расу");
            List<String> lore = new ArrayList<>();
            lore.add("§7Смена: §fраз в 2 дня");
            lore.add("§7Кликни по иконке расы");
            if (current != null) {
                ClassDefinition cur = plugin.service().get(current);
                lore.add("§7Сейчас: " + (cur == null ? current : ColorUtil.legacy(cur.display())));
            }
            if (admin) lore.add("§dАдмин — без кулдауна");
            im.setLore(lore);
            hideFlags(im);
            info.setItemMeta(im);
        }
        inv.setItem(INFO_SLOT, info);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.45f, 1.35f);
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1.1, 0), 10, 0.35, 0.35, 0.35, 0.4);
    }

    private static ItemStack raceIcon(ClassDefinition def) {
        String id = def.id() == null ? "" : def.id().toLowerCase(Locale.ROOT);
        String ceId = RACE_CE_ICONS.get(id);
        if (ceId == null && def.iconCe() != null && !def.iconCe().isBlank()) {
            ceId = def.iconCe();
        }

        ItemStack icon = ceItem(ceId);
        String raceName = ColorUtil.legacy(def.display());
        if (icon == null) {
            Material mat = def.icon() != null ? def.icon() : Material.PAPER;
            icon = new ItemStack(mat);
        }

        List<String> lore = new ArrayList<>();
        for (String line : def.lore()) {
            lore.add(ColorUtil.legacy(line));
        }
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(raceName);
            meta.setLore(lore);
            hideFlags(meta);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private static ItemStack ceItem(String ceId) {
        if (ceId == null || ceId.isBlank()) return null;
        try {
            BukkitItemDefinition def = CraftEngineItems.byId(Key.of(ceId));
            if (def == null) return null;
            ItemStack built = def.buildBukkitItem();
            if (built == null || built.getType().isAir()) return null;
            return built;
        } catch (Throwable t) {
            return null;
        }
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
        if (!(event.getInventory().getHolder() instanceof ClassGui gui)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String id = gui.slotToClass.get(event.getRawSlot());
        if (id == null || id.isEmpty()) return;

        if (!player.hasPermission("bydloclasses.use") && !player.hasPermission("bydloclasses.admin")) {
            player.sendMessage("§cНет прав");
            return;
        }

        ClassService.Source src = player.hasPermission("bydloclasses.admin")
                ? ClassService.Source.ADMIN
                : ClassService.Source.SELF;
        ClassService.Result r = plugin.service().setClass(player, id, true, src);
        switch (r) {
            case OK -> {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1.2, 0), 14, 0.35, 0.45, 0.35, 0.02);
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.0, 0), 8, 0.4, 0.35, 0.4, 0);
                player.closeInventory();
            }
            case UNKNOWN -> player.sendMessage("§cНеизвестная раса");
            case COOLDOWN -> {
                player.sendMessage("§cСменить расу можно через §f"
                        + ClassService.formatDuration(plugin.service().remainingCooldownMs(player.getUniqueId())));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 0.9f);
                player.closeInventory();
            }
            case NOT_SELECTABLE -> player.sendMessage("§cЭту расу нельзя выбрать самому");
            case SAME -> {
                player.sendMessage("§7У тебя уже эта раса");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 0.8f);
            }
        }
    }
}
