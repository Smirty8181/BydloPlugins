package ru.bydloland.paint;

import org.bukkit.ChatColor;

public final class ColorUtil {
    private ColorUtil() {}

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
