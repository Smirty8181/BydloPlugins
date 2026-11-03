package ru.bydlolend.bydlomod.mod;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import ru.bydlolend.bydlomod.BydloMod;

/**
 * Must run BEFORE OCD_chat (NORMAL): cancel at LOWEST so formatters never send.
 */
public final class ChatMuteListener implements Listener {
    private final BydloMod plugin;
    private final ModerationService mod;

    public ChatMuteListener(BydloMod plugin, ModerationService mod) {
        this.plugin = plugin;
        this.mod = mod;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        var mute = mod.getChatMute(p.getUniqueId());
        if (mute.isEmpty()) return;
        event.setCancelled(true);
        try {
            event.viewers().clear();
        } catch (Throwable ignored) {
        }
        ModerationService.Punishment pun = mute.get();
        String time = pun.expiresAt == 0 ? "навсегда" : ModerationService.formatLeft(pun.expiresAt - System.currentTimeMillis());
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                p.sendMessage(plugin.prefix() + BydloMod.color("&cТы в муте чата. Осталось: &f" + time)));
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onLegacy(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        var mute = mod.getChatMute(p.getUniqueId());
        if (mute.isEmpty()) return;
        event.setCancelled(true);
        event.getRecipients().clear();
    }
}
