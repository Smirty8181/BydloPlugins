package ru.bydloland.scale;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BydloScale extends JavaPlugin implements TabExecutor {

    private static final double MIN = 0.1;
    private static final double MAX = 16.0;

    @Override
    public void onEnable() {
        var cmd = getCommand("scale");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        getLogger().info("/scale ready (vanilla attribute, no TimuCore)");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("noobscale.use") && !sender.hasPermission("bydlo.admin") && !sender.isOp()) {
            sender.sendMessage("§d[Быдло] §cНет прав на /scale");
            return true;
        }
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage("§d[Быдло] §f/scale <0.1–16> §7[игрок]");
            return true;
        }
        double scale;
        try {
            scale = Double.parseDouble(args[0].replace(',', '.'));
        } catch (NumberFormatException e) {
            sender.sendMessage("§d[Быдло] §cНужно число, например §f0.7 §cили §f2");
            return true;
        }
        scale = Math.max(MIN, Math.min(MAX, scale));

        Player target;
        if (args.length == 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§d[Быдло] §cИгрок оффлайн: §f" + args[1]);
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("§d[Быдло] §cИз консоли: §f/scale <число> <игрок>");
            return true;
        }

        if (!setScale(target, scale)) {
            sender.sendMessage("§d[Быдло] §cНе удалось поставить рост (атрибут SCALE недоступен)");
            return true;
        }

        String shown = stripTrailing(scale);
        if (sender.equals(target)) {
            sender.sendMessage("§d[Быдло] §aРост §f" + shown + "x");
        } else {
            sender.sendMessage("§d[Быдло] §aРост §f" + target.getName() + " §7→ §f" + shown + "x");
            target.sendMessage("§d[Быдло] §7Рост изменён на §f" + shown + "x");
        }
        return true;
    }

    static boolean setScale(Player player, double scale) {
        AttributeInstance inst = player.getAttribute(Attribute.SCALE);
        if (inst == null) {
            return false;
        }
        inst.setBaseValue(scale);
        return true;
    }

    private static String stripTrailing(double v) {
        if (v == (long) v) {
            return Long.toString((long) v);
        }
        return String.format(Locale.US, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("0.5", "0.7", "1", "1.5", "2", "3")) {
                if (s.startsWith(args[0])) {
                    out.add(s);
                }
            }
        } else if (args.length == 2) {
            String t = args[1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(t)) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
