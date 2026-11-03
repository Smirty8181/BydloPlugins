package ru.bydloland.discs.cmd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.bydloland.discs.BydloDiscsPlugin;
import ru.bydloland.discs.download.DownloadPipeline;
import ru.bydloland.discs.item.DiscItems;
import ru.bydloland.discs.store.PlayerTracks;

public final class DiscCommand implements CommandExecutor {
    private final BydloDiscsPlugin plugin;
    private final DownloadPipeline downloads;
    private final DiscItems items;
    private final PlayerTracks tracks;

    public DiscCommand(BydloDiscsPlugin plugin, DownloadPipeline downloads, DiscItems items, PlayerTracks tracks) {
        this.plugin = plugin;
        this.downloads = downloads;
        this.items = items;
        this.tracks = tracks;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(plugin.prefix() + plugin.msg("usage"));
            var t = tracks.get(player.getUniqueId());
            if (t != null && t.title != null) {
                player.sendMessage(plugin.prefix() + BydloDiscsPlugin.color("&7Твой трек: &f" + t.title));
                if (tracks.onCooldown(player.getUniqueId()) && !player.hasPermission("bydlo.disc.bypasscooldown")) {
                    player.sendMessage(plugin.prefix() + plugin.msg("cooldown")
                            .replace("%time%", PlayerTracks.formatLeft(tracks.cooldownLeftSec(player.getUniqueId()))));
                }
                // give disc copy if missing
                player.getInventory().addItem(items.create(player.getUniqueId(), player.getName(), t.title));
            }
            return true;
        }
        String url = args[0].trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            player.sendMessage(plugin.prefix() + plugin.msg("bad-url"));
            return true;
        }
        downloads.start(player, url);
        return true;
    }
}
