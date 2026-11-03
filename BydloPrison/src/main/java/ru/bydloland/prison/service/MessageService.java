package ru.bydloland.prison.service;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import ru.bydloland.prison.BydloPrisonPlugin;
import ru.bydloland.prison.util.DurationParser;

public final class MessageService {

    private final BydloPrisonPlugin plugin;

    public MessageService(BydloPrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        // messages live in config.yml
    }

    public String prefix() {
        return color(cfg().getString("messages.prefix", "&d[&5Заключение&d]&r "));
    }

    public String raw(String path) {
        return cfg().getString("messages." + path, path);
    }

    public String get(String path) {
        return color(raw(path));
    }

    public String formatTime(long remainingMs) {
        return DurationParser.formatRemaining(remainingMs, get("permanent").replace(prefix(), "").trim().isEmpty()
                ? color(raw("permanent"))
                : color(raw("permanent")));
    }

    public String withTime(String path, long remainingMs) {
        String time = DurationParser.formatRemaining(remainingMs, color(raw("permanent")));
        return color(raw(path).replace("{time}", time).replace("&nl", "\n"));
    }

    public String apply(String path, String player, String time, String reason) {
        return color(raw(path)
                .replace("{player}", player == null ? "?" : player)
                .replace("{time}", time == null ? "?" : time)
                .replace("{reason}", reason == null ? "—" : reason)
                .replace("&nl", "\n"));
    }

    public String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }
}
