package ru.bydloland.fishing;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class FishCommand implements CommandExecutor, TabCompleter {
    private final BydloFishingPlugin plugin;

    public FishCommand(BydloFishingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            Msg.send(sender, "&f/рыбалка дать <дуб|сталь|ад|бездна> [ник]");
            Msg.send(sender, "&7Вода — любая удочка. Лава в Аду — адская или бездна.");
            Msg.send(sender, "&7Когда клюёт: &eзажми sneak&7 — зелёная рамка поднимается к рыбе.");
            Msg.send(sender, "&7Держи рыбу в рамке, пока полоска не заполнится. Катушку не дёргай.");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload") || args[0].equalsIgnoreCase("релоад")) {
            if (!sender.hasPermission("bydlo.fishing.admin")) {
                Msg.send(sender, "&cНет прав.");
                return true;
            }
            plugin.reloadConfig();
            Msg.send(sender, "&aКонфиг перезагружен.");
            return true;
        }
        if (args[0].equalsIgnoreCase("дать") || args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("bydlo.fishing.admin")) {
                Msg.send(sender, "&cНет прав.");
                return true;
            }
            if (args.length < 2) {
                Msg.send(sender, "&c/рыбалка дать <дуб|сталь|ад|бездна> [ник]");
                return true;
            }
            RodType type = RodType.byName(args[1]);
            if (type == null || type == RodType.VANILLA) {
                Msg.send(sender, "&cУдочки: дуб, сталь, ад, бездна");
                return true;
            }
            Player target;
            if (args.length >= 3) {
                target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.getName().equalsIgnoreCase(args[2])) {
                            target = online;
                            break;
                        }
                    }
                }
            } else if (sender instanceof Player pl) {
                target = pl;
            } else {
                Msg.send(sender, "&cУкажи ника.");
                return true;
            }
            if (target == null) {
                Msg.send(sender, "&cИгрок не в сети.");
                return true;
            }
            ItemStack item = CeItems.of(type.ceId());
            if (item == null) item = new ItemStack(Material.FISHING_ROD);
            target.getInventory().addItem(item);
            Msg.send(sender, "&aВыдал &f" + type.title + " &7→ &f" + target.getName());
            if (target != sender) Msg.send(target, "&dТебе удочка: &f" + type.title);
            return true;
        }
        Msg.send(sender, "&c/рыбалка дать · /рыбалка");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Stream.of("дать", "give", "reload").filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("дать") || args[0].equalsIgnoreCase("give"))) {
            return Stream.of("дуб", "сталь", "ад", "бездна").filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
