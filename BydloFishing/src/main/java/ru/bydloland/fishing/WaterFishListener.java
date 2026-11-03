package ru.bydloland.fishing;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class WaterFishListener implements Listener {
    private final BydloFishingPlugin plugin;
    private final FishSessions sessions;

    public WaterFishListener(BydloFishingPlugin plugin, FishSessions sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player p = event.getPlayer();
        RodType rod = RodType.of(p.getInventory().getItemInMainHand());
        if (rod == null) rod = RodType.of(p.getInventory().getItemInOffHand());
        if (rod == null) return;

        switch (event.getState()) {
            case FISHING -> {
                FishSession cur = sessions.get(p);
                if (cur != null && cur.phase == FishSession.Phase.FIGHT) {
                    event.setCancelled(true);
                    Msg.send(p, "&eНе бросай заново — &fдержи sneak&e, пока рыба в зелёной рамке.");
                    return;
                }
                FishSession s = new FishSession(rod, false);
                s.bobber = event.getHook();
                sessions.put(p, s);
            }
            case BITE -> {
                FishSession s = sessions.get(p);
                if (s == null) {
                    s = new FishSession(rod, false);
                    s.bobber = event.getHook();
                    sessions.put(p, s);
                }
                if (s.phase == FishSession.Phase.FIGHT) return;
                s.bobber = event.getHook();
                if (event.getHook() != null) s.catchAt = event.getHook().getLocation();
                Minigame.start(plugin, p, s);
            }
            case CAUGHT_FISH, REEL_IN, CAUGHT_ENTITY -> {
                FishSession s = sessions.get(p);
                if (s != null && s.phase == FishSession.Phase.FIGHT) {
                    event.setCancelled(true);
                    Msg.send(p, "&7Не дёргай катушку. &fЗажми sneak &7— рамка поднимается к рыбе.");
                    return;
                }
                if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
                    event.setCancelled(true);
                    if (event.getHook() != null) event.getHook().remove();
                    sessions.fail(p, "&7Рано дёрнул — рыба ещё не клюнула.");
                } else {
                    sessions.end(p);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.end(event.getPlayer());
    }
}
