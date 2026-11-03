package ru.bydloland.discs.store;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.bydloland.discs.BydloDiscsPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerTracks {
    public static final class TrackInfo {
        public String title;
        public String url;
        public long lastChange;
        public Path pcmPath;
        public Path oggPath;
    }

    private final BydloDiscsPlugin plugin;
    private final File file;
    private final Path tracksDir;
    private final Map<UUID, TrackInfo> data = new ConcurrentHashMap<>();

    public PlayerTracks(BydloDiscsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        this.tracksDir = plugin.getDataFolder().toPath().resolve("tracks");
        try {
            Files.createDirectories(tracksDir);
        } catch (IOException e) {
            plugin.getLogger().warning("tracks dir: " + e.getMessage());
        }
        load();
    }

    public Path tracksDir() { return tracksDir; }

    public TrackInfo get(UUID id) { return data.get(id); }

    public TrackInfo getOrCreate(UUID id) {
        return data.computeIfAbsent(id, k -> new TrackInfo());
    }

    public boolean onCooldown(UUID id) {
        if (plugin.getConfig().getBoolean("ignore-cooldown", false)) return false;
        TrackInfo t = data.get(id);
        if (t == null || t.lastChange <= 0) return false;
        long cd = plugin.getConfig().getLong("cooldown-seconds", 3600L) * 1000L;
        return System.currentTimeMillis() - t.lastChange < cd;
    }

    public long cooldownLeftSec(UUID id) {
        TrackInfo t = data.get(id);
        if (t == null || t.lastChange <= 0) return 0;
        long cd = plugin.getConfig().getLong("cooldown-seconds", 3600L) * 1000L;
        long left = cd - (System.currentTimeMillis() - t.lastChange);
        return Math.max(0, (left + 999) / 1000);
    }

    public static String formatLeft(long sec) {
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) return h + "ч " + m + "м";
        if (m > 0) return m + "м " + s + "с";
        return s + "с";
    }

    public void saveTrack(UUID id, String title, String url, Path pcm, Path ogg) {
        TrackInfo t = getOrCreate(id);
        t.title = title;
        t.url = url;
        t.lastChange = System.currentTimeMillis();
        t.pcmPath = pcm;
        t.oggPath = ogg;
        save();
    }

    public void save() {
        FileConfiguration conf = new YamlConfiguration();
        for (var e : data.entrySet()) {
            String base = e.getKey().toString();
            TrackInfo t = e.getValue();
            conf.set(base + ".title", t.title);
            conf.set(base + ".url", t.url);
            conf.set(base + ".lastChange", t.lastChange);
            conf.set(base + ".pcm", t.pcmPath != null ? t.pcmPath.getFileName().toString() : null);
            conf.set(base + ".ogg", t.oggPath != null ? t.oggPath.getFileName().toString() : null);
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            conf.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("save players: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration conf = YamlConfiguration.loadConfiguration(file);
        for (String key : conf.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                TrackInfo t = new TrackInfo();
                t.title = conf.getString(key + ".title", "Трек");
                t.url = conf.getString(key + ".url", "");
                t.lastChange = conf.getLong(key + ".lastChange");
                String pcm = conf.getString(key + ".pcm");
                String ogg = conf.getString(key + ".ogg");
                if (pcm != null) t.pcmPath = tracksDir.resolve(pcm);
                if (ogg != null) t.oggPath = tracksDir.resolve(ogg);
                data.put(id, t);
            } catch (Exception ignored) {
            }
        }
    }
}
