package ru.bydloland.tattoos;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TattooStorage {
    private final BydloTattoosPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerTattoos> players = new HashMap<>();
    private final Set<String> artistNames = new HashSet<>();
    private final Set<UUID> artistUuids = new HashSet<>();

    public TattooStorage(BydloTattoosPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        players.clear();
        artistNames.clear();
        artistUuids.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String raw : yaml.getStringList("artists")) {
            if (raw == null || raw.isBlank()) continue;
            try {
                artistUuids.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                artistNames.add(raw.toLowerCase(Locale.ROOT));
            }
        }
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection sec = root.getConfigurationSection(key);
                if (sec == null) continue;
                PlayerTattoos data = new PlayerTattoos();
                data.originalValue = sec.getString("original-value");
                data.originalSignature = sec.getString("original-signature");
                data.variant = sec.getString("variant", "CLASSIC");
                data.composeRev = sec.getInt("compose-rev", 0);
                List<Map<?, ?>> list = sec.getMapList("tattoos");
                for (Map<?, ?> map : list) {
                    String id = String.valueOf(map.get("id"));
                    TattooSlot slot = TattooSlot.fromConfig(String.valueOf(map.get("slot")));
                    if (slot == null) continue;
                    UUID artist = null;
                    Object a = map.get("artist");
                    if (a != null) {
                        try {
                            artist = UUID.fromString(String.valueOf(a));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    long time = 0L;
                    Object t = map.get("time");
                    if (t instanceof Number n) time = n.longValue();
                    Object png = map.get("png");
                    String pngB64 = png == null ? null : String.valueOf(png);
                    if (pngB64 != null && (pngB64.isBlank() || "null".equals(pngB64))) pngB64 = null;
                    List<Integer> colors = new java.util.ArrayList<>();
                    Object rawColors = map.get("colors");
                    if (rawColors instanceof List<?> cl) {
                        for (Object o : cl) {
                            if (o instanceof Number n) colors.add(n.intValue());
                        }
                    }
                    data.tattoos.add(new TattooRecord(id, slot, artist, time, pngB64, colors));
                }
                players.put(uuid, data);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> listed = new java.util.ArrayList<>(artistNames);
        for (UUID u : artistUuids) listed.add(u.toString());
        yaml.set("artists", listed);
        for (Map.Entry<UUID, PlayerTattoos> e : players.entrySet()) {
            String path = "players." + e.getKey();
            PlayerTattoos data = e.getValue();
            yaml.set(path + ".original-value", data.originalValue);
            yaml.set(path + ".original-signature", data.originalSignature);
            yaml.set(path + ".variant", data.variant);
            yaml.set(path + ".compose-rev", data.composeRev);
            List<Map<String, Object>> list = new java.util.ArrayList<>();
            for (TattooRecord rec : data.tattoos) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", rec.id());
                map.put("slot", rec.slot().name());
                if (rec.artist() != null) map.put("artist", rec.artist().toString());
                map.put("time", rec.time());
                if (rec.custom()) map.put("png", rec.pngB64());
                if (rec.colors() != null && !rec.colors().isEmpty()) map.put("colors", rec.colors());
                list.add(map);
            }
            yaml.set(path + ".tattoos", list);
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не сохранил data.yml: " + ex.getMessage());
        }
    }

    public PlayerTattoos get(UUID uuid) {
        return players.computeIfAbsent(uuid, k -> new PlayerTattoos());
    }

    public boolean isListedArtist(Player player) {
        if (player == null) return false;
        if (artistNames.contains(player.getName().toLowerCase(Locale.ROOT))) return true;
        return artistUuids.contains(player.getUniqueId());
    }

    public void setArtist(Player player, boolean on) {
        if (player == null) return;
        setArtistName(player.getName(), on);
        if (on) artistUuids.add(player.getUniqueId());
        else artistUuids.remove(player.getUniqueId());
        save();
    }

    public void setArtistName(String name, boolean on) {
        if (name == null || name.isBlank()) return;
        String key = name.toLowerCase(Locale.ROOT).trim();
        if (on) artistNames.add(key);
        else artistNames.remove(key);
        save();
    }

    public Set<String> artistNames() {
        return Set.copyOf(artistNames);
    }
}
