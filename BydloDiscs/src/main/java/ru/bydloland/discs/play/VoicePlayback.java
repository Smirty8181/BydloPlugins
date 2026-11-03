package ru.bydloland.discs.play;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import ru.bydloland.discs.BydloDiscsPlugin;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;

/** Plays s16le 48kHz mono PCM at a jukebox via Simple Voice Chat. */
public final class VoicePlayback {
    private static final int FRAME = 960;

    private final BydloDiscsPlugin plugin;
    private final AtomicBoolean hooked = new AtomicBoolean(false);
    private volatile Object serverApi;
    private final Map<String, Object> players = new ConcurrentHashMap<>();

    public VoicePlayback(BydloDiscsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean ready() {
        return serverApi != null;
    }

    public void tryHook() {
        if (serverApi != null) {
            hooked.set(true);
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("voicechat") == null) {
            plugin.getLogger().warning("voicechat missing — discs won't play audio");
            return;
        }
        try {
            // SVC PluginManager.init() runs once during voicechat enable.
            // BydloDiscs (softdepend) enables later, so initialize() is never called.
            // Grab VoicechatServerApiImpl.instance() directly.
            attachServerApi();
            if (serverApi != null) {
                if (!hooked.getAndSet(true)) {
                    plugin.getLogger().info("Hooked Simple Voice Chat for disc playback");
                }
                return;
            }

            Class<?> apiClass = Class.forName("de.maxhenkel.voicechat.api.BukkitVoicechatService");
            var reg = Bukkit.getServicesManager().getRegistration(apiClass);
            if (reg == null) {
                Bukkit.getScheduler().runTaskLater(plugin, this::tryHook, 40L);
                return;
            }
            if (!hooked.get()) {
                Object service = reg.getProvider();
                Class<?> pluginIface = Class.forName("de.maxhenkel.voicechat.api.VoicechatPlugin");
                Object vcPlugin = java.lang.reflect.Proxy.newProxyInstance(
                        pluginIface.getClassLoader(),
                        new Class[]{pluginIface},
                        (proxy, method, args) -> {
                            String name = method.getName();
                            if (name.equals("getPluginId")) return "bydlodiscs";
                            if (name.equals("initialize") && args != null && args.length == 1) {
                                serverApi = args[0];
                                return null;
                            }
                            if (method.getReturnType().equals(boolean.class)) return false;
                            if (method.getReturnType().equals(void.class)) return null;
                            return null;
                        });
                service.getClass().getMethod("registerPlugin", pluginIface).invoke(service, vcPlugin);
            }
            attachServerApi();
            if (serverApi != null) {
                if (!hooked.getAndSet(true)) {
                    plugin.getLogger().info("Hooked Simple Voice Chat for disc playback");
                }
            } else {
                plugin.getLogger().warning("SVC present but VoicechatServerApi not ready yet");
                Bukkit.getScheduler().runTaskLater(plugin, this::tryHook, 40L);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "SVC hook failed: " + t.getMessage(), t);
        }
    }

    private void attachServerApi() {
        if (serverApi != null) return;
        try {
            Class<?> impl = Class.forName("de.maxhenkel.voicechat.plugins.impl.VoicechatServerApiImpl");
            Object api = impl.getMethod("instance").invoke(null);
            if (api != null) {
                serverApi = api;
            }
        } catch (Throwable ignored) {
        }
    }

    public static String locKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public boolean isActive(Location loc) {
        return players.containsKey(locKey(loc));
    }

    public void silenceVanillaAround(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        double dist = plugin.getConfig().getDouble("play-distance", 48.0);
        double distSq = dist * dist;
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= distSq) {
                p.stopSound(SoundCategory.RECORDS);
                p.stopSound(SoundCategory.MUSIC);
            }
        }
    }

    public void tickSilence() {
        for (String key : players.keySet()) {
            Location loc = fromKey(key);
            if (loc != null) silenceVanillaAround(loc);
        }
    }

    public static Location fromKey(String key) {
        int zSep = key.lastIndexOf(':');
        if (zSep <= 0) return null;
        int ySep = key.lastIndexOf(':', zSep - 1);
        if (ySep <= 0) return null;
        int xSep = key.lastIndexOf(':', ySep - 1);
        if (xSep <= 0) return null;
        try {
            var world = Bukkit.getWorld(key.substring(0, xSep));
            if (world == null) return null;
            int x = Integer.parseInt(key.substring(xSep + 1, ySep));
            int y = Integer.parseInt(key.substring(ySep + 1, zSep));
            int z = Integer.parseInt(key.substring(zSep + 1));
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void stop(Location loc) {
        stopKey(locKey(loc));
    }

    public void stopAll() {
        for (String k : players.keySet()) stopKey(k);
    }

    private void stopKey(String key) {
        Object player = players.remove(key);
        if (player == null) return;
        try {
            player.getClass().getMethod("stopPlaying").invoke(player);
        } catch (Throwable ignored) {
        }
    }

    public boolean play(Location loc, Path pcmPath, String title) {
        Object api = serverApi;
        if (api == null) {
            tryHook();
            api = serverApi;
        }
        if (api == null) {
            plugin.getLogger().warning("play aborted: VoicechatServerApi is null (SVC not hooked)");
            return false;
        }
        if (pcmPath == null || !Files.isRegularFile(pcmPath)) {
            plugin.getLogger().warning("play aborted: pcm missing: " + pcmPath);
            return false;
        }

        stop(loc);
        try {
            short[] pcm = toShorts(Files.readAllBytes(pcmPath));
            if (pcm.length == 0) return false;

            Object level = api.getClass().getMethod("fromServerLevel", Object.class).invoke(api, loc.getWorld());
            Object position = api.getClass()
                    .getMethod("createPosition", double.class, double.class, double.class)
                    .invoke(api, loc.getBlockX() + 0.5, loc.getBlockY() + 0.5, loc.getBlockZ() + 0.5);

            Method createChannel = null;
            for (Method m : api.getClass().getMethods()) {
                if (m.getName().equals("createLocationalAudioChannel") && m.getParameterCount() == 3) {
                    createChannel = m;
                    break;
                }
            }
            if (createChannel == null) throw new IllegalStateException("createLocationalAudioChannel missing");
            Object channel = createChannel.invoke(api, UUID.randomUUID(), level, position);

            float dist = (float) plugin.getConfig().getDouble("play-distance", 48.0);
            try {
                channel.getClass().getMethod("setDistance", float.class).invoke(channel, dist);
            } catch (Throwable ignored) {
            }

            Object encoder = api.getClass().getMethod("createEncoder").invoke(api);

            AtomicInteger offset = new AtomicInteger(0);
            Supplier<short[]> supplier = () -> {
                int off = offset.get();
                if (off >= pcm.length) return null;
                int len = Math.min(FRAME, pcm.length - off);
                short[] frame = new short[FRAME];
                System.arraycopy(pcm, off, frame, 0, len);
                offset.addAndGet(len);
                return frame;
            };

            Method createPlayer = null;
            for (Method m : api.getClass().getMethods()) {
                if (m.getName().equals("createAudioPlayer") && m.getParameterCount() == 3
                        && Supplier.class.isAssignableFrom(m.getParameterTypes()[2])) {
                    createPlayer = m;
                    break;
                }
            }
            Object audioPlayer;
            if (createPlayer != null) {
                audioPlayer = createPlayer.invoke(api, channel, encoder, supplier);
            } else {
                Method m = find(api.getClass(), "createAudioPlayer", 3);
                audioPlayer = m.invoke(api, channel, encoder, pcm);
            }

            String key = locKey(loc);
            try {
                audioPlayer.getClass().getMethod("setOnStopped", Runnable.class)
                        .invoke(audioPlayer, (Runnable) () -> players.remove(key, audioPlayer));
            } catch (Throwable ignored) {
            }
            audioPlayer.getClass().getMethod("startPlaying").invoke(audioPlayer);
            players.put(key, audioPlayer);
            silenceVanillaAround(loc);
            plugin.getLogger().info("Playing '" + title + "' at " + key);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "play failed: " + t.getMessage(), t);
            return false;
        }
    }

    private static Method find(Class<?> c, String name, int n) {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == n) return m;
        }
        throw new IllegalStateException(name);
    }

    private static short[] toShorts(byte[] raw) {
        ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        short[] out = new short[raw.length / 2];
        for (int i = 0; i < out.length; i++) out[i] = bb.getShort();
        return out;
    }
}
