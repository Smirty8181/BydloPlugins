package ru.bydloland.discs;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.bydloland.discs.cmd.DiscCommand;
import ru.bydloland.discs.download.DownloadPipeline;
import ru.bydloland.discs.item.DiscItems;
import ru.bydloland.discs.listener.JukeboxListener;
import ru.bydloland.discs.play.VoicePlayback;
import ru.bydloland.discs.store.PlayerTracks;

public final class BydloDiscsPlugin extends JavaPlugin {
    private static BydloDiscsPlugin instance;
    private PlayerTracks tracks;
    private DiscItems items;
    private DownloadPipeline downloads;
    private VoicePlayback playback;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        tracks = new PlayerTracks(this);
        items = new DiscItems(this);
        downloads = new DownloadPipeline(this, tracks, items);
        playback = new VoicePlayback(this);

        getCommand("disc").setExecutor(new DiscCommand(this, downloads, items, tracks));
        Bukkit.getPluginManager().registerEvents(new JukeboxListener(this, items, tracks, playback), this);

        Bukkit.getScheduler().runTaskLater(this, () -> playback.tryHook(), 40L);
        Bukkit.getScheduler().runTaskLater(this, () -> playback.tryHook(), 120L);
        Bukkit.getScheduler().runTaskTimer(this, () -> playback.tickSilence(), 10L, 10L);
        getLogger().info("BydloDiscs: /пластина <url> — одна пластинка, смена раз в час");
    }

    @Override
    public void onDisable() {
        if (playback != null) playback.stopAll();
        if (tracks != null) tracks.save();
    }

    public static BydloDiscsPlugin get() { return instance; }
    public PlayerTracks tracks() { return tracks; }
    public DiscItems items() { return items; }
    public DownloadPipeline downloads() { return downloads; }
    public VoicePlayback playback() { return playback; }

    public String prefix() {
        return color(getConfig().getString("prefix", "&d[&5Быдло&d]&r") + " ");
    }

    public String msg(String key) {
        return color(getConfig().getString("messages." + key, key));
    }

    public static String color(String s) {
        return s == null ? "" : s.replace('&', '§');
    }
}
