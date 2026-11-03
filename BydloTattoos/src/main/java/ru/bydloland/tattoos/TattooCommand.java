package ru.bydloland.tattoos;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class TattooCommand implements CommandExecutor, TabCompleter {
    private final BydloTattoosPlugin plugin;

    public TattooCommand(BydloTattoosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) showOwn(p);
            else Msg.send(plugin, sender, "&c/тату мастер <ник>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("мастер") || sub.equals("artist")) {
            if (!sender.hasPermission("bydlo.tattoo.admin")) {
                Msg.send(plugin, sender, "&cНет прав.");
                return true;
            }
            if (args.length < 2) {
                Msg.send(plugin, sender, "&c/тату мастер <ник>");
                return true;
            }
            Player target = Players.byName(args[1]);
            if (target != null) {
                plugin.service().setArtist(target, true);
                Msg.send(plugin, sender, "&a" + target.getName() + " &7теперь мастер. Дал машинку и префикс.");
                Msg.send(plugin, target, "&dТебя сделали тату-мастером. ПКМ машинкой по игроку или в воздух — себе.");
            } else {
                plugin.service().setArtistName(args[1], true);
                Msg.send(plugin, sender, "&a" + args[1] + " &7назначен мастером по нику (оффлайн).");
            }
            return true;
        }
        if (sub.equals("забрать") || sub.equals("unartist")) {
            if (!sender.hasPermission("bydlo.tattoo.admin")) {
                Msg.send(plugin, sender, "&cНет прав.");
                return true;
            }
            if (args.length < 2) return true;
            Player target = Players.byName(args[1]);
            if (target != null) {
                plugin.service().setArtist(target, false);
                Msg.send(plugin, sender, "&7Мастера сняли с &f" + target.getName());
            } else {
                plugin.service().setArtistName(args[1], false);
                Msg.send(plugin, sender, "&7Мастера сняли с ника &f" + args[1]);
            }
            return true;
        }
        if (sub.equals("машинка") || sub.equals("machine")) {
            if (!(sender instanceof Player p)) return true;
            if (!plugin.service().isArtist(p) && !p.hasPermission("bydlo.tattoo.admin")) {
                Msg.send(plugin, p, "&cТолько мастер.");
                return true;
            }
            p.getInventory().addItem(MachineItem.create(plugin));
            Msg.send(plugin, p, "&aДержи машинку.");
            return true;
        }
        if (sub.equals("себе") || sub.equals("self")) {
            if (!(sender instanceof Player artist)) return true;
            if (!plugin.service().isArtist(artist)) {
                Msg.send(plugin, artist, "&cБить может только мастер.");
                return true;
            }
            TattooGui.openDesigns(plugin, artist, artist);
            return true;
        }
        if (sub.equals("свести") || sub.equals("remove")) {
            if (!(sender instanceof Player artist)) return true;
            if (!plugin.service().isArtist(artist)) {
                Msg.send(plugin, artist, "&cСводит только мастер.");
                return true;
            }
            if (args.length < 2) {
                Msg.send(plugin, artist, "&c/тату свести <ник> [слот|все]");
                return true;
            }
            Player client = Players.byName(args[1]);
            if (client == null) {
                Msg.send(plugin, artist, "&cИгрок с таким ником не в сети.");
                return true;
            }
            boolean all = args.length >= 3 && args[2].equalsIgnoreCase("все");
            TattooSlot slot = args.length >= 3 && !all ? TattooSlot.fromConfig(args[2]) : null;
            plugin.service().remove(artist, client, slot, all, err -> {
                if (err != null) Msg.send(plugin, artist, "&c" + err);
                else {
                    Msg.send(plugin, artist, "&aСвёл.");
                    if (!artist.getName().equalsIgnoreCase(client.getName())) {
                        Msg.send(plugin, client, "&7Тату свели.");
                    }
                }
            });
            return true;
        }
        if (sub.equals("бить") || sub.equals("ink")) {
            if (!(sender instanceof Player artist)) return true;
            if (!plugin.service().isArtist(artist)) {
                Msg.send(plugin, artist, "&cБить может только мастер.");
                return true;
            }
            if (args.length < 2) {
                Msg.send(plugin, artist, "&c/тату бить <ник>  &7или &f/тату себе");
                return true;
            }
            Player client = Players.byName(args[1]);
            if (client == null) {
                Msg.send(plugin, artist, "&cИгрок с таким ником не в сети.");
                return true;
            }
            TattooGui.openDesigns(plugin, artist, client);
            return true;
        }
        help(sender);
        return true;
    }

    private void showOwn(Player player) {
        PlayerTattoos data = plugin.storage().get(player.getUniqueId());
        if (data.tattoos.isEmpty()) {
            Msg.send(plugin, player, "&7На тебе нет тату. Ищи мастера.");
            if (plugin.service().isArtist(player)) {
                Msg.send(plugin, player, "&8Мастер: машинкой ПКМ по игроку, в воздух — себе, или &f/тату себе");
            }
            return;
        }
        Msg.send(plugin, player, "&fТвои наколки:");
        for (TattooRecord rec : data.tattoos) {
            String name;
            if (rec.custom()) name = "свой рисунок";
            else {
                TattooDef def = plugin.catalog().get(rec.id());
                name = def == null ? rec.id() : Msg.legacy(def.name());
            }
            String ink = rec.colors() == null || rec.colors().isEmpty() ? "" : " &8(" + InkPalette.names(rec.colors()) + ")";
            player.sendMessage(Msg.text(" &8• &f" + rec.slot().title + " &8— " + name + ink));
        }
    }

    private void help(CommandSender sender) {
        Msg.send(plugin, sender, "&f/тату &7— свои наколки");
        if (sender instanceof Player p && plugin.service().isArtist(p)) {
            Msg.send(plugin, sender, "&f/тату себе &7набить себе");
            Msg.send(plugin, sender, "&f/тату бить <ник> &7открыть стол");
            Msg.send(plugin, sender, "&f/тату свести <ник> [слот|все]");
            Msg.send(plugin, sender, "&f/тату машинка");
        }
        if (sender.hasPermission("bydlo.tattoo.admin")) {
            Msg.send(plugin, sender, "&f/тату мастер <ник> &7назначить");
            Msg.send(plugin, sender, "&f/тату забрать <ник>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(List.of("бить", "себе", "свести", "машинка"));
            if (sender.hasPermission("bydlo.tattoo.admin")) {
                out.add("мастер");
                out.add("забрать");
            }
            return out.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("свести")) {
            return Stream.concat(Stream.of("все"), Stream.of(TattooSlot.values()).map(s -> s.name().toLowerCase()))
                    .filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
