package ru.bydloland.classes;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Бомж: гниль без вреда, сон где попало, лут из бочек.
 */
public final class BomzhListener implements Listener {

    private static final long SLEEP_CD_MS = 30_000L;
    private static final long BARREL_CD_MS = 8 * 60_000L;

    private final BydloClassesPlugin plugin;
    private final Map<UUID, Long> sleepUntil = new ConcurrentHashMap<>();
    private final Map<String, Long> barrelUntil = new ConcurrentHashMap<>();

    public BomzhListener(BydloClassesPlugin plugin) {
        this.plugin = plugin;
    }

    static boolean isBomzh(Player player) {
        return "bomzh".equalsIgnoreCase(BydloClassesAPI.getClassId(player));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEatRot(PlayerItemConsumeEvent event) {
        if (!isBomzh(event.getPlayer())) return;
        if (event.getItem().getType() != Material.ROTTEN_FLESH) return;
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.removePotionEffect(PotionEffectType.HUNGER);
            int food = Math.min(20, player.getFoodLevel() + 2);
            player.setFoodLevel(food);
            player.setSaturation(Math.min(20f, player.getSaturation() + 1.5f));
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHungerFromRot(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isBomzh(player)) return;
        if (event.getNewEffect() == null) return;
        if (event.getNewEffect().getType() != PotionEffectType.HUNGER) return;
        if (event.getCause() != EntityPotionEffectEvent.Cause.FOOD) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSleepAnywhere(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!event.getPlayer().isSneaking()) return;
        Player player = event.getPlayer();
        if (!isBomzh(player)) return;
        Block block = event.getClickedBlock();
        if (block == null || !block.getType().isSolid()) return;
        if (block.getType().name().contains("BED")) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isBlock() && hand.getType() != Material.AIR) return;

        long now = System.currentTimeMillis();
        Long until = sleepUntil.get(player.getUniqueId());
        if (until != null && until > now) return;

        event.setCancelled(true);
        sleepUntil.put(player.getUniqueId(), now + SLEEP_CD_MS);

        if (plugin.getServer().getPluginManager().isPluginEnabled("GSit")) {
            player.performCommand("lay");
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 0, true, false, false));
        try {
            player.setStatistic(Statistic.TIME_SINCE_REST, 0);
        } catch (Throwable ignored) {
        }
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 0.2, 0), 8, 0.4, 0.1, 0.4, 0.01);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_WOOL_PLACE, 0.7f, 0.8f);
        player.sendMessage("§7Спишь где попало. Фантомы отстали.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBarrelOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!isBomzh(player)) return;
        if (!(event.getInventory().getHolder() instanceof Barrel barrel)) return;
        String key = locKey(barrel.getLocation().getBlock()) + ":" + player.getUniqueId();
        long now = System.currentTimeMillis();
        Long until = barrelUntil.get(key);
        if (until != null && until > now) return;
        barrelUntil.put(key, now + BARREL_CD_MS);

        ItemStack junk = randomJunk();
        Map<Integer, ItemStack> left = player.getInventory().addItem(junk);
        if (!left.isEmpty()) {
            left.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
        }
        player.sendMessage("§7В бочке нашлось: §f" + plain(junk));
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.5f, 1.3f);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBarrelBreak(BlockBreakEvent event) {
        if (!isBomzh(event.getPlayer())) return;
        if (event.getBlock().getType() != Material.BARREL) return;
        if (event.getPlayer().getInventory().getItemInMainHand().containsEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH)) {
            return;
        }
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), randomJunk());
    }

    private static ItemStack randomJunk() {
        Material[] pool = {
                Material.PAPER, Material.ROTTEN_FLESH, Material.STICK, Material.BONE,
                Material.STRING, Material.LEATHER, Material.BOWL, Material.GLASS_BOTTLE,
                Material.COAL, Material.FLINT, Material.SPIDER_EYE, Material.WHEAT_SEEDS
        };
        Material m = pool[ThreadLocalRandom.current().nextInt(pool.length)];
        int n = 1 + ThreadLocalRandom.current().nextInt(3);
        return new ItemStack(m, n);
    }

    private static String plain(ItemStack stack) {
        String n = stack.getType().translationKey();
        return stack.getAmount() + "× " + stack.getType().name().toLowerCase().replace('_', ' ');
    }

    private static String locKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
