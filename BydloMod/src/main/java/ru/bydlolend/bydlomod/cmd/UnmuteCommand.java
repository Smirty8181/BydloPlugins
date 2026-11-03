package ru.bydlolend.bydlomod.cmd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import ru.bydlolend.bydlomod.BydloMod;
import ru.bydlolend.bydlomod.mod.ModerationService;

public final class UnmuteCommand implements CommandExecutor {
    private final ModerationService mod;

    public UnmuteCommand(ModerationService mod) {
        this.mod = mod;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(BydloMod.color("&e/размут <ник>"));
            return true;
        }
        boolean ok = mod.removeChatByName(args[0]);
        if (!ok) {
            sender.sendMessage(BydloMod.color("&cМут чата не найден."));
            return true;
        }
        sender.sendMessage(BydloMod.get().prefix() + BydloMod.color("&aМут чата снят с &f" + args[0]));
        org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayerExact(args[0]);
        if (online != null) {
            online.sendMessage(BydloMod.get().prefix() + BydloMod.color("&aМут чата снят."));
        }
        return true;
    }
}
