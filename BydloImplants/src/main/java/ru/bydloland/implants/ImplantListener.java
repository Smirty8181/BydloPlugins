package ru.bydloland.implants;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ImplantListener implements Listener {

    private final BydloImplantsPlugin plugin;

    public ImplantListener(BydloImplantsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                plugin.service().applyEffects(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.service().clearEffects(event.getPlayer());
        plugin.service().save();
    }

    /** Без arms≥1: ближний урон ×0.7 (только для нетраннеров). */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ImplantService svc = plugin.service();
        if (!svc.canUseImplants(player)) return;
        if (svc.getLevel(player.getUniqueId(), "arms") >= 1) return;
        event.setDamage(event.getDamage() * 0.7);
    }

    /** Без eyes≥1: скорость стрелы ×0.75. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ImplantService svc = plugin.service();
        if (!svc.canUseImplants(player)) return;
        if (svc.getLevel(player.getUniqueId(), "eyes") >= 1) return;
        event.getProjectile().setVelocity(event.getProjectile().getVelocity().multiply(0.75));
    }
}
