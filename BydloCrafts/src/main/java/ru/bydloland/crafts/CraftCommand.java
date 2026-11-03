package ru.bydloland.crafts;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public final class CraftCommand implements CommandExecutor, TabCompleter {

    private final CraftGui gui;

    public CraftCommand(CraftGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только игрок: /крафты");
            return true;
        }
        if (!player.hasPermission("bydlocrafts.use") && !player.isOp()) {
            player.sendMessage("§cНет прав");
            return true;
        }
        gui.openHome(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
