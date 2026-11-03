package ru.bydlolend.bydlomod.cmd;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.bydlolend.bydlomod.BydloMod;
import ru.bydlolend.bydlomod.mod.WarnService;
import ru.bydlolend.bydlomod.util.Msg;

public final class WarnCommand implements CommandExecutor {
    private final WarnService warns;

    public WarnCommand(WarnService warns) {
        this.warns = warns;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            Msg.tip(sender, "/варн <ник> [причина]");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            Msg.err(sender, "Игрок не найден");
            return true;
        }
        String reason = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "Нарушение правил";
        String staff = sender instanceof Player p ? p.getName() : "Консоль";
        String result = warns.addWarn(target, staff, reason);
        sender.sendMessage(BydloMod.get().prefix() + result);
        warns.notifyStaff("&e" + staff + " &7выдал варн &c" + (target.getName() != null ? target.getName() : args[0]));
        return true;
    }
}
