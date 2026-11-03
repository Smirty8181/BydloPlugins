package ru.bydlolend.bydlomod.mod;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import ru.bydlolend.bydlomod.BydloMod;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Мут микрофона Simple Voice Chat:
 * 1) PermissionAttachment voicechat.speak=false (надёжно)
 * 2) Reflection: cancel MicrophonePacketEvent + connection.setDisabled
 */
public final class VoiceMuteHook {
    private static final AtomicBoolean HOOKED = new AtomicBoolean(false);
    private static volatile Object serverApi;
    private static volatile boolean loggedCancelFail;
    private static volatile BydloMod pluginRef;
    private static final Map<UUID, PermissionAttachment> SPEAK_DENY = new ConcurrentHashMap<>();

    private VoiceMuteHook() {}

    public static void tryRegister(BydloMod plugin) {
        pluginRef = plugin;
        if (HOOKED.get()) {
            reapplyAll(plugin);
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("voicechat") == null) {
            plugin.getLogger().info("voicechat not found — /vmute uses speak-permission deny only");
            HOOKED.set(true);
            reapplyAll(plugin);
            return;
        }
        try {
            Class<?> apiClass = Class.forName("de.maxhenkel.voicechat.api.BukkitVoicechatService");
            var registration = Bukkit.getServicesManager().getRegistration(apiClass);
            if (registration == null) {
                plugin.getLogger().warning("BukkitVoicechatService not ready yet, retrying...");
                Bukkit.getScheduler().runTaskLater(plugin, () -> tryRegister(plugin), 40L);
                return;
            }
            Object service = registration.getProvider();
            Class<?> pluginIface = Class.forName("de.maxhenkel.voicechat.api.VoicechatPlugin");
            Object vcPlugin = java.lang.reflect.Proxy.newProxyInstance(
                    pluginIface.getClassLoader(),
                    new Class[]{pluginIface},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.equals("getPluginId")) return "bydlomod";
                        if (name.equals("registerEvents") && args != null && args.length == 1) {
                            registerEvents(plugin, args[0]);
                            return null;
                        }
                        if (name.equals("initialize") && args != null && args.length == 1) {
                            serverApi = args[0];
                            return null;
                        }
                        if (method.getReturnType().equals(boolean.class)) return false;
                        if (method.getReturnType().equals(void.class)) return null;
                        return null;
                    });
            Method registerPlugin = service.getClass().getMethod("registerPlugin", pluginIface);
            registerPlugin.invoke(service, vcPlugin);
            HOOKED.set(true);
            plugin.getLogger().info("Hooked Simple Voice Chat for /vmute (+ voicechat.speak deny)");
            reapplyAll(plugin);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Could not hook voicechat packets (speak-deny still works): " + t.getMessage(), t);
            HOOKED.set(true);
            reapplyAll(plugin);
        }
    }

    public static void reapplyAll(BydloMod plugin) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.moderation().isVmuted(p.getUniqueId())) {
                setDisabled(p.getUniqueId(), true);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerEvents(BydloMod plugin, Object eventRegistration) throws Exception {
        Class<?> micEvent = Class.forName("de.maxhenkel.voicechat.api.events.MicrophonePacketEvent");
        Class<?> connectedEvent = Class.forName("de.maxhenkel.voicechat.api.events.PlayerConnectedEvent");

        Method registerEvent = null;
        for (Method m : eventRegistration.getClass().getMethods()) {
            if (!m.getName().equals("registerEvent")) continue;
            Class<?>[] pts = m.getParameterTypes();
            if (pts.length >= 2 && pts[0] == Class.class && Consumer.class.isAssignableFrom(pts[1])) {
                registerEvent = m;
                if (pts.length == 2) break;
            }
        }
        if (registerEvent == null) throw new IllegalStateException("registerEvent not found");

        Consumer micHandler = (Object event) -> {
            try {
                UUID uuid = extractSenderUuid(event);
                if (uuid == null) return;
                if (!plugin.moderation().isVmuted(uuid)) return;
                cancelEvent(event);
                setDisabled(uuid, true);
            } catch (Throwable t) {
                if (!loggedCancelFail) {
                    loggedCancelFail = true;
                    plugin.getLogger().log(Level.WARNING, "vmute mic cancel failed: " + t.getMessage(), t);
                }
            }
        };

        Consumer connectHandler = (Object event) -> {
            try {
                UUID uuid = extractConnectedUuid(event);
                if (uuid == null) return;
                if (plugin.moderation().isVmuted(uuid)) {
                    setDisabled(uuid, true);
                }
            } catch (Throwable ignored) {
            }
        };

        if (registerEvent.getParameterCount() == 3) {
            registerEvent.invoke(eventRegistration, micEvent, micHandler, 0);
            registerEvent.invoke(eventRegistration, connectedEvent, connectHandler, 0);
        } else {
            registerEvent.invoke(eventRegistration, micEvent, micHandler);
            registerEvent.invoke(eventRegistration, connectedEvent, connectHandler);
        }
    }

    /** Включить/выключить голосовой мут для игрока. */
    public static void setDisabled(UUID uuid, boolean disabled) {
        applySpeakPermission(uuid, disabled);
        try {
            Object api = serverApi;
            if (api == null) return;
            Method getConn = api.getClass().getMethod("getConnectionOf", UUID.class);
            Object conn = getConn.invoke(api, uuid);
            if (conn == null) return;
            Method setDisabled = conn.getClass().getMethod("setDisabled", boolean.class);
            setDisabled.invoke(conn, disabled);
        } catch (Throwable ignored) {
        }
    }

    private static void applySpeakPermission(UUID uuid, boolean muted) {
        BydloMod plugin = pluginRef;
        if (plugin == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (muted) {
            if (player == null || !player.isOnline()) return;
            SPEAK_DENY.compute(uuid, (id, old) -> {
                if (old != null) {
                    try {
                        player.removeAttachment(old);
                    } catch (Throwable ignored) {
                    }
                }
                PermissionAttachment att = player.addAttachment(plugin);
                // Simple Voice Chat: без voicechat.speak нельзя говорить в микрофон
                att.setPermission("voicechat.speak", false);
                att.setPermission("voicechat.speech", false);
                return att;
            });
            player.recalculatePermissions();
        } else {
            PermissionAttachment att = SPEAK_DENY.remove(uuid);
            if (att != null && player != null) {
                try {
                    player.removeAttachment(att);
                    player.recalculatePermissions();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static UUID extractConnectedUuid(Object event) throws Exception {
        try {
            Object conn = event.getClass().getMethod("getConnection").invoke(event);
            if (conn != null) {
                try {
                    Object u = conn.getClass().getMethod("getPlayerUuid").invoke(conn);
                    if (u instanceof UUID uuid) return uuid;
                } catch (NoSuchMethodException ignored) {}
                Object player = conn.getClass().getMethod("getPlayer").invoke(conn);
                if (player != null) {
                    try {
                        Object u = player.getClass().getMethod("getUuid").invoke(player);
                        if (u instanceof UUID uuid) return uuid;
                    } catch (NoSuchMethodException ignored) {}
                    Object bukkit = player.getClass().getMethod("getPlayer").invoke(player);
                    if (bukkit instanceof Player p) return p.getUniqueId();
                }
            }
        } catch (NoSuchMethodException ignored) {}
        return null;
    }

    private static UUID extractSenderUuid(Object event) throws Exception {
        Method getConn = event.getClass().getMethod("getSenderConnection");
        Object conn = getConn.invoke(event);
        if (conn == null) return null;

        try {
            Object u = conn.getClass().getMethod("getPlayerUuid").invoke(conn);
            if (u instanceof UUID uuid) return uuid;
        } catch (NoSuchMethodException ignored) {}

        Method getPlayer = conn.getClass().getMethod("getPlayer");
        Object vcPlayer = getPlayer.invoke(conn);
        if (vcPlayer == null) return null;

        for (String mn : new String[]{"getUuid", "getUniqueId"}) {
            try {
                Object u = vcPlayer.getClass().getMethod(mn).invoke(vcPlayer);
                if (u instanceof UUID uuid) return uuid;
            } catch (NoSuchMethodException ignored) {}
        }

        try {
            Object bukkit = vcPlayer.getClass().getMethod("getPlayer").invoke(vcPlayer);
            if (bukkit instanceof Player p) return p.getUniqueId();
            if (bukkit != null) {
                try {
                    Object u = bukkit.getClass().getMethod("getUniqueId").invoke(bukkit);
                    if (u instanceof UUID uuid) return uuid;
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (NoSuchMethodException ignored) {}

        return null;
    }

    private static void cancelEvent(Object event) throws Exception {
        try {
            event.getClass().getMethod("cancel").invoke(event);
            return;
        } catch (NoSuchMethodException ignored) {}
        event.getClass().getMethod("setCancelled", boolean.class).invoke(event, true);
    }
}
