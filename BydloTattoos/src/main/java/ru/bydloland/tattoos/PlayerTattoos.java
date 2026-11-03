package ru.bydloland.tattoos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayerTattoos {
    public static final int CURRENT_REV = 3;

    public String originalValue;
    public String originalSignature;
    public String variant = "CLASSIC";
    public int composeRev = 0;
    public final List<TattooRecord> tattoos = new ArrayList<>();

    public boolean hasSlot(TattooSlot slot) {
        for (TattooRecord rec : tattoos) {
            if (rec.slot() == slot) return true;
        }
        return false;
    }

    public void removeSlot(TattooSlot slot) {
        tattoos.removeIf(r -> r.slot() == slot);
    }

    public void add(String id, TattooSlot slot, UUID artist, String pngB64, List<Integer> colors) {
        removeSlot(slot);
        tattoos.add(new TattooRecord(id, slot, artist, System.currentTimeMillis(), pngB64, colors));
    }

    public void addCustom(TattooSlot slot, UUID artist, String pngB64) {
        add("custom", slot, artist, pngB64, List.of());
    }
}
