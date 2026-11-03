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
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class WarnService {
    public static final class Warn {
        public final int id;
        public final UUID targetId;
        public final String targetName;
        public final String staff;
        public final String reason;
        public final long createdAt;

        public Warn(int id, UUID targetId, String targetName, String staff, String reason, long createdAt) {
            this.id = id;
            this.targetId = targetId;
            this.targetName = targetName;
            this.staff = staff;
            this.reason = reason;
            this.createdAt = createdAt;
        }
    }

    private final BydloMod plugin;
    private final ModerationService moderation;
    private BlackMarkService blackMarks;
    private final File file;
    private final Map<UUID, List<Warn>> warns = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private int nextId = 1;

    public WarnService(BydloMod plugin, ModerationService moderation) {
        this.plugin = plugin;
        this.moderation = moderation;
        this.file = new File(plugin.getDataFolder(), "warns.yml");
        load();
    }

    public void setBlackMarks(BlackMarkService blackMarks) {
        this.blackMarks = blackMarks;
    }

    public List<Warn> getWarns(UUID id) {
        List<Warn> list = warns.get(id);
        if (list == null) return List.of();
        List<Warn> copy = new ArrayList<>(list);
        copy.sort(Comparator.comparingLong((Warn w) -> w.createdAt).reversed());
        return copy;
    }

    public int count(UUID id) {
        List<Warn> list = warns.get(id);
        return list == null ? 0 : list.size();
    }

    public String addWarn(OfflinePlayer target, String staff, String reason) {
        UUID id = target.getUniqueId();
        String name = target.getName() != null ? target.getName() : id.toString().substring(0, 8);
        String r = (reason == null || reason.isBlank()) ? "Нарушение правил" : reason.trim();
        int wid = nextId++;
        Warn w = new Warn(wid, id, name, staff, r, System.currentTimeMillis());
        warns.computeIfAbsent(id, k -> Collections.synchronizedList(new ArrayList<>())).add(w);
        nameIndex.put(name.toLowerCase(Locale.ROOT), id);
        save();

        int n = count(id);
        int autoAt = plugin.getConfig().getInt("warns.auto-mute-count", 3);
        String autoDur = plugin.getConfig().getString("warns.auto-mute-duration", "30м");

        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage(plugin.prefix() + BydloMod.color(
                    "&cТебе выдан варн #" + wid + " &7(" + n + "/" + autoAt + ")\n&7Причина: &f" + r));
        }

        if (blackMarks != null) {
            blackMarks.apply(id, name, r);
        }

        String result = BydloMod.color("&eВарн #" + wid + " &f" + name + " &7(" + n + "/" + autoAt + ")"
                + "\n&7Причина: &f" + r + "\n&8+ чёрная метка");

        if (n >= autoAt && target.isOnline() && target.getPlayer() != null) {
            Player p = target.getPlayer();
            String muteMsg = moderation.punishChat(p, "Авто", autoDur, "Автомут за " + n + " варнов", "");
            result += "\n&cАвтомут: &f" + autoDur;
            String broadcast = BydloMod.color("&d[&5БыдлоЛенд&d]&r &c" + name + " &7получил автомут за &f" + n + " &7варнов");
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(broadcast);
            }
        }
        return result;
    }

    public boolean removeWarn(UUID targetId, int warnId) {
        List<Warn> list = warns.get(targetId);
        if (list == null) return false;
        boolean ok = list.removeIf(w -> w.id == warnId);
        if (list.isEmpty()) warns.remove(targetId);
        if (ok) save();
        return ok;
    }

    public int clearWarns(UUID targetId) {
        List<Warn> list = warns.remove(targetId);
        if (list == null) return 0;
        save();
        return list.size();
    }

    public UUID findId(String name) {
        if (name == null) return null;
        UUID id = nameIndex.get(name.toLowerCase(Locale.ROOT));
        if (id != null) return id;
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        if (op.hasPlayedBefore() || op.isOnline()) return op.getUniqueId();
        for (var e : warns.entrySet()) {
            for (Warn w : e.getValue()) {
                if (w.targetName.equalsIgnoreCase(name)) return e.getKey();
            }
        }
        return null;
    }

    public static String formatTime(long ms) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(ms));
    }

    public void save() {
        FileConfiguration conf = new YamlConfiguration();
        conf.set("next-id", nextId);
        for (var e : warns.entrySet()) {
            String base = "warns." + e.getKey();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Warn w : e.getValue()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", w.id);
                row.put("name", w.targetName);
                row.put("staff", w.staff);
                row.put("reason", w.reason);
                row.put("createdAt", w.createdAt);
                rows.add(row);
            }
            conf.set(base, rows);
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            conf.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Cannot save warns: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration conf = YamlConfiguration.loadConfiguration(file);
        nextId = conf.getInt("next-id", 1);
        ConfigurationSection sec = conf.getConfigurationSection("warns");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                List<?> rows = sec.getList(key);
                if (rows == null) continue;
                List<Warn> list = Collections.synchronizedList(new ArrayList<>());
                for (Object o : rows) {
                    if (!(o instanceof Map<?, ?> map)) continue;
                    int wid = ((Number) map.get("id")).intValue();
                    String name = String.valueOf(map.get("name"));
                    String staff = String.valueOf(map.get("staff"));
                    String reason = String.valueOf(map.get("reason"));
                    long created = ((Number) map.get("createdAt")).longValue();
                    list.add(new Warn(wid, id, name, staff, reason, created));
                    nameIndex.put(name.toLowerCase(Locale.ROOT), id);
                    nextId = Math.max(nextId, wid + 1);
                }
                if (!list.isEmpty()) warns.put(id, list);
            } catch (Exception ignored) {
            }
        }
    }

    public void notifyStaff(String message) {
        String msg = plugin.prefix() + BydloMod.color(message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("bydlo.warn") || p.hasPermission("bydlo.mod") || p.hasPermission("bydlo.admin")) {
                p.sendMessage(msg);
            }
        }
    }
}
