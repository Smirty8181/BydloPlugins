package ru.bydloland.prison.service;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import ru.bydloland.prison.BydloPrisonPlugin;
import ru.bydloland.prison.model.PrisonRecord;
import ru.bydloland.prison.pig.PrisonPigManager;
import ru.bydloland.prison.util.DurationParser;

import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PrisonService {

    private final BydloPrisonPlugin plugin;
    private final PrisonStorage storage;
    private final PrisonPigManager pigs;
    private final MessageService messages;
    private final Set<UUID> activeSession = ConcurrentHashMap.newKeySet();
    private final Set<UUID> actionCooldown = ConcurrentHashMap.newKeySet();

    public PrisonService(BydloPrisonPlugin plugin, PrisonStorage storage,
                         PrisonPigManager pigs, MessageService messages) {
        this.plugin = plugin;
        this.storage = storage;
        this.pigs = pigs;
        this.messages = messages;
    }

    public boolean isImprisoned(UUID uuid) {
        if (uuid == null) return false;
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.hasPermission("bydloprison.bypass")) {
            return false;
        }
        return storage.isImprisoned(uuid);
    }

    public boolean isImprisoned(Player player) {
        return player != null && isImprisoned(player.getUniqueId());
    }

    public PrisonRecord get(UUID uuid) {
        return storage.get(uuid);
    }

    public PrisonStorage storage() {
        return storage;
    }

    /**
     * Imprison player. durationRaw like 5м / 2ч / навсегда.
     * @return error message or null on success
     */
    public String imprison(UUID uuid, String name, String durationRaw, String reason, String by) {
        OptionalLong expire = DurationParser.parseExpireAt(durationRaw, System.currentTimeMillis());
        if (expire.isEmpty()) {
            return messages.get("invalid-duration");
        }
        long now = System.currentTimeMillis();
        PrisonRecord existing = storage.get(uuid);
        if (existing != null && !existing.isExpired(now)) {
            // refresh / overwrite
            pigs.removeExisting(existing);
        }

        PrisonRecord record = new PrisonRecord(uuid, name, reason, by, now, expire.getAsLong());
        storage.put(record);

        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            applyImprisonment(online, record, true);
        }
        return null;
    }

    public void applyImprisonment(Player player, PrisonRecord record, boolean notify) {
        if (player.hasPermission("bydloprison.bypass")) {
            return;
        }
        record.setName(player.getName());
        Pig pig = pigs.spawnOrLoad(record, player);
        storage.put(record);
        if (pig == null) {
            plugin.getLogger().warning("Failed to spawn prison pig for " + player.getName());
            return;
        }

        player.closeInventory();
        player.setGameMode(GameMode.SPECTATOR);
        player.setSpectatorTarget(pig);
        player.teleport(pig.getLocation());
        activeSession.add(player.getUniqueId());

        if (notify) {
            long rem = record.remainingMs(System.currentTimeMillis());
            String time = DurationParser.formatRemaining(rem, messages.color(messages.raw("permanent")));
            player.sendMessage(messages.prefix() + messages.apply("ban-notify", player.getName(), time, record.reason()));
        }
    }

    public boolean release(UUID uuid, boolean notifyPlayer) {
        PrisonRecord record = storage.get(uuid);
        if (record == null) {
            return false;
        }
        pigs.removeExisting(record);
        storage.remove(uuid);
        activeSession.remove(uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.setSpectatorTarget(null);
            player.setGameMode(GameMode.SURVIVAL);
            player.teleport(pigs.releaseLocation());
            if (notifyPlayer) {
                player.sendMessage(messages.prefix() + messages.get("unbanned"));
            }
        }
        return true;
    }

    public void handleJoin(Player player) {
        UUID uuid = player.getUniqueId();
        PrisonRecord record = storage.get(uuid);
        if (record == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (record.isExpired(now)) {
            release(uuid, true);
            return;
        }
        // Delay one tick so client finishes login
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (!storage.isImprisoned(uuid)) return;
            applyImprisonment(player, record, false);
        });
    }

    public void handleQuit(Player player) {
        activeSession.remove(player.getUniqueId());
        // Keep pig in world for persistence; will be respawned cleanly on join
        PrisonRecord record = storage.get(player.getUniqueId());
        if (record != null) {
            storage.put(record);
        }
    }

    public void tickKeep() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isImprisoned(player)) continue;
            PrisonRecord record = storage.get(player.getUniqueId());
            if (record == null) continue;
            if (record.isExpired(now)) continue;

            Pig pig = pigs.findPig(record);
            if (pig == null || pig.isDead()) {
                pig = pigs.spawnOrLoad(record, player);
                storage.put(record);
            }
            if (pig == null) continue;

            // Freeze pig
            pig.setAI(false);
            pig.setVelocity(pig.getVelocity().zero());
            pig.teleport(pigs.prisonLocation());

            if (player.getGameMode() != GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SPECTATOR);
            }
            if (player.getSpectatorTarget() == null || !player.getSpectatorTarget().getUniqueId().equals(pig.getUniqueId())) {
                player.setSpectatorTarget(pig);
            }
        }
    }

    public void tickExpiry() {
        long now = System.currentTimeMillis();
        for (PrisonRecord record : List.copyOf(storage.all())) {
            if (record.isExpired(now)) {
                release(record.uuid(), true);
            }
        }
    }

    public void shutdown() {
        // Leave pigs; they are persistent. Clear spectator links.
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (activeSession.contains(player.getUniqueId())) {
                player.setSpectatorTarget(null);
            }
        }
        activeSession.clear();
    }

    public void denyAction(Player player, String messageKey) {
        UUID id = player.getUniqueId();
        if (actionCooldown.contains(id)) return;
        actionCooldown.add(id);
        Bukkit.getScheduler().runTaskLater(plugin, () -> actionCooldown.remove(id), 40L);

        PrisonRecord record = storage.get(id);
        long rem = record == null ? -1 : record.remainingMs(System.currentTimeMillis());
        player.sendMessage(messages.prefix() + messages.withTime(messageKey, rem));
    }

    public boolean isCommandAllowed(String label) {
        List<String> allowed = plugin.getConfig().getStringList("allowed-commands");
        if (allowed == null || allowed.isEmpty()) return false;
        String l = label.toLowerCase(Locale.ROOT);
        if (l.startsWith("/")) l = l.substring(1);
        for (String a : allowed) {
            if (a == null) continue;
            String x = a.toLowerCase(Locale.ROOT);
            if (x.startsWith("/")) x = x.substring(1);
            if (x.equals(l)) return true;
        }
        return false;
    }

    public PrisonPigManager pigs() {
        return pigs;
    }

    public MessageService messages() {
        return messages;
    }
}
