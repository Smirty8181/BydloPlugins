package ru.bydloland.fishing;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Вываживание как в Stardew: рыба дёргается, рамка поднимается, пока зажат sneak.
 * Прогресс растёт, пока рыба в рамке, иначе падает. 100% = улов, 0% = сорвалась.
 */
public final class Minigame {
    private Minigame() {
    }

    public static void start(BydloFishingPlugin plugin, Player p, FishSession s) {
        s.phase = FishSession.Phase.FIGHT;
        s.fish = FishKind.roll(s.lava, s.rod);
        int width = width(plugin);
        int win = windowSize(plugin, s);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        s.fishPos = r.nextDouble(2, Math.max(3, width - 3));
        s.fishVel = (r.nextBoolean() ? 1 : -1) * (0.25 + r.nextDouble() * 0.35) * s.fish.rarity.fight;
        s.windowPos = Math.max(0, Math.min(width - win, s.fishPos - win / 2.0));
        s.progress = plugin.getConfig().getDouble("minigame.progress-start", 0.24);
        s.hideBar(p);
        String title = (s.lava ? "§c" : "§b") + "Клюёт: §f" + s.fish.title;
        s.bar = BossBar.bossBar(
                Component.text(title.replace("§c", "").replace("§b", "").replace("§f", "")),
                (float) s.progress,
                s.lava ? BossBar.Color.RED : BossBar.Color.GREEN,
                BossBar.Overlay.NOTCHED_10);
        p.showBossBar(s.bar);
        p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 1f, s.lava ? 0.55f : 1.15f);
        p.sendTitle(s.lava ? "§cКЛЮЁТ" : "§bКЛЮЁТ", "§f" + s.fish.title + " §7· держи sneak", 0, 28, 6);
        Msg.send(p, "&e" + s.fish.title + "&7 — зажми &fsneak&7, чтобы поднять рамку. Не отпускай леску.");
        s.stopTask();
        s.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(plugin, p, s), 0L, 1L);
    }

    static int width(BydloFishingPlugin plugin) {
        return Math.max(13, plugin.getConfig().getInt("minigame.bar-width", 21));
    }

    static int windowSize(BydloFishingPlugin plugin, FishSession s) {
        int width = width(plugin);
        return Math.max(3, Math.min(width - 2, s.rod.window()));
    }

    private static void tick(BydloFishingPlugin plugin, Player p, FishSession s) {
        if (!p.isOnline()) {
            plugin.sessions().end(p);
            return;
        }
        int width = width(plugin);
        int win = windowSize(plugin, s);
        ThreadLocalRandom r = ThreadLocalRandom.current();

        double speed = s.rod.fishSpeed() * s.fish.rarity.fight * (s.lava ? 1.25 : 1.0);
        if (r.nextDouble() < s.rod.dashChance() * (s.lava ? 1.4 : 1.0)) {
            s.fishVel = (r.nextBoolean() ? 1 : -1) * (0.45 + r.nextDouble() * 0.85) * speed;
            p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.25f, 1.8f);
        }
        s.fishVel += (r.nextDouble() - 0.5) * 0.08 * speed;
        s.fishVel *= 0.97;
        s.fishPos += s.fishVel;
        if (s.fishPos < 0) {
            s.fishPos = 0;
            s.fishVel = Math.abs(s.fishVel) * 0.7;
        } else if (s.fishPos > width - 1) {
            s.fishPos = width - 1;
            s.fishVel = -Math.abs(s.fishVel) * 0.7;
        }

        double lift = s.rod.lift();
        double fall = s.rod.fall();
        if (p.isSneaking()) {
            s.windowPos += lift;
        } else {
            s.windowPos -= fall;
        }
        if (s.windowPos < 0) s.windowPos = 0;
        if (s.windowPos > width - win) s.windowPos = width - win;

        boolean overlap = inWindow(s, win);
        if (overlap) {
            s.progress += s.rod.fill();
            if (r.nextInt(8) == 0) {
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.15f, 1.6f);
            }
        } else {
            s.progress -= s.rod.drain();
        }
        s.progress = Math.max(0, Math.min(1, s.progress));

        splash(p, s);
        p.sendActionBar(Msg.text(bar(s, width, win)));
        if (s.bar != null) {
            s.bar.progress((float) s.progress);
            s.bar.name(Component.text((s.lava ? "Лава · " : "Вода · ") + s.fish.title
                    + (p.isSneaking() ? " · тянешь" : " · отпускаешь")));
        }

        if (s.progress >= 1) {
            plugin.sessions().succeed(p);
        } else if (s.progress <= 0) {
            plugin.sessions().fail(p, "&c" + s.fish.title + " &7сорвалась с крючка.");
        }
    }

    public static boolean inWindow(FishSession s, int win) {
        return s.fishPos >= s.windowPos && s.fishPos < s.windowPos + win;
    }

    private static void splash(Player p, FishSession s) {
        Location at = s.catchAt != null ? s.catchAt.clone().add(0.5, 0.4, 0.5)
                : (s.bobber != null && s.bobber.isValid() ? s.bobber.getLocation() : p.getLocation());
        if (at == null) return;
        if (s.lava) {
            at.getWorld().spawnParticle(Particle.LAVA, at, 1, 0.12, 0.05, 0.12, 0);
            at.getWorld().spawnParticle(Particle.FLAME, at, 2, 0.15, 0.1, 0.15, 0.01);
        } else {
            at.getWorld().spawnParticle(Particle.FISHING, at, 3, 0.18, 0.08, 0.18, 0.02);
            at.getWorld().spawnParticle(Particle.SPLASH, at, 4, 0.22, 0.06, 0.22, 0);
        }
    }

    static String bar(FishSession s, int width, int win) {
        int fish = (int) Math.round(s.fishPos);
        int left = (int) Math.round(s.windowPos);
        StringBuilder b = new StringBuilder("&8[");
        for (int i = 0; i < width; i++) {
            boolean w = i >= left && i < left + win;
            if (i == fish) {
                b.append(s.lava ? "&6●" : "&b●");
            } else if (w) {
                b.append(s.lava ? "&6█" : "&a█");
            } else {
                b.append(s.lava ? "&4░" : "&8░");
            }
        }
        int pct = (int) Math.round(s.progress * 100);
        String col = pct >= 70 ? "&a" : (pct >= 35 ? "&e" : "&c");
        b.append("&8] ").append(col).append(pct).append("%");
        if (!s.lava) b.append(" &7sneak");
        else b.append(" &6sneak");
        return b.toString();
    }
}
