package ru.bydlolend.bydlomod.mod;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import ru.bydlolend.bydlomod.BydloMod;
import ru.bydlolend.bydlomod.cmd.*;
import ru.bydlolend.bydlomod.gui.PlayerCardGui;
import ru.bydlolend.bydlomod.gui.ReportsGui;
import ru.bydlolend.bydlomod.gui.StaffGui;
import ru.bydlolend.bydlomod.gui.SupportGui;
import ru.bydlolend.bydlomod.gui.WarnsGui;
import ru.bydlolend.bydlomod.spec.SpecService;

import java.util.UUID;

public final class ModCommandOverride implements Listener {
    private final MuteCommand mute;
    private final UnmuteCommand unmute;
    private final VmuteCommand vmute;
    private final UnvmuteCommand unvmute;
    private final WarnCommand warn;
    private final WarnsCommand warnsCmd;
    private final UnwarnCommand unwarn;
    private final ReportCommand report;
    private final ReportsCommand reportsCmd;
    private final PlayerCardCommand playerCardCmd;
    private final MyWarnsCommand myWarnsCmd;
    private final RChatCommand rchatCmd;
    private final SupportCommand supportCmd;
    private final SChatCommand schatCmd;
    private final MailCommand mailCmd;
    private final StaffCommand staffCmd;
    private final SpecCommand specCmd;
    private final HistoryService history;

    public ModCommandOverride(ModerationService mod, WarnService warns, ReportService reports,
                              SupportService support, HistoryService history, BlackMarkService blackMarks,
                              WarnsGui warnsGui, ReportsGui reportsGui,
                              PlayerCardGui playerCardGui, SupportGui supportGui,
                              MailCommand mailCmd, StaffGui staffGui, SpecService spec) {
        this.history = history;
        this.mute = new MuteCommand(mod);
        this.unmute = new UnmuteCommand(mod);
        this.vmute = new VmuteCommand(mod);
        this.unvmute = new UnvmuteCommand(mod);
        this.warn = new WarnCommand(warns);
        this.warnsCmd = new WarnsCommand(warns, warnsGui);
        this.unwarn = new UnwarnCommand(warns);
        this.report = new ReportCommand(reports);
        this.reportsCmd = new ReportsCommand(reports, reportsGui);
        this.playerCardCmd = new PlayerCardCommand(playerCardGui, warns);
        this.myWarnsCmd = new MyWarnsCommand(warns, mod, blackMarks);
        this.rchatCmd = new RChatCommand(reports);
        this.supportCmd = new SupportCommand(support, supportGui);
        this.schatCmd = new SChatCommand(support);
        this.mailCmd = mailCmd;
        this.staffCmd = new StaffCommand(staffGui);
        this.specCmd = new SpecCommand(spec);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2 || raw.charAt(0) != '/') return;

        String body = raw.substring(1).trim();
        if (body.isEmpty()) return;

        int colon = body.indexOf(':');
        int space = body.indexOf(' ');
        if (colon > 0 && (space < 0 || colon < space)) {
            String ns = body.substring(0, colon).toLowerCase();
            if (ns.equals("essentials") || ns.equals("libertybans") || ns.equals("essentialsx")
                    || ns.equals("bydlomod") || ns.equals("minecraft") || ns.equals("bukkit")) {
                body = body.substring(colon + 1).trim();
            }
        }

        String[] parts = body.split("\\s+");
        if (parts.length == 0) return;
        String label = parts[0].toLowerCase();
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        Player sender = event.getPlayer();

