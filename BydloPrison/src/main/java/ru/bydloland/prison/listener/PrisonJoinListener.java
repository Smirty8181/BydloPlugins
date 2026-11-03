package ru.bydloland.prison.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import ru.bydloland.prison.service.PrisonService;

public final class PrisonJoinListener implements Listener {

    private final PrisonService prison;

    public PrisonJoinListener(PrisonService prison) {
        this.prison = prison;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        prison.handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        prison.handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!prison.isImprisoned(event.getPlayer())) return;
        // Re-apply after respawn tick
        org.bukkit.Bukkit.getScheduler().runTask(
                org.bukkit.Bukkit.getPluginManager().getPlugin("BydloPrison"),
                () -> prison.handleJoin(event.getPlayer()));
    }
}
