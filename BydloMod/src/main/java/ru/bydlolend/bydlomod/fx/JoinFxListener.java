package ru.bydlolend.bydlomod.fx;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.bydlolend.bydlomod.BydloMod;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Вход: титр, кольцо частиц, звук. */
public final class JoinFxListener implements Listener {

    private static final TextColor PINK = TextColor.color(0xF472B6);
    private static final TextColor LAVENDER = TextColor.color(0xC084FC);
    private static final TextColor CREAM = TextColor.color(0xF5D0FE);

    private final BydloMod plugin;
    private final Map<UUID, Integer> tasks = new ConcurrentHashMap<>();

    public JoinFxListener(BydloMod plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.cinematic().running()) return;
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || plugin.cinematic().running()) return;
            player.showTitle(Title.title(
                    Component.text("✦ ", PINK)
                            .append(Component.text("БыдлоЛенд", LAVENDER, TextDecoration.BOLD))
                            .append(Component.text(" ✦", PINK)),
                    Component.text(player.hasPlayedBefore() ? "снова здесь" : "добро пожаловать", CREAM),
                    Title.Times.times(Duration.ofMillis(350), Duration.ofMillis(2200), Duration.ofMillis(650))
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.55f, 1.35f);
            player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 0.4f, 1.1f);
            startRing(player);
        }, 25L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Integer id = tasks.remove(event.getPlayer().getUniqueId());
        if (id != null) plugin.getServer().getScheduler().cancelTask(id);
    }

    private void startRing(Player player) {
        UUID uuid = player.getUniqueId();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(244, 114, 182), 1.0f);
        final int[] step = {0};
        int taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!player.isOnline() || step[0] > 28) {
                Integer id = tasks.remove(uuid);
                if (id != null) plugin.getServer().getScheduler().cancelTask(id);
                return;
            }
            Location base = player.getLocation().add(0, 0.2, 0);
            double spin = step[0] * 0.28;
            for (int i = 0; i < 10; i++) {
                double a = spin + Math.PI * 2 * i / 10.0;
                Location at = base.clone().add(Math.cos(a) * 1.15, 0.04, Math.sin(a) * 1.15);
                player.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust);
            }
            if (step[0] % 4 == 0) {
                player.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1.1, 0), 2, 0.15, 0.25, 0.15, 0.005);
            }
            step[0]++;
        }, 1L, 2L);
        tasks.put(uuid, taskId);
    }
}
