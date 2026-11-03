package ru.bydloland.classes;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Самурай: эксклюзивный крафт стали/катан/брони и плавка стали.
 * Носить снаряжение могут все.
 */
public final class SamuraiGearListener implements Listener {

    private static final Set<String> CRAFT_IDS = Set.of(
            "bydlo:katana", "bydlo:wakizashi", "bydlo:odachi",
            "bydlo:samurai_helmet", "bydlo:samurai_chestplate",
            "bydlo:samurai_leggings", "bydlo:samurai_boots",
            "bydlo:steel_ingot",
            "bydlo:green_tea", "bydlo:japan_lantern", "bydlo:tatami", "bydlo:norimono"
    );
    private static final Set<String> STEEL_IDS = Set.of("bydlo:steel_ingot");

    private final BydloClassesPlugin plugin;
    private final Map<String, UUID> cookerOpener = new ConcurrentHashMap<>();

    public SamuraiGearListener(BydloClassesPlugin plugin) {
        this.plugin = plugin;
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        try {
            Key id = CraftEngineItems.getCustomItemId(stack);
            return id == null ? null : id.asString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String plainName(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || !stack.getItemMeta().hasDisplayName()) return "";
        return ChatColor.stripColor(stack.getItemMeta().getDisplayName()).toLowerCase(Locale.ROOT);
    }

    private static boolean isCraftLocked(ItemStack stack) {
        String id = itemId(stack);
        return id != null && CRAFT_IDS.contains(id);
    }

    private static boolean isSteel(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        String id = itemId(stack);
        if (id != null && STEEL_IDS.contains(id)) return true;
        String n = plainName(stack);
        return n.contains("сталь") && !n.contains("flint");
    }

    /**
     * Рецепт CE: ванильный iron_ingot → сталь. Ваниль не плавит слиток железа повторно,
     * значит любая плавка plain iron_ingot — это сталь.
     */
    private static boolean isSteelSmelt(ItemStack source, ItemStack result) {
        if (isSteel(result)) return true;
        if (source == null || source.getType() != Material.IRON_INGOT) return false;
        String sid = itemId(source);
        // обычный железный слиток (не мифрил/сталь) в печи = сталь
        return sid == null;
    }

    private boolean canCraft(Player player) {
        return BydloClassesAPI.canUseSamuraiGear(player);
    }

    private boolean canSteel(Player player) {
        return BydloClassesAPI.canSmeltSteel(player);
    }

    private void deny(Player player, String msg) {
        player.sendMessage("§c" + msg);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.5f, 0.9f);
    }

    private Player openerOf(org.bukkit.Location loc) {
        if (loc == null) return null;
        UUID id = cookerOpener.get(locKey(loc));
        return id == null ? null : Bukkit.getPlayer(id);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpenCooker(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        InventoryType type = inv.getType();
        if (type != InventoryType.FURNACE && type != InventoryType.BLAST_FURNACE && type != InventoryType.SMOKER) {
            return;
        }
        if (inv.getLocation() == null) return;
        cookerOpener.put(locKey(inv.getLocation()), player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCook(BlockCookEvent event) {
        if (!isSteelSmelt(event.getSource(), event.getResult())) return;
        Player player = openerOf(event.getBlock().getLocation());
        if (player != null && canSteel(player)) return;
        event.setCancelled(true);
        if (player != null) deny(player, "Сталь могут плавить только самураи.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(FurnaceBurnEvent event) {
        if (!(event.getBlock().getState() instanceof org.bukkit.block.Furnace furnace)) return;
        ItemStack input = furnace.getInventory().getSmelting();
        if (input == null || input.getType() != Material.IRON_INGOT) return;
        if (itemId(input) != null) return; // не ванильный слиток
        Player player = openerOf(event.getBlock().getLocation());
        if (player != null && canSteel(player)) return;
        event.setCancelled(true);
        if (player != null) deny(player, "Сталь могут плавить только самураи.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (!isCraftLocked(result) && !isSteel(result)) return;
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        if (isSteel(result) || "bydlo:steel_ingot".equals(itemId(result))) {
            if (!canSteel(player)) event.getInventory().setResult(null);
            return;
        }
        if (!canCraft(player)) event.getInventory().setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (!isCraftLocked(result) && !isSteel(result)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (isSteel(result)) {
            if (!canSteel(player)) {
                event.setCancelled(true);
                deny(player, "Сталь могут ковать только самураи.");
            }
            return;
        }
        if (!canCraft(player)) {
            event.setCancelled(true);
            deny(player, "Крафтить это могут только самураи.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top instanceof FurnaceInventory furnace)) return;

        // забрать результат-сталь
        if (event.getRawSlot() == 2 && isSteel(furnace.getResult()) && !canSteel(player)) {
            event.setCancelled(true);
            deny(player, "Сталь могут плавить только самураи.");
            return;
        }

        // положить ванильный iron_ingot в слот плавки — только самурай
        if (!canSteel(player) && isPlacingVanillaIron(event, furnace)) {
            event.setCancelled(true);
            deny(player, "Сталь могут плавить только самураи.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory() instanceof FurnaceInventory)) return;
        if (canSteel(player)) return;
        ItemStack old = event.getOldCursor();
        if (old == null || old.getType() != Material.IRON_INGOT || itemId(old) != null) return;
        if (event.getRawSlots().contains(0)) {
            event.setCancelled(true);
            deny(player, "Сталь могут плавить только самураи.");
        }
    }

    private static boolean isPlacingVanillaIron(InventoryClickEvent event, FurnaceInventory furnace) {
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        // клик по слоту 0 с iron в курсоре
        if (event.getRawSlot() == 0 && cursor != null && cursor.getType() == Material.IRON_INGOT && itemId(cursor) == null) {
            return true;
        }
        // shift-click из инвентаря игрока
        if (event.isShiftClick() && event.getRawSlot() >= furnace.getSize()
                && current != null && current.getType() == Material.IRON_INGOT && itemId(current) == null) {
            return true;
        }
        // number key etc. — курсор/hotbar
        if (event.getClick().isKeyboardClick() && event.getRawSlot() == 0) {
            ItemStack hot = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
            return hot != null && hot.getType() == Material.IRON_INGOT && itemId(hot) == null;
        }
        return false;
    }

    private static String locKey(org.bukkit.Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}
