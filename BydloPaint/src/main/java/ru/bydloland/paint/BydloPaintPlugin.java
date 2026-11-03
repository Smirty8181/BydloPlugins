package ru.bydloland.paint;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.bydloland.paint.command.CanvasCommand;
import ru.bydloland.paint.gui.SizeGui;
import ru.bydloland.paint.listener.PaintListener;
import ru.bydloland.paint.listener.SizeGuiListener;
import ru.bydloland.paint.paint.TileStore;
import ru.bydloland.paint.recipe.CanvasRecipes;
import ru.bydloland.paint.service.CanvasService;

public final class BydloPaintPlugin extends JavaPlugin {

    private CanvasService canvases;
    private TileStore tileStore;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        canvases = new CanvasService(this);
        tileStore = new TileStore(this);
        CanvasRecipes.register(this);

        Bukkit.getPluginManager().registerEvents(new PaintListener(this, canvases), this);
        Bukkit.getPluginManager().registerEvents(new SizeGuiListener(this, canvases), this);
        Bukkit.getPluginManager().registerEvents(new ru.bydloland.paint.listener.CraftListener(this), this);

        CanvasCommand cmd = new CanvasCommand(this, canvases);
        var pluginCmd = getCommand("canvas");
        if (pluginCmd != null) {
            pluginCmd.setExecutor(cmd);
            pluginCmd.setTabCompleter(cmd);
        }

        getLogger().info("BydloPaint enabled — canvas max "
                + getConfig().getInt("max-size", 10) + "x"
                + getConfig().getInt("max-size", 10));
    }

    @Override
    public void onDisable() {
        if (tileStore != null) {
            tileStore.flushAllSync();
        }
    }

    public CanvasService canvases() {
        return canvases;
    }

    public TileStore tileStore() {
        return tileStore;
    }

    public String msg(String path) {
        return ColorUtil.color(getConfig().getString("prefix", "") + " "
                + getConfig().getString("messages." + path, path));
    }

    public void openSizeGui(org.bukkit.entity.Player player, org.bukkit.inventory.ItemStack canvas) {
        SizeGui.open(this, player, canvas);
    }
}
