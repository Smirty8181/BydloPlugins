package ru.bydlolend.bydlomod.cmd;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.bydlolend.bydlomod.BydloMod;
import ru.bydlolend.bydlomod.mod.ModerationService;

public final class MuteCommand implements CommandExecutor {
    private final ModerationService mod;

    public MuteCommand(ModerationService mod) {
        this.mod = mod;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(BydloMod.color("&d[&5БыдлоЛенд&d]&r &e/мут <ник> <время> [причина]"));
            sender.sendMessage(BydloMod.color("&7Пример: &f/мут Nick 30м спам"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(BydloMod.color("&d[&5БыдлоЛенд&d]&r &cИгрок не онлайн"));
            return true;
        }
        String[] rd = args.length > 2
                ? ModerationService.splitReasonDesc(java.util.Arrays.copyOfRange(args, 2, args.length))
                : new String[]{"", ""};
        String result = mod.punishChat(target, sender.getName(), args[1], rd[0], rd[1]);
        sender.sendMessage(BydloMod.get().prefix() + result);
        String broadcast = BydloMod.color("&d[&5БыдлоЛенд&d]&r &c" + sender.getName()
                + " &7замутил &c" + target.getName() + " &7(" + args[1] + ")");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("bydlo.mute") || p.hasPermission("bydlo.mod") || p.hasPermission("bydlo.admin")) {
                p.sendMessage(broadcast);
            }
        }
        return true;
    }
}
