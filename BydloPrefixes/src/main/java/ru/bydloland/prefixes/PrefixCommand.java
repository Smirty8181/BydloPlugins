package ru.bydloland.prefixes;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class PrefixCommand implements CommandExecutor, TabCompleter {

    private final BydloPrefixesPlugin plugin;
    private final PrefixGui gui;
    private final PrefixCreateListener createListener;

    public PrefixCommand(BydloPrefixesPlugin plugin, PrefixGui gui, PrefixCreateListener createListener) {
        this.plugin = plugin;
        this.gui = gui;
        this.createListener = createListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("bydloprefixes.admin")) {
            sender.sendMessage("§cТолько админы");
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                gui.openPlayerList(player);
            } else {
                sender.sendMessage("/префикс create|delete|set|clear|list|reload");
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list", "список" -> {
                sender.sendMessage("§d[БыдлоЛенд] §fПрефиксы:");
                for (PrefixDefinition def : plugin.service().prefixes().values()) {
                    sender.sendMessage(" §7- §f" + def.id() + " §7→ " + ColorUtil.legacy(def.display()));
                }
            }
            case "create", "создать", "new" -> {
                if (args.length < 3) {
                    if (sender instanceof Player player) {
                        createListener.start(player);
                        return true;
                    }
                    sender.sendMessage("§e/префикс create <id> <текст...>");
                    sender.sendMessage("§7Пример: §f/префикс create korol <#FBBF24>&lКороль");
                    return true;
                }
                String id = args[1];
                String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                String err = plugin.service().createOrUpdate(id, text, null, null);
                if (err != null) {
                    sender.sendMessage("§c" + err);
                    return true;
                }
                PrefixDefinition def = plugin.service().get(id);
                sender.sendMessage("§aСоздан: §f" + def.id() + " §7→ " + ColorUtil.legacy(def.display()));
            }
            case "delete", "удалить", "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage("§e/префикс delete <id>");
                    return true;
                }
                if (!plugin.service().deletePrefix(args[1])) {
                    sender.sendMessage("§cНет такого префикса");
                    return true;
                }
                sender.sendMessage("§cУдалён: §f" + args[1].toLowerCase(Locale.ROOT));
            }
            case "set", "дать" -> {
                if (args.length < 3) {
                    sender.sendMessage("§e/префикс set <ник> <id>");
                    return true;
                }
                if (!plugin.service().setPrefixByName(args[1], args[2])) {
                    sender.sendMessage("§cНеизвестный префикс. /префикс list");
                    return true;
                }
                PrefixDefinition def = plugin.service().get(args[2]);
                sender.sendMessage("§aВыдан " + ColorUtil.legacy(def.display()) + " §a→ §f" + args[1]);
                Player online = PrefixService.onlineByName(args[1]);
                if (online != null) {
                    online.sendMessage("§d[БыдлоЛенд] §fТебе выдали префикс: " + ColorUtil.legacy(def.display()));
                }
            }
            case "clear", "снять" -> {
                if (args.length < 2) {
                    sender.sendMessage("§e/префикс clear <ник>");
                    return true;
                }
                plugin.service().clearPrefixByName(args[1]);
                sender.sendMessage("§aПрефикс снят у §f" + args[1]);
                Player online = PrefixService.onlineByName(args[1]);
                if (online != null) online.sendMessage("§d[БыдлоЛенд] §7Префикс снят");
            }
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage("§aBydloPrefixes перезагружен");
            }
            case "manage", "управление" -> {
                if (sender instanceof Player player) gui.openManage(player);
            }
            case "gui", "меню" -> {
                if (sender instanceof Player player) gui.openPlayerList(player);
            }
            default -> sender.sendMessage("§e/префикс §7меню  §e/префикс create <id> <текст> §7создать");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("bydloprefixes.admin")) return List.of();
        if (args.length == 1) {
            return filter(Arrays.asList("list", "create", "delete", "set", "clear", "reload", "gui", "manage"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("clear"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("set"))) {
            if (args[0].equalsIgnoreCase("delete")) {
                return filter(new ArrayList<>(plugin.service().prefixes().keySet()), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return filter(new ArrayList<>(plugin.service().prefixes().keySet()), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> src, String token) {
        String t = token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : src) {
            if (s.toLowerCase(Locale.ROOT).startsWith(t)) out.add(s);
        }
        return out;
    }
}
