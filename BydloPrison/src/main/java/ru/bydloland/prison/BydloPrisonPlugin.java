package ru.bydloland.prison;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.bydloland.prison.command.PrisonCommand;
import ru.bydloland.prison.hook.ExternalBanHook;
import ru.bydloland.prison.hook.VoiceChatHook;
import ru.bydloland.prison.listener.PrisonJoinListener;
import ru.bydloland.prison.listener.PrisonRestrictListener;
import ru.bydloland.prison.pig.PrisonPigManager;
import ru.bydloland.prison.service.MessageService;
import ru.bydloland.prison.service.PrisonService;
import ru.bydloland.prison.service.PrisonStorage;

public final class BydloPrisonPlugin extends JavaPlugin {

    private PrisonStorage storage;
    private MessageService messages;
    private PrisonPigManager pigs;
    private PrisonService prison;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new MessageService(this);
        storage = new PrisonStorage(this);
        storage.load();
        pigs = new PrisonPigManager(this);
        prison = new PrisonService(this, storage, pigs, messages);

        Bukkit.getPluginManager().registerEvents(new PrisonRestrictListener(prison), this);
        Bukkit.getPluginManager().registerEvents(new PrisonJoinListener(prison), this);

        PrisonCommand cmd = new PrisonCommand(this, prison, messages);
        for (String name : new String[]{"prison", "setprison", "заключить", "разбан"}) {
            var pluginCmd = getCommand(name);
            if (pluginCmd != null) {
                pluginCmd.setExecutor(cmd);
                pluginCmd.setTabCompleter(cmd);
            } else {
                getLogger().warning("Command not in plugin.yml: " + name);
            }
        }

        VoiceChatHook.tryRegister(this, prison);
        ExternalBanHook.register(this, prison);

        int keep = Math.max(1, getConfig().getInt("keep-spectate-ticks", 5));
        Bukkit.getScheduler().runTaskTimer(this, prison::tickKeep, keep, keep);

        int expiry = Math.max(20, getConfig().getInt("expiry-check-ticks", 40));
        Bukkit.getScheduler().runTaskTimer(this, prison::tickExpiry, expiry, expiry);

        // Restore online prisoners after reload
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Bukkit.getOnlinePlayers().forEach(prison::handleJoin);
        }, 40L);

        getLogger().info("BydloPrison enabled — bans become pig imprisonment");
    }

    @Override
    public void onDisable() {
        if (prison != null) {
            prison.shutdown();
        }
        if (storage != null) {
            storage.save();
        }
    }

    public PrisonService prison() {
        return prison;
    }

    public MessageService messages() {
        return messages;
    }

    public PrisonStorage storage() {
        return storage;
    }

    public PrisonPigManager pigs() {
        return pigs;
    }

    public void reloadAll() {
        reloadConfig();
        messages.reload();
        storage.load();
    }
}
