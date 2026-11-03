package ru.bydlolend.bydlomod.mod;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.bydlolend.bydlomod.BydloMod;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Только мут войса (Simple Voice Chat). Баны/муты чата — LibertyBans и т.п. */
public final class ModerationService implements Listener {
    public static final class Punishment {
        public final String targetName;
        public final UUID targetId;
        public final String staff;
        public final String reason;
        public final String description;
        public final long expiresAt; // 0 = permanent
        public final long createdAt;

        public Punishment(String targetName, UUID targetId, String staff,
                          String reason, String description, long expiresAt, long createdAt) {
            this.targetName = targetName;
            this.targetId = targetId;
            this.staff = staff;
            this.reason = reason;
            this.description = description;
            this.expiresAt = expiresAt;
            this.createdAt = createdAt;
        }

        public boolean expired() {
            return expiresAt > 0 && System.currentTimeMillis() >= expiresAt;
        }
    }

    // EN + RU units: 30m / 30м / 2h / 2ч / 7d / 7д / 10s / 10с / 1w / 1н
    private static final Pattern DURATION = Pattern.compile(
            "(?i)^(\\d+)(s|м|m|sec|сек|h|ч|час|часа|часов|d|д|ден|день|дня|дней|w|н|нед|неделя|mo|мес|y|г|год|лет)?$");

    private final BydloMod plugin;
    private HistoryService history;
    private final Map<UUID, Punishment> vmutes = new ConcurrentHashMap<>();
    private final Map<UUID, Punishment> chatMutes = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private final File file;

