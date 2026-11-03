package ru.bydlolend.bydlomod.twitch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.bydlolend.bydlomod.BydloMod;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class TwitchService {
    public static final class Link {
        public final UUID uuid;
        public String playerName;
        public String twitchLogin;
        public String description;
        public String color; // &-code, e.g. &d
        public boolean wasLive;

        public Link(UUID uuid, String playerName, String twitchLogin, String description, String color, boolean wasLive) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.twitchLogin = twitchLogin;
            this.description = description;
            this.color = color == null || color.isBlank() ? "&d" : color;
            this.wasLive = wasLive;
        }
    }

    private final BydloMod plugin;
    private final Map<UUID, Link> links = new ConcurrentHashMap<>();
    private final File file;
    private String appToken;
    private long tokenExpiresAt;
    private int taskId = -1;

    public TwitchService(BydloMod plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "twitch.yml");
        load();
    }

    public Optional<Link> get(UUID id) { return Optional.ofNullable(links.get(id)); }

    public void link(Player player, String twitchLogin) {
        String login = twitchLogin.trim().toLowerCase(Locale.ROOT).replace("https://twitch.tv/", "")
                .replace("http://twitch.tv/", "").replace("www.", "");
        if (login.contains("/")) login = login.substring(login.lastIndexOf('/') + 1);
        Link link = links.getOrDefault(player.getUniqueId(),
                new Link(player.getUniqueId(), player.getName(), login, "Стрим БыдлоЛенда", "&d", false));
        link.twitchLogin = login;
        link.playerName = player.getName();
        links.put(player.getUniqueId(), link);
        save();
    }

    public void setDescription(Player player, String desc) {
        Link link = links.get(player.getUniqueId());
        if (link == null) {
            link = new Link(player.getUniqueId(), player.getName(), "", desc, "&d", false);
        } else {
            link.description = desc;
            link.playerName = player.getName();
        }
        links.put(player.getUniqueId(), link);
        save();
    }

    public void setColor(Player player, String colorCode) {
        Link link = links.get(player.getUniqueId());
        if (link == null) {
            link = new Link(player.getUniqueId(), player.getName(), "", "Стрим БыдлоЛенда", colorCode, false);
        } else {
            link.color = colorCode;
            link.playerName = player.getName();
        }
        links.put(player.getUniqueId(), link);
        save();
    }

    public boolean unlink(UUID id) {
        boolean ok = links.remove(id) != null;
        save();
        return ok;
    }

    public void reloadCredentials() {
        appToken = null;
        tokenExpiresAt = 0;
    }

    /** Тестовый анонс в чат (как при реальном старте стрима). */
    public void announceTest(Player player) {
        Link link = links.get(player.getUniqueId());
        if (link == null || link.twitchLogin == null || link.twitchLogin.isBlank()) {
            throw new IllegalStateException("not_linked");
        }
        link.playerName = player.getName();
        String desc = (link.description == null || link.description.isBlank())
                ? "Тестовый стрим БыдлоЛенда" : link.description;
        announce(link, desc, true);
    }

    public void start() {
        int sec = Math.max(30, plugin.getConfig().getInt("twitch.poll-seconds", 60));
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::poll, 20L * 40, 20L * sec).getTaskId();
    }

    public void stop() {
        if (taskId >= 0) Bukkit.getScheduler().cancelTask(taskId);
        save();
    }

    private void poll() {
        String clientId = plugin.getConfig().getString("twitch.client-id", "");
        String secret = plugin.getConfig().getString("twitch.client-secret", "");
        if (clientId == null || secret == null || clientId.contains("ВСТАВЬ") || secret.contains("ВСТАВЬ")
                || clientId.isBlank() || secret.isBlank()) {
            return;
        }
        List<Link> withTwitch = links.values().stream()
                .filter(l -> l.twitchLogin != null && !l.twitchLogin.isBlank())
                .toList();
        if (withTwitch.isEmpty()) return;

        try {
            ensureToken(clientId, secret);
            for (int i = 0; i < withTwitch.size(); i += 100) {
                List<Link> batch = withTwitch.subList(i, Math.min(i + 100, withTwitch.size()));
                StringBuilder q = new StringBuilder();
                for (Link l : batch) {
                    if (!q.isEmpty()) q.append('&');
                    q.append("user_login=").append(URLEncoder.encode(l.twitchLogin, StandardCharsets.UTF_8));
                }
                String body = httpGet("https://api.twitch.tv/helix/streams?" + q, clientId, appToken);
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                JsonArray data = root.has("data") ? root.getAsJsonArray("data") : new JsonArray();
                Set<String> liveLogins = new HashSet<>();
                Map<String, String> titles = new HashMap<>();
                for (JsonElement el : data) {
                    JsonObject o = el.getAsJsonObject();
                    String login = o.get("user_login").getAsString().toLowerCase(Locale.ROOT);
                    liveLogins.add(login);
                    if (o.has("title")) titles.put(login, o.get("title").getAsString());
                }
                for (Link l : batch) {
                    boolean live = liveLogins.contains(l.twitchLogin.toLowerCase(Locale.ROOT));
                    if (live && !l.wasLive) {
                        String desc = (l.description == null || l.description.isBlank())
                                ? titles.getOrDefault(l.twitchLogin.toLowerCase(Locale.ROOT), "Стрим")
                                : l.description;
                        announce(l, desc, false);
                    }
                    if (l.wasLive != live) {
                        l.wasLive = live;
                    }
                }
            }
            save();
        } catch (Exception ex) {
            plugin.getLogger().warning("Twitch poll error: " + ex.getMessage());
        }
    }

    private void announce(Link link, String description, boolean test) {
        String color = link.color == null || link.color.isBlank() ? "&d" : link.color;
        String tpl = plugin.getConfig().getString("twitch.announce",
                "&d&m                                        \n"
                        + "&d  ✦ &f%player% &dподрубил стрим!&r\n"
                        + "&7  twitch.tv/%twitch%\n"
                        + "  %description%\n"
                        + "&d&m                                        ");
        String desc = (test ? "&8[&eТЕСТ&8] " : "") + color + description;
        String msg = BydloMod.color(tpl
                .replace("%player%", link.playerName)
                .replace("%twitch%", link.twitchLogin)
                .replace("%color%", color)
                .replace("%description%", desc));
        final String out = msg;
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcastMessage(out));
    }

    private void ensureToken(String clientId, String secret) throws IOException {
        if (appToken != null && System.currentTimeMillis() < tokenExpiresAt - 60_000) return;
        String url = "https://id.twitch.tv/oauth2/token"
                + "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8)
                + "&grant_type=client_credentials";
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.connect();
        String body = read(conn);
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        appToken = o.get("access_token").getAsString();
        long expiresIn = o.has("expires_in") ? o.get("expires_in").getAsLong() : 3600;
        tokenExpiresAt = System.currentTimeMillis() + expiresIn * 1000L;
    }

    private String httpGet(String url, String clientId, String token) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Client-ID", clientId);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.connect();
        return read(conn);
    }

    private static String read(HttpURLConnection conn) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            if (conn.getResponseCode() >= 400) {
                throw new IOException("HTTP " + conn.getResponseCode() + ": " + sb);
            }
            return sb.toString();
        }
    }

    private void save() {
        FileConfiguration conf = new YamlConfiguration();
        for (Link l : links.values()) {
            String base = "links." + l.uuid;
            conf.set(base + ".name", l.playerName);
            conf.set(base + ".twitch", l.twitchLogin);
            conf.set(base + ".description", l.description);
            conf.set(base + ".color", l.color);
            conf.set(base + ".wasLive", l.wasLive);
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            conf.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Cannot save twitch.yml: " + e.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration conf = YamlConfiguration.loadConfiguration(file);
        if (!conf.isConfigurationSection("links")) return;
        for (String key : Objects.requireNonNull(conf.getConfigurationSection("links")).getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String base = "links." + key;
                Link l = new Link(id,
                        conf.getString(base + ".name", "unknown"),
                        conf.getString(base + ".twitch", ""),
                        conf.getString(base + ".description", ""),
                        conf.getString(base + ".color", "&d"),
                        conf.getBoolean(base + ".wasLive", false));
                links.put(id, l);
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
