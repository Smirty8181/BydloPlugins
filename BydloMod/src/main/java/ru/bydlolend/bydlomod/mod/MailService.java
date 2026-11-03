package ru.bydlolend.bydlomod.mod;

import org.bukkit.Bukkit;
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

/** Оффлайн-письма: доставка при входе. */
public final class MailService implements Listener {
    private final BydloMod plugin;
    private final File file;
    private final Map<UUID, List<String>> pending = new ConcurrentHashMap<>();

    public MailService(BydloMod plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mail.yml");
        load();
    }

    public void send(UUID target, String coloredOrAmpMessage) {
        Player online = Bukkit.getPlayer(target);
        String msg = plugin.prefix() + BydloMod.color(coloredOrAmpMessage);
        if (online != null) {
            online.sendMessage(msg);
            return;
        }
        pending.computeIfAbsent(target, k -> Collections.synchronizedList(new ArrayList<>())).add(coloredOrAmpMessage);
        save();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        List<String> list = pending.remove(e.getPlayer().getUniqueId());
        if (list == null || list.isEmpty()) return;
        save();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            e.getPlayer().sendMessage(plugin.prefix() + BydloMod.color("&eСообщения, пока тебя не было:"));
            for (String m : list) {
                e.getPlayer().sendMessage(plugin.prefix() + BydloMod.color(m));
            }
        }, 40L);
    }

    public void save() {
        FileConfiguration conf = new YamlConfiguration();
        for (var e : pending.entrySet()) {
            conf.set("mail." + e.getKey(), new ArrayList<>(e.getValue()));
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            conf.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Cannot save mail: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration conf = YamlConfiguration.loadConfiguration(file);
        if (!conf.isConfigurationSection("mail")) return;
        for (String key : Objects.requireNonNull(conf.getConfigurationSection("mail")).getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                List<String> msgs = conf.getStringList("mail." + key);
                if (!msgs.isEmpty()) {
                    pending.put(id, Collections.synchronizedList(new ArrayList<>(msgs)));
                }
            } catch (Exception ignored) {
            }
        }
    }
}