    public ModerationService(BydloMod plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "punishments.yml");
        load();
    }

    public void setHistory(HistoryService history) {
        this.history = history;
    }

    public Optional<Punishment> getVmute(UUID id) {
        Punishment p = vmutes.get(id);
        if (p == null) return Optional.empty();
        if (p.expired()) {
            remove(id);
            return Optional.empty();
        }
        return Optional.of(p);
    }

    public boolean isVmuted(UUID id) {
        return getVmute(id).isPresent();
    }

    public Optional<Punishment> getChatMute(UUID id) {
        Punishment p = chatMutes.get(id);
        if (p == null) return Optional.empty();
        if (p.expired()) {
            chatMutes.remove(id);
            save();
            return Optional.empty();
        }
        return Optional.of(p);
    }

    public boolean isChatMuted(UUID id) {
        return getChatMute(id).isPresent();
    }

    public String punish(Player target, String staff, String durationRaw, String reason, String description) {
        return punishVoice(target, staff, durationRaw, reason, description);
    }

    public String punishVoice(Player target, String staff, String durationRaw, String reason, String description) {
        long expires = parseExpiry(durationRaw);
        if (expires < 0) return "Неверное время. Примеры: &f30м&7, &f2ч&7, &f7д&7, &fнавсегда";

        String r = reason == null ? "" : reason.trim();
        String d = description == null ? "" : description.trim();

        Punishment p = new Punishment(target.getName(), target.getUniqueId(), staff,
                r, d, expires, System.currentTimeMillis());
        vmutes.put(target.getUniqueId(), p);
        nameIndex.put(target.getName().toLowerCase(Locale.ROOT), target.getUniqueId());
        save();
        VoiceMuteHook.setDisabled(target.getUniqueId(), true);
        if (history != null) {
            history.log(target.getUniqueId(), target.getName(), "VOICE_MUTE", "APPLY", staff, r, expires);
        }

        String timeLabel = expires == 0 ? "навсегда" : formatLeft(expires - System.currentTimeMillis());
        StringBuilder toTarget = new StringBuilder("&cТебя замутили в войсе на &f").append(timeLabel);
        if (!r.isEmpty()) toTarget.append("\n&7Причина: &f").append(r);
        target.sendMessage(plugin.prefix() + BydloMod.color(toTarget.toString()));

        StringBuilder toStaff = new StringBuilder("&cМут войса &f").append(target.getName())
                .append(" &7(").append(timeLabel).append(")");
        if (!r.isEmpty()) toStaff.append("\n&7Причина: &f").append(r);
        if (!d.isEmpty() && !d.equals(r)) toStaff.append("\n&7Описание: &f").append(d);
        return BydloMod.color(toStaff.toString());
    }

    public String punishChat(Player target, String staff, String durationRaw, String reason, String description) {
        long expires = parseExpiry(durationRaw);
        if (expires < 0) return "Неверное время. Примеры: &f30м&7, &f2ч&7, &f7д&7, &fнавсегда";

        String r = reason == null ? "" : reason.trim();
        String d = description == null ? "" : description.trim();

        Punishment p = new Punishment(target.getName(), target.getUniqueId(), staff,
                r, d, expires, System.currentTimeMillis());
        chatMutes.put(target.getUniqueId(), p);
        nameIndex.put(target.getName().toLowerCase(Locale.ROOT), target.getUniqueId());
        save();
        if (history != null) {
            history.log(target.getUniqueId(), target.getName(), "CHAT_MUTE", "APPLY", staff, r, expires);
        }

        String timeLabel = expires == 0 ? "навсегда" : formatLeft(expires - System.currentTimeMillis());
        StringBuilder toTarget = new StringBuilder("&cТебя замутили в чате на &f").append(timeLabel);
        if (!r.isEmpty()) toTarget.append("\n&7Причина: &f").append(r);
        target.sendMessage(plugin.prefix() + BydloMod.color(toTarget.toString()));

        StringBuilder toStaff = new StringBuilder("&cМут чата &f").append(target.getName())
                .append(" &7(").append(timeLabel).append(")");
        if (!r.isEmpty()) toStaff.append("\n&7Причина: &f").append(r);
        if (!d.isEmpty() && !d.equals(r)) toStaff.append("\n&7Описание: &f").append(d);
        return BydloMod.color(toStaff.toString());
    }

    public boolean remove(UUID id) {
        Punishment old = vmutes.remove(id);
        boolean a = old != null;
        if (a) {
            VoiceMuteHook.setDisabled(id, false);
            if (history != null) {
                history.log(id, old.targetName, "VOICE_MUTE", "REMOVE", "system", old.reason, 0);
            }
        }
        save();
        return a;
    }

    public boolean removeChatMute(UUID id) {
        Punishment old = chatMutes.remove(id);
        boolean a = old != null;
        if (a && history != null) {
            history.log(id, old.targetName, "CHAT_MUTE", "REMOVE", "system", old.reason, 0);
        }
        save();
        return a;
    }

    public boolean removeByName(String name) {
        UUID id = findIdByName(name);
        if (id == null) return false;
        return remove(id);
    }

    public boolean removeChatByName(String name) {
        UUID id = findIdByName(name);
        if (id == null) {
            for (Punishment p : chatMutes.values()) {
                if (p.targetName.equalsIgnoreCase(name)) {
                    id = p.targetId;
                    break;
                }
            }
        }
        if (id == null) return false;
        return removeChatMute(id);
    }

    private UUID findIdByName(String name) {
        UUID id = nameIndex.get(name.toLowerCase(Locale.ROOT));
        if (id != null) return id;
        for (Punishment p : vmutes.values()) {
            if (p.targetName.equalsIgnoreCase(name)) return p.targetId;
        }
        for (Punishment p : chatMutes.values()) {
            if (p.targetName.equalsIgnoreCase(name)) return p.targetId;
        }
        return null;
    }

    public static long parseExpiry(String raw) {
        if (raw == null) return -1;
        String s = raw.trim();
        if (s.equalsIgnoreCase("perm") || s.equalsIgnoreCase("permanent")
                || s.equalsIgnoreCase("навсегда") || s.equalsIgnoreCase("-1")
                || s.equalsIgnoreCase("перм")) {
            return 0;
        }
        Matcher m = DURATION.matcher(s);
        if (!m.matches()) return -1;
        long n = Long.parseLong(m.group(1));
        String u = m.group(2) == null ? "m" : m.group(2).toLowerCase(Locale.ROOT);
        long mult = switch (u) {
            case "s", "sec", "сек" -> 1000L;
            case "m", "м" -> 60_000L;
            case "h", "ч", "час", "часа", "часов" -> 3_600_000L;
            case "d", "д", "ден", "день", "дня", "дней" -> 86_400_000L;
            case "w", "н", "нед", "неделя" -> 604_800_000L;
            case "mo", "мес" -> 2_592_000_000L;
            case "y", "г", "год", "лет" -> 31_536_000_000L;
            default -> -1L;
        };
        if (mult < 0) return -1;
        return System.currentTimeMillis() + n * mult;
    }

    public static String formatLeft(long ms) {
        if (ms <= 0) return "0с";
        long s = ms / 1000;
        long d = s / 86400; s %= 86400;
        long h = s / 3600; s %= 3600;
        long m = s / 60; s %= 60;
        StringBuilder b = new StringBuilder();
        if (d > 0) b.append(d).append("д ");
        if (h > 0) b.append(h).append("ч ");
        if (m > 0) b.append(m).append("м ");
        if (d == 0 && h == 0) b.append(s).append("с");
        return b.toString().trim();
    }

    /** Опциональные причина / описание. Пустой ввод → пустые строки. */
    public static String[] splitReasonDesc(String[] argsFrom2) {
        if (argsFrom2 == null || argsFrom2.length == 0) return new String[]{"", ""};
        String joined = String.join(" ", argsFrom2).trim();
        if (joined.isEmpty()) return new String[]{"", ""};
        if (joined.contains("|")) {
            String[] parts = joined.split("\\|", 2);
            return new String[]{parts[0].trim(), parts.length > 1 ? parts[1].trim() : ""};
        }
        return new String[]{joined, ""};
    }

    public void startExpiryTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<UUID> deadV = new ArrayList<>();
            List<UUID> deadC = new ArrayList<>();
            for (var e : vmutes.entrySet()) {
                if (e.getValue().expired()) deadV.add(e.getKey());
            }
            for (var e : chatMutes.entrySet()) {
                if (e.getValue().expired()) deadC.add(e.getKey());
            }
            if (!deadV.isEmpty() || !deadC.isEmpty()) {
                for (UUID id : deadV) {
                    vmutes.remove(id);
                    VoiceMuteHook.setDisabled(id, false);
                }
                deadC.forEach(chatMutes::remove);
                save();
            }
        }, 20L * 15, 20L * 15);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        getVmute(e.getPlayer().getUniqueId()).ifPresent(p -> {
            String time = p.expiresAt == 0 ? "навсегда" : formatLeft(p.expiresAt - System.currentTimeMillis());
            e.getPlayer().sendMessage(plugin.prefix() + BydloMod.color("&cУ тебя мут войса ещё &f" + time));
            // Переприменить блок микрофона после входа / reconnect voicechat
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    VoiceMuteHook.setDisabled(e.getPlayer().getUniqueId(), true), 20L);
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    VoiceMuteHook.setDisabled(e.getPlayer().getUniqueId(), true), 100L);
        });
        getChatMute(e.getPlayer().getUniqueId()).ifPresent(p -> {
            String time = p.expiresAt == 0 ? "навсегда" : formatLeft(p.expiresAt - System.currentTimeMillis());
            e.getPlayer().sendMessage(plugin.prefix() + BydloMod.color("&cУ тебя мут чата ещё &f" + time));
        });
    }

    public void save() {
        FileConfiguration conf = new YamlConfiguration();
        writeSection(conf, "vmutes", vmutes);
        writeSection(conf, "chatmutes", chatMutes);
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            conf.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Cannot save punishments: " + ex.getMessage());
        }
    }

    private static void writeSection(FileConfiguration conf, String section, Map<UUID, Punishment> map) {
        for (Punishment p : map.values()) {
            String base = section + "." + p.targetId;
            conf.set(base + ".name", p.targetName);
            conf.set(base + ".staff", p.staff);
            conf.set(base + ".reason", p.reason);
            conf.set(base + ".description", p.description);
            conf.set(base + ".expiresAt", p.expiresAt);
            conf.set(base + ".createdAt", p.createdAt);
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration conf = YamlConfiguration.loadConfiguration(file);
        loadSection(conf, "vmutes", vmutes);
        loadSection(conf, "chatmutes", chatMutes);
    }

    private void loadSection(FileConfiguration conf, String section, Map<UUID, Punishment> into) {
        if (!conf.isConfigurationSection(section)) return;
        for (String key : Objects.requireNonNull(conf.getConfigurationSection(section)).getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String base = section + "." + key;
                Punishment p = new Punishment(
                        conf.getString(base + ".name", "unknown"),
                        id,
                        conf.getString(base + ".staff", "console"),
                        conf.getString(base + ".reason", ""),
                        conf.getString(base + ".description", ""),
                        conf.getLong(base + ".expiresAt"),
                        conf.getLong(base + ".createdAt"));
                if (!p.expired()) {
                    into.put(id, p);
                    nameIndex.put(p.targetName.toLowerCase(Locale.ROOT), id);
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
