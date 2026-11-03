package ru.bydlolend.bydlomod.util;

import org.bukkit.command.CommandSender;
import ru.bydlolend.bydlomod.BydloMod;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Красивые сообщения в стиле БыдлоЛенд */
public final class Msg {
    private Msg() {}

    public static final Map<String, String> COLORS = new LinkedHashMap<>();
    static {
        COLORS.put("розовый", "&d");
        COLORS.put("pink", "&d");
        COLORS.put("фиолетовый", "&5");
        COLORS.put("purple", "&5");
        COLORS.put("белый", "&f");
        COLORS.put("white", "&f");
        COLORS.put("серый", "&7");
        COLORS.put("gray", "&7");
        COLORS.put("жёлтый", "&e");
        COLORS.put("yellow", "&e");
        COLORS.put("золотой", "&6");
        COLORS.put("gold", "&6");
        COLORS.put("красный", "&c");
        COLORS.put("red", "&c");
        COLORS.put("зелёный", "&a");
        COLORS.put("green", "&a");
        COLORS.put("бирюза", "&b");
        COLORS.put("aqua", "&b");
        COLORS.put("синий", "&9");
        COLORS.put("blue", "&9");
    }

    /** @return color code like &d, or null if unknown */
    public static String resolveColor(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (COLORS.containsKey(s)) return COLORS.get(s);
        if (s.matches("&[0-9a-fk-or]")) return s;
        if (s.matches("[0-9a-fk-or]")) return "&" + s;
        return null;
    }

    public static String colorName(String code) {
        if (code == null) return "розовый";
        for (var e : COLORS.entrySet()) {
            if (e.getValue().equalsIgnoreCase(code) && e.getKey().matches("[а-яё]+")) {
                return e.getKey();
            }
        }
        return code;
    }

    public static void line(CommandSender s) {
        s.sendMessage(BydloMod.color("&d&m                                        "));
    }

    public static void header(CommandSender s, String title) {
        s.sendMessage("");
        line(s);
        s.sendMessage(BydloMod.color("&d  ✦ &f&l" + title + " &d✦"));
        line(s);
    }

    public static void footer(CommandSender s) {
        line(s);
        s.sendMessage("");
    }

    public static void ok(CommandSender s, String text) {
        s.sendMessage(BydloMod.get().prefix() + BydloMod.color("&a✔ &f" + text));
    }

    public static void err(CommandSender s, String text) {
        s.sendMessage(BydloMod.get().prefix() + BydloMod.color("&c✖ &f" + text));
    }

    public static void tip(CommandSender s, String text) {
        s.sendMessage(BydloMod.get().prefix() + BydloMod.color("&7› &f" + text));
    }

    public static void cmd(CommandSender s, String command, String desc) {
        s.sendMessage(BydloMod.color("&d  /" + command + " &8— &7" + desc));
    }
}
