package ru.bydlolend.bydlomod.cmd;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.bydlolend.bydlomod.BydloMod;
import ru.bydlolend.bydlomod.mod.ModerationService;

public final class VmuteCommand implements CommandExecutor {
    private final ModerationService mod;
    public VmuteCommand(ModerationService mod) { this.mod = mod; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(BydloMod.color("&e/вмут <ник> <время> [причина]"));
            sender.sendMessage(BydloMod.color("&7Пример: &f/вмут Nick 30м &7или &f/vmute Nick 10m"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(BydloMod.color("&cИгрок не онлайн"));
            return true;
        }
        String[] rd = args.length > 2
                ? ModerationService.splitReasonDesc(java.util.Arrays.copyOfRange(args, 2, args.length))
                : new String[]{"", ""};
        String result = mod.punishVoice(target, sender.getName(), args[1], rd[0], rd[1]);
        sender.sendMessage(BydloMod.get().prefix() + result);
        return true;
    }
}
