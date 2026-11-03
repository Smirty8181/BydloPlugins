package ru.bydloland.tattoos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

public final class Msg {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Msg() {
    }

    public static Component text(String raw) {
        return LEGACY.deserialize(raw == null ? "" : raw);
    }

    public static void send(BydloTattoosPlugin plugin, CommandSender to, String raw) {
        String prefix = plugin.getConfig().getString("prefix", "&d[&5Тату&d]&r ");
        to.sendMessage(text(prefix + raw));
    }

    public static String legacy(String raw) {
        return raw == null ? "" : raw.replace('&', '§');
    }
}
