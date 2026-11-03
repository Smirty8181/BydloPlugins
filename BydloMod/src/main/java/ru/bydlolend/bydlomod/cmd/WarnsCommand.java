package ru.bydlolend.bydlomod.cmd;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.bydlolend.bydlomod.gui.WarnsGui;
import ru.bydlolend.bydlomod.mod.WarnService;
import ru.bydlolend.bydlomod.mod.WarnService.Warn;
import ru.bydlolend.bydlomod.util.Msg;

import java.util.List;
import java.util.UUID;

public final class WarnsCommand implements CommandExecutor {
    private final WarnService warns;
    private final WarnsGui gui;

    public WarnsCommand(WarnService warns, WarnsGui gui) {
        this.warns = warns;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            Msg.tip(sender, "/варны <ник>");
            return true;
        }
        UUID id = warns.findId(args[0]);
        OfflinePlayer op = Bukkit.getOfflinePlayer(args[0]);
        if (id == null) {
            if (!op.hasPlayedBefore() && !op.isOnline()) {
                Msg.err(sender, "Игрок не найден");
                return true;
            }
            id = op.getUniqueId();
        }
        String name = op.getName() != null ? op.getName() : args[0];
        List<Warn> list = warns.getWarns(id);
        if (sender instanceof Player p) {
            gui.open(p, id, name);
        } else {
            Msg.header(sender, "Варны " + name + " (" + list.size() + ")");
            for (Warn w : list) {
                sender.sendMessage("§e#" + w.id + " §7" + WarnService.formatTime(w.createdAt)
                        + " §8| §f" + w.staff + " §8| §7" + w.reason);
            }
            Msg.footer(sender);
        }
        return true;
    }
}
