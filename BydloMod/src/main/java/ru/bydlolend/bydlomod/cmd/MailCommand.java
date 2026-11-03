package ru.bydlolend.bydlomod.cmd;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.bydlolend.bydlomod.gui.MailboxGui;
import ru.bydlolend.bydlomod.mail.LetterService;
import ru.bydlolend.bydlomod.util.Msg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MailCommand implements CommandExecutor, TabCompleter {
    private final LetterService letters;
    private final MailboxGui gui;

    public MailCommand(LetterService letters, MailboxGui gui) {
        this.letters = letters;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Только для игроков");
            return true;
        }
        if (args.length == 0) {
            gui.open(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("ящик") || args[0].equalsIgnoreCase("inbox")) {
            gui.open(player);
            return true;
        }
        if (args.length < 2) {
            Msg.tip(sender, "/почта <ник> <текст>");
            Msg.tip(sender, "/почта — открыть ящик");
            return true;
        }
        if (letters.onCooldown(player.getUniqueId())) {
            Msg.err(sender, "Подожди ещё " + letters.cooldownLeft(player.getUniqueId()) + "с");
            return true;
        }

        OfflinePlayer target = resolve(args[0]);
        if (target == null) {
            Msg.err(sender, "Игрок не найден (должен хотя бы раз заходить)");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            Msg.err(sender, "Нельзя писать себе");
            return true;
        }

        String body = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        if (body.length() < 2) {
            Msg.err(sender, "Слишком коротко");
            return true;
        }
        if (body.length() > letters.maxLength()) {
            Msg.err(sender, "Максимум " + letters.maxLength() + " символов");
            return true;
        }

        String toName = target.getName() != null ? target.getName() : args[0];
        var letter = letters.send(player, target.getUniqueId(), toName, body);
        if (target.isOnline()) {
            Msg.ok(player, "Письмо ушло §d" + toName + " §7#" + letter.id);
        } else {
            Msg.ok(player, "Письмо для §d" + toName + " §fждёт входа §7#" + letter.id);
        }
        return true;
    }

    private static OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        String needle = name.toLowerCase(Locale.ROOT);
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(needle)
                    && (op.hasPlayedBefore() || op.isOnline())) {
                return op;
            }
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer byName = Bukkit.getOfflinePlayer(name);
        if (byName.hasPlayedBefore() || byName.isOnline()) return byName;
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (pl.getName().toLowerCase(Locale.ROOT).startsWith(p)) out.add(pl.getName());
            }
            if ("ящик".startsWith(p)) out.add("ящик");
        }
        return out;
    }
}
