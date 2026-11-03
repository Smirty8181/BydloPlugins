package ru.bydlolend.bydlomod.mail;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import ru.bydlolend.bydlomod.BydloMod;
import ru.bydlolend.bydlomod.util.Msg;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Настоящие письма — письменная книга + ящик. */
public final class LetterService implements Listener {
    public static final class Letter {
        public final int id;
        public final UUID fromId;
        public final String fromName;
        public final UUID toId;
        public final String toName;
        public final String body;
        public final long createdAt;
        public boolean delivered;

        public Letter(int id, UUID fromId, String fromName, UUID toId, String toName,
                      String body, long createdAt, boolean delivered) {
            this.id = id;
            this.fromId = fromId;
            this.fromName = fromName;
            this.toId = toId;
            this.toName = toName;
            this.body = body;
            this.createdAt = createdAt;
            this.delivered = delivered;
        }
    }

    private final BydloMod plugin;
    private final File file;
    private final Map<Integer, Letter> letters = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();
    private int nextId = 1;

    public LetterService(BydloMod plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "letters.yml");
        load();
    }

    public int cooldownSec() {
        return Math.max(5, plugin.getConfig().getInt("mail.cooldown-seconds", 30));
    }

    public int maxLength() {
        return Math.max(20, plugin.getConfig().getInt("mail.max-length", 800));
    }

    public boolean onCooldown(UUID id) {
        Long until = cooldown.get(id);
        return until != null && until > System.currentTimeMillis();
    }

    public int cooldownLeft(UUID id) {
        Long until = cooldown.get(id);
        if (until == null) return 0;
        return (int) Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
    }

    public Letter send(Player from, UUID toId, String toName, String body) {
        Letter letter = new Letter(nextId++, from.getUniqueId(), from.getName(),
                toId, toName, body.trim(), System.currentTimeMillis(), false);
        letters.put(letter.id, letter);
        cooldown.put(from.getUniqueId(), System.currentTimeMillis() + cooldownSec() * 1000L);
        save();

        Player online = Bukkit.getPlayer(toId);
        if (online != null && online.isOnline()) {
            deliver(online, letter, true);
        }
        return letter;
    }

    public List<Letter> inbox(UUID playerId) {
        List<Letter> out = new ArrayList<>();
        for (Letter l : letters.values()) {
            if (l.toId.equals(playerId)) out.add(l);
        }
        out.sort(Comparator.comparingLong((Letter l) -> l.createdAt).reversed());
        if (out.size() > 36) return out.subList(0, 36);
        return out;
    }

    public Letter get(int id) {
        return letters.get(id);
    }

    public ItemStack toBook(Letter letter) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        String title = "Письмо от " + letter.fromName;
        if (title.length() > 32) title = title.substring(0, 32);
        meta.setTitle(title);
        meta.setAuthor(letter.fromName);
        meta.setGeneration(BookMeta.Generation.ORIGINAL);
        String header = "§5✦ Письмо\n§8от §d" + letter.fromName + "\n§8 \n§0";
        List<String> pages = paginate(header + letter.body);
        for (String page : pages) {
            meta.addPage(page);
        }
        book.setItemMeta(meta);
        return book;
    }

    public void openBook(Player player, Letter letter) {
        player.openBook(toBook(letter));
    }

    private void deliver(Player player, Letter letter, boolean announce) {
        ItemStack book = toBook(letter);
        var leftover = player.getInventory().addItem(book);
        letter.delivered = true;
        save();
        if (announce) {
            Msg.ok(player, "Тебе письмо от §d" + letter.fromName);
            Msg.tip(player, "Книга в инвентаре · /почта — ящик");
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) player.openBook(book);
        }, leftover.isEmpty() ? 8L : 25L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        List<Letter> pending = new ArrayList<>();
        for (Letter l : letters.values()) {
            if (l.toId.equals(e.getPlayer().getUniqueId()) && !l.delivered) pending.add(l);
        }
        if (pending.isEmpty()) return;
        pending.sort(Comparator.comparingLong(l -> l.createdAt));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = e.getPlayer();
            if (!p.isOnline()) return;
            for (Letter l : pending) {
                if (!p.isOnline()) return;
                deliver(p, l, false);
            }
            Msg.ok(p, "Пока тебя не было: §d" + pending.size() + " §fписьм"
                    + (pending.size() == 1 ? "о" : "а"));
        }, 50L);
    }

    public void save() {
        FileConfiguration conf = new YamlConfiguration();
        conf.set("next-id", nextId);
        for (Letter l : letters.values()) {
            String path = "letters." + l.id;
            conf.set(path + ".from", l.fromId.toString());
            conf.set(path + ".from-name", l.fromName);
            conf.set(path + ".to", l.toId.toString());
            conf.set(path + ".to-name", l.toName);
            conf.set(path + ".body", l.body);
            conf.set(path + ".at", l.createdAt);
            conf.set(path + ".delivered", l.delivered);
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            conf.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Cannot save letters: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration conf = YamlConfiguration.loadConfiguration(file);
        nextId = Math.max(1, conf.getInt("next-id", 1));
        ConfigurationSection root = conf.getConfigurationSection("letters");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                Letter l = new Letter(
                        id,
                        UUID.fromString(s.getString("from")),
                        s.getString("from-name", "?"),
                        UUID.fromString(s.getString("to")),
                        s.getString("to-name", "?"),
                        s.getString("body", ""),
                        s.getLong("at"),
                        s.getBoolean("delivered")
                );
                letters.put(id, l);
                nextId = Math.max(nextId, id + 1);
            } catch (Exception ignored) {
            }
        }
    }

    private static List<String> paginate(String text) {
        List<String> pages = new ArrayList<>();
        if (text == null) text = "";
        int i = 0;
        while (i < text.length() && pages.size() < 50) {
            int end = Math.min(text.length(), i + 240);
            if (end < text.length()) {
                int nl = text.lastIndexOf('\n', end);
                if (nl > i + 80) end = nl;
            }
            pages.add(text.substring(i, end).stripLeading());
            i = end;
        }
        if (pages.isEmpty()) pages.add(" ");
        return pages;
    }
}
