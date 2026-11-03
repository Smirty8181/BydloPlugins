package ru.bydloland.mage;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Tower level: 1 base, 2 CD -20%, 3 dig 3x3x2.
 */
public final class MageProgress {

    private final BydloMagePlugin plugin;
    private final File file;
    private FileConfiguration data;

    public MageProgress(BydloMagePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException ignored) {
            }
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save players.yml: " + e.getMessage());
        }
    }

    public int level(Player player) {
        return Math.max(1, Math.min(3, data.getInt(key(player) + ".level", 1)));
    }

    public void setLevel(Player player, int level) {
        data.set(key(player) + ".level", Math.max(1, Math.min(3, level)));
        save();
    }

    /** Base dig CD 8000ms, lumber 12000ms; level 2+ → -20%. */
    public long digCooldownMs(Player player) {
        long base = 8000L;
        if (level(player) >= 2) base = (long) (base * 0.8);
        return base;
    }

    public long lumberCooldownMs(Player player) {
        long base = 12000L;
        if (level(player) >= 2) base = (long) (base * 0.8);
        return base;
    }

    public boolean digDepth2(Player player) {
        return level(player) >= 3;
    }

    public int upgradeCost(int toLevel) {
        return switch (toLevel) {
            case 2 -> 8;  // etherium crystals
            case 3 -> 16;
            default -> 0;
        };
    }

    private static String key(Player player) {
        return player.getUniqueId().toString();
    }

    public UUID uuid(Player player) {
        return player.getUniqueId();
    }
}
