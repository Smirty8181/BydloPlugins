package ru.bydlolend.bydlomod.cmd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import ru.bydlolend.bydlomod.BydloMod;
import ru.bydlolend.bydlomod.mod.ModerationService;

public final class UnvmuteCommand implements CommandExecutor {
    private final ModerationService mod;
    public UnvmuteCommand(ModerationService mod) { this.mod = mod; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(BydloMod.color("&e/unvmute <ник>"));
            return true;
        }
        boolean ok = mod.removeByName(args[0]);
        sender.sendMessage(BydloMod.get().prefix() + BydloMod.color(
                ok ? "&aВойс-мут снят: &f" + args[0] : "&cВойс-мут не найден"));
        return true;
    }
}
