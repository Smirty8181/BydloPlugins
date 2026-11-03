package ru.bydloland.prison.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.plugin.EventExecutor;
import ru.bydloland.prison.BydloPrisonPlugin;
import ru.bydloland.prison.service.PrisonService;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Soft integrations:
 * - Cancel ban-related kicks and convert online targets into prison
 * - Reflectively listen LiteBans / LibertyBans / AdvancedBan / BanManager punish events when present
 */
public final class ExternalBanHook {

    private ExternalBanHook() {}

    public static void register(BydloPrisonPlugin plugin, PrisonService prison) {
        var cfg = plugin.getConfig().getConfigurationSection("integrations");
        boolean intercept = cfg == null || cfg.getBoolean("intercept-kicks", true);

        if (intercept) {
            Bukkit.getPluginManager().registerEvents(new Listener() {
                @org.bukkit.event.EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
                public void onKick(PlayerKickEvent event) {
                    if (!plugin.getConfig().getBoolean("integrations.intercept-kicks", true)) return;
                    Player player = event.getPlayer();
                    String reason = "";
                    try {
                        Object comp = event.getClass().getMethod("reason").invoke(event);
                        if (comp != null) {
                            reason = strip(comp.toString());
                        }
                    } catch (Throwable ignored) {}
                    String legacy = "";
                    try {
                        legacy = strip(event.getReason());
                    } catch (Throwable ignored) {}
                    String all = (reason + " " + legacy).toLowerCase(Locale.ROOT);
                    if (!looksLikeBan(all)) return;

                    // Already our prison? still cancel kick
                    if (prison.isImprisoned(player)) {
                        event.setCancelled(true);
                        return;
                    }

                    event.setCancelled(true);
                    String banReason = legacy.isBlank() ? reason : legacy;
                    if (banReason.isBlank()) banReason = "Бан";
                    // Default duration permanent when kicked by external ban without duration
                    String err = prison.imprison(player.getUniqueId(), player.getName(), "навсегда", banReason, "external");
                    if (err != null) {
                        plugin.getLogger().warning("Failed to imprison after kick intercept: " + err);
                    } else {
                        plugin.getLogger().info("Intercepted ban kick for " + player.getName() + " → prison");
                    }
                }
            }, plugin);
        }

        if (cfg == null || cfg.getBoolean("litebans", true)) {
            tryHookLiteBans(plugin, prison);
        }
        if (cfg == null || cfg.getBoolean("libertybans", true)) {
            tryHookBukkitEvent(plugin, prison,
                    "space.arim.libertybans.api.event.PostPunishEvent",
                    "LibertyBans");
        }
        if (cfg == null || cfg.getBoolean("advancedban", true)) {
            tryHookBukkitEvent(plugin, prison,
                    "me.leoko.advancedban.bukkit.event.PunishmentEvent",
                    "AdvancedBan");
        }
        if (cfg == null || cfg.getBoolean("banmanager", true)) {
            tryHookBukkitEvent(plugin, prison,
                    "me.confuser.banmanager.bukkit.api.events.PlayerBannedEvent",
                    "BanManager");
        }
    }

    private static boolean looksLikeBan(String text) {
        return text.contains("ban")
                || text.contains("бан")
                || text.contains("забан")
                || text.contains("blacklist")
                || text.contains("permanent")
                || text.contains("навсегда")
                || text.contains("you are banned")
                || text.contains("вы забанены");
    }

    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    private static void tryHookLiteBans(BydloPrisonPlugin plugin, PrisonService prison) {
        if (Bukkit.getPluginManager().getPlugin("LiteBans") == null) return;
        try {
            Class<?> eventsCl = Class.forName("litebans.api.Events");
            Object events = eventsCl.getMethod("get").invoke(null);
            Class<?> listenerCl = Class.forName("litebans.api.Events$Listener");
            Object listener = java.lang.reflect.Proxy.newProxyInstance(
                    listenerCl.getClassLoader(),
                    new Class[]{listenerCl},
                    (proxy, method, args) -> {
                        if ("broadcastBan".equals(method.getName()) && args != null && args.length >= 1) {
                            handleLiteBanBroadcast(plugin, prison, args[0]);
                        }
                        return null;
                    });
            eventsCl.getMethod("register", listenerCl).invoke(events, listener);
            plugin.getLogger().info("Hooked LiteBans Events");
        } catch (Throwable t) {
            plugin.getLogger().log(Level.INFO, "LiteBans hook skipped: " + t.getMessage());
        }
    }

