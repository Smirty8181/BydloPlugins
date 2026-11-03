package ru.bydloland.implants;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ImplantCommand implements CommandExecutor, TabCompleter {

    private final BydloImplantsPlugin plugin;

    public ImplantCommand(BydloImplantsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("reload") || sub.equals("перезагрузка")) {
                if (!sender.hasPermission("bydloimplants.admin")) {
                    sender.sendMessage("§cНет прав");
                    return true;
                }
                plugin.reloadConfig();
                plugin.service().load();
                sender.sendMessage("§aBydloImplants перезагружен.");
                return true;
            }
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков: /импланты");
            return true;
        }

        if (!player.hasPermission("bydloimplants.use") && !player.hasPermission("bydloimplants.admin")) {
            player.sendMessage("§cНет прав");
            return true;
        }

        if (!plugin.service().canUseImplants(player)) {
            player.sendMessage("§cИмпланты доступны только Киберпанкам.");
            return true;
        }

        if (!player.hasPermission("bydloimplants.admin")
                && plugin.ripper() != null
                && !plugin.ripper().isNearRipper(player, 6)) {
            player.sendMessage("§cИмпланты ставит Рипер.");
            player.sendMessage("§7Стол рипера рядом с жителем без профессии.");
            return true;
        }

        ImplantGui.open(plugin, player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("bydloimplants.admin")) {
            String p = args[0].toLowerCase(Locale.ROOT);
            if ("reload".startsWith(p)) return List.of("reload");
        }
        return Collections.emptyList();
    }
}
