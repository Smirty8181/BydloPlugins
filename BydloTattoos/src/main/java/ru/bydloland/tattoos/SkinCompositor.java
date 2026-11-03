package ru.bydloland.tattoos;

import java.awt.image.BufferedImage;
import java.util.List;

public final class SkinCompositor {
    private SkinCompositor() {
    }

    public static BufferedImage apply(BufferedImage base, List<Stamp> stamps, boolean slim) {
        BufferedImage skin = to64(base);
        for (Stamp stamp : stamps) {
            TattooSlot slot = stamp.slot();
            int w = slot.width(slim);
            BufferedImage ink = inkify(stamp.image(), w, slot.h);
            blit(skin, ink, slot.innerX, slot.innerY);
            blit(skin, ink, slot.overlayX, slot.overlayY);
        }
        return skin;
    }

    public static BufferedImage inkify(BufferedImage src, int w, int h) {
        BufferedImage placed = Pngs.placeInSlot(src, w, h);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        boolean[][] solid = new boolean[h][w];
        int count = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = placed.getRGB(x, y);
                int a = (p >>> 24) & 0xFF;
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                if (a < 32 || (r == 0 && g == 0 && b == 0)) continue;
                r = Math.max(0, r - 12);
                g = Math.max(0, g - 12);
                b = Math.max(0, b - 12);
                out.setRGB(x, y, (255 << 24) | (r << 16) | (g << 8) | b);
                solid[y][x] = true;
                count++;
            }
        }
        // Tiny marks (tear under the eye) must stay 1–2 px — outline would balloon them.
        if (count >= 8) {
            int outline = 0xFF1A1210;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (solid[y][x]) continue;
                    if (cardinal(solid, x, y)) out.setRGB(x, y, outline);
                }
            }
        }
        return out;
    }

    private static boolean cardinal(boolean[][] solid, int x, int y) {
        int h = solid.length;
        int w = solid[0].length;
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] n : d) {
            int nx = x + n[0];
            int ny = y + n[1];
            if (nx >= 0 && ny >= 0 && nx < w && ny < h && solid[ny][nx]) return true;
        }
        return false;
    }

    private static void blit(BufferedImage dest, BufferedImage src, int x, int y) {
        int w = src.getWidth();
        int h = src.getHeight();
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                int p = src.getRGB(xx, yy);
                if (((p >>> 24) & 0xFF) < 16) continue;
                int dx = x + xx;
                int dy = y + yy;
                if (dx < 0 || dy < 0 || dx >= dest.getWidth() || dy >= dest.getHeight()) continue;
                dest.setRGB(dx, dy, p);
            }
        }
    }

    public static BufferedImage to64(BufferedImage src) {
        src = Pngs.ensureArgb(src);
        if (src.getWidth() == 64 && src.getHeight() == 64) {
            BufferedImage copy = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) copy.setRGB(x, y, src.getRGB(x, y));
            }
            return copy;
        }
        BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        var g = out.createGraphics();
        g.drawImage(src, 0, 0, 64, Math.min(32, src.getHeight()), 0, 0, 64, Math.min(32, src.getHeight()), null);
        if (src.getHeight() <= 32) {
            g.drawImage(src, 16, 48, 20, 64, 4, 16, 8, 32, null);
            g.drawImage(src, 32, 48, 36, 64, 44, 16, 48, 32, null);
        }
        g.dispose();
        return out;
    }

    public record Stamp(TattooSlot slot, BufferedImage image) {
    }
}
