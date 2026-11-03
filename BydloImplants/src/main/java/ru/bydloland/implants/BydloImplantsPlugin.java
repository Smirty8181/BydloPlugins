package ru.bydloland.implants;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class BydloImplantsPlugin extends JavaPlugin {

    private ImplantService service;
    private ImplantGui gui;
    private RipperListener ripper;
    private BukkitTask effectTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        service = new ImplantService(this);
        service.load();

        gui = new ImplantGui(this);
        Bukkit.getPluginManager().registerEvents(gui, this);
        Bukkit.getPluginManager().registerEvents(new ImplantListener(this), this);
        ripper = new RipperListener(this);
        Bukkit.getPluginManager().registerEvents(ripper, this);
        Bukkit.getPluginManager().registerEvents(new RipperCraftListener(this), this);

        ImplantCommand cmd = new ImplantCommand(this);
        var implants = getCommand("implants");
        if (implants == null) {
            getLogger().severe("Command 'implants' is missing from plugin.yml");
        } else {
            implants.setExecutor(cmd);
            implants.setTabCompleter(cmd);
        }

        long ticks = Math.max(20L, getConfig().getLong("effect-refresh-ticks", 100L));
        effectTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                service.applyEffects(player);
            }
        }, ticks, ticks);

        getLogger().info("BydloImplants enabled — /импланты");
    }

    @Override
    public void onDisable() {
        if (effectTask != null) effectTask.cancel();
        if (service != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                service.clearEffects(player);
            }
            service.save();
        }
    }

    public ImplantService service() {
        return service;
    }

    public ImplantGui gui() {
        return gui;
    }

    public RipperListener ripper() {
        return ripper;
    }
}
