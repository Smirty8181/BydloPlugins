package ru.bydloland.tattoos;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class Players {
    private Players() {
    }

    public static Player byName(String name) {
        if (name == null || name.isBlank()) return null;
        String want = name.trim();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(want)) return p;
        }
        return null;
    }
}
