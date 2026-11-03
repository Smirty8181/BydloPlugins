package ru.bydloland.prefixes;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PrefixService {

    private final BydloPrefixesPlugin plugin;
    private final Map<String, PrefixDefinition> prefixes = new LinkedHashMap<>();
    private final Map<UUID, String> playerPrefix = new ConcurrentHashMap<>();
    private final Map<String, String> namePrefix = new ConcurrentHashMap<>();
    private File playersFile;
    private FileConfiguration playersCfg;

    public PrefixService(BydloPrefixesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        prefixes.clear();
        ConfigurationSection section = cfg.getConfigurationSection("prefixes");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection c = section.getConfigurationSection(id);
                if (c == null) continue;
                PrefixDefinition def = PrefixDefinition.from(id.toLowerCase(Locale.ROOT), c);
                prefixes.put(def.id(), def);
            }
        }

        playersFile = new File(plugin.getDataFolder(), "players.yml");
        if (!playersFile.exists()) {
            try {
                playersFile.getParentFile().mkdirs();
                playersFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("players.yml: " + e.getMessage());
            }
        }
        playersCfg = YamlConfiguration.loadConfiguration(playersFile);
        playerPrefix.clear();
        namePrefix.clear();
        ConfigurationSection ps = playersCfg.getConfigurationSection("players");
        if (ps != null) {
            for (String key : ps.getKeys(false)) {
                String id = ps.getString(key);
                if (id == null || id.isEmpty()) continue;
                try {
                    UUID uuid = UUID.fromString(key);
                    playerPrefix.put(uuid, id.toLowerCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    namePrefix.put(key.toLowerCase(Locale.ROOT), id.toLowerCase(Locale.ROOT));
                }
            }
        }
        ConfigurationSection ns = playersCfg.getConfigurationSection("names");
        if (ns != null) {
            for (String key : ns.getKeys(false)) {
                String id = ns.getString(key);
                if (id != null && !id.isEmpty()) {
                    namePrefix.put(key.toLowerCase(Locale.ROOT), id.toLowerCase(Locale.ROOT));
                }
            }
        }
        plugin.getLogger().info("Loaded " + prefixes.size() + " prefixes");
    }

    public void savePlayers() {
        if (playersCfg == null || playersFile == null) return;
        playersCfg.set("players", null);
        playersCfg.set("names", null);
        for (Map.Entry<UUID, String> e : playerPrefix.entrySet()) {
            playersCfg.set("players." + e.getKey(), e.getValue());
        }
        for (Map.Entry<String, String> e : namePrefix.entrySet()) {
            playersCfg.set("names." + e.getKey(), e.getValue());
        }
        try {
            playersCfg.save(playersFile);
        } catch (IOException e) {
            plugin.getLogger().warning("save players.yml: " + e.getMessage());
        }
    }

    public void savePrefixesConfig() {
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("prefixes", null);
        for (PrefixDefinition def : prefixes.values()) {
            String path = "prefixes." + def.id();
            cfg.set(path + ".tab", def.tab());
            cfg.set(path + ".display", def.display());
            cfg.set(path + ".icon", def.icon().name());
            cfg.set(path + ".lore", new ArrayList<>(def.lore()));
            if (def.luckPermsGroup() != null) {
                cfg.set(path + ".luckperms-group", def.luckPermsGroup());
            }
        }
        plugin.saveConfig();
    }

    public void save() {
        savePlayers();
    }

    public Map<String, PrefixDefinition> prefixes() {
        return Collections.unmodifiableMap(prefixes);
    }

    public PrefixDefinition get(String id) {
        if (id == null) return null;
        return prefixes.get(id.toLowerCase(Locale.ROOT));
    }

    public PrefixDefinition getPlayerPrefix(UUID uuid) {
        String id = playerPrefix.get(uuid);
        return id == null ? null : get(id);
    }

    public PrefixDefinition getPlayerPrefix(Player player) {
        PrefixDefinition byUuid = getPlayerPrefix(player.getUniqueId());
        if (byUuid != null) return byUuid;
        String id = namePrefix.get(player.getName().toLowerCase(Locale.ROOT));
        return id == null ? null : get(id);
    }

    public static Player onlineByName(String name) {
        if (name == null || name.isBlank()) return null;
        String want = name.trim();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(want)) return p;
        }
        return null;
    }

    public String tabText(Player player) {
        PrefixDefinition def = getPlayerPrefix(player);
        if (def == null || def.tab().isEmpty()) return "";
        // без ведущего пробела — иконка сразу перед текстом префикса
        return ColorUtil.legacy(def.tab());
    }

    public boolean setPrefix(UUID uuid, String prefixId) {
        PrefixDefinition def = get(prefixId);
        if (def == null) return false;
        PrefixDefinition old = getPlayerPrefix(uuid);
        playerPrefix.put(uuid, def.id());
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            namePrefix.put(online.getName().toLowerCase(Locale.ROOT), def.id());
        }
        savePlayers();
        syncLuckPerms(uuid, old, def);
        return true;
    }

    public boolean setPrefix(Player player, String prefixId) {
        if (player == null) return false;
        boolean ok = setPrefix(player.getUniqueId(), prefixId);
        if (ok) {
            PrefixDefinition def = get(prefixId);
            if (def != null) {
                namePrefix.put(player.getName().toLowerCase(Locale.ROOT), def.id());
                savePlayers();
            }
        }
        return ok;
    }

    public boolean setPrefixByName(String name, String prefixId) {
        PrefixDefinition def = get(prefixId);
        if (def == null || name == null || name.isBlank()) return false;
        Player online = onlineByName(name);
        if (online != null) return setPrefix(online, prefixId);
        namePrefix.put(name.toLowerCase(Locale.ROOT).trim(), def.id());
        savePlayers();
        return true;
    }

    public void clearPrefix(UUID uuid) {
        PrefixDefinition old = getPlayerPrefix(uuid);
        playerPrefix.remove(uuid);
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            namePrefix.remove(online.getName().toLowerCase(Locale.ROOT));
        }
        savePlayers();
        syncLuckPerms(uuid, old, null);
    }

    public void clearPrefixByName(String name) {
        if (name == null || name.isBlank()) return;
        Player online = onlineByName(name);
        if (online != null) {
            clearPrefix(online.getUniqueId());
            return;
        }
        namePrefix.remove(name.toLowerCase(Locale.ROOT).trim());
        savePlayers();
    }

    public void bindOnlineName(Player player) {
        if (player == null) return;
        String nick = player.getName().toLowerCase(Locale.ROOT);
        String byName = namePrefix.get(nick);
        String byUuid = playerPrefix.get(player.getUniqueId());
        if (byUuid != null) {
            namePrefix.put(nick, byUuid);
            savePlayers();
            return;
        }
        if (byName != null) {
            playerPrefix.put(player.getUniqueId(), byName);
            savePlayers();
        }
    }

    /**
     * Если у префикса указан luckperms-group — ставим/снимаем группу LP.
     * Staff-группы: parent set. Косметика без группы — не трогаем LP.
     */
    private void syncLuckPerms(UUID uuid, PrefixDefinition oldDef, PrefixDefinition newDef) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) return;

        String oldGroup = oldDef != null ? oldDef.luckPermsGroup() : null;
        String newGroup = newDef != null ? newDef.luckPermsGroup() : null;

        if (oldGroup == null && newGroup == null) return;

        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String name = op.getName() != null ? op.getName() : uuid.toString();

        Runnable run = () -> {
            // OP с хостинга всегда admin — префикс не снимает права
            if (op.isOp()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "lp user " + name + " parent set admin");
                return;
            }
            if (oldGroup != null && (newGroup == null || !oldGroup.equalsIgnoreCase(newGroup))) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "lp user " + name + " parent remove " + oldGroup);
            }
            if (newGroup != null) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "lp user " + name + " parent set " + newGroup);
            } else if (oldGroup != null) {
                // сняли staff-префикс → default
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "lp user " + name + " parent set default");
            }
        };

        if (Bukkit.isPrimaryThread()) {
            run.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, run);
        }
    }

    public static boolean isValidId(String id) {
        return id != null && id.matches("[a-z0-9_]{2,24}");
    }

    public String createOrUpdate(String rawId, String text, Material icon, List<String> lore) {
        return createOrUpdate(rawId, text, icon, lore, null);
    }

    public String createOrUpdate(String rawId, String text, Material icon, List<String> lore, String luckPermsGroup) {
        if (rawId == null) return "Пустой id";
        String id = rawId.toLowerCase(Locale.ROOT).trim();
        if (!isValidId(id)) {
            return "ID: только a-z 0-9 _ , длина 2–24 (пример: korol)";
        }
        if (text == null || text.isBlank()) return "Пустой текст префикса";
        String tab = text.trim();
        Material mat = icon == null ? Material.NAME_TAG : icon;
        List<String> l = lore == null ? List.of("<white>Кастомный префикс") : lore;
        // сохранить group если обновляем существующий без нового group
        String group = luckPermsGroup;
        if (group == null) {
            PrefixDefinition existing = prefixes.get(id);
            if (existing != null) group = existing.luckPermsGroup();
        }
        PrefixDefinition def = new PrefixDefinition(id, tab, tab, mat, l, group);
        prefixes.put(id, def);
        savePrefixesConfig();
        return null;
    }

    public boolean deletePrefix(String rawId) {
        if (rawId == null) return false;
        String id = rawId.toLowerCase(Locale.ROOT);
        if (prefixes.remove(id) == null) return false;
        List<UUID> toClear = new ArrayList<>();
        for (Map.Entry<UUID, String> e : playerPrefix.entrySet()) {
            if (id.equals(e.getValue())) toClear.add(e.getKey());
        }
        for (UUID u : toClear) clearPrefix(u);
        savePrefixesConfig();
        return true;
    }
}
