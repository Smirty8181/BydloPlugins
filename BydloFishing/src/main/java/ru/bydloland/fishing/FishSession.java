package ru.bydloland.fishing;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class FishSession {
    public enum Phase {WAIT, FIGHT}

    public final RodType rod;
    public final boolean lava;
    public Phase phase = Phase.WAIT;
    public Entity bobber;
    public Location catchAt;
    public FishKind fish = FishKind.PLOTVA;

    /** 0..width-1 — рыба */
    public double fishPos;
    public double fishVel;
    /** 0..width-1 — левый край рамки */
    public double windowPos;
    /** 0..1 — прогресс вываживания */
    public double progress = 0.22;

    public BukkitTask task;
    public BossBar bar;

    public FishSession(RodType rod, boolean lava) {
        this.rod = rod;
        this.lava = lava;
    }

    public void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void hideBar(Player p) {
        if (bar != null && p != null) {
            p.hideBossBar(bar);
        }
        bar = null;
    }
}
