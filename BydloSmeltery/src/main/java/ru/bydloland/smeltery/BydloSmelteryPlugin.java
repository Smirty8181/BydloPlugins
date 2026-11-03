package ru.bydloland.smeltery;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.api.event.CustomBlockInteractEvent;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Мифриловая плавильня (дворфы) + Киберпечь (нетранеры).
 */
public final class BydloSmelteryPlugin extends JavaPlugin implements Listener {

    private static final int COOK_TOTAL = 200;

    private enum Kind {
        MITHRIL(
                Key.of("bydlo", "mithril_smeltery"),
                Key.of("bydlo", "mithril_ingot"),
                ChatColor.DARK_AQUA + "Мифриловая плавильня",
                "canUseMithrilSmeltery",
                ChatColor.RED + "Мифриловую плавильню могут использовать только дворфы.",
                Set.of("raw_mithril", "mithril_ore", "deepslate_mithril_ore"),
                Set.of("необработанный мифрил", "мифриловая руда", "глубинная мифриловая руда"),
                "мифриловый слиток",
                Material.IRON_INGOT,
                ChatColor.AQUA + "Мифриловый слиток"
        ),
        TITAN(
                Key.of("bydlo", "cyber_furnace"),
                Key.of("bydlo", "titan_ingot"),
                ChatColor.AQUA + "Киберпечь",
                "canUseTitanSmeltery",
                ChatColor.RED + "Киберпечь могут использовать только Киберпанки.",
                Set.of("raw_titan", "titan_ore", "deepslate_titan_ore"),
                Set.of("титановая руда", "глубинная титановая руда", "сырой титан"),
                "титановый слиток",
                Material.IRON_INGOT,
                ChatColor.AQUA + "Титановый слиток"
        );

        final Key blockId;
        final Key ingotId;
        final String title;
        final String apiMethod;
        final String denyMsg;
        final Set<String> inputIds;
        final Set<String> inputNames;
        final String ingotNamePart;
        final Material fallbackMat;
        final String fallbackName;

        Kind(Key blockId, Key ingotId, String title, String apiMethod, String denyMsg,
             Set<String> inputIds, Set<String> inputNames, String ingotNamePart,
             Material fallbackMat, String fallbackName) {
            this.blockId = blockId;
            this.ingotId = ingotId;
            this.title = title;
            this.apiMethod = apiMethod;
            this.denyMsg = denyMsg;
            this.inputIds = inputIds;
            this.inputNames = inputNames;
            this.ingotNamePart = ingotNamePart;
            this.fallbackMat = fallbackMat;
            this.fallbackName = fallbackName;
        }

        static Kind byBlock(Key id) {
            if (id == null) return null;
            for (Kind k : values()) {
                if (k.blockId.equals(id)) return k;
            }
            return null;
        }
    }

