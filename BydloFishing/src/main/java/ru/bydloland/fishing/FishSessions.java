package ru.bydloland.fishing;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FishSessions {
    private final BydloFishingPlugin plugin;
    private final Map<UUID, FishSession> byPlayer = new ConcurrentHashMap<>();

    public FishSessions(BydloFishingPlugin plugin) {
        this.plugin = plugin;
    }

    public FishSession get(Player p) {
        return byPlayer.get(p.getUniqueId());
    }

    public void put(Player p, FishSession s) {
        FishSession old = byPlayer.put(p.getUniqueId(), s);
        if (old != null) {
            old.hideBar(p);
            finish(old);
        }
    }

    public void end(Player p) {
        FishSession s = byPlayer.remove(p.getUniqueId());
        if (s != null) {
            s.hideBar(p);
            finish(s);
        }
    }

    public void clearAll() {
        for (FishSession s : byPlayer.values()) finish(s);
        byPlayer.clear();
    }

    public void succeed(Player p) {
        FishSession s = byPlayer.remove(p.getUniqueId());
        if (s == null) return;
        s.hideBar(p);
        p.sendActionBar(Msg.text(""));
        finish(s);
        LootTables.give(plugin, p, s);
    }

    public void fail(Player p, String why) {
        FishSession s = byPlayer.remove(p.getUniqueId());
        if (s == null) return;
        s.hideBar(p);
        p.sendActionBar(Msg.text(""));
        finish(s);
        if (why != null) Msg.send(p, why);
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.7f, 0.6f);
    }

    private static void finish(FishSession s) {
        s.stopTask();
        if (s.bobber != null && s.bobber.isValid()) {
            s.bobber.remove();
        }
        s.bobber = null;
    }
}
