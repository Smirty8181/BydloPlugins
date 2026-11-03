package ru.bydloland.tattoos;

import org.bukkit.Material;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public final class InkPalette {
    public static final Color BLACK = new Color("Чёрный", Material.BLACK_DYE, 0xFF111111);
    public static final Color GRAY = new Color("Серый", Material.GRAY_DYE, 0xFF4B5563);
    public static final Color WHITE = new Color("Белый", Material.WHITE_DYE, 0xFFF5F5F5);
    public static final Color RED = new Color("Красный", Material.RED_DYE, 0xFFB91C1C);
    public static final Color CRIMSON = new Color("Бордо", Material.NETHER_WART, 0xFF7F1D1D);
    public static final Color BROWN = new Color("Коричневый", Material.BROWN_DYE, 0xFF6B3F1F);
    public static final Color GOLD = new Color("Золотой", Material.GOLD_NUGGET, 0xFFD4A017);
    public static final Color BLUE = new Color("Синий", Material.BLUE_DYE, 0xFF1D4ED8);
    public static final Color CYAN = new Color("Бирюза", Material.CYAN_DYE, 0xFF0E7490);
    public static final Color GREEN = new Color("Зелёный", Material.GREEN_DYE, 0xFF166534);
    public static final Color PURPLE = new Color("Фиолет", Material.PURPLE_DYE, 0xFF6D28D9);
    public static final Color PINK = new Color("Розовый", Material.PINK_DYE, 0xFFDB2777);

    public static final List<Color> ALL = List.of(
            BLACK, GRAY, WHITE, RED, CRIMSON, BROWN, GOLD, BLUE, CYAN, GREEN, PURPLE, PINK
    );

    private InkPalette() {
    }

    public static Color byArgb(int argb) {
        for (Color c : ALL) {
            if (c.argb == argb) return c;
        }
        return BLACK;
    }

    public static BufferedImage paint(BufferedImage src, List<Integer> colors) {
        List<Integer> cols = colors == null ? List.of() : colors.stream().filter(c -> c != 0).toList();
        if (cols.isEmpty()) cols = List.of(BLACK.argb);
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                int a = (p >>> 24) & 0xFF;
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                if (a < 32 || (r == 0 && g == 0 && b == 0)) continue;
                float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
                int ink = pick(cols, lum);
                int ir = (ink >> 16) & 0xFF;
                int ig = (ink >> 8) & 0xFF;
                int ib = ink & 0xFF;
                float shade = 0.45f + 0.55f * (1f - lum);
                int nr = clamp((int) (ir * shade));
                int ng = clamp((int) (ig * shade));
                int nb = clamp((int) (ib * shade));
                out.setRGB(x, y, (255 << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        return out;
    }

    private static int pick(List<Integer> cols, float lum) {
        if (cols.size() == 1) return cols.get(0);
        if (cols.size() == 2) return lum < 0.45f ? cols.get(0) : cols.get(1);
        if (lum < 0.33f) return cols.get(0);
        if (lum < 0.66f) return cols.get(1);
        return cols.get(Math.min(2, cols.size() - 1));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public static String names(List<Integer> colors) {
        if (colors == null || colors.isEmpty()) return "чёрный";
        List<String> names = new ArrayList<>();
        for (int c : colors) names.add(byArgb(c).name);
        return String.join(" + ", names);
    }

    public record Color(String name, Material mat, int argb) {
    }
}
