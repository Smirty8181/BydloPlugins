package ru.bydlolend.bydlomod.mod;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.bydlolend.bydlomod.BydloMod;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SupportService {
    public enum Status { OPEN, CLOSED }

    public static final class Ticket {
        public final int id;
        public final UUID playerId;
        public final String playerName;
        public final String body;
        public final long createdAt;
        public Status status;
        public String claimedBy;
        public UUID claimedByUuid;
        public String closedBy;

        public Ticket(int id, UUID playerId, String playerName, String body, long createdAt,
                      Status status, String claimedBy, UUID claimedByUuid, String closedBy) {
            this.id = id;
            this.playerId = playerId;
            this.playerName = playerName;
            this.body = body;
            this.createdAt = createdAt;
            this.status = status;
            this.claimedBy = claimedBy;
            this.claimedByUuid = claimedByUuid;
            this.closedBy = closedBy;
        }
    }

    private final BydloMod plugin;
    private final MailService mail;
    private final File file;
    private final Map<Integer, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private int nextId = 1;

    public SupportService(BydloMod plugin, MailService mail) {
        this.plugin = plugin;
        this.mail = mail;
        this.file = new File(plugin.getDataFolder(), "support.yml");
        load();
    }

    public boolean onCooldown(UUID id) {
        long cd = plugin.getConfig().getLong("support.cooldown-seconds", 60L) * 1000L;
        Long last = cooldowns.get(id);
        return last != null && System.currentTimeMillis() - last < cd;
    }

    public long cooldownLeftSec(UUID id) {
        long cd = plugin.getConfig().getLong("support.cooldown-seconds", 60L) * 1000L;
        Long last = cooldowns.get(id);
        if (last == null) return 0;
        return Math.max(0, (cd - (System.currentTimeMillis() - last) + 999) / 1000);
    }

    public Ticket create(Player player, String body) {
        int id = nextId++;
        Ticket t = new Ticket(id, player.getUniqueId(), player.getName(), body.trim(),
                System.currentTimeMillis(), Status.OPEN, null, null, null);
        tickets.put(id, t);
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        save();
        notifyStaff("&bТикет поддержки &f#" + id + " &7от &f" + player.getName()
                + "\n&7" + body + " &8— /поддержка");
        return t;
    }

    public Ticket get(int id) {
        return tickets.get(id);
    }

    public List<Ticket> openTickets() {
        List<Ticket> list = new ArrayList<>();
        for (Ticket t : tickets.values()) {
            if (t.status == Status.OPEN) list.add(t);
        }
        list.sort(Comparator.comparingLong((Ticket t) -> t.createdAt).reversed());
        return list;
    }

    public List<Ticket> ticketsOf(UUID playerId) {
        List<Ticket> list = new ArrayList<>();
        for (Ticket t : tickets.values()) {
            if (t.playerId.equals(playerId)) list.add(t);
        }
        list.sort(Comparator.comparingLong((Ticket t) -> t.createdAt).reversed());
        return list;
    }

    public boolean claim(int id, Player staff) {
        Ticket t = tickets.get(id);
        if (t == null || t.status != Status.OPEN) return false;
        t.claimedBy = staff.getName();
        t.claimedByUuid = staff.getUniqueId();
        save();
        mail.send(t.playerId, "&bТикет &f#" + id + " &7взял &f" + staff.getName()
                + "\n&7Пиши: &f/пс <текст>");
        return true;
    }

    public boolean close(int id, String staff) {
        Ticket t = tickets.get(id);
        if (t == null || t.status == Status.CLOSED) return false;
        t.status = Status.CLOSED;
        t.closedBy = staff;
        save();
        mail.send(t.playerId, "&bТикет &f#" + id + " &aзакрыт &7(&f" + staff + "&7)");
        return true;
    }

    public Ticket activeChatFor(UUID player) {
        Ticket best = null;
        for (Ticket t : tickets.values()) {
            if (t.status != Status.OPEN || t.claimedByUuid == null) continue;
            if (!t.playerId.equals(player) && !t.claimedByUuid.equals(player)) continue;
            if (best == null || t.createdAt > best.createdAt) best = t;
        }
        return best;
    }

    public boolean relayChat(Player from, String text) {
        Ticket t = activeChatFor(from.getUniqueId());
        if (t == null) return false;
        UUID otherId = from.getUniqueId().equals(t.playerId) ? t.claimedByUuid : t.playerId;
        if (otherId == null) return false;
        String line = "&b[Поддержка #" + t.id + "] &f" + from.getName() + "&7: &f" + text;
        from.sendMessage(plugin.prefix() + BydloMod.color(line));
        mail.send(otherId, line);
        return true;
    }

    public static boolean isStaff(Player p) {
        return p.hasPermission("bydlo.support") || p.hasPermission("bydlo.mod")
                || p.hasPermission("bydlo.admin") || p.isOp();
    }

    public void notifyStaff(String message) {
        String msg = plugin.prefix() + BydloMod.color(message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isStaff(p)) p.sendMessage(msg);
        }
    }

    public void save() {
        FileConfiguration conf = new YamlConfiguration();
        conf.set("next-id", nextId);
        for (Ticket t : tickets.values()) {
            String base = "tickets." + t.id;
            conf.set(base + ".playerId", t.playerId.toString());
            conf.set(base + ".playerName", t.playerName);
            conf.set(base + ".body", t.body);
            conf.set(base + ".createdAt", t.createdAt);
            conf.set(base + ".status", t.status.name());
            conf.set(base + ".claimedBy", t.claimedBy);
            conf.set(base + ".claimedByUuid", t.claimedByUuid == null ? null : t.claimedByUuid.toString());
            conf.set(base + ".closedBy", t.closedBy);
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            conf.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Cannot save support: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration conf = YamlConfiguration.loadConfiguration(file);
        nextId = conf.getInt("next-id", 1);
        ConfigurationSection sec = conf.getConfigurationSection("tickets");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                String base = "tickets." + key;
                String cu = conf.getString(base + ".claimedByUuid");
                Ticket t = new Ticket(
                        id,
                        UUID.fromString(conf.getString(base + ".playerId")),
                        conf.getString(base + ".playerName", "?"),
                        conf.getString(base + ".body", ""),
                        conf.getLong(base + ".createdAt"),
                        Status.valueOf(conf.getString(base + ".status", "OPEN")),
                        conf.getString(base + ".claimedBy"),
                        cu == null || cu.isBlank() ? null : UUID.fromString(cu),
                        conf.getString(base + ".closedBy")
                );
                tickets.put(id, t);
                nextId = Math.max(nextId, id + 1);
            } catch (Exception ignored) {
            }
        }
    }
}
