package ru.bydloland.classes;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Киберпанк: ночью слабый glow и ночное зрение без факела.
 * Коротыш в воде — в BydloImplants.
 */
public final class NetrunnerNightListener implements Listener {

    public void tick(Player player) {
        if (!"netrunner".equalsIgnoreCase(BydloClassesAPI.getClassId(player))) {
            if (player.isGlowing()) player.setGlowing(false);
            return;
        }
        long t = player.getWorld().getTime() % 24000L;
        boolean night = t >= 12500L && t < 23500L;
        player.setGlowing(night);
        if (night) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, true, false, false));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.getPlayer().setGlowing(false);
    }
}
