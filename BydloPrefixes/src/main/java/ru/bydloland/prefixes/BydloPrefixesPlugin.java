package ru.bydloland.prefixes;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class BydloPrefixesPlugin extends JavaPlugin {

    private PrefixService service;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        service = new PrefixService(this);
        service.load();

        PrefixCreateListener createListener = new PrefixCreateListener(this);
        Bukkit.getPluginManager().registerEvents(createListener, this);

        PrefixGui gui = new PrefixGui(this, createListener);
        Bukkit.getPluginManager().registerEvents(gui, this);
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
                service.bindOnlineName(event.getPlayer());
            }
        }, this);

        PrefixCommand cmd = new PrefixCommand(this, gui, createListener);
        getCommand("prefix").setExecutor(cmd);
        getCommand("prefix").setTabCompleter(cmd);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PrefixExpansion(this).register();
            getLogger().info("PlaceholderAPI: %bydloprefixes_tab%");
        } else {
            getLogger().warning("PlaceholderAPI не найден — TAB не увидит префиксы");
        }

        getLogger().info("BydloPrefixes enabled — /префикс create");
    }

    @Override
    public void onDisable() {
        if (service != null) service.save();
    }

    public void reloadAll() {
        service.load();
    }

    public PrefixService service() {
        return service;
    }
}
