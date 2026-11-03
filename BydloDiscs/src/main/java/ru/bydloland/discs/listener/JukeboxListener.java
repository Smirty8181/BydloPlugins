package ru.bydloland.discs.listener;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import ru.bydloland.discs.BydloDiscsPlugin;
import ru.bydloland.discs.item.DiscItems;
import ru.bydloland.discs.play.VoicePlayback;
import ru.bydloland.discs.store.PlayerTracks;

import java.util.UUID;

public final class JukeboxListener implements Listener {
    private final BydloDiscsPlugin plugin;
    private final DiscItems items;
    private final PlayerTracks tracks;
    private final VoicePlayback playback;

    public JukeboxListener(BydloDiscsPlugin plugin, DiscItems items, PlayerTracks tracks, VoicePlayback playback) {
        this.plugin = plugin;
        this.items = items;
        this.tracks = tracks;
        this.playback = playback;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null || block.getType() != Material.JUKEBOX) return;
        if (!(block.getState() instanceof Jukebox jukebox)) return;

        Player player = e.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        // Take out / stop
        if (!jukebox.getRecord().getType().isAir()) {
            if (items.isCustom(jukebox.getRecord()) || playbackPlaying(block)) {
                playback.stop(block.getLocation());
                stopVanillaSong(block);
                player.sendMessage(plugin.prefix() + plugin.msg("stopped"));
            }
            return;
        }

        // Insert custom disc
        if (!items.isCustom(hand)) return;
        UUID owner = items.owner(hand);
        if (owner == null) return;
        var info = tracks.get(owner);
        if (info == null || info.pcmPath == null) {
            player.sendMessage(plugin.prefix() + plugin.msg("failed"));
            return;
        }

        e.setCancelled(true);
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);

        ItemStack disc = items.asSilentRecord(hand);
        hand.setAmount(hand.getAmount() - 1);
        jukebox.setRecord(disc);
        jukebox.update();
        // CraftJukebox.update() вызывает setTheItem и стартует ванильную песню — глушим.
        stopVanillaSong(block);
        playback.silenceVanillaAround(block.getLocation());
        for (long delay : new long[]{1L, 2L, 5L, 10L, 20L}) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                stopVanillaSong(block);
                playback.silenceVanillaAround(block.getLocation());
            }, delay);
        }

        String title = info.title != null ? info.title : "Трек";

        boolean ok = playback.play(block.getLocation(), info.pcmPath, title);
        if (ok) {
            player.sendMessage(plugin.prefix() + plugin.msg("playing").replace("%title%", title));
        } else if (!playback.ready()) {
            player.sendMessage(plugin.prefix() + plugin.msg("no-voice"));
        } else {
            player.sendMessage(plugin.prefix() + plugin.msg("play-failed"));
        }
    }

    private boolean playbackPlaying(Block block) {
        return playback.isActive(block.getLocation());
    }

    private static void stopVanillaSong(Block block) {
        if (block.getState() instanceof Jukebox live && live.isPlaying()) {
            live.stopPlaying();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() == Material.JUKEBOX) {
            playback.stop(e.getBlock().getLocation());
        }
    }
}