    private final Map<String, ItemStack[]> stored = new HashMap<>();
    private final Map<String, Integer> cookProgress = new HashMap<>();
    private final Map<UUID, String> openKey = new HashMap<>();
    private final Map<UUID, Integer> openedThisTick = new HashMap<>();
    private final Set<String> litBlocks = new HashSet<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        new BukkitRunnable() {
            @Override
            public void run() {
                tickAll();
            }
        }.runTaskTimer(this, 1L, 1L);
        getLogger().info("BydloSmeltery enabled (mithril + cyber furnace)");
    }

    private static String locKey(Location loc) {
        return loc.getWorld().getName() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }

    private static Key blockIdAt(Block block) {
        if (block == null) return null;
        ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
        if (state == null || state.isEmpty()) return null;
        BlockDefinition def = state.owner().value();
        return def == null ? null : def.id();
    }

    private static String plainName(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return "";
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return "";
        return ChatColor.stripColor(meta.getDisplayName()).toLowerCase(Locale.ROOT);
    }

    private static boolean isInput(Kind kind, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        Key id = CraftEngineItems.getCustomItemId(stack);
        if (id != null && "bydlo".equals(id.namespace()) && kind.inputIds.contains(id.value())) {
            return true;
        }
        String n = plainName(stack);
        for (String part : kind.inputNames) {
            if (n.contains(part)) return true;
        }
        return false;
    }

    private static boolean isIngot(Kind kind, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        Key id = CraftEngineItems.getCustomItemId(stack);
        if (id != null && kind.ingotId.equals(id)) return true;
        return plainName(stack).contains(kind.ingotNamePart);
    }

    private ItemStack createIngot(Kind kind) {
        BukkitItemDefinition def = CraftEngineItems.byId(kind.ingotId);
        if (def != null) {
            ItemStack item = def.buildBukkitItem();
            if (item != null && !item.getType().isAir()) {
                item.setAmount(1);
                return item;
            }
        }
        ItemStack fallback = new ItemStack(kind.fallbackMat);
        ItemMeta meta = fallback.getItemMeta();
        meta.setDisplayName(kind.fallbackName);
        fallback.setItemMeta(meta);
        return fallback;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBukkitInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getPlayer().isSneaking()) return;
        Kind kind = Kind.byBlock(blockIdAt(event.getClickedBlock()));
        if (kind == null) return;
        event.setCancelled(true);
        if (!canUse(event.getPlayer(), kind)) return;
        openFurnace(event.getPlayer(), event.getClickedBlock().getLocation(), kind);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCustomInteract(CustomBlockInteractEvent event) {
        if (event.action() != CustomBlockInteractEvent.Action.RIGHT_CLICK) return;
        if (event.hand() != net.momirealms.craftengine.core.entity.player.InteractionHand.MAIN_HAND) return;
        if (event.customBlock() == null) return;
        Kind kind = Kind.byBlock(event.customBlock().id());
        if (kind == null) return;
        event.setCancelled(true);
        if (!canUse(event.player(), kind)) return;
        openFurnace(event.player(), event.bukkitBlock().getLocation(), kind);
    }

    private boolean hasRaceAccess(Player player, Kind kind) {
        if (!Bukkit.getPluginManager().isPluginEnabled("BydloClasses")) return false;
        try {
            Class<?> api = Class.forName("ru.bydloland.classes.BydloClassesAPI");
            Object ok = api.getMethod(kind.apiMethod, Player.class).invoke(null, player);
            return ok instanceof Boolean b && b;
        } catch (Throwable t) {
            getLogger().warning("BydloClasses check failed: " + t.getMessage());
            return false;
        }
    }

    private boolean canUse(Player player, Kind kind) {
        if (!Bukkit.getPluginManager().isPluginEnabled("BydloClasses")) {
            player.sendMessage(ChatColor.RED + "Печь временно недоступна.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 0.9f);
            return false;
        }
        if (hasRaceAccess(player, kind)) return true;
        player.sendMessage(kind.denyMsg);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 0.9f);
        return false;
    }

    private static boolean isTitanMaterial(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        Key id = CraftEngineItems.getCustomItemId(stack);
        if (id != null && "bydlo".equals(id.namespace())) {
            String v = id.value();
            if (v.equals("raw_titan") || v.equals("titan_ore") || v.equals("deepslate_titan_ore")
                    || v.equals("titan_ingot") || v.startsWith("titan_")) {
                return true;
            }
        }
        String n = plainName(stack);
        return n.contains("титанов") || n.contains("сырой титан");
    }

    private void openFurnace(Player player, Location blockLoc, Kind kind) {
        int tick = Bukkit.getCurrentTick();
        UUID uuid = player.getUniqueId();
        Integer last = openedThisTick.get(uuid);
        if (last != null && last == tick) return;
        openedThisTick.put(uuid, tick);

        String key = locKey(blockLoc);
        SmelteryHolder holder = new SmelteryHolder(blockLoc.clone(), kind);
        Inventory inv = Bukkit.createInventory(holder, InventoryType.FURNACE, kind.title);
        holder.setInventory(inv);

        ItemStack[] saved = stored.get(key);
        if (saved != null) {
            for (int i = 0; i < Math.min(3, saved.length); i++) {
                if (saved[i] != null) inv.setItem(i, saved[i].clone());
            }
        }

        openKey.put(uuid, key);
        player.openInventory(inv);
        syncBars(player.getOpenInventory(), cookProgress.getOrDefault(key, 0), canStart(kind, inv));
        player.playSound(player.getLocation(), Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.55f, 1.35f);
    }

    private void saveInv(String key, Inventory inv) {
        ItemStack[] data = new ItemStack[3];
        for (int i = 0; i < 3; i++) {
            ItemStack it = inv.getItem(i);
            data[i] = it == null ? null : it.clone();
        }
        stored.put(key, data);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof SmelteryHolder holder)) return;
        saveInv(locKey(holder.getBlockLocation()), event.getInventory());
        openKey.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SmelteryHolder holder)) return;
        Kind kind = holder.getKind();
        int slot = event.getRawSlot();
        ItemStack cursor = event.getCursor();
        boolean placing = cursor != null && !cursor.getType().isAir();

        if (slot == 0 && placing && !isInput(kind, cursor)) {
            event.setCancelled(true);
            return;
        }
        if (slot == 1 && placing && cursor.getType() != Material.LAVA_BUCKET) {
            event.setCancelled(true);
            return;
        }
        if (slot == 2 && placing) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof SmelteryHolder holder)) return;
        Kind kind = holder.getKind();
        ItemStack old = event.getOldCursor();
        if (old == null || old.getType().isAir()) return;
        if (event.getRawSlots().contains(0) && !isInput(kind, old)) {
            event.setCancelled(true);
            return;
        }
        if (event.getRawSlots().contains(1) && old.getType() != Material.LAVA_BUCKET) {
            event.setCancelled(true);
            return;
        }
        if (event.getRawSlots().contains(2)) {
            event.setCancelled(true);
        }
    }

    /** Крафт киберпечи / титановых предметов — только нетранеры (без спама сообщений). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType().isAir()) return;
        Key id = CraftEngineItems.getCustomItemId(result);
        if (id == null || !"bydlo".equals(id.namespace())) return;
        String v = id.value();
        boolean titanRelated = v.startsWith("titan") || v.equals("cyber_furnace") || v.equals("cyber_chip")
                || v.startsWith("cyber_");
        if (!titanRelated) return;
        if (!(event.getView().getPlayer() instanceof Player player)
                || !hasRaceAccess(player, Kind.TITAN)) {
            event.getInventory().setResult(null);
        }
    }

    /** Обычные печи / коптильни / варочные не плавят титан. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVanillaSmelt(FurnaceSmeltEvent event) {
        if (isTitanMaterial(event.getSource())) {
            event.setCancelled(true);
        }
    }

    private static boolean canStart(Kind kind, Inventory inv) {
        ItemStack ore = inv.getItem(0);
        ItemStack fuel = inv.getItem(1);
        ItemStack out = inv.getItem(2);
        if (fuel == null || fuel.getType() != Material.LAVA_BUCKET) return false;
        if (!isInput(kind, ore)) return false;
        if (out != null && !out.getType().isAir()) {
            return isIngot(kind, out) && out.getAmount() < 64;
        }
        return true;
    }

    private void syncBars(InventoryView view, int progress, boolean active) {
        if (view == null) return;
        if (active && progress > 0) {
            view.setProperty(InventoryView.Property.COOK_TIME, progress);
            view.setProperty(InventoryView.Property.TICKS_FOR_CURRENT_SMELTING, COOK_TOTAL);
            int burnLeft = Math.max(1, COOK_TOTAL - progress);
            view.setProperty(InventoryView.Property.BURN_TIME, burnLeft);
            view.setProperty(InventoryView.Property.TICKS_FOR_CURRENT_FUEL, COOK_TOTAL);
        } else if (active) {
            view.setProperty(InventoryView.Property.COOK_TIME, 0);
            view.setProperty(InventoryView.Property.TICKS_FOR_CURRENT_SMELTING, COOK_TOTAL);
            view.setProperty(InventoryView.Property.BURN_TIME, COOK_TOTAL);
            view.setProperty(InventoryView.Property.TICKS_FOR_CURRENT_FUEL, COOK_TOTAL);
        } else {
            view.setProperty(InventoryView.Property.COOK_TIME, 0);
            view.setProperty(InventoryView.Property.TICKS_FOR_CURRENT_SMELTING, COOK_TOTAL);
            view.setProperty(InventoryView.Property.BURN_TIME, 0);
            view.setProperty(InventoryView.Property.TICKS_FOR_CURRENT_FUEL, COOK_TOTAL);
        }
    }

    private void setBlockLit(Location loc, Kind kind, boolean lit) {
        String key = locKey(loc);
        if (lit) litBlocks.add(key);
        else litBlocks.remove(key);
        try {
            Block block = loc.getBlock();
            ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
            if (state == null || state.isEmpty()) return;
            CompoundTag tag = state.propertiesNbt();
            if (tag.getBoolean("lit", false) == lit) return;
            tag.putBoolean("lit", lit);
            CraftEngineBlocks.place(loc, kind.blockId, tag, false);
        } catch (Throwable t) {
            getLogger().warning("lit toggle failed: " + t.getMessage());
        }
    }

    private void finishSmelt(Kind kind, Inventory inv) {
        ItemStack ore = inv.getItem(0);
        ItemStack out = inv.getItem(2);
        if (ore == null) return;

        if (ore.getAmount() > 1) {
            ore.setAmount(ore.getAmount() - 1);
            inv.setItem(0, ore);
        } else {
            inv.setItem(0, null);
        }
        inv.setItem(1, new ItemStack(Material.BUCKET));

        ItemStack ingot = createIngot(kind);
        if (out == null || out.getType().isAir()) {
            inv.setItem(2, ingot);
        } else {
            out.setAmount(out.getAmount() + 1);
            inv.setItem(2, out);
        }
    }

    private void playCookFx(Location loc, int progress) {
        World world = loc.getWorld();
        if (world == null) return;
        Location c = loc.clone().add(0.5, 0.55, 0.5);
        world.spawnParticle(Particle.FLAME, c, 2, 0.18, 0.12, 0.18, 0.01);
        world.spawnParticle(Particle.SMOKE, c, 1, 0.12, 0.2, 0.12, 0.01);
        if (progress % 20 == 0) {
            world.playSound(c, Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.7f, 1.25f);
            world.spawnParticle(Particle.LAVA, c, 1, 0.1, 0.05, 0.1, 0);
        }
        if (progress % 8 == 0) {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, c, 1, 0.15, 0.1, 0.15, 0.005);
        }
    }

    private void tickAll() {
        Set<String> activeKeys = new HashSet<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            String key = openKey.get(player.getUniqueId());
            if (key == null) continue;
            Inventory top = player.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof SmelteryHolder holder)) continue;
            Kind kind = holder.getKind();

            Location loc = holder.getBlockLocation();
            boolean ready = canStart(kind, top);
            int progress = cookProgress.getOrDefault(key, 0);

            if (ready) {
                activeKeys.add(key);
                progress++;
                if (progress >= COOK_TOTAL) {
                    finishSmelt(kind, top);
                    saveInv(key, top);
                    progress = 0;
                    cookProgress.put(key, 0);
                    setBlockLit(loc, kind, canStart(kind, top));
                    loc.getWorld().playSound(loc.clone().add(0.5, 0.5, 0.5), Sound.BLOCK_LAVA_EXTINGUISH, 0.45f, 1.4f);
                } else {
                    cookProgress.put(key, progress);
                    setBlockLit(loc, kind, true);
                    playCookFx(loc, progress);
                }
                syncBars(player.getOpenInventory(), cookProgress.getOrDefault(key, 0), canStart(kind, top) || progress > 0);
            } else {
                if (progress != 0) cookProgress.put(key, 0);
                setBlockLit(loc, kind, false);
                syncBars(player.getOpenInventory(), 0, false);
            }
        }
    }

    public static final class SmelteryHolder implements InventoryHolder {
        private final Location blockLocation;
        private final Kind kind;
        private Inventory inventory;

        public SmelteryHolder(Location blockLocation, Kind kind) {
            this.blockLocation = blockLocation;
            this.kind = kind;
        }

        public Location getBlockLocation() {
            return blockLocation;
        }

        public Kind getKind() {
            return kind;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
