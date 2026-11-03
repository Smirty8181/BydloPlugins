package ru.bydlolend.bydlomod.cmd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.bydlolend.bydlomod.gui.StaffGui;
import ru.bydlolend.bydlomod.util.Msg;

public final class StaffCommand implements CommandExecutor {
    private final StaffGui gui;

    public StaffCommand(StaffGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Только для игроков");
            return true;
        }
        gui.openMain(player);
        return true;
    }
}
