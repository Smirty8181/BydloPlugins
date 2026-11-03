package ru.bydloland.classes;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Маг: фамильяр кот / ворон — подбирает дроп, сидит на плече.
 */
public final class MageFamiliarListener implements Listener {

    private final BydloClassesPlugin plugin;
    private final NamespacedKey typeKey;
    private final NamespacedKey ownerKey;
    private final Map<UUID, UUID> familiarOf = new ConcurrentHashMap<>();

    public MageFamiliarListener(BydloClassesPlugin plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "familiar_type");
        this.ownerKey = new NamespacedKey(plugin, "familiar_owner");
    }

    public void tick(Player player) {
        UUID fid = familiarOf.get(player.getUniqueId());
        if (fid == null) return;
        Entity entity = plugin.getServer().getEntity(fid);
        if (entity == null || entity.isDead()) {
            if (!BydloClassesAPI.canUseMageGear(player)) {
                familiarOf.remove(player.getUniqueId());
                return;
            }
            entity = spawn(player, currentType(player));
            if (entity == null) return;
        }
        LivingEntity fam = (LivingEntity) entity;
        if (fam.getLocation().distanceSquared(player.getLocation()) > 24 * 24) {
            fam.teleport(player.getLocation());
        }
        pickup(player, fam);
        if (player.isSneaking() && fam.getVehicle() == null
                && fam.getLocation().distanceSquared(player.getLocation()) < 9) {
            player.addPassenger(fam);
        } else if (!player.isSneaking() && player.getPassengers().contains(fam)) {
            player.removePassenger(fam);
            fam.teleport(player.getLocation().add(player.getLocation().getDirection().multiply(0.6)));
        }
    }

    private void pickup(Player player, LivingEntity fam) {
        for (Entity e : fam.getNearbyEntities(2.6, 1.5, 2.6)) {
            if (!(e instanceof Item drop) || drop.isDead()) continue;
            ItemStack stack = drop.getItemStack();
            Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
            if (left.isEmpty()) {
                drop.remove();
                fam.getWorld().playSound(fam.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.4f);
            } else {
                drop.setItemStack(left.values().iterator().next());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCharm(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!CeIds.is(player.getInventory().getItemInMainHand(), "bydlo:familiar_charm")) return;
        event.setCancelled(true);
        if (!BydloClassesAPI.canUseMageGear(player)) {
            player.sendMessage("§cФамильяр слушает только магов.");
            return;
        }
        if (player.isSneaking()) {
            String next = "crow".equals(currentType(player)) ? "cat" : "crow";
            player.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, next);
            player.sendMessage("§dФамильяр: §f" + ("crow".equals(next) ? "ворон" : "кот"));
            UUID fid = familiarOf.get(player.getUniqueId());
            if (fid != null) {
                Entity old = plugin.getServer().getEntity(fid);
                if (old != null) old.remove();
                spawn(player, next);
            }
            return;
        }
        UUID fid = familiarOf.get(player.getUniqueId());
        if (fid != null) {
            Entity old = plugin.getServer().getEntity(fid);
            if (old != null) old.remove();
            familiarOf.remove(player.getUniqueId());
            player.sendMessage("§7Фамильяр отпущен.");
            return;
        }
        spawn(player, currentType(player));
        player.sendMessage("§dФамильяр рядом. Шифт — на плечо, подбирает дроп.");
    }

    private String currentType(Player player) {
        String t = player.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        return t == null ? "cat" : t;
    }

    private LivingEntity spawn(Player player, String type) {
        Location loc = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(1.2));
        LivingEntity fam;
        if ("crow".equals(type)) {
            Allay allay = player.getWorld().spawn(loc, Allay.class);
            allay.customName(net.kyori.adventure.text.Component.text("Ворон"));
            allay.setCustomNameVisible(true);
            allay.setCanPickupItems(false);
            allay.setSilent(true);
            fam = allay;
        } else {
            Cat cat = player.getWorld().spawn(loc, Cat.class);
            try {
                cat.setCatType(Cat.Type.ALL_BLACK);
            } catch (Throwable t) {
                try {
                    cat.setCatType(Cat.Type.BLACK);
                } catch (Throwable ignored) {
                }
            }
            cat.setOwner(player);
            cat.setSitting(false);
            cat.setSilent(true);
            cat.customName(net.kyori.adventure.text.Component.text("Кот"));
            cat.setCustomNameVisible(false);
            fam = cat;
        }
        fam.setPersistent(true);
        fam.setRemoveWhenFarAway(false);
        fam.setInvulnerable(true);
        fam.setCollidable(false);
        fam.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        if (fam instanceof Tameable tameable) {
            tameable.setTamed(true);
            tameable.setOwner(player);
        }
        familiarOf.put(player.getUniqueId(), fam.getUniqueId());
        fam.getWorld().spawnParticle(Particle.WITCH, fam.getLocation().add(0, 0.4, 0), 8, 0.2, 0.3, 0.2, 0);
        fam.getWorld().playSound(fam.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 0.7f, 1.2f);
        return fam;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID fid = familiarOf.remove(event.getPlayer().getUniqueId());
        if (fid == null) return;
        Entity e = plugin.getServer().getEntity(fid);
        if (e != null) e.remove();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHitFamiliar(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        String owner = living.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (owner == null) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHitBy(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (living.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING) == null) return;
        event.setCancelled(true);
    }
}
