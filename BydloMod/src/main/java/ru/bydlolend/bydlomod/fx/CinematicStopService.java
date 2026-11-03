package ru.bydlolend.bydlomod.fx;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.bydlolend.bydlomod.BydloMod;

import java.time.Duration;

/**
 * Кинематографичный рестарт / выключение: боссбар, титры, кольца частиц, кик-экран.
 */
public final class CinematicStopService {

    public enum Mode { RESTART, SHUTDOWN }

    private static final TextColor PINK = TextColor.color(0xF472B6);
    private static final TextColor LAVENDER = TextColor.color(0xC084FC);
    private static final TextColor CREAM = TextColor.color(0xF5D0FE);
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final BydloMod plugin;
    private Mode mode;
    private int left;
    private int total;
    private BossBar bar;
    private BukkitTask task;
    private boolean executing;
    private boolean running;

    public CinematicStopService(BydloMod plugin) {
        this.plugin = plugin;
    }

    public boolean running() {
        return running;
    }

    public boolean executing() {
        return executing;
    }

    public boolean start(Mode mode, int seconds, org.bukkit.command.CommandSender source) {
        if (executing) return false;
        if (running) {
            cancel();
        }
        this.mode = mode;
        this.total = Math.max(3, Math.min(60, seconds));
        this.left = this.total;
        this.pulse = 0;
        this.running = true;
        this.executing = false;

        BarColor color = mode == Mode.RESTART ? BarColor.PINK : BarColor.PURPLE;
        bar = Bukkit.createBossBar(barTitle(), color, BarStyle.SEGMENTED_10);
        bar.setProgress(1.0);
        bar.setVisible(true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            bar.addPlayer(p);
        }

        String who = source == null ? "консоль" : source.getName();
        Bukkit.broadcast(frame(mode == Mode.RESTART
                ? "§d✦ §fРестарт через §d" + total + "с §8· §7" + who
                : "§5✦ §fВыключение через §d" + total + "с §8· §7" + who));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.45f, mode == Mode.RESTART ? 1.4f : 0.7f);
            showTitle(p, brandTitle(), Component.text(mode == Mode.RESTART ? "сейчас рестарт" : "сервер выключается", CREAM), 400, 1600, 400);
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
        return true;
    }

    public void skipToEnd() {
        if (executing) return;
        if (!running) return;
        left = 0;
        finish();
    }

    public void cancel() {
        if (executing) return;
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
            bar = null;
        }
        Bukkit.broadcast(LEGACY.deserialize("§8· §7рестарт отменён"));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(Component.empty());
            p.showTitle(Title.title(Component.empty(), Component.empty(), Title.Times.times(
                    Duration.ZERO, Duration.ofMillis(1), Duration.ZERO)));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.6f);
        }
    }

    private int pulse;

    private void tick() {
        if (!running || executing) return;
        fxPulse();
        pulse++;
        if (pulse % 2 == 1) {
            actionBars();
            return;
        }
        left--;
        if (bar != null) {
            bar.setTitle(barTitle());
            bar.setProgress(Math.max(0.0, left / (double) total));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
            }
        }
        actionBars();
        secondTitles();
        if (left == 3) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-all");
            } catch (Throwable ignored) {
            }
        }
        if (left <= 0) {
            finish();
        }
    }

    private void actionBars() {
        String kind = mode == Mode.RESTART ? "рестарт" : "выключение";
        Component msg = Component.text("✦  ", PINK)
                .append(Component.text(kind + "  ", LAVENDER))
                .append(Component.text(Math.max(0, left) + "с", CREAM, TextDecoration.BOLD));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(msg);
        }
    }

    private void secondTitles() {
        if (left == 10 || left == 7) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                showTitle(p, Component.text(left + "", PINK, TextDecoration.BOLD),
                        Component.text(mode == Mode.RESTART ? "сохраняй инвентарь" : "до встречи", CREAM),
                        80, 700, 120);
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.2f + (10 - left) * 0.05f);
            }
        } else if (left >= 1 && left <= 5) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                TextColor c = left <= 2 ? TextColor.color(0xF472B6) : LAVENDER;
                showTitle(p, Component.text(String.valueOf(left), c, TextDecoration.BOLD),
                        Component.text(mode == Mode.RESTART ? "рестарт" : "выключение", CREAM),
                        40, 500, 80);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.85f, 0.8f + left * 0.18f);
            }
        }
        if (left == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, mode == Mode.RESTART ? 1.3f : 0.55f);
            }
        }
    }

    private void fxPulse() {
        Particle.DustOptions dust = mode == Mode.RESTART
                ? new Particle.DustOptions(Color.fromRGB(244, 114, 182), 1.15f)
                : new Particle.DustOptions(Color.fromRGB(88, 28, 135), 1.2f);
        Particle extra = mode == Mode.RESTART ? Particle.END_ROD : Particle.SOUL;
        double spin = (Bukkit.getCurrentTick() % 80) / 80.0 * Math.PI * 2;
        int n = left <= 3 ? 20 : 12;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location base = p.getLocation().add(0, 0.15, 0);
            for (int i = 0; i < n; i++) {
                double a = spin + (Math.PI * 2 * i / n);
                double r = left <= 3 ? 1.55 : 1.25;
                Location at = base.clone().add(Math.cos(a) * r, 0.05 + Math.sin(spin + i) * 0.08, Math.sin(a) * r);
                p.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust);
                if (i % 3 == 0) {
                    p.spawnParticle(extra, at.clone().add(0, 0.35, 0), 1, 0, 0, 0, 0);
                }
            }
            if (left <= 3 && mode == Mode.RESTART) {
                p.spawnParticle(Particle.FIREWORK, p.getLocation().add(0, 1.2, 0), 3, 0.3, 0.4, 0.3, 0.01);
            }
        }
    }

    private void finish() {
        if (executing) return;
        executing = true;
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
            bar = null;
        }

        Component kick = kickScreen();
        for (Player p : Bukkit.getOnlinePlayers()) {
            showTitle(p, brandTitle(),
                    Component.text(mode == Mode.RESTART ? "до встречи" : "спокойной ночи", CREAM),
                    80, 1200, 400);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.35f, mode == Mode.RESTART ? 1.35f : 0.7f);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    p.kick(kick);
                } catch (Throwable t) {
                    p.kickPlayer(LEGACY.serialize(kick));
                }
            }
        }, 12L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (mode == Mode.RESTART) {
                try {
                    Bukkit.spigot().restart();
                    return;
                } catch (Throwable ignored) {
                }
            }
            Bukkit.shutdown();
        }, 28L);
    }

    private Component kickScreen() {
        if (mode == Mode.RESTART) {
            return Component.text()
                    .append(Component.text("✦  ", PINK))
                    .append(Component.text("БыдлоЛенд", LAVENDER, TextDecoration.BOLD))
                    .append(Component.text("  ✦", PINK))
                    .append(Component.newline()).append(Component.newline())
                    .append(Component.text("сервер на рестарте", CREAM))
                    .append(Component.newline())
                    .append(Component.text("зайди через минуту", NamedTextColor.GRAY))
                    .append(Component.newline()).append(Component.newline())
                    .append(Component.text("bidloland.sosal.today", PINK))
                    .build();
        }
        return Component.text()
                .append(Component.text("✦  ", LAVENDER))
                .append(Component.text("БыдлоЛенд", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                .append(Component.text("  ✦", LAVENDER))
                .append(Component.newline()).append(Component.newline())
                .append(Component.text("сервер выключили", CREAM))
                .append(Component.newline())
                .append(Component.text("зайди позже", NamedTextColor.GRAY))
                .build();
    }

    private Component brandTitle() {
        return Component.text("✦ ", PINK)
                .append(Component.text("БыдлоЛенд", LAVENDER, TextDecoration.BOLD))
                .append(Component.text(" ✦", PINK));
    }

    private String barTitle() {
        String kind = mode == Mode.RESTART ? "Рестарт" : "Выключение";
        String heart = left <= 3 ? "✧" : "✦";
        return "§d" + heart + " §f" + kind + " §d" + Math.max(0, left) + "с";
    }

    private static Component frame(String legacy) {
        return LEGACY.deserialize(legacy);
    }

    private static void showTitle(Player p, Component title, Component sub, long fadeIn, long stay, long fadeOut) {
        p.showTitle(Title.title(title, sub, Title.Times.times(
                Duration.ofMillis(fadeIn), Duration.ofMillis(stay), Duration.ofMillis(fadeOut))));
    }
}
