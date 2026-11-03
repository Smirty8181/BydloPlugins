package ru.bydlolend.bydlomod.cmd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.bydlolend.bydlomod.mod.*;
import ru.bydlolend.bydlomod.util.Msg;

public final class MyWarnsCommand implements CommandExecutor {
    private final WarnService warns;
    private final ModerationService moderation;
    private final BlackMarkService blackMarks;

    public MyWarnsCommand(WarnService warns, ModerationService moderation, BlackMarkService blackMarks) {
        this.warns = warns;
        this.moderation = moderation;
        this.blackMarks = blackMarks;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Только для игроков");
            return true;
        }
        Msg.header(player, "Мои наказания");

        moderation.getChatMute(player.getUniqueId()).ifPresentOrElse(p ->
                        Msg.tip(player, "Мут чата: &cактивен &7("
                                + (p.expiresAt == 0 ? "навсегда"
                                : ModerationService.formatLeft(p.expiresAt - System.currentTimeMillis()))
                                + ") &8— &f" + p.reason),
                () -> Msg.tip(player, "Мут чата: &aнет"));

        moderation.getVmute(player.getUniqueId()).ifPresentOrElse(p ->
                        Msg.tip(player, "Мут войса: &cактивен &7("
                                + (p.expiresAt == 0 ? "навсегда"
                                : ModerationService.formatLeft(p.expiresAt - System.currentTimeMillis()))
                                + ") &8— &f" + p.reason),
                () -> Msg.tip(player, "Мут войса: &aнет"));

        PrisonHook.BanInfo ban = PrisonHook.getBan(player.getUniqueId());
        if (ban != null) {
            String left = ban.expiresAt < 0 ? "навсегда"
                    : ModerationService.formatLeft(ban.expiresAt - System.currentTimeMillis());
            Msg.tip(player, "Бан/тюрьма: &cактивен &7(" + left + ") &8— &f" + ban.reason);
        } else {
            Msg.tip(player, "Бан/тюрьма: &aнет");
        }

        blackMarks.get(player.getUniqueId()).ifPresentOrElse(m ->
                        Msg.tip(player, "Чёрная метка: &cещё &f"
                                + ModerationService.formatLeft(m.expiresAt - System.currentTimeMillis())),
                () -> Msg.tip(player, "Чёрная метка: &aнет"));

        var list = warns.getWarns(player.getUniqueId());
        Msg.tip(player, "Варнов: &f" + list.size());
        int i = 0;
        for (WarnService.Warn w : list) {
            if (i++ >= 15) {
                Msg.tip(player, "&8… и ещё " + (list.size() - 15));
                break;
            }
            player.sendMessage(ru.bydlolend.bydlomod.BydloMod.color(
                    "&e  #" + w.id + " &7" + WarnService.formatTime(w.createdAt)
                            + " &8— &f" + w.reason + " &7(" + w.staff + ")"));
        }
        Msg.footer(player);
        return true;
    }
}
