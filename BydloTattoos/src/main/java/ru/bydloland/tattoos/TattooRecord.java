package ru.bydloland.tattoos;

import java.util.List;
import java.util.UUID;

public record TattooRecord(String id, TattooSlot slot, UUID artist, long time, String pngB64, List<Integer> colors) {
    public TattooRecord(String id, TattooSlot slot, UUID artist, long time) {
        this(id, slot, artist, time, null, List.of());
    }

    public TattooRecord {
        colors = colors == null ? List.of() : List.copyOf(colors);
    }

    public boolean custom() {
        return "custom".equalsIgnoreCase(id) && pngB64 != null && !pngB64.isBlank();
    }
}