    private static void handleLiteBanBroadcast(BydloPrisonPlugin plugin, PrisonService prison, Object entry) {
        try {
            UUID uuid = null;
            String name = null;
            String reason = "LiteBans";
            long until = -1L;
            for (Method m : entry.getClass().getMethods()) {
                String n = m.getName();
                if (m.getParameterCount() != 0) continue;
                Object v = m.invoke(entry);
                if (("getUuid".equals(n) || "uuid".equals(n)) && v instanceof UUID u) uuid = u;
                if (("getName".equals(n) || "getPlayerName".equals(n)) && v instanceof String s) name = s;
                if ("getReason".equals(n) && v instanceof String s) reason = s;
                if (("getUntil".equals(n) || "until".equals(n)) && v instanceof Number num) until = num.longValue();
            }
            if (uuid == null) return;
            final UUID fUuid = uuid;
            final String fName = name != null ? name : "unknown";
            final String fReason = reason;
            final long fUntil = until;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(fUuid);
                String duration = fUntil <= 0 ? "навсегда" : Math.max(1, (fUntil - System.currentTimeMillis()) / 1000) + "с";
                prison.imprison(fUuid, p != null ? p.getName() : fName, duration, fReason, "LiteBans");
            });
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "LiteBans imprison failed: " + t.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void tryHookBukkitEvent(BydloPrisonPlugin plugin, PrisonService prison, String className, String label) {
        try {
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(className);
            EventExecutor exec = (listener, event) -> handleGenericPunish(plugin, prison, event, label);
            Bukkit.getPluginManager().registerEvent(eventClass, new Listener() {}, EventPriority.MONITOR, exec, plugin, true);
            plugin.getLogger().info("Hooked " + label + " event " + className);
        } catch (ClassNotFoundException e) {
            // plugin not present
        } catch (Throwable t) {
            plugin.getLogger().log(Level.INFO, label + " hook skipped: " + t.getMessage());
        }
    }

    private static void handleGenericPunish(BydloPrisonPlugin plugin, PrisonService prison, Event event, String label) {
        try {
            UUID uuid = null;
            String name = null;
            String reason = label;
            long durationMs = -1;

            for (Method m : event.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;
                String n = m.getName().toLowerCase(Locale.ROOT);
                Object v;
                try {
                    v = m.invoke(event);
                } catch (Throwable ignored) {
                    continue;
                }
                if (v == null) continue;
                if (uuid == null && v instanceof UUID u && (n.contains("uuid") || n.contains("unique"))) {
                    uuid = u;
                }
                if (name == null && v instanceof String s && (n.contains("name") || n.contains("player"))) {
                    name = s;
                }
                if (v instanceof String s && n.contains("reason")) {
                    reason = s;
                }
                if (v instanceof Number num && (n.contains("duration") || n.contains("until") || n.contains("end"))) {
                    long val = num.longValue();
                    if (n.contains("until") || n.contains("end")) {
                        durationMs = val <= 0 ? -1 : Math.max(0, val - System.currentTimeMillis());
                    } else {
                        // assume seconds if small, ms if large
                        durationMs = val > 10_000_000L ? val : val * 1000L;
                    }
                }
                if (v instanceof Player p) {
                    uuid = p.getUniqueId();
                    name = p.getName();
                }
                // nested punishment object
                if (n.contains("punishment") || n.contains("ban")) {
                    uuid = firstUuid(v, uuid);
                    name = firstString(v, name, "name", "player");
                    reason = firstString(v, reason, "reason");
                }
            }
            if (uuid == null) return;
            final UUID fUuid = uuid;
            final String fName = name != null ? name : "unknown";
            final String fReason = reason;
            final String duration = durationMs < 0 ? "навсегда"
                    : Math.max(1, durationMs / 1000) + "с";
            Bukkit.getScheduler().runTask(plugin, () ->
                    prison.imprison(fUuid, fName, duration, fReason, label));
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, label + " punish handle failed: " + t.getMessage());
        }
    }

    private static UUID firstUuid(Object obj, UUID fallback) {
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;
                Object v = m.invoke(obj);
                if (v instanceof UUID u) return u;
            }
        } catch (Throwable ignored) {}
        return fallback;
    }

    private static String firstString(Object obj, String fallback, String... nameHints) {
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;
                String n = m.getName().toLowerCase(Locale.ROOT);
                boolean ok = false;
                for (String h : nameHints) {
                    if (n.contains(h)) { ok = true; break; }
                }
                if (!ok) continue;
                Object v = m.invoke(obj);
                if (v instanceof String s && !s.isBlank()) return s;
            }
        } catch (Throwable ignored) {}
        return fallback;
    }
}
