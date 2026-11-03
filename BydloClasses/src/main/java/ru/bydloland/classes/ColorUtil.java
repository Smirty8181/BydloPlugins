package ru.bydloland.classes;

import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {
    private static final Pattern HEX = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final Pattern TAG = Pattern.compile("</?[a-zA-Z0-9:_-]+>");

    private ColorUtil() {}

    public static String legacy(String input) {
        if (input == null || input.isEmpty()) return "";
        String s = input;
        Matcher m = HEX.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex = m.group(1);
            StringBuilder repl = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                repl.append('§').append(c);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(repl.toString()));
        }
        m.appendTail(sb);
        s = sb.toString();
        s = ChatColor.translateAlternateColorCodes('&', s);
        s = TAG.matcher(s).replaceAll("");
        return s;
    }

    public static String strip(String input) {
        return ChatColor.stripColor(legacy(input));
    }
}
