package ru.bydlolend.bydlomod.adminlock;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.MerchantInventory;
import ru.bydlolend.bydlomod.BydloMod;
import ru.bydlolend.bydlomod.util.Msg;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminLockService implements Listener {
    public enum Feature {
        END("Энд"),
        NETHER("Ад"),
        VILLAGER_TRADES("Торги с жителями");

        private final String title;

        Feature(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    public record ParseResult(boolean success, boolean clear, long unlockAt, String error) {}

    private final BydloMod plugin;
    private final File file;
    private final Map<Feature, Boolean> locked = new EnumMap<>(Feature.class);
    private final Map<Feature, Long> unlockAt = new EnumMap<>(Feature.class);
    private final Map<UUID, Feature> pendingTimerInput = new ConcurrentHashMap<>();
    private final Map<UUID, Long> portalKickCooldown = new ConcurrentHashMap<>();

    public AdminLockService(BydloMod plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "admin-locks.yml");
        for (Feature feature : Feature.values()) {
            locked.put(feature, false);
            unlockAt.put(feature, 0L);
        }
        load();
    }

    public boolean isLocked(Feature feature) {
        return locked.getOrDefault(feature, false);
    }

    public long unlockAt(Feature feature) {
        return unlockAt.getOrDefault(feature, 0L);
    }

    public boolean hasPendingTimer(Player player) {
        return pendingTimerInput.containsKey(player.getUniqueId());
    }

    public Feature pendingTimerFeature(Player player) {
        return pendingTimerInput.get(player.getUniqueId());
    }

    public void promptTimerInput(Player player, Feature feature) {
        pendingTimerInput.put(player.getUniqueId(), feature);
        player.closeInventory();
        Msg.header(player, "Таймер: " + feature.title());
        Msg.tip(player, "Напиши в чат время авторазблокировки.");
        Msg.tip(player, "Примеры: &f30m&7, &f2h&7, &f1d");
        Msg.tip(player, "Или точное время: &f23:30 &7/ &f21.07 18:00");
        Msg.tip(player, "Очистить таймер: &fclear");
        Msg.tip(player, "Отмена: &fcancel");
        Msg.footer(player);
    }

    public void clearPrompt(Player player) {
        pendingTimerInput.remove(player.getUniqueId());
    }

    public void setLocked(Feature feature, boolean value, CommandSource source) {
        locked.put(feature, value);
        if (!value) {
            unlockAt.put(feature, 0L);
        }
        save();
        if (value) {
            if (feature == Feature.END || feature == Feature.NETHER) {
                evacuateDimension(feature);
            }
            broadcast("&c" + feature.title() + " &fзакрыт" + actorSuffix(source));
        } else {
            broadcast("&a" + feature.title() + " &fоткрыт" + actorSuffix(source));
        }
    }

    public void setUnlockAt(Feature feature, long when, CommandSource source) {
        unlockAt.put(feature, when);
        locked.put(feature, true);
        save();
        if (feature == Feature.END || feature == Feature.NETHER) {
            evacuateDimension(feature);
        }
        broadcast("&e" + feature.title() + " &fзакрыт до &d" + formatDateTime(when) + actorSuffix(source));
    }

    public void clearUnlockAt(Feature feature, CommandSource source) {
        unlockAt.put(feature, 0L);
        save();
        if (source != null) {
            Msg.ok(source.sender(), "Таймер для \"" + feature.title() + "\" очищен");
        }
    }

    public void startExpiryTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            List<Feature> expired = new ArrayList<>();
            for (Feature feature : Feature.values()) {
                long until = unlockAt(feature);
                if (isLocked(feature) && until > 0 && now >= until) {
                    expired.add(feature);
                }
            }
            for (Feature feature : expired) {
                locked.put(feature, false);
                unlockAt.put(feature, 0L);
                broadcast("&a" + feature.title() + " &fавтоматически открыт");
            }
            if (!expired.isEmpty()) {
                save();
            }
        }, 20L, 20L * 15);
    }

    public String statusLine(Feature feature) {
        if (!isLocked(feature)) {
            return "§aОткрыто";
        }
        long until = unlockAt(feature);
        if (until <= 0) {
            return "§cЗакрыто";
        }
        return "§cЗакрыто до §f" + formatDateTime(until);
    }

    public String remainingLine(Feature feature) {
        if (!isLocked(feature)) {
            return "§7Таймер: §8нет";
        }
        long until = unlockAt(feature);
        if (until <= 0) {
            return "§7Таймер: §8не задан";
        }
        return "§7Осталось: §f" + formatLeft(until - System.currentTimeMillis());
    }

    public ParseResult parseUnlockInput(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ParseResult(false, false, 0L, "Пустой ввод");
        }
        String s = ChatColor.stripColor(raw).trim();
        if (s.equalsIgnoreCase("cancel") || s.equalsIgnoreCase("отмена")) {
            return new ParseResult(false, false, 0L, "Отменено");
        }
        if (s.equalsIgnoreCase("clear") || s.equalsIgnoreCase("off") || s.equalsIgnoreCase("none")
                || s.equalsIgnoreCase("убрать") || s.equalsIgnoreCase("очистить")) {
            return new ParseResult(true, true, 0L, null);
        }

        long duration = ru.bydlolend.bydlomod.mod.ModerationService.parseExpiry(s);
        if (duration > System.currentTimeMillis()) {
            return new ParseResult(true, false, duration, null);
        }

        LocalDateTime dt = parseDateTime(s);
        if (dt != null) {
            long when = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            if (when <= System.currentTimeMillis()) {
                return new ParseResult(false, false, 0L, "Время уже прошло");
            }
            return new ParseResult(true, false, when, null);
        }

        return new ParseResult(false, false, 0L, "Формат: 30m, 2h, 1d, 23:30 или 21.07 18:00");
    }

    @EventHandler
    public void onTimerInput(AsyncPlayerChatEvent event) {
        Feature feature = pendingTimerInput.remove(event.getPlayer().getUniqueId());
        if (feature == null) {
            return;
        }
        event.setCancelled(true);
        ParseResult parsed = parseUnlockInput(event.getMessage());
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (parsed.error() != null && parsed.error().equals("Отменено")) {
                Msg.tip(player, "Ввод таймера отменён");
                return;
            }
            if (!parsed.success()) {
                Msg.err(player, parsed.error());
                return;
            }
            if (parsed.clear()) {
                clearUnlockAt(feature, new CommandSource(player));
                return;
            }
            setUnlockAt(feature, parsed.unlockAt(), new CommandSource(player));
            Msg.ok(player, feature.title() + " будет открыт в " + formatDateTime(parsed.unlockAt()));
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPortal(PlayerPortalEvent event) {
        Feature byCause = featureByTeleportCause(event.getCause());
        if (byCause != null && denyFeature(event.getPlayer(), byCause)) {
            event.setCancelled(true);
            event.setTo(event.getFrom());
            return;
        }
        World to = event.getTo() == null ? null : event.getTo().getWorld();
        if (denyDimension(event.getPlayer(), to)) {
            event.setCancelled(true);
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityPortal(EntityPortalEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        World to = event.getTo() == null ? null : event.getTo().getWorld();
        // Если пункт назначения ещё не рассчитан — смотрим мир, ИЗ которого идут
        if (to == null) {
            World from = event.getFrom() == null ? null : event.getFrom().getWorld();
            if (from != null && from.getEnvironment() == World.Environment.NORMAL) {
                // обычный мир → портал почти всегда ведёт в ад (если не энд-рамка)
                // точнее определим по блоку портала ниже в EntityPortalEnterEvent
            }
        }
        if (denyDimension(player, to)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPortalEnter(EntityPortalEnterEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (canBypass(player)) {
            return;
        }
        Material type = event.getLocation().getBlock().getType();
        Feature feature = null;
        if (type == Material.NETHER_PORTAL) {
            feature = Feature.NETHER;
        } else if (type == Material.END_PORTAL) {
            feature = Feature.END;
        }
        if (feature == null || !isLocked(feature)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long last = portalKickCooldown.get(player.getUniqueId());
        if (last != null && now - last < 800L) {
            // всё равно выталкиваем без спама сообщений
            pushOutOfPortal(player);
            return;
        }
        portalKickCooldown.put(player.getUniqueId(), now);
        pushOutOfPortal(player);
        Msg.err(player, feature.title() + " сейчас закрыт");
    }

    private void pushOutOfPortal(Player player) {
        Location loc = player.getLocation().clone();
        org.bukkit.util.Vector away = loc.getDirection().setY(0);
        if (away.lengthSquared() < 0.01) {
            away = new org.bukkit.util.Vector(1, 0, 0);
        }
        away.normalize().multiply(-1.5);
        loc.add(away);
        loc.setY(loc.getY() + 0.3);
        player.setVelocity(away.multiply(0.35).setY(0.15));
        player.teleport(loc);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onTeleport(PlayerTeleportEvent event) {
        // PlayerPortalEvent тоже наследует TeleportEvent — не дублируем лишний раз,
        // но ловим команды /mvtp /tp и т.п.
        if (event instanceof PlayerPortalEvent) {
            return;
        }
        Feature byCause = featureByTeleportCause(event.getCause());
        if (byCause != null && denyFeature(event.getPlayer(), byCause)) {
            event.setCancelled(true);
            return;
        }
        World to = event.getTo() == null ? null : event.getTo().getWorld();
        World from = event.getFrom() == null ? null : event.getFrom().getWorld();
        if (to == null) {
            return;
        }
        if (from != null && from.getUID().equals(to.getUID())) {
            return;
        }
        if (denyDimension(event.getPlayer(), to)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        if (!isLocked(Feature.VILLAGER_TRADES) || canBypass(event.getPlayer())) {
            return;
        }
        Entity entity = event.getRightClicked();
        if (entity instanceof AbstractVillager) {
            event.setCancelled(true);
            Msg.err(event.getPlayer(), "Торги с жителями сейчас заблокированы");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTradeInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!isLocked(Feature.VILLAGER_TRADES) || canBypass(player)) {
            return;
        }
        Inventory inventory = event.getInventory();
        if (!(inventory instanceof MerchantInventory)) {
            return;
        }
        // Любой merchant-GUI (житель / странствующий торговец)
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, (Runnable) player::closeInventory);
        Msg.err(player, "Торги с жителями сейчас заблокированы");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTradeInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!isLocked(Feature.VILLAGER_TRADES) || canBypass(player)) {
            return;
        }
        if (!(event.getInventory() instanceof MerchantInventory)) {
            return;
        }
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, (Runnable) player::closeInventory);
        Msg.err(player, "Торги с жителями сейчас заблокированы");
    }

    /** @return true если вход запрещён и сообщение уже отправлено */
    private boolean denyDimension(Player player, World to) {
        Feature feature = featureByWorld(to);
        if (feature == null) {
            return false;
        }
        return denyFeature(player, feature);
    }

    private boolean denyFeature(Player player, Feature feature) {
        if (!isLocked(feature) || canBypass(player)) {
            return false;
        }
        Msg.err(player, feature.title() + " сейчас закрыт");
        return true;
    }

    private Feature featureByTeleportCause(PlayerTeleportEvent.TeleportCause cause) {
        if (cause == null) {
            return null;
        }
        return switch (cause) {
            case NETHER_PORTAL -> Feature.NETHER;
            case END_PORTAL, END_GATEWAY -> Feature.END;
            default -> null;
        };
    }

    public void save() {
        FileConfiguration conf = new YamlConfiguration();
        for (Feature feature : Feature.values()) {
            String base = "features." + feature.name().toLowerCase(Locale.ROOT);
            conf.set(base + ".locked", isLocked(feature));
            conf.set(base + ".unlockAt", unlockAt(feature));
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            conf.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Cannot save admin locks: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        FileConfiguration conf = YamlConfiguration.loadConfiguration(file);
        for (Feature feature : Feature.values()) {
            String base = "features." + feature.name().toLowerCase(Locale.ROOT);
            locked.put(feature, conf.getBoolean(base + ".locked", false));
            unlockAt.put(feature, conf.getLong(base + ".unlockAt", 0L));
        }
    }

    private void broadcast(String message) {
        String colored = plugin.prefix() + BydloMod.color(message);
        Bukkit.broadcastMessage(colored);
    }

    private String actorSuffix(CommandSource source) {
        if (source == null || source.sender() == null) {
            return "";
        }
        return BydloMod.color(" &8(" + source.sender().getName() + ")");
    }

    private void evacuateDimension(Feature feature) {
        World fallback = Bukkit.getWorld("world");
        if (fallback == null) {
            fallback = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        }
        if (fallback == null) {
            return;
        }
        Location target = fallback.getSpawnLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (canBypass(player)) {
                continue;
            }
            if (feature == featureByWorld(player.getWorld())) {
                player.teleport(target);
                Msg.tip(player, feature.title() + " закрыт. Ты возвращён в обычный мир.");
            }
        }
    }

    private Feature featureByWorld(World world) {
        if (world == null) {
            return null;
        }
        return switch (world.getEnvironment()) {
            case NETHER -> Feature.NETHER;
            case THE_END -> Feature.END;
            default -> {
                String name = world.getName().toLowerCase(Locale.ROOT);
                if (name.contains("nether") || name.endsWith("_nether")) {
                    yield Feature.NETHER;
                }
                if (name.contains("the_end") || name.endsWith("_the_end") || name.contains("end")) {
                    // не путать с "legend" и т.п. — только явные end-имена
                    if (name.contains("the_end") || name.endsWith("_end") || name.equals("end")) {
                        yield Feature.END;
                    }
                }
                yield null;
            }
        };
    }

    /** Обход отключён: иначе OP / право * спокойно проходят в закрытый Ад. */
    private boolean canBypass(Player player) {
        return false;
    }

    private static LocalDateTime parseDateTime(String raw) {
        try {
            LocalTime time = LocalTime.parse(raw, DateTimeFormatter.ofPattern("H:mm"));
            LocalDate date = LocalDate.now();
            LocalDateTime dt = LocalDateTime.of(date, time);
            if (dt.isBefore(LocalDateTime.now())) {
                dt = dt.plusDays(1);
            }
            return dt;
        } catch (DateTimeParseException ignored) {
        }
        try {
            var parsed = java.time.format.DateTimeFormatter.ofPattern("d.MM H:mm").parse(raw);
            LocalDate date = LocalDate.of(LocalDate.now().getYear(),
                    parsed.get(java.time.temporal.ChronoField.MONTH_OF_YEAR),
                    parsed.get(java.time.temporal.ChronoField.DAY_OF_MONTH));
            LocalTime time = LocalTime.of(
                    parsed.get(java.time.temporal.ChronoField.HOUR_OF_DAY),
                    parsed.get(java.time.temporal.ChronoField.MINUTE_OF_HOUR)
            );
            LocalDateTime dt = LocalDateTime.of(date, time);
            if (dt.isBefore(LocalDateTime.now())) {
                dt = dt.plusYears(1);
            }
            return dt;
        } catch (DateTimeParseException ignored) {
        }
        try {
            var parsed = java.time.format.DateTimeFormatter.ofPattern("dd.MM H:mm").parse(raw);
            LocalDate date = LocalDate.of(LocalDate.now().getYear(),
                    parsed.get(java.time.temporal.ChronoField.MONTH_OF_YEAR),
                    parsed.get(java.time.temporal.ChronoField.DAY_OF_MONTH));
            LocalTime time = LocalTime.of(
                    parsed.get(java.time.temporal.ChronoField.HOUR_OF_DAY),
                    parsed.get(java.time.temporal.ChronoField.MINUTE_OF_HOUR)
            );
            LocalDateTime dt = LocalDateTime.of(date, time);
            if (dt.isBefore(LocalDateTime.now())) {
                dt = dt.plusYears(1);
            }
            return dt;
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    public static String formatDateTime(long epochMs) {
        if (epochMs <= 0) {
            return "без таймера";
        }
        LocalDateTime dt = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(epochMs),
                ZoneId.systemDefault()
        );
        return dt.format(DateTimeFormatter.ofPattern("dd.MM HH:mm"));
    }

    public static String formatLeft(long ms) {
        if (ms <= 0) {
            return "0с";
        }
        long s = ms / 1000;
        long d = s / 86400;
        s %= 86400;
        long h = s / 3600;
        s %= 3600;
        long m = s / 60;
        s %= 60;
        StringBuilder b = new StringBuilder();
        if (d > 0) b.append(d).append("д ");
        if (h > 0) b.append(h).append("ч ");
        if (m > 0) b.append(m).append("м ");
        if (d == 0 && h == 0) b.append(s).append("с");
        return b.toString().trim();
    }

    public record CommandSource(org.bukkit.command.CommandSender sender) {}
}
