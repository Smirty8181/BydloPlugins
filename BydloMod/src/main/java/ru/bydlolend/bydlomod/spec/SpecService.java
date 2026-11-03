package ru.bydlolend.bydlomod.spec;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import ru.bydlolend.bydlomod.BydloMod;
import ru.bydlolend.bydlomod.mod.PrisonHook;
import ru.bydlolend.bydlomod.util.Msg;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Тихий спектатор: цель не видит и не слышит, тюрьма не трогает. */
public final class SpecService implements Listener {
    public static final class Session {
        public final Location back;
        public final GameMode mode;
        public UUID targetId;
        public String targetName;

        Session(Location back, GameMode mode, UUID targetId, String targetName) {
            this.back = back;
            this.mode = mode;
            this.targetId = targetId;
            this.targetName = targetName;
        }
    }

    private final BydloMod plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public SpecService(BydloMod plugin) {
        this.plugin = plugin;
    }

    public boolean isWatching(UUID staffId) {
        return sessions.containsKey(staffId);
    }

    public Session session(UUID staffId) {
        return sessions.get(staffId);
    }

    public boolean start(Player staff, Player target) {
        if (staff.getUniqueId().equals(target.getUniqueId())) {
            Msg.err(staff, "Нельзя следить за собой");
            return false;
        }
        if (PrisonHook.isImprisoned(staff.getUniqueId())) {
            Msg.err(staff, "Ты в клетке — спек недоступен");
            return false;
        }
        if (PrisonHook.isImprisoned(target.getUniqueId())) {
            Msg.err(staff, "Цель в клетке — смотри через тюрьму");
            return false;
        }

        Session existing = sessions.get(staff.getUniqueId());
        Location back = existing != null ? existing.back : staff.getLocation().clone();
        GameMode mode = existing != null ? existing.mode : staff.getGameMode();
        sessions.put(staff.getUniqueId(), new Session(back, mode, target.getUniqueId(), target.getName()));

        hideFromPlayers(staff);
        staff.setGameMode(GameMode.SPECTATOR);
        staff.setSpectatorTarget(null);
        staff.teleport(target.getLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (staff.isOnline() && target.isOnline() && isWatching(staff.getUniqueId())) {
                staff.setSpectatorTarget(target);
            }
        }, 2L);
        Msg.ok(staff, "Следишь за §d" + target.getName() + " §8· /спек выкл");
        return true;
    }

    public void stop(Player staff, boolean announce) {
        Session session = sessions.remove(staff.getUniqueId());
        if (session == null) {
            if (announce) Msg.err(staff, "Ты не в спеке");
            return;
        }
        staff.setSpectatorTarget(null);
        showToPlayers(staff);
        if (session.mode != null && session.mode != GameMode.SPECTATOR) {
            staff.setGameMode(session.mode);
        } else {
            staff.setGameMode(GameMode.SURVIVAL);
        }
        if (session.back != null && session.back.getWorld() != null) {
            staff.teleport(session.back, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
        if (announce) Msg.ok(staff, "Спек выключен");
    }

    public void stopAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (sessions.containsKey(p.getUniqueId())) {
                stop(p, false);
            }
        }
        sessions.clear();
    }

    private void hideFromPlayers(Player staff) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(staff)) continue;
            if (other.hasPermission("bydlo.staff") || other.hasPermission("bydlo.spec") || other.isOp()) continue;
            other.hidePlayer(plugin, staff);
        }
    }

    private void showToPlayers(Player staff) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(staff)) continue;
            other.showPlayer(plugin, staff);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player joined = e.getPlayer();
        for (UUID staffId : sessions.keySet()) {
            Player staff = Bukkit.getPlayer(staffId);
            if (staff == null || !staff.isOnline()) continue;
            if (joined.hasPermission("bydlo.staff") || joined.hasPermission("bydlo.spec") || joined.isOp()) continue;
            joined.hidePlayer(plugin, staff);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (sessions.containsKey(p.getUniqueId())) {
            stop(p, false);
        }
        for (Session s : sessions.values()) {
            if (p.getUniqueId().equals(s.targetId)) {
                s.targetId = null;
            }
        }
        Player staffWatching = null;
        for (var e2 : sessions.entrySet()) {
            if (p.getName().equalsIgnoreCase(e2.getValue().targetName) && e2.getValue().targetId == null) {
                staffWatching = Bukkit.getPlayer(e2.getKey());
                break;
            }
        }
        if (staffWatching != null) {
            staffWatching.setSpectatorTarget(null);
            Msg.tip(staffWatching, p.getName() + " вышел · ты всё ещё в спеке, /спек выкл");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        if (!isWatching(e.getPlayer().getUniqueId())) return;
        if (e.getNewGameMode() != GameMode.SPECTATOR) {
            e.setCancelled(true);
        }
    }
}
