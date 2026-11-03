package ru.bydlolend.bydlomod.cmd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.bydlolend.bydlomod.gui.ReportsGui;
import ru.bydlolend.bydlomod.mod.ReportService;
import ru.bydlolend.bydlomod.util.Msg;

public final class ReportsCommand implements CommandExecutor {
    private final ReportService reports;
    private final ReportsGui gui;

    public ReportsCommand(ReportService reports, ReportsGui gui) {
        this.reports = reports;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            int n = reports.openReports().size();
            Msg.tip(sender, "Открытых репортов: " + n + " (GUI только в игре)");
            return true;
        }
        gui.openList(player);
        return true;
    }
}
