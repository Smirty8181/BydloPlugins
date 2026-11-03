package ru.bydlolend.bydlomod.cmd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.bydlolend.bydlomod.mod.ReportService;
import ru.bydlolend.bydlomod.util.Msg;

public final class RChatCommand implements CommandExecutor {
    private final ReportService reports;

    public RChatCommand(ReportService reports) {
        this.reports = reports;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Только для игроков");
            return true;
        }
        if (args.length < 1) {
            Msg.tip(sender, "/рс <текст> — чат по открытому репорту");
            return true;
        }
        String text = String.join(" ", args);
        if (!reports.relayChat(player, text)) {
            Msg.err(sender, "Нет активного репорта с чатом (нужно чтобы мод взял твой репорт)");
            return true;
        }
        return true;
    }
}
