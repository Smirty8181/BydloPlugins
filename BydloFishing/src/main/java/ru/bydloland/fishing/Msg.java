package ru.bydloland.fishing;

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

    public static void send(CommandSender to, String raw) {
        String prefix = BydloFishingPlugin.get().getConfig().getString("prefix", "&d[&5Рыбалка&d]&r ");
        to.sendMessage(text(prefix + raw));
    }
}
