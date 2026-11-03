package ru.bydloland.tattoos;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class BydloTattoosPlugin extends JavaPlugin {

    private TattooStorage storage;
    private TattooCatalog catalog;
    private SkinBridge skins;
    private TattooService service;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().addDefault("mineskin-api-key", "");
        getConfig().options().copyDefaults(true);
        saveConfig();
        storage = new TattooStorage(this);
        storage.load();
        catalog = new TattooCatalog(this);
        skins = new SkinBridge(this);
        service = new TattooService(this, storage, catalog, skins);

        Bukkit.getPluginManager().registerEvents(new ArtistListener(this), this);
        Bukkit.getPluginManager().registerEvents(new TattooGui(this), this);
        Bukkit.getPluginManager().registerEvents(new DrawGui(this), this);

        TattooCommand cmd = new TattooCommand(this);
        var pluginCmd = getCommand("тату");
        if (pluginCmd != null) {
            pluginCmd.setExecutor(cmd);
            pluginCmd.setTabCompleter(cmd);
        }

        getLogger().info("BydloTattoos — машинка, свой рисунок, себе, префикс мастера");
        String key = getConfig().getString("mineskin-api-key", "");
        if (key == null || key.isBlank() || "key".equalsIgnoreCase(key.trim())) {
            getLogger().info("MineSkin без ключа (лимит ~10/мин). Если тату не ставятся — ключ в config.yml");
        }
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.save();
        }
    }

    public TattooStorage storage() {
        return storage;
    }

    public TattooCatalog catalog() {
        return catalog;
    }

    public SkinBridge skins() {
        return skins;
    }

    public TattooService service() {
        return service;
    }
}
