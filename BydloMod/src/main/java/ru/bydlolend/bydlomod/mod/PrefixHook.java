package ru.bydlolend.bydlomod.mod;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;

/** Soft-hook BydloPrefixes via reflection. */
public final class PrefixHook {
    private PrefixHook() {}

    public static boolean available() {
        Plugin pl = Bukkit.getPluginManager().getPlugin("BydloPrefixes");
        return pl != null && pl.isEnabled();
    }

    public static String getPrefixId(UUID uuid) {
        try {
            Object service = service();
            if (service == null) return null;
            Object def = service.getClass().getMethod("getPlayerPrefix", UUID.class).invoke(service, uuid);
            if (def == null) return null;
            return String.valueOf(def.getClass().getMethod("id").invoke(def));
        } catch (Throwable t) {
            return null;
        }
    }

    /** Creates or updates prefix definition (always refresh tab text). */
    public static void ensurePrefix(String id, String tabText) {
        try {
            Object service = service();
            if (service == null) return;
            service.getClass()
                    .getMethod("createOrUpdate", String.class, String.class, Material.class, List.class)
                    .invoke(service, id, tabText, Material.WITHER_SKELETON_SKULL,
                            List.of("<gray>Чёрная метка за варн", "<dark_gray>Иконка в TAB"));
        } catch (Throwable ignored) {
        }
    }

    public static boolean setPrefix(UUID uuid, String prefixId) {
        try {
            Object service = service();
            if (service == null) return false;
            Object ok = service.getClass().getMethod("setPrefix", UUID.class, String.class)
                    .invoke(service, uuid, prefixId);
            return ok instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void clearPrefix(UUID uuid) {
        try {
            Object service = service();
            if (service == null) return;
            service.getClass().getMethod("clearPrefix", UUID.class).invoke(service, uuid);
        } catch (Throwable ignored) {
        }
    }

    private static Object service() throws Exception {
        Plugin pl = Bukkit.getPluginManager().getPlugin("BydloPrefixes");
        if (pl == null || !pl.isEnabled()) return null;
        return pl.getClass().getMethod("service").invoke(pl);
    }
}
