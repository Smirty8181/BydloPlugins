package ru.bydlolend.bydlomod.cmd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.bydlolend.bydlomod.gui.SupportGui;
import ru.bydlolend.bydlomod.mod.SupportService;
import ru.bydlolend.bydlomod.util.Msg;

public final class SupportCommand implements CommandExecutor {
    private final SupportService support;
    private final SupportGui gui;

    public SupportCommand(SupportService support, SupportGui gui) {
        this.support = support;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Только для игроков");
            return true;
        }

        if (args.length == 0) {
            if (SupportService.isStaff(player)) {
                gui.openStaffList(player);
            } else {
                gui.openMine(player);
                Msg.tip(player, "Написать: &f/поддержка <текст>");
            }
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("list") && SupportService.isStaff(player)) {
            gui.openStaffList(player);
            return true;
        }

        if (args.length == 1 && (args[0].equalsIgnoreCase("mine") || args[0].equalsIgnoreCase("мои"))) {
            gui.openMine(player);
            return true;
        }

        String body = String.join(" ", args);
        if (body.length() < 3) {
            Msg.err(player, "Слишком короткое сообщение");
            return true;
        }
        if (support.onCooldown(player.getUniqueId())) {
            Msg.err(player, "Подожди ещё " + support.cooldownLeftSec(player.getUniqueId()) + "с");
            return true;
        }
        var t = support.create(player, body);
        Msg.ok(player, "Тикет #" + t.id + " создан. Жди ответа стаффа, затем /пс");
        return true;
    }
}
