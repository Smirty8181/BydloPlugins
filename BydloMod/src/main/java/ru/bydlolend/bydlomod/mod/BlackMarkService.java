package ru.bydlolend.bydlomod.mod;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.bydlolend.bydlomod.BydloMod;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BlackMarkService implements Listener {
    public static final String PREFIX_ID = "chernaya_metka";

    public static final class Mark {
        public final UUID uuid;
        public final String previousPrefixId; // nullable
        public final long expiresAt;
        public final String reason;

        public Mark(UUID uuid, String previousPrefixId, long expiresAt, String reason) {
            this.uuid = uuid;
            this.previousPrefixId = previousPrefixId;
            this.expiresAt = expiresAt;
            this.reason = reason;
        }

        public boolean expired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }

    private final BydloMod plugin;
    private final File file;
    private final Map<UUID, Mark> marks = new ConcurrentHashMap<>();

    public BlackMarkService(BydloMod plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "blackmarks.yml");
        load();
    }

    public Optional<Mark> get(UUID id) {
        Mark m = marks.get(id);
        if (m == null) return Optional.empty();
        if (m.expired()) {
            clear(id, false);
            return Optional.empty();
        }
        return Optional.of(m);
    }

    public boolean has(UUID id) {
        return get(id).isPresent();
    }

    public void apply(UUID uuid, String name, String reason) {
        String durRaw = plugin.getConfig().getString("warns.black-mark-duration", "30м");
        long expires = ModerationService.parseExpiry(durRaw);
        if (expires <= 0) expires = System.currentTimeMillis() + 30L * 60_000L;

        String prev = PrefixHook.getPrefixId(uuid);
        if (PREFIX_ID.equals(prev)) {
            Mark old = marks.get(uuid);
            prev = old != null ? old.previousPrefixId : null;
        }

        PrefixHook.ensurePrefix(PREFIX_ID, " §r<image:bydlo:rank_mark>");
        PrefixHook.setPrefix(uuid, PREFIX_ID);

        marks.put(uuid, new Mark(uuid, prev, expires, reason == null ? "" : reason));
        save();

        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            p.sendMessage(plugin.prefix() + BydloMod.color(
                    "&8Чёрная метка &7на &f" + ModerationService.formatLeft(expires - System.currentTimeMillis())
                            + "\n&7Все видят: ты недавно получил варн."));
        }
    }

    public void clear(UUID uuid, boolean message) {
        Mark m = marks.remove(uuid);
        if (m == null) return;
        if (PREFIX_ID.equals(PrefixHook.getPrefixId(uuid))) {
            if (m.previousPrefixId != null && !m.previousPrefixId.isBlank()) {
                PrefixHook.setPrefix(uuid, m.previousPrefixId);
            } else {
                PrefixHook.clearPrefix(uuid);
            }
        }
        save();
        if (message) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(plugin.prefix() + BydloMod.color("&aЧёрная метка снята."));
            }
        }
    }

    public void startTasks() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<UUID> dead = new ArrayList<>();
            for (var e : marks.entrySet()) {
                if (e.getValue().expired()) dead.add(e.getKey());
            }
            for (UUID id : dead) clear(id, true);
        }, 20L * 20, 20L * 20);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID id : marks.keySet()) {
                if (!has(id)) continue;
                Player p = Bukkit.getPlayer(id);
                if (p == null || !p.isOnline()) continue;
                p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 0.2, 0),
                        6, 0.25, 0.15, 0.25, 0.01);
                p.getWorld().spawnParticle(Particle.ASH, p.getLocation().add(0, 1.0, 0),
                        3, 0.2, 0.3, 0.2, 0.0);
            }
        }, 40L, 40L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        get(e.getPlayer().getUniqueId()).ifPresent(m -> {
            String left = ModerationService.formatLeft(m.expiresAt - System.currentTimeMillis());
            e.getPlayer().sendMessage(plugin.prefix() + BydloMod.color(
                    "&8У тебя чёрная метка ещё &f" + left));
            if (!PREFIX_ID.equals(PrefixHook.getPrefixId(e.getPlayer().getUniqueId()))) {
                PrefixHook.setPrefix(e.getPlayer().getUniqueId(), PREFIX_ID);
            }
        });
    }

    public void save() {
        FileConfiguration conf = new YamlConfiguration();
        for (Mark m : marks.values()) {
            String base = "marks." + m.uuid;
            conf.set(base + ".previous", m.previousPrefixId);
            conf.set(base + ".expiresAt", m.expiresAt);
            conf.set(base + ".reason", m.reason);
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            conf.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Cannot save blackmarks: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration conf = YamlConfiguration.loadConfiguration(file);
        if (!conf.isConfigurationSection("marks")) return;
        for (String key : Objects.requireNonNull(conf.getConfigurationSection("marks")).getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String base = "marks." + key;
                Mark m = new Mark(id,
                        conf.getString(base + ".previous"),
                        conf.getLong(base + ".expiresAt"),
                        conf.getString(base + ".reason", ""));
                if (!m.expired()) marks.put(id, m);
            } catch (Exception ignored) {
            }
        }
    }
}