        switch (label) {
            case "mute", "мут", "chatmute" -> {
                if (!canMute(sender)) return;
                event.setCancelled(true);
                mute.onCommand(sender, null, label, args);
            }
            case "unmute", "размут", "chatunmute" -> {
                if (!canMute(sender)) return;
                event.setCancelled(true);
                unmute.onCommand(sender, null, label, args);
            }
            case "vmute", "вмут", "voicemute" -> {
                if (!canVmute(sender)) return;
                event.setCancelled(true);
                vmute.onCommand(sender, null, label, args);
            }
            case "unvmute", "развмут", "unvoicemute" -> {
                if (!canVmute(sender)) return;
                event.setCancelled(true);
                unvmute.onCommand(sender, null, label, args);
            }
            case "warn", "варн", "warning" -> {
                if (!canWarn(sender)) return;
                event.setCancelled(true);
                warn.onCommand(sender, null, label, args);
            }
            case "warns", "варны", "warnings" -> {
                if (!canWarn(sender)) return;
                event.setCancelled(true);
                warnsCmd.onCommand(sender, null, label, args);
            }
            case "unwarn", "снятварн", "delwarn" -> {
                if (!canWarn(sender)) return;
                event.setCancelled(true);
                unwarn.onCommand(sender, null, label, args);
            }
            case "report", "репорт", "complaint" -> {
                if (!sender.hasPermission("bydlo.report") && !sender.isOp()) return;
                event.setCancelled(true);
                report.onCommand(sender, null, label, args);
            }
            case "reports", "репорты" -> {
                if (!canReports(sender)) return;
                event.setCancelled(true);
                reportsCmd.onCommand(sender, null, label, args);
            }
            case "playercard", "карточка", "card" -> {
                if (!canReports(sender)) return;
                event.setCancelled(true);
                playerCardCmd.onCommand(sender, null, label, args);
            }
            case "mywarns", "моиварны", "mypunishments", "моинаказания" -> {
                event.setCancelled(true);
                myWarnsCmd.onCommand(sender, null, label, args);
            }
            case "rchat", "рс", "рчат" -> {
                event.setCancelled(true);
                rchatCmd.onCommand(sender, null, label, args);
            }
            case "support", "поддержка", "ticket", "тикет" -> {
                event.setCancelled(true);
                supportCmd.onCommand(sender, null, label, args);
            }
            case "schat", "пс", "пчат" -> {
                event.setCancelled(true);
                schatCmd.onCommand(sender, null, label, args);
            }
            case "mail", "почта", "письмо", "letter" -> {
                event.setCancelled(true);
                mailCmd.onCommand(sender, null, label, args);
            }
            case "staff", "стафф", "модменю", "staffmenu" -> {
                if (!canStaff(sender)) return;
                event.setCancelled(true);
                staffCmd.onCommand(sender, null, label, args);
            }
            case "spec", "спек", "следить", "watch" -> {
                if (!canSpec(sender)) return;
                event.setCancelled(true);
                specCmd.onCommand(sender, null, label, args);
            }
            case "ban", "tempban", "бан", "заключить" -> {
                if (!sender.hasPermission("bydloprison.ban") && !sender.isOp()
                        && !sender.hasPermission("bydlo.mod") && !sender.hasPermission("bydlo.admin")) {
                    return;
                }
                event.setCancelled(true);
                if (args.length < 2) {
                    sender.sendMessage(BydloMod.color("&d[&5БыдлоЛенд&d]&r &e/бан <ник> <время> [причина]"));
                    return;
                }
                logBan(args[0], sender.getName(), args.length > 2
                        ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "", args[1]);
                // От имени модератора — в записи бана будет его ник, не Console
                Bukkit.dispatchCommand(sender, "prison ban " + String.join(" ", args));
            }
            case "unban", "pardon", "разбан", "освободить" -> {
                if (!sender.hasPermission("bydloprison.unban") && !sender.isOp()
                        && !sender.hasPermission("bydlo.mod") && !sender.hasPermission("bydlo.admin")) {
                    return;
                }
                event.setCancelled(true);
                if (args.length < 1) {
                    sender.sendMessage(BydloMod.color("&d[&5БыдлоЛенд&d]&r &e/разбан <ник>"));
                    return;
                }
                logUnban(args[0], sender.getName());
                Bukkit.dispatchCommand(sender, "prison unban " + args[0]);
            }
            default -> {
            }
        }
    }

    private void logBan(String name, String staff, String reason, String duration) {
        if (history == null) return;
        UUID id = resolveUuid(name);
        if (id == null) return;
        long exp = ModerationService.parseExpiry(duration);
        history.log(id, name, "BAN", "APPLY", staff, reason, exp < 0 ? 0 : exp);
    }

    private void logUnban(String name, String staff) {
        if (history == null) return;
        UUID id = resolveUuid(name);
        if (id == null) return;
        history.log(id, name, "BAN", "REMOVE", staff, "", 0);
    }

    private static UUID resolveUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)
                    && (op.hasPlayedBefore() || op.isOnline())) {
                return op.getUniqueId();
            }
        }
        return null;
    }

    private static boolean canMute(CommandSender s) {
        return s.hasPermission("bydlo.mute") || s.hasPermission("bydlo.mod")
                || s.hasPermission("bydlo.admin") || s.isOp();
    }

    private static boolean canVmute(CommandSender s) {
        return s.hasPermission("bydlo.vmute") || s.hasPermission("bydlo.mod")
                || s.hasPermission("bydlo.admin") || s.isOp();
    }

    private static boolean canWarn(CommandSender s) {
        return s.hasPermission("bydlo.warn") || s.hasPermission("bydlo.mod")
                || s.hasPermission("bydlo.admin") || s.isOp();
    }

    private static boolean canReports(CommandSender s) {
        return s.hasPermission("bydlo.reports") || s.hasPermission("bydlo.mod")
                || s.hasPermission("bydlo.admin") || s.isOp();
    }

    private static boolean canStaff(CommandSender s) {
        return s.hasPermission("bydlo.staff") || s.hasPermission("bydlo.mod")
                || s.hasPermission("bydlo.admin") || s.isOp();
    }

    private static boolean canSpec(CommandSender s) {
        return s.hasPermission("bydlo.spec") || s.hasPermission("bydlo.staff")
                || s.hasPermission("bydlo.mod") || s.hasPermission("bydlo.admin") || s.isOp();
    }
}
