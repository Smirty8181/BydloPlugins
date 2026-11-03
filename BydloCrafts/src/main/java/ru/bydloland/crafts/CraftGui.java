package ru.bydloland.crafts;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CraftGui implements Listener {

    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int RESULT = 24;
    private static final int BACK = 45;
    private static final int INFO = 49;

    private final BydloCraftsPlugin plugin;
    private final CraftCatalog catalog;
    private final Map<UUID, View> views = new ConcurrentHashMap<>();

    private enum Mode { HOME, LIST, RECIPE }

    private record View(Mode mode, String categoryId, String recipeId, Map<Integer, String> slotRecipes) {
        View(Mode mode, String categoryId, String recipeId) {
            this(mode, categoryId, recipeId, Map.of());
        }
    }

    public CraftGui(BydloCraftsPlugin plugin, CraftCatalog catalog) {
        this.plugin = plugin;
        this.catalog = catalog;
    }

    public void openHome(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder(), 54, "§d§l✦ §fКрафты §d§l✦");
        fillFrame(inv);

        int[] slots = {10, 12, 14, 28, 30, 32};
        List<CraftCatalog.Category> cats = catalog.categories();
        for (int i = 0; i < cats.size() && i < slots.length; i++) {
            CraftCatalog.Category c = cats.get(i);
            ItemStack better = switch (c.id()) {
                case "mithril" -> categoryIcon("bydlo:mithril_pickaxe", c);
                case "samurai" -> categoryIcon("bydlo:katana", c);
                case "titan" -> categoryIcon("bydlo:titan_revolver", c);
                case "mage" -> categoryIcon("bydlo:staff_dig", c);
                case "bar" -> categoryIcon("bydlo:vodka", c);
                case "misc" -> categoryIcon("bydlo:basement_key", c);
                default -> null;
            };
            if (better == null || better.getType() == Material.BARRIER) {
                better = named(Material.BOOK, c.color() + "§l" + c.title(),
                        List.of("§7Открыть крафты", "§7ЛКМ"));
            }
            inv.setItem(slots[i], better);
        }

        inv.setItem(INFO, uiIcon("bydlo:ui_info", Material.BOOK, "§eКак крафтить",
                List.of("§7Выбери категорию", "§7Потом крафт — сетка 3×3", "§7с настоящими предметами")));
        views.put(player.getUniqueId(), new View(Mode.HOME, null, null));
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.1f);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1.1, 0), 6, 0.25, 0.3, 0.25, 0.01);
    }

    public void openList(Player player, String categoryId) {
        CraftCatalog.Category cat = catalog.categories().stream()
                .filter(c -> c.id().equals(categoryId)).findFirst().orElse(null);
        if (cat == null) {
            openHome(player);
            return;
        }
        Inventory inv = Bukkit.createInventory(new Holder(), 54, "§d§l✦ " + cat.color() + "§l" + cat.title() + " §d§l✦");
        fillFrame(inv);

        List<CraftRecipe> recipes = catalog.recipes(categoryId);
        Map<Integer, String> slotMap = new java.util.HashMap<>();
        int slot = 10;
        for (CraftRecipe r : recipes) {
            while (slot % 9 == 0 || slot % 9 == 8 || slot >= 44) slot++;
            if (slot >= 44) break;
            ItemStack icon = CeItems.of(r.resultId());
            if (icon == null || icon.getType() == Material.BARRIER) {
                icon = named(Material.PAPER, "§f" + r.displayName(), List.of("§7ЛКМ — показать крафт"));
            }
            // CE item as-is — без перезаписи meta
            inv.setItem(slot, icon);
            slotMap.put(slot, r.id());
            slot++;
        }

        inv.setItem(BACK, uiIcon("bydlo:ui_back", Material.ARROW, "§d← Назад", List.of("§7К категориям")));
        views.put(player.getUniqueId(), new View(Mode.LIST, categoryId, null, Map.copyOf(slotMap)));
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    public void openRecipe(Player player, CraftRecipe recipe) {
        Inventory inv = Bukkit.createInventory(new Holder(), 54, "§d§l✦ §f" + recipe.displayName() + " §d§l✦");
        fillFrame(inv);

        ItemStack frame = named(Material.CRAFTING_TABLE, "§dВерстак", List.of("§7Сетка 3×3"));
        inv.setItem(1, frame);
        inv.setItem(7, uiIcon("bydlo:ui_next", Material.PINK_STAINED_GLASS_PANE, "§d→", List.of()));

        for (int i = 0; i < 9; i++) {
            String cell = recipe.grid()[i];
            if (cell == null) {
                inv.setItem(GRID[i], named(Material.BLACK_STAINED_GLASS_PANE, "§8·", List.of()));
            } else {
                ItemStack stack = CeItems.of(cell);
                if (stack == null) stack = named(Material.BARRIER, "§c" + cell, List.of());
                inv.setItem(GRID[i], stack);
            }
        }

        ItemStack result = CeItems.of(recipe.resultId());
        if (result == null) result = named(Material.BARRIER, "§c" + recipe.resultId(), List.of());
        result.setAmount(Math.max(1, recipe.resultCount()));
        // result as-is — без setItemMeta
        inv.setItem(RESULT, result);

        if (recipe.note() != null && !recipe.note().isBlank()) {
            inv.setItem(40, uiIcon("bydlo:ui_info", Material.PAPER, "§eПодсказка", List.of(recipe.note())));
        }

        inv.setItem(BACK, uiIcon("bydlo:ui_back", Material.ARROW, "§d← Назад", List.of("§7К списку")));
        views.put(player.getUniqueId(), new View(Mode.RECIPE, recipe.categoryId(), recipe.id()));
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.5f, 1.3f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.05, 0), 5, 0.3, 0.25, 0.3, 0);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        View view = views.get(player.getUniqueId());
        if (view == null) return;
        int slot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        if (slot == BACK) {
            if (view.mode() == Mode.RECIPE && view.categoryId() != null) {
                openList(player, view.categoryId());
            } else {
                openHome(player);
            }
            return;
        }

        if (view.mode() == Mode.HOME) {
            String cat = switch (slot) {
                case 10 -> "mithril";
                case 12 -> "samurai";
                case 14 -> "titan";
                case 28 -> "mage";
                case 30 -> "bar";
                case 32 -> "misc";
                default -> null;
            };
            if (cat != null) openList(player, cat);
            return;
        }

        if (view.mode() == Mode.LIST && view.categoryId() != null) {
            String rid = view.slotRecipes().get(slot);
            if (rid != null) {
                CraftRecipe r = catalog.byId(rid);
                if (r != null) openRecipe(player, r);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private static void fillFrame(Inventory inv) {
        ItemStack border = named(Material.PINK_STAINED_GLASS_PANE, " ", List.of());
        ItemStack fill = named(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        ItemStack accent = named(Material.MAGENTA_STAINED_GLASS_PANE, " ", List.of());
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            boolean edge = i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8;
            boolean corner = i == 0 || i == 8 || i == size - 9 || i == size - 1;
            inv.setItem(i, corner ? accent : (edge ? border : fill));
        }
    }

    /** CE UI-иконка с fallback на ванильный материал. */
    private static ItemStack uiIcon(String ceId, Material fallback, String name, List<String> lore) {
        ItemStack stack = CeItems.of(ceId);
        if (stack == null || stack.getType() == Material.BARRIER || stack.getType().isAir()) {
            return named(fallback, name, lore);
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            try {
                meta.addItemFlags(ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP"));
            } catch (IllegalArgumentException ignored) {
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** CE-иконка категории: модель предмета + displayName = название расы. */
    private static ItemStack categoryIcon(String ceId, CraftCatalog.Category c) {
        ItemStack stack = CeItems.of(ceId);
        if (stack == null || stack.getType() == Material.BARRIER) {
            return named(Material.BOOK, c.color() + "§l" + c.title(),
                    List.of("§7Открыть крафты", "§7ЛКМ"));
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(c.color() + "§l" + c.title());
            meta.setLore(List.of("§7Открыть крафты", "§7ЛКМ"));
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
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack named(Material mat, String name, List<String> lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
