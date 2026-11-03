package ru.bydloland.classes;

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

public final class ClassCommand implements CommandExecutor, TabCompleter {

    private final BydloClassesPlugin plugin;

    public ClassCommand(BydloClassesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                openSelect(player);
            } else {
                sender.sendMessage("Использование: /раса | /class set|list|reload|info");
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list", "список" -> {
                sender.sendMessage("§d[БыдлоЛенд] §fРасы:");
                for (ClassDefinition def : plugin.service().classes().values()) {
                    sender.sendMessage(" §7- §f" + def.id() + " §7→ " + ColorUtil.legacy(def.display()));
                }
            }
            case "info", "инфо", "кд", "cd" -> {
                Player target;
                if (args.length >= 2) {
                    target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        sender.sendMessage("§cИгрок оффлайн");
                        return true;
                    }
                } else if (sender instanceof Player p) {
                    target = p;
                } else {
                    sender.sendMessage("Укажи игрока");
                    return true;
                }
                ClassDefinition def = plugin.service().getPlayerClass(target);
                long left = plugin.service().remainingCooldownMs(target.getUniqueId());
                if (def == null) {
                    sender.sendMessage("§7У " + target.getName() + " нет расы");
                } else {
                    sender.sendMessage("§d[БыдлоЛенд] §f" + target.getName() + ": " + ColorUtil.legacy(def.display())
                            + " §7(" + def.id() + ")");
                }
                if (left > 0) {
                    sender.sendMessage("§7Смена расы через: §f" + ClassService.formatDuration(left));
                } else {
                    sender.sendMessage("§7Смену расы можно сделать сейчас: §f/раса");
                }
            }
            case "set", "дать" -> {
                if (!sender.hasPermission("bydloclasses.admin")) {
                    sender.sendMessage("§cНет прав");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§e/class set <игрок> <раса>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cИгрок оффлайн");
                    return true;
                }
                ClassService.Result r = plugin.service().setClass(target, args[2], true, ClassService.Source.ADMIN);
                if (r == ClassService.Result.UNKNOWN) {
                    sender.sendMessage("§cНеизвестная раса. /class list");
                    return true;
                }
                sender.sendMessage("§aОк (админ, без КД): " + target.getName() + " → " + args[2].toLowerCase(Locale.ROOT));
            }
            case "select", "выбрать" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Только игрок");
                    return true;
                }
                if (args.length < 2) {
                    openSelect(player);
                    return true;
                }
                trySelfSelect(player, args[1]);
            }
            case "clear", "сброс" -> {
                if (!sender.hasPermission("bydloclasses.admin")) {
                    sender.sendMessage("§cНет прав");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§e/class clear <игрок>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cИгрок оффлайн");
                    return true;
                }
                plugin.service().clearClass(target);
                plugin.service().setClass(target, plugin.service().defaultClassId(), false, ClassService.Source.ADMIN);
                sender.sendMessage("§aСброшен на " + plugin.service().defaultClassId() + ": " + target.getName());
            }
            case "reload" -> {
                if (!sender.hasPermission("bydloclasses.admin")) {
                    sender.sendMessage("§cНет прав");
                    return true;
                }
                plugin.reloadAll();
                sender.sendMessage("§aBydloClasses перезагружен");
            }
            case "gui", "меню" -> {
                if (sender instanceof Player player) openSelect(player);
            }
            default -> {
                if (sender instanceof Player player) {
                    // /раса dwarf — короткий выбор
                    if (plugin.service().get(sub) != null) {
                        trySelfSelect(player, sub);
                        return true;
                    }
                }
                sender.sendMessage("§e/раса §7— меню  §e/class set <игрок> <раса> §7— админ");
            }
        }
        return true;
    }

    private void openSelect(Player player) {
        if (!player.hasPermission("bydloclasses.use") && !player.hasPermission("bydloclasses.admin")) {
            player.sendMessage("§cНет прав");
            return;
        }
        long left = plugin.service().remainingCooldownMs(player.getUniqueId());
        if (left > 0 && !player.hasPermission("bydloclasses.admin")) {
            player.sendMessage("§cСменить расу можно через §f" + ClassService.formatDuration(left));
            player.sendMessage("§7Текущая: " + (
                    plugin.service().getPlayerClass(player) == null
                            ? "§7нет"
                            : ColorUtil.legacy(plugin.service().getPlayerClass(player).display())
            ));
            // всё равно открыть меню для просмотра
        }
        ClassGui.open(plugin, player);
    }

    private void trySelfSelect(Player player, String classId) {
        if (!player.hasPermission("bydloclasses.use") && !player.hasPermission("bydloclasses.admin")) {
            player.sendMessage("§cНет прав");
            return;
        }
        ClassService.Source src = player.hasPermission("bydloclasses.admin")
                ? ClassService.Source.ADMIN
                : ClassService.Source.SELF;
        ClassService.Result r = plugin.service().setClass(player, classId, true, src);
        switch (r) {
            case OK -> {}
            case UNKNOWN -> player.sendMessage("§cНеизвестная раса");
            case COOLDOWN -> player.sendMessage("§cКулдаун: §f"
                    + ClassService.formatDuration(plugin.service().remainingCooldownMs(player.getUniqueId())));
            case NOT_SELECTABLE -> player.sendMessage("§cЭту расу нельзя выбрать самому");
            case SAME -> player.sendMessage("§7У тебя уже эта раса");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(Arrays.asList("list", "info", "set", "select", "clear", "reload", "gui"));
            base.addAll(plugin.service().classIds());
            return filter(base, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("info"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("select")) {
            return filter(plugin.service().tabCompleteClasses(args[1]), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return filter(plugin.service().tabCompleteClasses(args[2]), args[2]);
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
