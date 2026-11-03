package ru.bydloland.paint;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class Keys {
    public final NamespacedKey canvas;
    public final NamespacedKey width;
    public final NamespacedKey height;
    public final NamespacedKey paintingId;
    public final NamespacedKey tileX;
    public final NamespacedKey tileY;
    public final NamespacedKey sized;

    public Keys(Plugin plugin) {
        canvas = new NamespacedKey(plugin, "canvas");
        width = new NamespacedKey(plugin, "width");
        height = new NamespacedKey(plugin, "height");
        paintingId = new NamespacedKey(plugin, "painting_id");
        tileX = new NamespacedKey(plugin, "tile_x");
        tileY = new NamespacedKey(plugin, "tile_y");
        sized = new NamespacedKey(plugin, "sized");
    }
}
