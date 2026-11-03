package ru.bydlolend.bydlomod.cmd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import ru.bydlolend.bydlomod.BydloMod;
import ru.bydlolend.bydlomod.fx.CinematicStopService;
import ru.bydlolend.bydlomod.util.Msg;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class CinematicStopCommand implements CommandExecutor, TabCompleter {

    private final BydloMod plugin;
    private final CinematicStopService.Mode mode;

    public CinematicStopCommand(BydloMod plugin, CinematicStopService.Mode mode) {
        this.plugin = plugin;
        this.mode = mode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("bydlo.admin") && !sender.hasPermission("bydlo.adminlock")) {
            Msg.err(sender, "Нет прав");
            return true;
        }
        String arg = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (arg.equals("отмена") || arg.equals("cancel")) {
            if (plugin.cinematic().running()) plugin.cinematic().cancel();
            else Msg.tip(sender, "Сейчас ничего не выключается");
            return true;
        }
        if (arg.equals("сейчас") || arg.equals("now") || arg.equals("force")) {
            plugin.cinematic().start(mode, 4, sender);
            plugin.cinematic().skipToEnd();
            return true;
        }
        int sec = plugin.getConfig().getInt("cinematic.seconds", 12);
        if (arg.matches("\\d+")) {
            sec = Integer.parseInt(arg);
        }
        plugin.cinematic().start(mode, sec, sender);
        Msg.ok(sender, (mode == CinematicStopService.Mode.RESTART ? "Рестарт" : "Выключение")
                + " через " + sec + "с · отмена: /" + label + " отмена");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String p = args[0].toLowerCase(Locale.ROOT);
        return Stream.of("отмена", "сейчас", "10", "12", "15", "20")
                .filter(s -> s.startsWith(p))
                .toList();
    }
}
