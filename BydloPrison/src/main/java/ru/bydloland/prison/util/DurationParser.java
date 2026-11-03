package ru.bydloland.prison.util;

import java.util.Locale;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses durations: 5с/5s, 5м/5m, 2ч/2h, 3д/3d, навсегда / forever / permanent.
 * Returns expire epoch millis, or -1 for permanent. Empty = invalid.
 */
public final class DurationParser {

    private static final Pattern TOKEN = Pattern.compile(
            "(\\d+)\\s*(с|сек|sec|s|м|мин|min|m|ч|час|часа|часов|h|д|день|дня|дней|d)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private DurationParser() {}

    public static OptionalLong parseExpireAt(String raw, long nowMs) {
        if (raw == null || raw.isBlank()) {
            return OptionalLong.empty();
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (isPermanent(s)) {
            return OptionalLong.of(-1L);
        }

        Matcher m = TOKEN.matcher(s.replace(',', '.'));
        long totalMs = 0;
        boolean any = false;
        while (m.find()) {
            any = true;
            long n = Long.parseLong(m.group(1));
            String u = m.group(2).toLowerCase(Locale.ROOT);
            totalMs += switch (u) {
                case "с", "сек", "sec", "s" -> TimeUnit.SECONDS.toMillis(n);
                case "м", "мин", "min", "m" -> TimeUnit.MINUTES.toMillis(n);
                case "ч", "час", "часа", "часов", "h" -> TimeUnit.HOURS.toMillis(n);
                case "д", "день", "дня", "дней", "d" -> TimeUnit.DAYS.toMillis(n);
                default -> 0;
            };
        }
        if (!any || totalMs <= 0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(nowMs + totalMs);
    }

    public static boolean isPermanent(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        return t.equals("навсегда")
                || t.equals("forever")
                || t.equals("permanent")
                || t.equals("perm")
                || t.equals("-1")
                || t.equals("permaban");
    }

    public static String formatRemaining(long remainingMs, String permanentLabel) {
        if (remainingMs < 0) {
            return permanentLabel;
        }
        long sec = remainingMs / 1000L;
        long days = sec / 86400L;
        sec %= 86400L;
        long hours = sec / 3600L;
        sec %= 3600L;
        long mins = sec / 60L;
        sec %= 60L;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("д ");
        if (hours > 0 || days > 0) sb.append(hours).append("ч ");
        if (mins > 0 || hours > 0 || days > 0) sb.append(mins).append("м");
        if (days == 0 && hours == 0 && mins == 0) {
            sb.append(sec).append("с");
        }
        return sb.toString().trim();
    }
}
