package ru.bydloland.paint.paint;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.map.MapPalette;

import java.awt.Color;

@SuppressWarnings("deprecation")
public final class MapColors {
    private MapColors() {}

    public static byte fromDye(Material dye) {
        DyeColor dc = dyeColor(dye);
        if (dc == null) {
            return MapPalette.matchColor(Color.BLACK);
        }
        org.bukkit.Color c = dc.getColor();
        return MapPalette.matchColor(new Color(c.getRed(), c.getGreen(), c.getBlue()));
    }

    public static boolean isDye(Material m) {
        return dyeColor(m) != null;
    }

    public static DyeColor dyeColor(Material m) {
        String n = m.name();
        if (!n.endsWith("_DYE")) return null;
        try {
            return DyeColor.valueOf(n.substring(0, n.length() - 4));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static byte background(int configured) {
        return (byte) configured;
    }
}
