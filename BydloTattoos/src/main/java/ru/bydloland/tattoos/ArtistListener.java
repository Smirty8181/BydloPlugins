package ru.bydloland.tattoos;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class ArtistListener implements Listener {
    private final BydloTattoosPlugin plugin;

    public ArtistListener(BydloTattoosPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerTattoos data = plugin.storage().get(player.getUniqueId());
        if (data.tattoos.isEmpty() || data.composeRev >= PlayerTattoos.CURRENT_REV) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            plugin.service().refresh(player, err -> {
                if (err != null) plugin.getLogger().warning("Обновление тату " + player.getName() + ": " + err);
            });
        }, 40L);
    }

    @EventHandler
    public void onHit(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player client)) return;
        Player artist = event.getPlayer();
        if (!MachineItem.isMachine(plugin, artist.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        open(artist, client);
    }

    @EventHandler
    public void onAir(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR) return;
        Player artist = event.getPlayer();
        if (!MachineItem.isMachine(plugin, artist.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        open(artist, artist);
    }

    private void open(Player artist, Player client) {
        if (!plugin.service().isArtist(artist)) {
            Msg.send(plugin, artist, "&cМашинка без мастера — просто железо.");
            return;
        }
        if (!artist.getName().equalsIgnoreCase(client.getName())) {
            double range = plugin.getConfig().getDouble("range", 4);
            if (artist.getLocation().distanceSquared(client.getLocation()) > range * range) {
                Msg.send(plugin, artist, "&cПодойди ближе.");
                return;
            }
        }
        TattooGui.openDesigns(plugin, artist, client);
    }
}
