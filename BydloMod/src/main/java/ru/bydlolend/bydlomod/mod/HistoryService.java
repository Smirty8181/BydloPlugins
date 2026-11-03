package ru.bydlolend.bydlomod.mod;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.bydlolend.bydlomod.BydloMod;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Append-only журнал мутов/банов с момента обновления. */
public final class HistoryService {
    public static final class Entry {
        public final long id;
        public final UUID targetId;
        public final String targetName;
        public final String type; // CHAT_MUTE, VOICE_MUTE, BAN, UNMUTE, UNVMUTE, UNBAN
        public final String action; // APPLY / REMOVE
        public final String staff;
        public final String reason;
        public final long createdAt;
        public final long expiresAt; // 0 = permanent / n/a

        public Entry(long id, UUID targetId, String targetName, String type, String action,
                     String staff, String reason, long createdAt, long expiresAt) {
            this.id = id;
            this.targetId = targetId;
            this.targetName = targetName;
            this.type = type;
            this.action = action;
            this.staff = staff;
            this.reason = reason;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }
    }

    private final BydloMod plugin;
    private final File file;
    private final Map<UUID, List<Entry>> byPlayer = new ConcurrentHashMap<>();
    private long nextId = 1;

    public HistoryService(BydloMod plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "history.yml");
        load();
    }

    public void log(UUID targetId, String targetName, String type, String action,
                    String staff, String reason, long expiresAt) {
        long id = nextId++;
        Entry e = new Entry(id, targetId, targetName == null ? "?" : targetName, type, action,
                staff == null ? "?" : staff, reason == null ? "" : reason,
                System.currentTimeMillis(), expiresAt);
        byPlayer.computeIfAbsent(targetId, k -> Collections.synchronizedList(new ArrayList<>())).add(e);
        save();
    }

    public List<Entry> get(UUID targetId) {
        List<Entry> list = byPlayer.get(targetId);
        if (list == null) return List.of();
        List<Entry> copy = new ArrayList<>(list);
        copy.sort(Comparator.comparingLong((Entry e) -> e.createdAt).reversed());
        return copy;
    }

    public void save() {
        FileConfiguration conf = new YamlConfiguration();
        conf.set("next-id", nextId);
        for (var e : byPlayer.entrySet()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Entry h : e.getValue()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", h.id);
                row.put("name", h.targetName);
                row.put("type", h.type);
                row.put("action", h.action);
                row.put("staff", h.staff);
                row.put("reason", h.reason);
                row.put("createdAt", h.createdAt);
                row.put("expiresAt", h.expiresAt);
                rows.add(row);
            }
            conf.set("players." + e.getKey(), rows);
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            conf.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Cannot save history: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration conf = YamlConfiguration.loadConfiguration(file);
        nextId = conf.getLong("next-id", 1);
        if (!conf.isConfigurationSection("players")) return;
        for (String key : Objects.requireNonNull(conf.getConfigurationSection("players")).getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                List<?> rows = conf.getList("players." + key);
                if (rows == null) continue;
                List<Entry> list = Collections.synchronizedList(new ArrayList<>());
                for (Object o : rows) {
                    if (!(o instanceof Map<?, ?> map)) continue;
                    long hid = ((Number) map.get("id")).longValue();
                    list.add(new Entry(
                            hid, id,
                            String.valueOf(map.get("name")),
                            String.valueOf(map.get("type")),
                            String.valueOf(map.get("action")),
                            String.valueOf(map.get("staff")),
                            String.valueOf(map.get("reason") == null ? "" : map.get("reason")),
                            ((Number) map.get("createdAt")).longValue(),
                            map.get("expiresAt") == null ? 0L : ((Number) map.get("expiresAt")).longValue()
                    ));
                    nextId = Math.max(nextId, hid + 1);
                }
                if (!list.isEmpty()) byPlayer.put(id, list);
            } catch (Exception ignored) {
            }
        }
    }
}
