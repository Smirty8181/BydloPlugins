package ru.bydloland.fishing;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/** Sneak читается в тике мини-игры (удержание). Тап больше не ловит рыбу. */
public final class MinigameListener implements Listener {
    public MinigameListener(BydloFishingPlugin plugin, FishSessions sessions) {
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        // намеренно пусто: рамка двигается, пока player.isSneaking()
    }
}
