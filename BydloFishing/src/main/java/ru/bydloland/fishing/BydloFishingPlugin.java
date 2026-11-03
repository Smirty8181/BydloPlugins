package ru.bydloland.fishing;

import org.bukkit.plugin.java.JavaPlugin;

public final class BydloFishingPlugin extends JavaPlugin {
    private static BydloFishingPlugin instance;
    private FishSessions sessions;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        sessions = new FishSessions(this);
        getServer().getPluginManager().registerEvents(new WaterFishListener(this, sessions), this);
        getServer().getPluginManager().registerEvents(new LavaFishListener(this, sessions), this);
        getServer().getPluginManager().registerEvents(new MinigameListener(this, sessions), this);
        FishCommand cmd = new FishCommand(this);
        var c = getCommand("рыбалка");
        if (c != null) {
            c.setExecutor(cmd);
            c.setTabCompleter(cmd);
        }
        getLogger().info("BydloFishing 1.1 — вываживание, именной улов, лава");
    }

    @Override
    public void onDisable() {
        if (sessions != null) sessions.clearAll();
    }

    public static BydloFishingPlugin get() {
        return instance;
    }

    public FishSessions sessions() {
        return sessions;
    }
}
