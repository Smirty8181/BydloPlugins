package ru.bydloland.prison.model;

import java.util.UUID;

/** Persistent ban / imprisonment record. expiresAtMs = -1 means permanent. */
public final class PrisonRecord {

    private final UUID uuid;
    private String name;
    private String reason;
    private String bannedBy;
    private long bannedAtMs;
    private long expiresAtMs;
    private UUID pigUuid;
    private String world;
    private double x;
    private double y;
    private double z;

    public PrisonRecord(UUID uuid, String name, String reason, String bannedBy,
                        long bannedAtMs, long expiresAtMs) {
        this.uuid = uuid;
        this.name = name;
        this.reason = reason == null ? "—" : reason;
        this.bannedBy = bannedBy == null ? "console" : bannedBy;
        this.bannedAtMs = bannedAtMs;
        this.expiresAtMs = expiresAtMs;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String reason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String bannedBy() {
        return bannedBy;
    }

    public long bannedAtMs() {
        return bannedAtMs;
    }

    public long expiresAtMs() {
        return expiresAtMs;
    }

    public void setExpiresAtMs(long expiresAtMs) {
        this.expiresAtMs = expiresAtMs;
    }

    public boolean isPermanent() {
        return expiresAtMs < 0;
    }

    public boolean isExpired(long nowMs) {
        return !isPermanent() && nowMs >= expiresAtMs;
    }

    public long remainingMs(long nowMs) {
        if (isPermanent()) return -1;
        return Math.max(0, expiresAtMs - nowMs);
    }

    public UUID pigUuid() {
        return pigUuid;
    }

    public void setPigUuid(UUID pigUuid) {
        this.pigUuid = pigUuid;
    }

    public String world() {
        return world;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public void setLocation(String world, double x, double y, double z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
