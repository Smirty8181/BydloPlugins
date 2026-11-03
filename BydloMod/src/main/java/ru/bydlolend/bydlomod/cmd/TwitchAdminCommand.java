package ru.bydlolend.bydlomod.cmd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import ru.bydlolend.bydlomod.BydloMod;
import ru.bydlolend.bydlomod.util.Msg;

public final class TwitchAdminCommand implements CommandExecutor {
    private final BydloMod plugin;

    public TwitchAdminCommand(BydloMod plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            Msg.header(sender, "Twitch Admin");
            Msg.cmd(sender, "twitchadmin setid <id>", "Client ID");
            Msg.cmd(sender, "twitchadmin setsecret <secret>", "Client Secret");
            Msg.cmd(sender, "twitchadmin status", "проверка");
            Msg.tip(sender, "Один раз для всех стримеров сервера");
            Msg.footer(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "setid", "clientid", "id" -> {
                if (args.length < 2) {
                    Msg.err(sender, "Использование: &e/twitchadmin setid <client-id>");
                    return true;
                }
                plugin.getConfig().set("twitch.client-id", args[1].trim());
                plugin.saveConfig();
                plugin.twitch().reloadCredentials();
                Msg.ok(sender, "Client-ID сохранён");
                Msg.tip(sender, "Стримеры: &f/twitch link");
            }
            case "setsecret", "secret" -> {
                if (args.length < 2) {
                    Msg.err(sender, "Использование: &e/twitchadmin setsecret <secret>");
                    return true;
                }
                plugin.getConfig().set("twitch.client-secret", args[1].trim());
                plugin.saveConfig();
                plugin.twitch().reloadCredentials();
                Msg.ok(sender, "Client-Secret сохранён");
            }
            case "status" -> {
                String id = plugin.getConfig().getString("twitch.client-id", "");
                String sec = plugin.getConfig().getString("twitch.client-secret", "");
                boolean ok = id != null && !id.isBlank() && sec != null && !sec.isBlank();
                Msg.header(sender, "Twitch API");
                if (ok) {
                    Msg.ok(sender, "Настроен &7(id: &f" + id.substring(0, Math.min(6, id.length())) + "...&7)");
                } else {
                    Msg.err(sender, "Не настроен — setid + setsecret");
                }
                Msg.footer(sender);
            }
            default -> Msg.err(sender, "setid | setsecret | status");
        }
        return true;
    }
}
