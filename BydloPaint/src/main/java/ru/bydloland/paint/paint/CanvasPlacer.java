package ru.bydloland.paint.paint;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import ru.bydloland.paint.BydloPaintPlugin;
import ru.bydloland.paint.service.CanvasService;

public final class CanvasPlacer {

    private final BydloPaintPlugin plugin;
    private final CanvasService canvases;
    private final TileStore store;

    public CanvasPlacer(BydloPaintPlugin plugin, CanvasService canvases, TileStore store) {
        this.plugin = plugin;
        this.canvases = canvases;
        this.store = store;
    }

    public boolean place(Player player, ItemStack canvas, Block clicked, BlockFace face) {
        if (face == BlockFace.UP || face == BlockFace.DOWN) {
            player.sendMessage(ColorUtilMsg.noSpace(plugin, 0, 0));
            return false;
        }

        int w = canvases.width(canvas);
        int h = canvases.height(canvas);
        String paintingId = canvases.paintingId(canvas);
        if (paintingId == null) return false;

        BlockFace right = rightOf(face);
        BlockFace up = BlockFace.UP;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Block support = clicked.getRelative(right, x).getRelative(up, y);
                Block air = support.getRelative(face);
                if (!air.getType().isAir() || !support.getType().isSolid()) {
                    player.sendMessage(plugin.msg("no-space")
                            .replace("%w%", String.valueOf(w))
                            .replace("%h%", String.valueOf(h)));
                    return false;
                }
                Location check = air.getLocation().add(0.5, 0.5, 0.5);
                boolean blocked = !air.getWorld()
                        .getNearbyEntities(check, 0.35, 0.35, 0.35, e -> e instanceof ItemFrame)
                        .isEmpty();
                if (blocked) {
                    player.sendMessage(plugin.msg("no-space")
                            .replace("%w%", String.valueOf(w))
                            .replace("%h%", String.valueOf(h)));
                    return false;
                }
            }
        }

        byte bg = MapColors.background(plugin.getConfig().getInt("background-color", 34));

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Block support = clicked.getRelative(right, x).getRelative(up, y);
                Location loc = centerOnFace(support, face);
                int tileY = (h - 1) - y;
                ItemStack mapItem = createTileMap(player, paintingId, x, tileY, w, h, bg);
                airSpawnFrame(loc, face, mapItem);
            }
        }

        player.sendMessage(plugin.msg("placed")
                .replace("%w%", String.valueOf(w))
                .replace("%h%", String.valueOf(h)));
        return true;
    }

    private void airSpawnFrame(Location loc, BlockFace face, ItemStack mapItem) {
        loc.getWorld().spawn(loc, ItemFrame.class, frame -> {
            frame.setFacingDirection(face, true);
            frame.setVisible(true);
            frame.setFixed(false);
            frame.setItem(mapItem, false);
            frame.setRotation(Rotation.NONE);
        });
    }

    private ItemStack createTileMap(Player player, String paintingId, int tileX, int tileY,
                                    int w, int h, byte bg) {
        MapView view = Bukkit.createMap(player.getWorld());
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);

        String key = TileStore.key(paintingId, tileX, tileY);
        store.attachRenderer(view, key, bg);

        ItemStack map = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) map.getItemMeta();
        meta.setMapView(view);
        meta.displayName(Component.text("Картина " + w + "×" + h)
                .decoration(TextDecoration.ITALIC, false));
        var pdc = meta.getPersistentDataContainer();
        pdc.set(canvases.keys().paintingId, PersistentDataType.STRING, paintingId);
        pdc.set(canvases.keys().tileX, PersistentDataType.INTEGER, tileX);
        pdc.set(canvases.keys().tileY, PersistentDataType.INTEGER, tileY);
        pdc.set(canvases.keys().width, PersistentDataType.INTEGER, w);
        pdc.set(canvases.keys().height, PersistentDataType.INTEGER, h);
        pdc.set(canvases.keys().canvas, PersistentDataType.BYTE, (byte) 1);
        map.setItemMeta(meta);
        return map;
    }

    private static Location centerOnFace(Block support, BlockFace face) {
        Location base = support.getLocation().add(0.5, 0.5, 0.5);
        Vector off = face.getDirection().multiply(0.53125);
        return base.add(off);
    }

    private static BlockFace rightOf(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case SOUTH -> BlockFace.EAST;
            case WEST -> BlockFace.SOUTH;
            case EAST -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }

    /** tiny helper to avoid unused warning if messages change */
    private static final class ColorUtilMsg {
        static String noSpace(BydloPaintPlugin plugin, int w, int h) {
            return plugin.msg("no-space")
                    .replace("%w%", String.valueOf(Math.max(w, 1)))
                    .replace("%h%", String.valueOf(Math.max(h, 1)));
        }
    }
}
