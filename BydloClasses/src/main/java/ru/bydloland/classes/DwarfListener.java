package ru.bydloland.classes;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Дворф: чует руду, тонет, меньше урона от падения, почти не садится на лошадь/верблюда.
 */
public final class DwarfListener implements Listener {

    private static final Set<Material> VANILLA_ORES = Set.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE,
            Material.ANCIENT_DEBRIS
    );

    private static final Set<String> CUSTOM_ORES = Set.of(
            "bydlo:mithril_ore", "bydlo:deepslate_mithril_ore",
            "bydlo:titan_ore", "bydlo:deepslate_titan_ore",
            "bydlo:etherium_ore", "bydlo:deepslate_etherium_ore"
    );

    private static final Set<EntityType> TOO_TALL = Set.of(
            EntityType.HORSE, EntityType.DONKEY, EntityType.MULE,
            EntityType.SKELETON_HORSE, EntityType.ZOMBIE_HORSE,
            EntityType.CAMEL, EntityType.LLAMA, EntityType.TRADER_LLAMA
    );

    private int orePulse;

    private final BydloClassesPlugin plugin;

    public DwarfListener(BydloClassesPlugin plugin) {
        this.plugin = plugin;
    }

    static boolean isDwarf(Player player) {
        return "dwarf".equalsIgnoreCase(BydloClassesAPI.getClassId(player));
    }

    public void tick(Player player) {
        if (!isDwarf(player)) return;
        sink(player);
        orePulse++;
        if (orePulse % 4 == 0) {
            senseOre(player);
        }
    }

    private void sink(Player player) {
        if (!player.isInWater()) return;
        Vector v = player.getVelocity();
        double y = Math.min(v.getY(), -0.08);
        player.setVelocity(new Vector(v.getX() * 0.45, y - 0.04, v.getZ() * 0.45));
    }

    private void senseOre(Player player) {
        int r = 6;
        Block origin = player.getLocation().getBlock();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(61, 220, 132), 0.9f);
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    Block b = origin.getRelative(x, y, z);
                    if (!isOre(b)) continue;
                    player.spawnParticle(Particle.DUST, b.getLocation().add(0.5, 0.5, 0.5),
                            2, 0.15, 0.15, 0.15, 0, dust);
                }
            }
        }
    }

    private static boolean isOre(Block block) {
        if (VANILLA_ORES.contains(block.getType())) return true;
        try {
            ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
            if (state == null || state.isEmpty()) return false;
            var def = state.owner().value();
            if (def == null || def.id() == null) return false;
            return CUSTOM_ORES.contains(def.id().asString());
        } catch (Throwable t) {
            return false;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isDwarf(player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        event.setDamage(event.getDamage() * 0.45);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isDwarf(player)) return;
        EntityType type = event.getMount().getType();
        if (!TOO_TALL.contains(type)) return;
        if (ThreadLocalRandom.current().nextDouble() < 0.12) return;
        event.setCancelled(true);
        player.sendMessage("§7Слишком мелкий для " + type.name().toLowerCase().replace('_', ' ') + ".");
    }
}
