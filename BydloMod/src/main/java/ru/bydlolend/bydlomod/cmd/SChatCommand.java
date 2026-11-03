package ru.bydlolend.bydlomod.cmd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.bydlolend.bydlomod.mod.SupportService;
import ru.bydlolend.bydlomod.util.Msg;

public final class SChatCommand implements CommandExecutor {
    private final SupportService support;

    public SChatCommand(SupportService support) {
        this.support = support;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Только для игроков");
            return true;
        }
        if (args.length < 1) {
            Msg.tip(sender, "/пс <текст> — чат по тикету поддержки");
            return true;
        }
        String text = String.join(" ", args);
        if (!support.relayChat(player, text)) {
            Msg.err(sender, "Нет активного тикета (создай /поддержка или дождись ответа)");
            return true;
        }
        return true;
    }
}
