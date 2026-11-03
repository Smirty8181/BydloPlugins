package ru.bydlolend.bydlomod.mod;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/** Soft-hook BydloPrison via reflection. */
public final class PrisonHook {
    private PrisonHook() {}

    public static final class BanInfo {
        public final String reason;
        public final String bannedBy;
        public final long bannedAt;
        public final long expiresAt; // -1 permanent

        public BanInfo(String reason, String bannedBy, long bannedAt, long expiresAt) {
            this.reason = reason;
            this.bannedBy = bannedBy;
            this.bannedAt = bannedAt;
            this.expiresAt = expiresAt;
        }
    }

    public static BanInfo getBan(UUID uuid) {
        try {
            Plugin pl = Bukkit.getPluginManager().getPlugin("BydloPrison");
            if (pl == null || !pl.isEnabled()) return null;
            Object prison = pl.getClass().getMethod("prison").invoke(pl);
            Object rec = prison.getClass().getMethod("get", UUID.class).invoke(prison, uuid);
            if (rec == null) return null;
            String reason = String.valueOf(rec.getClass().getMethod("reason").invoke(rec));
            String by = String.valueOf(rec.getClass().getMethod("bannedBy").invoke(rec));
            long at = ((Number) rec.getClass().getMethod("bannedAtMs").invoke(rec)).longValue();
            long exp = ((Number) rec.getClass().getMethod("expiresAtMs").invoke(rec)).longValue();
            return new BanInfo(reason, by, at, exp);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isImprisoned(UUID uuid) {
        return getBan(uuid) != null;
    }
}
