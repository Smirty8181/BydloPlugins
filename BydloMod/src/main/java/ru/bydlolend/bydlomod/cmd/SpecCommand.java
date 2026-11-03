package ru.bydlolend.bydlomod.cmd;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.bydlolend.bydlomod.spec.SpecService;
import ru.bydlolend.bydlomod.util.Msg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SpecCommand implements CommandExecutor, TabCompleter {
    private final SpecService spec;

    public SpecCommand(SpecService spec) {
        this.spec = spec;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player staff)) {
            Msg.err(sender, "Только для игроков");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("выкл")
                || args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("stop")) {
            if (spec.isWatching(staff.getUniqueId())) {
                spec.stop(staff, true);
            } else {
                Msg.tip(staff, "/спек <ник> — тихо следить");
            }
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Msg.err(staff, "Игрок оффлайн");
            return true;
        }
        spec.start(staff, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            if ("выкл".startsWith(p)) out.add("выкл");
            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (pl.getName().toLowerCase(Locale.ROOT).startsWith(p)) out.add(pl.getName());
            }
        }
        return out;
    }
}
