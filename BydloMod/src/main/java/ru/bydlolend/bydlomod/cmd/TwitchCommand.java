package ru.bydlolend.bydlomod.cmd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.bydlolend.bydlomod.BydloMod;
import ru.bydlolend.bydlomod.twitch.TwitchService;
import ru.bydlolend.bydlomod.util.Msg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class TwitchCommand implements CommandExecutor, TabCompleter {
    private final TwitchService twitch;

    public TwitchCommand(TwitchService twitch) { this.twitch = twitch; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Msg.err(sender, "Только игрок");
            return true;
        }
        if (args.length == 0) {
            help(p);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "link", "привязать" -> {
                if (args.length < 2) {
                    Msg.err(p, "Использование: &e/twitch link <ник>");
                    return true;
                }
                twitch.link(p, args[1]);
                Msg.header(p, "Twitch привязан");
                Msg.tip(p, "Канал: &d" + args[1].toLowerCase(Locale.ROOT));
                Msg.tip(p, "Дальше: &f/twitch desc &7текст");
                Msg.tip(p, "Цвет: &f/twitch color розовый");
                Msg.tip(p, "Проверка: &f/twitch test");
                Msg.footer(p);
            }
            case "desc", "description", "описание" -> {
                if (args.length < 2) {
                    Msg.err(p, "Использование: &e/twitch desc <текст>");
                    return true;
                }
                if (!linked(p)) {
                    Msg.err(p, "Сначала &e/twitch link <ник>");
                    return true;
                }
                String desc = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                twitch.setDescription(p, desc);
                var link = twitch.get(p.getUniqueId()).get();
                Msg.header(p, "Описание сохранено");
                p.sendMessage(BydloMod.color("  " + link.color + desc));
                Msg.tip(p, "Тест анонса: &f/twitch test");
                Msg.footer(p);
            }
            case "color", "цвет", "colour" -> {
                if (args.length < 2) {
                    Msg.header(p, "Цвета описания");
                    for (String name : List.of("розовый", "фиолетовый", "белый", "серый", "жёлтый", "золотой", "красный", "зелёный", "бирюза", "синий")) {
                        String code = Msg.COLORS.get(name);
                        p.sendMessage(BydloMod.color("  " + code + "■ &f" + name + " &8(/twitch color " + name + ")"));
                    }
                    Msg.footer(p);
                    return true;
                }
                if (!linked(p)) {
                    Msg.err(p, "Сначала &e/twitch link <ник>");
                    return true;
                }
                String code = Msg.resolveColor(args[1]);
                if (code == null) {
                    Msg.err(p, "Неизвестный цвет. Смотри &e/twitch color");
                    return true;
                }
                twitch.setColor(p, code);
                var link = twitch.get(p.getUniqueId()).get();
                String sample = link.description == null || link.description.isBlank()
                        ? "Пример описания стрима" : link.description;
                Msg.header(p, "Цвет выбран");
                Msg.tip(p, "Цвет: " + code + Msg.colorName(code));
                p.sendMessage(BydloMod.color("  Превью: " + code + sample));
                Msg.tip(p, "Проверка: &f/twitch test");
                Msg.footer(p);
            }
            case "test", "тест", "preview" -> {
                if (!linked(p)) {
                    Msg.err(p, "Сначала &e/twitch link <ник>");
                    return true;
                }
                try {
                    twitch.announceTest(p);
                    Msg.ok(p, "Тестовый анонс отправлен в чат");
                } catch (IllegalStateException e) {
                    Msg.err(p, "Сначала &e/twitch link <ник>");
                }
            }
            case "unlink", "отвязать" -> {
                boolean ok = twitch.unlink(p.getUniqueId());
                if (ok) Msg.ok(p, "Twitch отвязан");
                else Msg.err(p, "Не был привязан");
            }
            case "info", "статус" -> {
                var opt = twitch.get(p.getUniqueId());
                if (opt.isEmpty() || opt.get().twitchLogin == null || opt.get().twitchLogin.isBlank()) {
                    Msg.err(p, "Не привязан. &e/twitch link <ник>");
                } else {
                    var l = opt.get();
                    Msg.header(p, "Твой Twitch");
                    Msg.tip(p, "Канал: &d" + l.twitchLogin);
                    Msg.tip(p, "Цвет: " + l.color + Msg.colorName(l.color));
                    p.sendMessage(BydloMod.color("  Описание: " + l.color + (l.description == null ? "" : l.description)));
                    Msg.tip(p, "Live сейчас: &f" + (l.wasLive ? "&aда" : "&7нет"));
                    Msg.tip(p, "&f/twitch test &7— пробный анонс");
                    Msg.footer(p);
                }
            }
            default -> help(p);
        }
        return true;
    }

    private boolean linked(Player p) {
        var opt = twitch.get(p.getUniqueId());
        return opt.isPresent() && opt.get().twitchLogin != null && !opt.get().twitchLogin.isBlank();
    }

    private void help(Player p) {
        Msg.header(p, "Twitch БыдлоЛенд");
        Msg.cmd(p, "twitch link <ник>", "привязать канал");
        Msg.cmd(p, "twitch desc <текст>", "описание анонса");
        Msg.cmd(p, "twitch color <цвет>", "цвет описания");
        Msg.cmd(p, "twitch test", "тестовый анонс в чат");
        Msg.cmd(p, "twitch info", "статус");
        Msg.cmd(p, "twitch unlink", "отвязать");
        Msg.tip(p, "Когда стрим стартует — анонс даже оффлайн");
        Msg.footer(p);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("link", "desc", "color", "test", "info", "unlink"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("color")) {
            return filter(args[1], List.of("розовый", "фиолетовый", "белый", "серый", "жёлтый", "золотой", "красный", "зелёный", "бирюза", "синий"));
        }
        return List.of();
    }

    private static List<String> filter(String typed, List<String> opts) {
        String t = typed.toLowerCase(Locale.ROOT);
        return opts.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(t)).collect(Collectors.toCollection(ArrayList::new));
    }
}
