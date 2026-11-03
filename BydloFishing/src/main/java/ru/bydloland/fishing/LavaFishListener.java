package ru.bydloland.fishing;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.concurrent.ThreadLocalRandom;

public final class LavaFishListener implements Listener {
    private final BydloFishingPlugin plugin;
    private final FishSessions sessions;

    public LavaFishListener(BydloFishingPlugin plugin, FishSessions sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        RodType rod = RodType.of(hand);
        if (rod == null || !rod.lava()) return;
        Location lava = lookLava(p);
        if (lava == null) return;
        if (sessions.get(p) != null) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        FishSession s = new FishSession(rod, true);
        s.catchAt = lava;
        ArmorStand stand = p.getWorld().spawn(lava.clone().add(0.5, 0.15, 0.5), ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);
            as.setSmall(true);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setSilent(true);
            as.setGlowing(true);
            as.getEquipment().setHelmet(new ItemStack(Material.MAGMA_CREAM));
        });
        s.bobber = stand;
        sessions.put(p, s);
        p.swingMainHand();
        p.playSound(lava, org.bukkit.Sound.ITEM_BUCKET_FILL_LAVA, 0.6f, 1.4f);
        Msg.send(p, "&6Поплавок в лаве. Жди поклёвки...");
        int wait = ThreadLocalRandom.current().nextInt(rod.waitMin(), rod.waitMax());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (sessions.get(p) != s) return;
            Minigame.start(plugin, p, s);
        }, wait);
    }

    static Location lookLava(Player p) {
        RayTraceResult hit = p.getWorld().rayTraceBlocks(
                p.getEyeLocation(), p.getLocation().getDirection(), 16,
                FluidCollisionMode.ALWAYS, true);
        if (hit == null || hit.getHitBlock() == null) return null;
        Block b = hit.getHitBlock();
        Material t = b.getType();
        if (t == Material.LAVA || t == Material.LAVA_CAULDRON) {
            return b.getLocation();
        }
        return null;
    }
}
