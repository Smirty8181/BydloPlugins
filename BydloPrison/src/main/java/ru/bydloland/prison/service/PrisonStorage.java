package ru.bydloland.prison.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.bydloland.prison.BydloPrisonPlugin;
import ru.bydloland.prison.model.PrisonRecord;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class PrisonStorage {

    private final BydloPrisonPlugin plugin;
    private final Map<UUID, PrisonRecord> records = new ConcurrentHashMap<>();
    private File file;
    private FileConfiguration yaml;

    public PrisonStorage(BydloPrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "prisons.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Cannot create prisons.yml", e);
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        records.clear();
        ConfigurationSection root = yaml.getConfigurationSection("prisons");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                PrisonRecord r = new PrisonRecord(
                        uuid,
                        s.getString("name", "unknown"),
                        s.getString("reason", "—"),
                        s.getString("banned-by", "console"),
                        s.getLong("banned-at", System.currentTimeMillis()),
                        s.getLong("expires-at", -1L)
                );
                String pig = s.getString("pig-uuid");
                if (pig != null && !pig.isBlank()) {
                    r.setPigUuid(UUID.fromString(pig));
                }
                if (s.contains("world")) {
                    r.setLocation(s.getString("world"), s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));
                }
                records.put(uuid, r);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skip invalid prison entry: " + key + " — " + ex.getMessage());
            }
        }
    }

    public void save() {
        if (yaml == null) {
            yaml = new YamlConfiguration();
        }
        yaml.set("prisons", null);
        for (PrisonRecord r : records.values()) {
            String path = "prisons." + r.uuid();
            yaml.set(path + ".name", r.name());
            yaml.set(path + ".reason", r.reason());
            yaml.set(path + ".banned-by", r.bannedBy());
            yaml.set(path + ".banned-at", r.bannedAtMs());
            yaml.set(path + ".expires-at", r.expiresAtMs());
            if (r.pigUuid() != null) {
                yaml.set(path + ".pig-uuid", r.pigUuid().toString());
            }
            if (r.world() != null) {
                yaml.set(path + ".world", r.world());
                yaml.set(path + ".x", r.x());
                yaml.set(path + ".y", r.y());
                yaml.set(path + ".z", r.z());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Cannot save prisons.yml", e);
        }
    }

    public PrisonRecord get(UUID uuid) {
        return records.get(uuid);
    }

    public void put(PrisonRecord record) {
        records.put(record.uuid(), record);
        save();
    }

    public void remove(UUID uuid) {
        records.remove(uuid);
        save();
    }

    public Collection<PrisonRecord> all() {
        return records.values();
    }

    public boolean isImprisoned(UUID uuid) {
        PrisonRecord r = records.get(uuid);
        if (r == null) return false;
        if (r.isExpired(System.currentTimeMillis())) {
            records.remove(uuid);
            save();
            return false;
        }
        return true;
    }
}
