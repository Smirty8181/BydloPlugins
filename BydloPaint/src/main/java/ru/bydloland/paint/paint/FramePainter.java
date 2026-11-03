package ru.bydloland.paint.paint;

import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import ru.bydloland.paint.BydloPaintPlugin;
import ru.bydloland.paint.Keys;
import ru.bydloland.paint.service.CanvasService;

public final class FramePainter {

    private final BydloPaintPlugin plugin;
    private final CanvasService canvases;
    private final TileStore store;

    public FramePainter(BydloPaintPlugin plugin, CanvasService canvases, TileStore store) {
        this.plugin = plugin;
        this.canvases = canvases;
        this.store = store;
    }

    public boolean tryPaint(Player player, ItemStack dye, int brush) {
        RayTraceResult hit = player.getWorld().rayTrace(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                6.0,
                FluidCollisionMode.NEVER,
                true,
                0.0,
                e -> e instanceof ItemFrame
        );
        if (hit == null || hit.getHitEntity() == null) return false;
        Entity ent = hit.getHitEntity();
        if (!(ent instanceof ItemFrame frame)) return false;

        ItemStack mapItem = frame.getItem();
        if (!canvases.isPaintingTile(mapItem) && !canvases.isCanvas(mapItem)) {
            return false;
        }
        if (!(mapItem.getItemMeta() instanceof MapMeta meta) || meta.getMapView() == null) {
            return false;
        }

        Keys keys = canvases.keys();
        var pdc = meta.getPersistentDataContainer();
        String paintingId = pdc.get(keys.paintingId, PersistentDataType.STRING);
        Integer tileX = pdc.get(keys.tileX, PersistentDataType.INTEGER);
        Integer tileY = pdc.get(keys.tileY, PersistentDataType.INTEGER);
        if (paintingId == null || tileX == null || tileY == null) {
            player.sendMessage(plugin.msg("only-canvas"));
            return true; // consume interaction
        }

        int[] uv = mapUv(player, frame);
        if (uv == null) return true;

        byte color = MapColors.fromDye(dye.getType());
        String key = TileStore.key(paintingId, tileX, tileY);
        store.setPixel(key, uv[0], uv[1], color, brush);

        MapView view = meta.getMapView();
        ensureRenderer(view, key);
        store.bumpAndPush(view, frame.getLocation());
        return true;
    }

    private void ensureRenderer(MapView view, String key) {
        boolean has = false;
        for (var r : view.getRenderers()) {
            if (r instanceof TileStore.StoredRenderer) {
                has = true;
                break;
            }
        }
        if (!has) {
            byte bg = MapColors.background(plugin.getConfig().getInt("background-color", 34));
            store.attachRenderer(view, key, bg);
        }
    }

    /**
     * Approximate map UV (0-127) from player look vs item frame.
     */
    private static int[] mapUv(Player player, ItemFrame frame) {
        Vector eye = player.getEyeLocation().toVector();
        Vector dir = player.getEyeLocation().getDirection().normalize();
        Vector origin = frame.getLocation().toVector();
        Vector normal = frame.getFacing().getDirection();

        // Plane of the frame
        double denom = normal.dot(dir);
        if (Math.abs(denom) < 1e-6) return null;
        double t = normal.dot(origin.clone().subtract(eye)) / denom;
        if (t < 0 || t > 6) return null;
        Vector hit = eye.clone().add(dir.clone().multiply(t));
        Vector local = hit.clone().subtract(origin);

        // Build tangent axes on frame plane
        Vector up = new Vector(0, 1, 0);
        Vector right = up.clone().crossProduct(normal).normalize();
        if (right.lengthSquared() < 1e-6) {
            right = new Vector(1, 0, 0);
        }
        up = normal.clone().crossProduct(right).normalize();

        double u = local.dot(right); // -0.5..0.5 roughly
        double v = local.dot(up);

        // Item frame face is ~1 block; map is centered
        int x = (int) Math.floor((u + 0.5) * 128.0);
        int y = (int) Math.floor((0.5 - v) * 128.0);
        if (x < 0 || y < 0 || x >= 128 || y >= 128) {
            x = Math.max(0, Math.min(127, x));
            y = Math.max(0, Math.min(127, y));
        }
        return new int[]{x, y};
    }
}
