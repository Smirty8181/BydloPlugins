package ru.bydlolend.bydlomod.cmd;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.bydlolend.bydlomod.mod.ReportService;
import ru.bydlolend.bydlomod.util.Msg;

import java.util.Locale;
import java.util.UUID;

public final class ReportCommand implements CommandExecutor {
    private final ReportService reports;

    public ReportCommand(ReportService reports) {
        this.reports = reports;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Только для игроков");
            return true;
        }
        if (args.length < 2) {
            Msg.tip(sender, "/репорт <ник> <причина> &7(можно оффлайн, если уже заходил)");
            return true;
        }
        if (reports.onCooldown(player.getUniqueId())) {
            Msg.err(sender, "Подожди ещё " + reports.cooldownLeftSec(player.getUniqueId()) + "с");
            return true;
        }

        OfflinePlayer target = resolveTarget(args[0]);
        if (target == null) {
            Msg.err(sender, "Игрок не найден (должен хотя бы раз заходить на сервер)");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            Msg.err(sender, "Нельзя репортить себя");
            return true;
        }

        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        var r = reports.create(player, target, reason);
        String offlineHint = target.isOnline() ? "" : " &7(цель оффлайн)";
        Msg.ok(player, "Репорт #" + r.id + " отправлен модерации" + offlineHint);
        return true;
    }

    /** Online exact match, else known offline player (hasPlayedBefore). */
    private static OfflinePlayer resolveTarget(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        String needle = name.toLowerCase(Locale.ROOT);
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() == null) continue;
            if (!op.getName().equalsIgnoreCase(needle)) continue;
            if (!op.hasPlayedBefore() && !op.isOnline()) continue;
            return op;
        }

        // last resort: cached UUID lookup (Paper may resolve known names)
        @SuppressWarnings("deprecation")
        OfflinePlayer byName = Bukkit.getOfflinePlayer(name);
        if (byName.hasPlayedBefore() || byName.isOnline()) {
            UUID id = byName.getUniqueId();
            if (id != null) return byName;
        }
        return null;
    }
}
