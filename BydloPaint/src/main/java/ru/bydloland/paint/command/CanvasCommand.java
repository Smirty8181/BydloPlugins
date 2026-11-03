package ru.bydloland.paint.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.bydloland.paint.BydloPaintPlugin;
import ru.bydloland.paint.ColorUtil;
import ru.bydloland.paint.service.CanvasService;

import java.util.ArrayList;
import java.util.List;

public final class CanvasCommand implements CommandExecutor, TabCompleter {

    private final BydloPaintPlugin plugin;
    private final CanvasService canvases;

    public CanvasCommand(BydloPaintPlugin plugin, CanvasService canvases) {
        this.plugin = plugin;
        this.canvases = canvases;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ColorUtil.color(plugin.getConfig().getString("prefix", "")
                    + " &fПолотно&7: крафт шерсть+палки → ПКМ размер → ПКМ стена → краситель."));
            sender.sendMessage(ColorUtil.color("&7Команды: &f/полотно дать &7| &f/полотно размер <ш> <в>"));
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("give") || sub.equals("дать")) {
            if (!sender.hasPermission("bydlopaint.admin")) {
                sender.sendMessage(ColorUtil.color("&cНет прав."));
                return true;
            }
            Player target = sender instanceof Player p ? p : null;
            if (args.length >= 2) {
                target = plugin.getServer().getPlayerExact(args[1]);
            }
            if (target == null) {
                sender.sendMessage(ColorUtil.color("&cИгрок не найден."));
                return true;
            }
            target.getInventory().addItem(canvases.createBlankCanvas());
            sender.sendMessage(ColorUtil.color("&aПолотно выдано &f" + target.getName()));
            return true;
        }

        if (sub.equals("size") || sub.equals("размер")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only");
                return true;
            }
            if (args.length < 3) {
                player.sendMessage(ColorUtil.color("&c/полотно размер <ширина> <высота>"));
                return true;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (!canvases.isCanvas(hand)) {
                player.sendMessage(plugin.msg("only-canvas"));
                return true;
            }
            int w;
            int h;
            try {
                w = Integer.parseInt(args[1]);
                h = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                player.sendMessage(ColorUtil.color("&cЧисла, например 3 2"));
                return true;
            }
            int max = canvases.maxSize();
            if (w < 1 || h < 1 || w > max || h > max) {
                player.sendMessage(ColorUtil.color("&cРазмер 1.." + max));
                return true;
            }
            canvases.setSize(hand, w, h);
            player.sendMessage(plugin.msg("sized")
                    .replace("%w%", String.valueOf(w))
                    .replace("%h%", String.valueOf(h)));
            return true;
        }

        sender.sendMessage(ColorUtil.color("&c/полотно | дать | размер"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("дать", "размер", "give", "size")) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        }
        return out;
    }
}
