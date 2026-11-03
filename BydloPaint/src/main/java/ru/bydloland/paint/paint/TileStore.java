package ru.bydloland.paint.paint;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;
import ru.bydloland.paint.BydloPaintPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TileStore {

    private final BydloPaintPlugin plugin;
    private final Path root;
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();
    private final Map<Integer, String> mapIdToKey = new ConcurrentHashMap<>();
    private final Set<String> dirtyKeys = ConcurrentHashMap.newKeySet();
    private final Map<Integer, StoredRenderer> renderers = new ConcurrentHashMap<>();

    public TileStore(BydloPaintPlugin plugin) {
        this.plugin = plugin;
        this.root = plugin.getDataFolder().toPath().resolve("tiles");
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            plugin.getLogger().warning("Cannot create tiles dir: " + e.getMessage());
        }
        // flush dirty tiles every 2s off the hot paint path
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushDirty, 40L, 40L);
    }

    public static String key(String paintingId, int tileX, int tileY) {
        return paintingId + "_" + tileX + "_" + tileY;
    }

    public byte[] getOrCreate(String key, byte bg) {
        return cache.computeIfAbsent(key, k -> {
            Path file = root.resolve(k + ".bin");
            if (Files.isRegularFile(file)) {
                try {
                    byte[] data = Files.readAllBytes(file);
                    if (data.length == 128 * 128) return data;
                } catch (IOException ignored) {
                }
            }
            byte[] blank = new byte[128 * 128];
            Arrays.fill(blank, bg);
            return blank;
        });
    }

    public void bindMap(MapView view, String key) {
        mapIdToKey.put(view.getId(), key);
    }

    public String keyOf(MapView view) {
        return mapIdToKey.get(view.getId());
    }

    public void setPixel(String key, int x, int y, byte color, int brush) {
        byte[] data = getOrCreate(key, (byte) 0);
        int r = Math.max(1, brush);
        for (int dy = -r + 1; dy < r; dy++) {
            for (int dx = -r + 1; dx < r; dx++) {
                if (dx * dx + dy * dy >= r * r) continue;
                int px = x + dx;
                int py = y + dy;
                if (px < 0 || py < 0 || px >= 128 || py >= 128) continue;
                data[py * 128 + px] = color;
            }
        }
        dirtyKeys.add(key);
        // keep memory cache hot; disk flush is async
        cache.put(key, data);
    }

    public void flushDirty() {
        for (String key : dirtyKeys) {
            dirtyKeys.remove(key);
            byte[] data = cache.get(key);
            if (data == null) continue;
            Path file = root.resolve(key + ".bin");
            try {
                Files.write(file, data);
            } catch (IOException e) {
                plugin.getLogger().warning("Save tile failed " + key + ": " + e.getMessage());
                dirtyKeys.add(key);
            }
        }
    }

    public void flushAllSync() {
        for (String key : dirtyKeys) {
            byte[] data = cache.get(key);
            if (data == null) continue;
            try {
                Files.write(root.resolve(key + ".bin"), data);
            } catch (IOException ignored) {
            }
        }
        dirtyKeys.clear();
    }

    public void attachRenderer(MapView view, String key, byte bg) {
        byte[] data = getOrCreate(key, bg);
        bindMap(view, key);
        for (var r : view.getRenderers()) {
            view.removeRenderer(r);
        }
        StoredRenderer renderer = new StoredRenderer(this, key, data);
        view.addRenderer(renderer);
        renderers.put(view.getId(), renderer);
    }

    public void bumpAndPush(MapView view, Location near) {
        StoredRenderer sr = renderers.get(view.getId());
        if (sr == null) {
            for (var r : view.getRenderers()) {
                if (r instanceof StoredRenderer found) {
                    sr = found;
                    renderers.put(view.getId(), found);
                    break;
                }
            }
        }
        if (sr != null) {
            sr.bump();
        }
        // Force immediate client refresh (vanilla map ticks are slow ~seconds)
        double r2 = 48 * 48;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(near.getWorld())) continue;
            if (p.getLocation().distanceSquared(near) > r2) continue;
            try {
                p.sendMap(view);
            } catch (Throwable ignored) {
            }
        }
    }

    public static final class StoredRenderer extends org.bukkit.map.MapRenderer {
        private final TileStore store;
        private final String key;
        private byte[] local;
        private int version;
        private int drawnVersion = -1;

        public StoredRenderer(TileStore store, String key, byte[] data) {
            super(false); // shared image for all players — cheaper + pushable
            this.store = store;
            this.key = key;
            this.local = data;
            this.version = 0;
        }

        public void bump() {
            local = store.cache.getOrDefault(key, local);
            version++;
            drawnVersion = -1; // force next render
        }

        @Override
        public void render(MapView map, org.bukkit.map.MapCanvas canvas, org.bukkit.entity.Player player) {
            byte[] data = store.cache.getOrDefault(key, local);
            if (drawnVersion == version) return;
            // bulk write — much faster than setPixel x16384
            for (int y = 0; y < 128; y++) {
                int row = y * 128;
                for (int x = 0; x < 128; x++) {
                    canvas.setPixel(x, y, data[row + x]);
                }
            }
            local = data;
            drawnVersion = version;
        }
    }
}
