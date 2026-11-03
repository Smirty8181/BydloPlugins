package ru.bydlolend.bydlomod.cmd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import ru.bydlolend.bydlomod.mod.WarnService;
import ru.bydlolend.bydlomod.util.Msg;

import java.util.UUID;

public final class UnwarnCommand implements CommandExecutor {
    private final WarnService warns;

    public UnwarnCommand(WarnService warns) {
        this.warns = warns;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            Msg.tip(sender, "/снятварн <ник> <номер|#all>");
            return true;
        }
        UUID id = warns.findId(args[0]);
        if (id == null) {
            Msg.err(sender, "Игрок/варны не найдены");
            return true;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("#all") || args[1].equalsIgnoreCase("all")) {
            int n = warns.clearWarns(id);
            Msg.ok(sender, "Снято варнов: " + n);
            return true;
        }
        try {
            int wid = Integer.parseInt(args[1].replace("#", ""));
            if (warns.removeWarn(id, wid)) {
                Msg.ok(sender, "Варн #" + wid + " снят");
            } else {
                Msg.err(sender, "Варн #" + wid + " не найден");
            }
        } catch (NumberFormatException e) {
            Msg.err(sender, "Укажи номер варна или #all");
        }
        return true;
    }
}
