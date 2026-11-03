package ru.bydlolend.bydlomod.cmd;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.bydlolend.bydlomod.gui.PlayerCardGui;
import ru.bydlolend.bydlomod.mod.WarnService;
import ru.bydlolend.bydlomod.util.Msg;

import java.util.UUID;

public final class PlayerCardCommand implements CommandExecutor {
    private final PlayerCardGui gui;
    private final WarnService warns;

    public PlayerCardCommand(PlayerCardGui gui, WarnService warns) {
        this.gui = gui;
        this.warns = warns;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Только для игроков");
            return true;
        }
        if (args.length < 1) {
            Msg.tip(sender, "/карточка <ник>");
            return true;
        }
        UUID id = resolve(args[0]);
        if (id == null) {
            Msg.err(sender, "Игрок не найден");
            return true;
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        String name = op.getName() != null ? op.getName() : args[0];
        gui.open(player, id, name);
        return true;
    }

    private UUID resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        UUID fromWarns = warns.findId(name);
        if (fromWarns != null) return fromWarns;
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)
                    && (op.hasPlayedBefore() || op.isOnline())) {
                return op.getUniqueId();
            }
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer byName = Bukkit.getOfflinePlayer(name);
        if (byName.hasPlayedBefore() || byName.isOnline()) return byName.getUniqueId();
        return null;
    }
}
