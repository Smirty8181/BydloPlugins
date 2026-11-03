package ru.bydloland.crafts;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class BydloCraftsPlugin extends JavaPlugin {

    private CraftCatalog catalog;

    @Override
    public void onEnable() {
        catalog = CraftCatalog.create();
        CraftGui gui = new CraftGui(this, catalog);
        Bukkit.getPluginManager().registerEvents(gui, this);

        CraftCommand cmd = new CraftCommand(gui);
        var c = getCommand("crafts");
        if (c != null) {
            c.setExecutor(cmd);
            c.setTabCompleter(cmd);
        }
        getLogger().info("BydloCrafts enabled — /крафты (GUI 3×3)");
    }

    public CraftCatalog catalog() {
        return catalog;
    }
}
