package ru.bydlolend.bydlomod.cmd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.bydlolend.bydlomod.adminlock.AdminLockGui;
import ru.bydlolend.bydlomod.util.Msg;

public final class AdminLockCommand implements CommandExecutor {
    private final AdminLockGui gui;

    public AdminLockCommand(AdminLockGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Только игрок");
            return true;
        }
        if (!player.hasPermission("bydlo.adminlock")) {
            Msg.err(player, "Нет прав");
            return true;
        }
        gui.open(player);
        return true;
    }
}
