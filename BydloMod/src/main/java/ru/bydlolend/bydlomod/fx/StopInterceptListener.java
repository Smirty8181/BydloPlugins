package ru.bydlolend.bydlomod.fx;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import ru.bydlolend.bydlomod.BydloMod;
import ru.bydlolend.bydlomod.util.Msg;

import java.util.Locale;
import java.util.Set;

/** Перехватывает /stop и /restart — сначала анимация, потом стоп. */
public final class StopInterceptListener implements Listener {

    private static final Set<String> STOP = Set.of("stop", "выкл", "выключение", "shutdown");
    private static final Set<String> RESTART = Set.of("restart", "рестарт", "ребут", "reboot");

    private final BydloMod plugin;

    public StopInterceptListener(BydloMod plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayer(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage().trim();
        if (raw.startsWith("/")) raw = raw.substring(1);
        if (handle(raw, event.getPlayer(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onConsole(ServerCommandEvent event) {
        if (handle(event.getCommand().trim(), event.getSender(), false)) {
            event.setCancelled(true);
        }
    }

    private boolean handle(String raw, org.bukkit.command.CommandSender sender, boolean player) {
        if (plugin.cinematic().executing()) return false;
        String[] parts = raw.split("\\s+");
        if (parts.length == 0) return false;
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        int colon = cmd.indexOf(':');
        if (colon >= 0) cmd = cmd.substring(colon + 1);

        CinematicStopService.Mode mode = null;
        if (STOP.contains(cmd)) mode = CinematicStopService.Mode.SHUTDOWN;
        else if (RESTART.contains(cmd)) mode = CinematicStopService.Mode.RESTART;
        else return false;

        if (player && !sender.isOp() && !sender.hasPermission("bydlo.admin") && !sender.hasPermission("bydlo.adminlock")) {
            return false;
        }

        String arg = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";
        if (arg.equals("force") || arg.equals("сейчас") || arg.equals("now") || arg.equals("-f")) {
            plugin.cinematic().skipToEnd();
            if (!plugin.cinematic().running()) {
                plugin.cinematic().start(mode, 3, sender);
                plugin.cinematic().skipToEnd();
            }
            return true;
        }
        if (arg.equals("отмена") || arg.equals("cancel") || arg.equals("abort")) {
            if (plugin.cinematic().running()) plugin.cinematic().cancel();
            else Msg.tip(sender, "Сейчас ничего не выключается");
            return true;
        }

        int sec = plugin.getConfig().getInt("cinematic.seconds", 12);
        if (arg.matches("\\d+")) {
            try {
                sec = Integer.parseInt(arg);
            } catch (NumberFormatException ignored) {
            }
        }
        plugin.cinematic().start(mode, sec, sender);
        return true;
    }
}
