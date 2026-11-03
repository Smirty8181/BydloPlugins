package ru.bydloland.classes;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class ClassService {

    public enum Source { SELF, ADMIN, SYSTEM }

    public enum Result {
        OK, UNKNOWN, COOLDOWN, NOT_SELECTABLE, SAME
    }

    private final BydloClassesPlugin plugin;
    private final Map<String, ClassDefinition> classes = new LinkedHashMap<>();
    private final Map<UUID, String> playerClass = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSelfChange = new ConcurrentHashMap<>();
    private File playersFile;
    private FileConfiguration playersCfg;
    private String defaultClass = "bomzh";
    private String scaleMode = "both";
    private String scaleCommand = "scale %scale% %player%";
    private int effectRefreshTicks = 100;
    private boolean assignDefaultOnJoin = true;
    private long selfCooldownMs = TimeUnit.DAYS.toMillis(2);

    public ClassService(BydloClassesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        defaultClass = cfg.getString("settings.default-class", "bomzh").toLowerCase(Locale.ROOT);
        scaleMode = cfg.getString("settings.scale-mode", "both").toLowerCase(Locale.ROOT);
        scaleCommand = cfg.getString("settings.scale-command", "scale %scale% %player%");
        effectRefreshTicks = Math.max(20, cfg.getInt("settings.effect-refresh-ticks", 100));
        assignDefaultOnJoin = cfg.getBoolean("settings.assign-default-on-join", true);
        selfCooldownMs = Math.max(0L, cfg.getLong("settings.self-change-cooldown-ms", TimeUnit.DAYS.toMillis(2)));

        classes.clear();
        ConfigurationSection section = cfg.getConfigurationSection("classes");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection c = section.getConfigurationSection(id);
                if (c == null) continue;
                ClassDefinition def = ClassDefinition.from(id.toLowerCase(Locale.ROOT), c);
                classes.put(def.id(), def);
            }
        }

        playersFile = new File(plugin.getDataFolder(), "players.yml");
        if (!playersFile.exists()) {
            try {
                playersFile.getParentFile().mkdirs();
                playersFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Cannot create players.yml: " + e.getMessage());
            }
        }
        playersCfg = YamlConfiguration.loadConfiguration(playersFile);
        playerClass.clear();
        lastSelfChange.clear();

        ConfigurationSection ps = playersCfg.getConfigurationSection("players");
        if (ps != null) {
            for (String key : ps.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    if (ps.isConfigurationSection(key)) {
                        ConfigurationSection row = ps.getConfigurationSection(key);
                        String cls = row.getString("class", defaultClass);
                        if ("human".equalsIgnoreCase(cls)) cls = defaultClass;
                        playerClass.put(uuid, cls.toLowerCase(Locale.ROOT));
                        lastSelfChange.put(uuid, row.getLong("last-self-change", 0L));
                    } else {
                        // старый формат: uuid: classId
                        String cls = ps.getString(key, defaultClass);
                        if ("human".equalsIgnoreCase(cls)) cls = defaultClass;
                        playerClass.put(uuid, cls.toLowerCase(Locale.ROOT));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        plugin.getLogger().info("Loaded " + classes.size() + " classes: " + String.join(", ", classes.keySet())
                + " | self CD=" + formatDuration(selfCooldownMs));
    }

    public void savePlayers() {
        if (playersCfg == null || playersFile == null) return;
        playersCfg.set("players", null);
        for (Map.Entry<UUID, String> e : playerClass.entrySet()) {
            String path = "players." + e.getKey();
            playersCfg.set(path + ".class", e.getValue());
            playersCfg.set(path + ".last-self-change", lastSelfChange.getOrDefault(e.getKey(), 0L));
        }
        try {
            playersCfg.save(playersFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Cannot save players.yml: " + e.getMessage());
        }
    }

    public String defaultClassId() {
        return classes.containsKey(defaultClass) ? defaultClass : classes.keySet().stream().findFirst().orElse("bomzh");
    }

    public boolean assignDefaultOnJoin() {
        return assignDefaultOnJoin;
    }

    public int effectRefreshTicks() {
        return effectRefreshTicks;
    }

    public long selfCooldownMs() {
        return selfCooldownMs;
    }

    public Map<String, ClassDefinition> classes() {
        return Collections.unmodifiableMap(classes);
    }

    public ClassDefinition get(String id) {
        if (id == null) return null;
        return classes.get(id.toLowerCase(Locale.ROOT));
    }

    public String getPlayerClassId(UUID uuid) {
        return playerClass.get(uuid);
    }

    public ClassDefinition getPlayerClass(Player player) {
        String id = playerClass.get(player.getUniqueId());
        if (id == null) return null;
        return get(id);
    }

    public boolean hasFlag(Player player, String flag) {
        ClassDefinition def = getPlayerClass(player);
        return def != null && def.flags().contains(flag.toLowerCase(Locale.ROOT));
    }

    public Set<String> classIds() {
        return classes.keySet();
    }

    /** Сколько мс осталось до следующей самостоятельной смены. 0 = можно. */
    public long remainingCooldownMs(UUID uuid) {
        long last = lastSelfChange.getOrDefault(uuid, 0L);
        if (last <= 0L || selfCooldownMs <= 0L) return 0L;
        long left = (last + selfCooldownMs) - System.currentTimeMillis();
        return Math.max(0L, left);
    }

    public boolean canSelfChange(Player player) {
        return remainingCooldownMs(player.getUniqueId()) <= 0L;
    }

    public Result setClass(Player player, String classId, boolean announce, Source source) {
        ClassDefinition def = get(classId);
        if (def == null) return Result.UNKNOWN;

        if (source == Source.SELF) {
            if (!def.selectable()) return Result.NOT_SELECTABLE;
            long left = remainingCooldownMs(player.getUniqueId());
            if (left > 0L) return Result.COOLDOWN;
        }

        String current = playerClass.get(player.getUniqueId());
        if (current != null && current.equals(def.id()) && source == Source.SELF) {
            return Result.SAME;
        }

        String oldId = playerClass.put(player.getUniqueId(), def.id());
        if (source == Source.SELF) {
            lastSelfChange.put(player.getUniqueId(), System.currentTimeMillis());
        }
        savePlayers();

        clearClassEffects(player, oldId);
        apply(player, def);

        if (announce) {
            player.sendMessage("§d[БыдлоЛенд] §fРаса: " + ColorUtil.legacy(def.display()));
            if (source == Source.SELF && selfCooldownMs > 0L) {
                player.sendMessage("§7Следующая смена через §f" + formatDuration(selfCooldownMs));
            }
        }
        return Result.OK;
    }

    /** Совместимость: как SELF без проверки — не использовать. */
    public boolean setClass(Player player, String classId, boolean announce) {
        return setClass(player, classId, announce, Source.ADMIN) == Result.OK;
    }

    public void clearClass(Player player) {
        String old = playerClass.remove(player.getUniqueId());
        lastSelfChange.remove(player.getUniqueId());
        savePlayers();
        clearClassEffects(player, old);
        resetScale(player);
    }

    public void applyCurrent(Player player) {
        ClassDefinition def = getPlayerClass(player);
        if (def == null) {
            if (assignDefaultOnJoin) {
                setClass(player, defaultClassId(), false, Source.SYSTEM);
            }
            return;
        }
        apply(player, def);
    }

    public void apply(Player player, ClassDefinition def) {
        applyScale(player, def.scale());
        applyEffects(player, def);
        refreshPermissions(player, def);
    }

    public void refreshEffectsOnly(Player player) {
        ClassDefinition def = getPlayerClass(player);
        if (def != null) applyEffects(player, def);
    }

    private void clearClassEffects(Player player, String oldId) {
        ClassDefinition old = get(oldId);
        if (old == null) return;
        for (ClassDefinition.EffectSpec spec : old.effects()) {
            PotionEffectType type = spec.type();
            if (type != null) player.removePotionEffect(type);
        }
    }

    private void applyEffects(Player player, ClassDefinition def) {
        for (ClassDefinition.EffectSpec spec : def.effects()) {
            PotionEffectType type = spec.type();
            if (type == null) continue;
            player.addPotionEffect(new PotionEffect(
                    type,
                    PotionEffect.INFINITE_DURATION,
                    spec.amplifier(),
                    spec.ambient(),
                    spec.particles(),
                    spec.icon()
            ));
        }
    }

    private void applyScale(Player player, double scale) {
        double clamped = Math.max(0.0625, Math.min(16.0, scale));
        // Attribute — основной надёжный способ (1.21+)
        if ("attribute".equals(scaleMode) || "both".equals(scaleMode) || !"command".equals(scaleMode)) {
            try {
                var attr = player.getAttribute(org.bukkit.attribute.Attribute.SCALE);
                if (attr != null) attr.setBaseValue(clamped);
            } catch (Throwable t) {
                plugin.getLogger().warning("SCALE attribute failed: " + t.getMessage());
            }
        }
        // NoobScale: только от имени игрока (консоль у них не принимает /scale)
        if (("command".equals(scaleMode) || "both".equals(scaleMode))
                && Bukkit.getPluginManager().isPluginEnabled("NoobScale")) {
            try {
                player.performCommand("scale " + clamped);
            } catch (Throwable ignored) {
            }
        }
    }

    private void resetScale(Player player) {
        applyScale(player, 1.0);
    }

    private void refreshPermissions(Player player, ClassDefinition def) {
        plugin.clearAttachments(player);
        for (String flag : def.flags()) {
            plugin.attachPermission(player, "bydloclasses.flag." + flag);
        }
        plugin.attachPermission(player, "bydloclasses.class." + def.id());
    }

    public List<String> tabCompleteClasses(String token) {
        String t = token == null ? "" : token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String id : classes.keySet()) {
            if (id.startsWith(t)) out.add(id);
        }
        return out;
    }

    public static String formatDuration(long ms) {
        if (ms <= 0L) return "сейчас";
        long totalSec = ms / 1000L;
        long days = totalSec / 86400L;
        long hours = (totalSec % 86400L) / 3600L;
        long mins = (totalSec % 3600L) / 60L;
        if (days > 0) return days + "д " + hours + "ч";
        if (hours > 0) return hours + "ч " + mins + "м";
        return Math.max(1, mins) + "м";
    }
}
