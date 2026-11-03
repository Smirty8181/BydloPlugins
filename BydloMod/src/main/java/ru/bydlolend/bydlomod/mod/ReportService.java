package ru.bydlolend.bydlomod.mod;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.bydlolend.bydlomod.BydloMod;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ReportService {
    public enum Status { OPEN, CLOSED }

    public static final class Report {
        public final int id;
        public final UUID reporterId;
        public final String reporterName;
        public final UUID targetId;
        public final String targetName;
        public final String reason;
        public final long createdAt;
        public Status status;
        public String claimedBy;
        public UUID claimedByUuid;
        public String closedBy;
        public String closeNote;

        public Report(int id, UUID reporterId, String reporterName, UUID targetId, String targetName,
                      String reason, long createdAt, Status status, String claimedBy, UUID claimedByUuid,
                      String closedBy, String closeNote) {
            this.id = id;
            this.reporterId = reporterId;
            this.reporterName = reporterName;
            this.targetId = targetId;
            this.targetName = targetName;
            this.reason = reason;
            this.createdAt = createdAt;
            this.status = status;
            this.claimedBy = claimedBy;
            this.claimedByUuid = claimedByUuid;
            this.closedBy = closedBy;
            this.closeNote = closeNote;
        }
    }

    private final BydloMod plugin;
    private final MailService mail;
    private final File file;
    private final Map<Integer, Report> reports = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private int nextId = 1;

    public ReportService(BydloMod plugin, MailService mail) {
        this.plugin = plugin;
        this.mail = mail;
        this.file = new File(plugin.getDataFolder(), "reports.yml");
        load();
    }

    public boolean onCooldown(UUID id) {
        long cd = plugin.getConfig().getLong("reports.cooldown-seconds", 60L) * 1000L;
        Long last = cooldowns.get(id);
        return last != null && System.currentTimeMillis() - last < cd;
    }

    public long cooldownLeftSec(UUID id) {
        long cd = plugin.getConfig().getLong("reports.cooldown-seconds", 60L) * 1000L;
        Long last = cooldowns.get(id);
        if (last == null) return 0;
        long left = cd - (System.currentTimeMillis() - last);
        return Math.max(0, (left + 999) / 1000);
    }

    public Report create(Player reporter, OfflinePlayer target, String reason) {
        int id = nextId++;
        String targetName = target.getName() != null ? target.getName() : "unknown";
        Report r = new Report(id, reporter.getUniqueId(), reporter.getName(),
                target.getUniqueId(), targetName, reason.trim(),
                System.currentTimeMillis(), Status.OPEN, null, null, null, null);
        reports.put(id, r);
        cooldowns.put(reporter.getUniqueId(), System.currentTimeMillis());
        save();
        boolean online = target.isOnline();
        notifyStaff("&eНовый репорт &f#" + id + " &7на &c" + targetName
                + (online ? "" : " &8(оффлайн)")
                + " &7от &f" + reporter.getName() + "\n&7" + reason + " &8— /репорты");
        return r;
    }

    public Report get(int id) {
        return reports.get(id);
    }

    public List<Report> openReports() {
        List<Report> list = new ArrayList<>();
        for (Report r : reports.values()) {
            if (r.status == Status.OPEN) list.add(r);
        }
        list.sort(Comparator.comparingLong((Report r) -> r.createdAt).reversed());
        return list;
    }

    public List<Report> reportsForTarget(UUID targetId) {
        List<Report> list = new ArrayList<>();
        for (Report r : reports.values()) {
            if (r.targetId.equals(targetId)) list.add(r);
        }
        list.sort(Comparator.comparingLong((Report r) -> r.createdAt).reversed());
        return list;
    }

    public boolean claim(int id, Player staff) {
        Report r = reports.get(id);
        if (r == null || r.status != Status.OPEN) return false;
        r.claimedBy = staff.getName();
        r.claimedByUuid = staff.getUniqueId();
        save();
        mail.send(r.reporterId, "&eРепорт &f#" + id + " &7взял &f" + staff.getName()
                + "\n&7Пиши модератору: &f/рс <текст>");
        return true;
    }

    public boolean close(int id, String staff, String noteToReporter) {
        Report r = reports.get(id);
        if (r == null || r.status == Status.CLOSED) return false;
        r.status = Status.CLOSED;
        r.closedBy = staff;
        r.closeNote = noteToReporter;
        save();
        String note = noteToReporter == null || noteToReporter.isBlank()
                ? "&7Репорт закрыт без дополнительных действий."
                : noteToReporter;
        mail.send(r.reporterId, "&eРепорт &f#" + id + " &aзакрыт &7(&f" + staff + "&7)\n" + note);
        return true;
    }

    public Report activeChatFor(UUID player) {
        Report best = null;
        for (Report r : reports.values()) {
            if (r.status != Status.OPEN || r.claimedByUuid == null) continue;
            if (!r.reporterId.equals(player) && !r.claimedByUuid.equals(player)) continue;
            if (best == null || r.createdAt > best.createdAt) best = r;
        }
        return best;
    }

    public boolean relayChat(Player from, String text) {
        Report r = activeChatFor(from.getUniqueId());
        if (r == null) return false;
        UUID otherId = from.getUniqueId().equals(r.reporterId) ? r.claimedByUuid : r.reporterId;
        if (otherId == null) return false;
        String line = "&e[Репорт #" + r.id + "] &f" + from.getName() + "&7: &f" + text;
        from.sendMessage(plugin.prefix() + BydloMod.color(line));
        mail.send(otherId, line);
        return true;
    }

    public void notifyStaff(String message) {
        String msg = plugin.prefix() + BydloMod.color(message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("bydlo.reports") || p.hasPermission("bydlo.mod") || p.hasPermission("bydlo.admin")) {
                p.sendMessage(msg);
            }
        }
    }

    public void save() {
        FileConfiguration conf = new YamlConfiguration();
        conf.set("next-id", nextId);
        for (Report r : reports.values()) {
            String base = "reports." + r.id;
            conf.set(base + ".reporterId", r.reporterId.toString());
            conf.set(base + ".reporterName", r.reporterName);
            conf.set(base + ".targetId", r.targetId.toString());
            conf.set(base + ".targetName", r.targetName);
            conf.set(base + ".reason", r.reason);
            conf.set(base + ".createdAt", r.createdAt);
            conf.set(base + ".status", r.status.name());
            conf.set(base + ".claimedBy", r.claimedBy);
            conf.set(base + ".claimedByUuid", r.claimedByUuid == null ? null : r.claimedByUuid.toString());
            conf.set(base + ".closedBy", r.closedBy);
            conf.set(base + ".closeNote", r.closeNote);
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            conf.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Cannot save reports: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration conf = YamlConfiguration.loadConfiguration(file);
        nextId = conf.getInt("next-id", 1);
        ConfigurationSection sec = conf.getConfigurationSection("reports");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                String base = "reports." + key;
                String cu = conf.getString(base + ".claimedByUuid");
                Report r = new Report(
                        id,
                        UUID.fromString(conf.getString(base + ".reporterId")),
                        conf.getString(base + ".reporterName", "?"),
                        UUID.fromString(conf.getString(base + ".targetId")),
                        conf.getString(base + ".targetName", "?"),
                        conf.getString(base + ".reason", ""),
                        conf.getLong(base + ".createdAt"),
                        Status.valueOf(conf.getString(base + ".status", "OPEN")),
                        conf.getString(base + ".claimedBy"),
                        cu == null || cu.isBlank() ? null : UUID.fromString(cu),
                        conf.getString(base + ".closedBy"),
                        conf.getString(base + ".closeNote")
                );
                reports.put(id, r);
                nextId = Math.max(nextId, id + 1);
            } catch (Exception ignored) {
            }
        }
    }
}
