package ru.bydloland.mage;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class BydloMagePlugin extends JavaPlugin {

    private MageProgress progress;
    private TowerGui towerGui;

    @Override
    public void onEnable() {
        progress = new MageProgress(this);
        progress.load();
        towerGui = new TowerGui(this);
        Bukkit.getPluginManager().registerEvents(towerGui, this);
        Bukkit.getPluginManager().registerEvents(new StaffListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MageCraftListener(this), this);
        Bukkit.getPluginManager().registerEvents(new TowerInteractListener(this), this);

        var cmd = getCommand("magetower");
        if (cmd != null) {
            cmd.setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof org.bukkit.entity.Player p)) {
                    sender.sendMessage("Players only");
                    return true;
                }
                if (!ClassesBridge.canUseMagicTower(p)) {
                    p.sendMessage("§cБашня магии доступна только магам.");
                    return true;
                }
                towerGui.open(p);
                return true;
            });
        }
        getLogger().info("BydloMage enabled — staffs + magic tower");
    }

    @Override
    public void onDisable() {
        if (progress != null) progress.save();
    }

    public MageProgress progress() {
        return progress;
    }

    public TowerGui towerGui() {
        return towerGui;
    }
}
