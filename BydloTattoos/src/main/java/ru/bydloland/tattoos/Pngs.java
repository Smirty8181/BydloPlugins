package ru.bydloland.tattoos;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

public final class Pngs {
    private Pngs() {
    }

    public static String encode(BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception ex) {
            return null;
        }
    }

    public static BufferedImage decode(String b64) {
        if (b64 == null || b64.isBlank()) return null;
        try {
            return ensureArgb(ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(b64))));
        } catch (Exception ex) {
            return null;
        }
    }

    public static BufferedImage ensureArgb(BufferedImage src) {
        if (src == null) return null;
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                out.setRGB(x, y, src.getRGB(x, y));
            }
        }
        return out;
    }

    public static BufferedImage scaleNearest(BufferedImage src, int w, int h) {
        src = ensureArgb(src);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        if (src == null || w <= 0 || h <= 0) return out;
        int sw = src.getWidth();
        int sh = src.getHeight();
        for (int y = 0; y < h; y++) {
            int sy = Math.min(sh - 1, y * sh / h);
            for (int x = 0; x < w; x++) {
                int sx = Math.min(sw - 1, x * sw / w);
                out.setRGB(x, y, src.getRGB(sx, sy));
            }
        }
        return out;
    }

    /** Paste 1:1. Never scale up — one stamp pixel stays one skin pixel. Shrink only if larger. */
    public static BufferedImage placeInSlot(BufferedImage src, int w, int h) {
        src = ensureArgb(src);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        if (src == null || w <= 0 || h <= 0) return out;
        int sw = src.getWidth();
        int sh = src.getHeight();
        if (sw > w || sh > h) {
            return fitCenter(src, w, h);
        }
        for (int y = 0; y < sh && y < h; y++) {
            for (int x = 0; x < sw && x < w; x++) {
                out.setRGB(x, y, src.getRGB(x, y));
            }
        }
        return out;
    }

    public static BufferedImage fitCenter(BufferedImage src, int w, int h) {
        src = ensureArgb(src);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        if (src == null || w <= 0 || h <= 0) return out;
        int sw = src.getWidth();
        int sh = src.getHeight();
        double scale = Math.min(w / (double) sw, h / (double) sh);
        int nw = Math.max(1, (int) Math.round(sw * scale));
        int nh = Math.max(1, (int) Math.round(sh * scale));
        int ox = (w - nw) / 2;
        int oy = (h - nh) / 2;
        BufferedImage scaled = (nw == sw && nh == sh) ? src : scaleNearest(src, nw, nh);
        for (int y = 0; y < nh; y++) {
            for (int x = 0; x < nw; x++) {
                int dx = ox + x;
                int dy = oy + y;
                if (dx < 0 || dy < 0 || dx >= w || dy >= h) continue;
                out.setRGB(dx, dy, scaled.getRGB(x, y));
            }
        }
        return out;
    }
}
