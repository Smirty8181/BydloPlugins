package ru.bydlolend.bydlomod.fx;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.time.Duration;

/** Смена мира — короткий титр и вспышка. */
public final class WorldFxListener implements Listener {

    private static final TextColor PINK = TextColor.color(0xF472B6);
    private static final TextColor CREAM = TextColor.color(0xF5D0FE);

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        String w = p.getWorld().getName();
        String sub;
        Sound sound;
        Particle particle;
        float pitch;
        switch (w) {
            case "lobby" -> {
                sub = "лобби";
                sound = Sound.UI_TOAST_IN;
                particle = Particle.END_ROD;
                pitch = 1.25f;
            }
            case "world" -> {
                sub = "выживание";
                sound = Sound.BLOCK_AMETHYST_BLOCK_CHIME;
                particle = Particle.CHERRY_LEAVES;
                pitch = 1.35f;
            }
            case "event" -> {
                sub = "ивент";
                sound = Sound.BLOCK_NOTE_BLOCK_BELL;
                particle = Particle.NOTE;
                pitch = 1.2f;
            }
            case "world_nether" -> {
                sub = "ад";
                sound = Sound.BLOCK_LAVA_POP;
                particle = Particle.FLAME;
                pitch = 0.85f;
            }
            case "world_the_end" -> {
                sub = "край";
                sound = Sound.BLOCK_PORTAL_AMBIENT;
                particle = Particle.PORTAL;
                pitch = 1.15f;
            }
            default -> {
                sub = w;
                sound = Sound.UI_TOAST_IN;
                particle = Particle.END_ROD;
                pitch = 1.1f;
            }
        }
        p.showTitle(Title.title(
                Component.text("✦", PINK),
                Component.text(sub, CREAM),
                Title.Times.times(Duration.ofMillis(120), Duration.ofMillis(1100), Duration.ofMillis(280))
        ));
        p.playSound(p.getLocation(), sound, 0.38f, pitch);
        p.spawnParticle(particle, p.getLocation().add(0, 1.05, 0), 10, 0.35, 0.35, 0.35, 0.02);
    }
}
