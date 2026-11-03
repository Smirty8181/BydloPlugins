package ru.bydloland.tattoos;

public enum TattooSlot {
    FACE("лицо", 8, 8, 40, 8, 8, 8),
    CHEST("грудь", 20, 20, 20, 36, 8, 12),
    BACK("спина", 32, 20, 32, 36, 8, 12),
    ARM_RIGHT("правая рука", 44, 20, 44, 36, 4, 12),
    ARM_LEFT("левая рука", 36, 52, 52, 52, 4, 12),
    LEG_RIGHT("правая нога", 4, 20, 4, 36, 4, 12),
    LEG_LEFT("левая нога", 20, 52, 4, 52, 4, 12);

    public final String title;
    public final int innerX;
    public final int innerY;
    public final int overlayX;
    public final int overlayY;
    public final int w;
    public final int h;

    TattooSlot(String title, int innerX, int innerY, int overlayX, int overlayY, int w, int h) {
        this.title = title;
        this.innerX = innerX;
        this.innerY = innerY;
        this.overlayX = overlayX;
        this.overlayY = overlayY;
        this.w = w;
        this.h = h;
    }

    public int width(boolean slim) {
        if (slim && (this == ARM_RIGHT || this == ARM_LEFT)) return 3;
        return w;
    }

    public static TattooSlot fromConfig(String raw) {
        if (raw == null) return null;
        try {
            return TattooSlot.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
