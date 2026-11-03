package ru.bydloland.prison.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.bydloland.prison.BydloPrisonPlugin;
import ru.bydloland.prison.model.PrisonRecord;
import ru.bydloland.prison.service.MessageService;
import ru.bydloland.prison.service.PrisonService;
import ru.bydloland.prison.util.DurationParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public final class PrisonCommand implements CommandExecutor, TabCompleter {

    private final BydloPrisonPlugin plugin;
    private final PrisonService prison;
    private final MessageService messages;

    public PrisonCommand(BydloPrisonPlugin plugin, PrisonService prison, MessageService messages) {
        this.plugin = plugin;
        this.prison = prison;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String root = label.toLowerCase(Locale.ROOT);

        // /заключить /бан /ban <ник> <время> [причина]
        if (root.equals("заключить") || root.equals("заключениебан")
                || root.equals("бан") || root.equals("ban") || root.equals("tempban")) {
            return banAlias(sender, args);
        }

        // /разбан /unban /освободить
        if (root.equals("разбан") || root.equals("освободить")
                || root.equals("unban") || root.equals("pardon")) {
            return doUnban(sender, args);
        }

        // /setprison | /тюрьматочка | /prisonpos
        if (root.equals("setprison") || root.equals("тюрьматочка") || root.equals("prisonpos")) {
            return doSetPos(sender);
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "ban", "заключить", "imprison" -> doBan(sender, Arrays.copyOfRange(args, 1, args.length));
            case "unban", "освободить", "release" -> doUnban(sender, Arrays.copyOfRange(args, 1, args.length));
            case "info", "check" -> doInfo(sender, Arrays.copyOfRange(args, 1, args.length));
            case "list", "список" -> doList(sender);
            case "setpos", "setspawn", "точка", "pos" -> doSetPos(sender);
            case "reload" -> doReload(sender);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean banAlias(CommandSender sender, String[] args) {
        return doBan(sender, args);
    }

    private boolean doBan(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bydloprison.ban")) {
            sender.sendMessage(messages.prefix() + messages.get("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.prefix() + messages.get("usage-ban"));
            return true;
        }
        OfflinePlayer target = resolvePlayer(args[0]);
        if (target == null || (target.getName() == null && !target.hasPlayedBefore() && !target.isOnline())) {
            // still allow by exact online name
            Player online = Bukkit.getPlayerExact(args[0]);
            if (online == null) {
                sender.sendMessage(messages.prefix() + messages.get("player-not-found"));
                return true;
            }
            target = online;
        }
        UUID uuid = target.getUniqueId();
        String name = target.getName() != null ? target.getName() : args[0];
        String duration = args[1];
        String reason = args.length > 2
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : "Нарушение правил";

        String err = prison.imprison(uuid, name, duration, reason, sender.getName());
        if (err != null) {
            sender.sendMessage(messages.prefix() + err);
            return true;
        }

        PrisonRecord record = prison.get(uuid);
        long rem = record == null ? -1 : record.remainingMs(System.currentTimeMillis());
        String time = DurationParser.formatRemaining(rem, messages.color(messages.raw("permanent")));
        String broadcast = messages.prefix() + messages.apply("ban-broadcast", name, time, reason);
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("bydloprison.ban") || p.hasPermission("bydloprison.info"))
                .forEach(p -> p.sendMessage(broadcast));
        sender.sendMessage(broadcast);
        return true;
    }

    private boolean doUnban(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bydloprison.unban")) {
            sender.sendMessage(messages.prefix() + messages.get("no-permission"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(messages.prefix() + messages.get("usage-unban"));
            return true;
        }
        OfflinePlayer target = resolvePlayer(args[0]);
        if (target == null) {
            sender.sendMessage(messages.prefix() + messages.get("player-not-found"));
            return true;
        }
        if (!prison.release(target.getUniqueId(), true)) {
            sender.sendMessage(messages.prefix() + messages.get("not-imprisoned"));
            return true;
        }
        sender.sendMessage(messages.prefix() + messages.color("&a" + args[0] + " освобождён."));
        return true;
    }

    private boolean doInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bydloprison.info")) {
            sender.sendMessage(messages.prefix() + messages.get("no-permission"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(messages.prefix() + messages.color("&e/prison info <ник>"));
            return true;
        }
        OfflinePlayer target = resolvePlayer(args[0]);
        if (target == null) {
            sender.sendMessage(messages.prefix() + messages.get("player-not-found"));
            return true;
        }
        PrisonRecord record = prison.get(target.getUniqueId());
        if (record == null || record.isExpired(System.currentTimeMillis())) {
            sender.sendMessage(messages.prefix() + messages.get("not-imprisoned"));
            return true;
        }
        String time = DurationParser.formatRemaining(record.remainingMs(System.currentTimeMillis()),
                messages.color(messages.raw("permanent")));
        sender.sendMessage(messages.prefix() + messages.apply("info-line", record.name(), time, record.reason()));
        sender.sendMessage(messages.color("&7Выдал: &f" + record.bannedBy()));
        return true;
    }

    private boolean doList(CommandSender sender) {
        if (!sender.hasPermission("bydloprison.list")) {
            sender.sendMessage(messages.prefix() + messages.get("no-permission"));
            return true;
        }
        var all = prison.storage().all().stream()
                .filter(r -> !r.isExpired(System.currentTimeMillis()))
                .collect(Collectors.toList());
        if (all.isEmpty()) {
            sender.sendMessage(messages.prefix() + messages.color("&7Нет заключённых."));
            return true;
        }
        sender.sendMessage(messages.prefix() + messages.color("&eЗаключённые (&f" + all.size() + "&e):"));
        for (PrisonRecord r : all) {
            String time = DurationParser.formatRemaining(r.remainingMs(System.currentTimeMillis()),
                    messages.color(messages.raw("permanent")));
            sender.sendMessage(messages.apply("info-line", r.name(), time, r.reason()));
        }
        return true;
    }

    private boolean doSetPos(CommandSender sender) {
        if (!sender.hasPermission("bydloprison.setpos")) {
            sender.sendMessage(messages.prefix() + messages.get("no-permission"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.color("&cТолько игрок."));
            return true;
        }
        Location loc = player.getLocation();
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        plugin.getConfig().set("prison.world", world);
        plugin.getConfig().set("prison.x", loc.getX());
        plugin.getConfig().set("prison.y", loc.getY());
        plugin.getConfig().set("prison.z", loc.getZ());
        plugin.getConfig().set("prison.yaw", (double) loc.getYaw());
        plugin.getConfig().set("prison.pitch", (double) loc.getPitch());
        // если релиз не задан — тоже сюда рядом со спавном мира не трогаем, только тюрьма
        plugin.saveConfig();
        plugin.reloadConfig();
        sender.sendMessage(messages.prefix() + messages.get("setpos-ok"));
        sender.sendMessage(messages.color("&7Мир: &f" + world
                + " &7XYZ: &f" + String.format("%.1f %.1f %.1f", loc.getX(), loc.getY(), loc.getZ())));
        return true;
    }

    private boolean doReload(CommandSender sender) {
        if (!sender.hasPermission("bydloprison.reload")) {
            sender.sendMessage(messages.prefix() + messages.get("no-permission"));
            return true;
        }
        plugin.reloadAll();
        sender.sendMessage(messages.prefix() + messages.get("reload-ok"));
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(messages.prefix() + messages.color("&e/prison ban <ник> <время> [причина]"));
        sender.sendMessage(messages.color("&e/prison unban <ник>"));
        sender.sendMessage(messages.color("&e/prison info <ник>"));
        sender.sendMessage(messages.color("&e/prison list"));
        sender.sendMessage(messages.color("&e/prison setpos"));
        sender.sendMessage(messages.color("&e/prison reload"));
        sender.sendMessage(messages.color("&7Алиас: &f/заключить <ник> <время> [причина]"));
        sender.sendMessage(messages.color("&7Время: &f5м 2ч 3д 30д навсегда"));
    }

    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) {
                return op;
            }
        }
        // last resort: create offline by name (Paper)
        return Bukkit.getOfflinePlayer(name);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String root = alias.toLowerCase(Locale.ROOT);
        if (List.of("заключить", "заключениебан", "бан", "ban", "tempban").contains(root)) {
            if (args.length == 1) {
                return filterPlayers(args[0]);
            }
            if (args.length == 2) {
                return filter(args[1], List.of("5м", "30м", "1ч", "1д", "7д", "30д", "навсегда"));
            }
            return out;
        }
        if (List.of("разбан", "освободить", "unban", "pardon").contains(root)) {
            if (args.length == 1) return filterPlayers(args[0]);
            return out;
        }
        if (args.length == 1) {
            return filter(args[0], List.of("ban", "unban", "info", "list", "setpos", "reload", "заключить", "освободить"));
        }
        if (args.length == 2 && List.of("ban", "unban", "info", "заключить", "освободить").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filterPlayers(args[1]);
        }
        if (args.length == 3 && List.of("ban", "заключить").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(args[2], List.of("5м", "30м", "1ч", "1д", "7д", "30д", "навсегда"));
        }
        return out;
    }

    private List<String> filterPlayers(String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p))
                .collect(Collectors.toList());
    }

    private List<String> filter(String prefix, List<String> options) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }
}
