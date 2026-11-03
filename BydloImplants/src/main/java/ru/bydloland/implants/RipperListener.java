package ru.bydloland.implants;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.event.CustomBlockInteractEvent;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Merchant;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Стол рипера рядом с безработным жителем → Рипер, у которого ставят импланты.
 */
public final class RipperListener implements Listener {

    public static final String TABLE_ID = "bydlo:ripper_table";

    private final BydloImplantsPlugin plugin;
    private final NamespacedKey ripperKey;

    public RipperListener(BydloImplantsPlugin plugin) {
        this.plugin = plugin;
        this.ripperKey = new NamespacedKey(plugin, "ripper");
    }

    public static boolean isRipper(Entity entity, NamespacedKey key) {
        if (!(entity instanceof Villager villager)) return false;
        Byte mark = villager.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        if (mark != null && mark == (byte) 1) return true;
        String name = villager.getCustomName();
        return name != null && ChatColor.stripColor(name).equalsIgnoreCase("Рипер");
    }

    public boolean isRipper(Entity entity) {
        return isRipper(entity, ripperKey);
    }

    public static boolean isNearRipper(Player player, double range, NamespacedKey key) {
        double r2 = range * range;
        for (Entity e : player.getNearbyEntities(range, range, range)) {
            if (isRipper(e, key) && e.getLocation().distanceSquared(player.getLocation()) <= r2) {
                return true;
            }
        }
        return false;
    }

    public boolean isNearRipper(Player player, double range) {
        return isNearRipper(player, range, ripperKey);
    }

    public NamespacedKey key() {
        return ripperKey;
    }

    static String blockId(Block block) {
        if (block == null) return null;
        try {
            ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
            if (state == null || state.isEmpty()) return null;
            BlockDefinition def = state.owner().value();
            return def == null || def.id() == null ? null : def.id().asString();
        } catch (Throwable t) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!TABLE_ID.equals(blockId(event.getBlock()))) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> tryConvert(event.getPlayer(), event.getBlock().getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTableClick(CustomBlockInteractEvent event) {
        if (event.action() != CustomBlockInteractEvent.Action.RIGHT_CLICK) return;
        if (event.customBlock() == null || event.customBlock().id() == null) return;
        if (!TABLE_ID.equals(event.customBlock().id().asString())) return;
        event.setCancelled(true);
        tryConvert(event.player(), event.bukkitBlock().getLocation());
    }

    private void tryConvert(Player player, Location table) {
        Villager target = null;
        double best = 9.0;
        for (Entity e : table.getWorld().getNearbyEntities(table, 2.6, 2.6, 2.6)) {
            if (!(e instanceof Villager v)) continue;
            if (isRipper(v)) {
                player.sendMessage("§bРипер уже здесь. ПКМ по нему — импланты.");
                return;
            }
            if (v.getProfession() != Villager.Profession.NONE) continue;
            double d = e.getLocation().distanceSquared(table);
            if (d < best) {
                best = d;
                target = v;
            }
        }
        if (target == null) {
            player.sendMessage("§7Поставь стол рядом с жителем без профессии.");
            return;
        }
        convert(target);
        player.sendMessage("§bЖитель стал Рипером. ПКМ по нему — импланты.");
        table.getWorld().playSound(target.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
        table.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1.2, 0), 18, 0.3, 0.5, 0.3, 0.02);
    }

    private void convert(Villager villager) {
        villager.setProfession(Villager.Profession.NITWIT);
        villager.setVillagerLevel(1);
        villager.setRecipes(List.of());
        villager.setCustomName("§bРипер");
        villager.setCustomNameVisible(true);
        villager.setPersistent(true);
        villager.setRemoveWhenFarAway(false);
        villager.getPersistentDataContainer().set(ripperKey, PersistentDataType.BYTE, (byte) 1);
        try {
            villager.setAI(true);
        } catch (Throwable ignored) {
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRipperClick(PlayerInteractEntityEvent event) {
        if (!isRipper(event.getRightClicked())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!plugin.service().canUseImplants(player)) {
            player.sendMessage("§cРипер работает только с Киберпанками.");
            return;
        }
        ImplantGui.open(plugin, player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrade(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        if (!(event.getInventory().getHolder() instanceof Merchant merchant)) return;
        if (!(merchant instanceof Villager villager)) return;
        if (!isRipper(villager)) return;
        event.setCancelled(true);
    }
}
