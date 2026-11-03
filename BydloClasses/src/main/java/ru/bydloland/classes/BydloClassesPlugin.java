package ru.bydloland.classes;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BydloClassesPlugin extends JavaPlugin {

    private ClassService service;
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();
    private BukkitTask effectTask;
    private BukkitTask traitTask;
    private ClassGui guiListener;
    private SamuraiGearListener samuraiGear;
    private DwarfListener dwarfListener;
    private SamuraiDashListener samuraiDash;
    private MageFamiliarListener familiarListener;
    private NetrunnerNightListener netrunnerNight;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        service = new ClassService(this);
        service.load();

        ClassCommand cmd = new ClassCommand(this);
        getCommand("class").setExecutor(cmd);
        getCommand("class").setTabCompleter(cmd);

        Bukkit.getPluginManager().registerEvents(new ClassListener(this), this);
        guiListener = new ClassGui(this);
        Bukkit.getPluginManager().registerEvents(guiListener, this);
        Bukkit.getPluginManager().registerEvents(new BomzhListener(this), this);
        dwarfListener = new DwarfListener(this);
        Bukkit.getPluginManager().registerEvents(dwarfListener, this);
        netrunnerNight = new NetrunnerNightListener();
        Bukkit.getPluginManager().registerEvents(netrunnerNight, this);

        if (Bukkit.getPluginManager().isPluginEnabled("CraftEngine")) {
            samuraiGear = new SamuraiGearListener(this);
            Bukkit.getPluginManager().registerEvents(samuraiGear, this);
            samuraiDash = new SamuraiDashListener(this);
            Bukkit.getPluginManager().registerEvents(samuraiDash, this);
            familiarListener = new MageFamiliarListener(this);
            Bukkit.getPluginManager().registerEvents(familiarListener, this);
            getLogger().info("Samurai craft/steel locks + dash/tea + familiar enabled (CraftEngine)");
        } else {
            getLogger().warning("CraftEngine not found — samurai craft locks disabled");
        }

        Bukkit.getPluginManager().registerEvents(new MageEnchantListener(), this);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new ClassesExpansion(this).register();
            getLogger().info("PlaceholderAPI: %bydloclasses_*% registered");
        } else {
            getLogger().warning("PlaceholderAPI not found — TAB placeholders won't work");
        }

        effectTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                service.refreshEffectsOnly(player);
            }
        }, service.effectRefreshTicks(), service.effectRefreshTicks());

        traitTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (dwarfListener != null) dwarfListener.tick(player);
                if (samuraiDash != null) samuraiDash.tick(player);
                if (familiarListener != null) familiarListener.tick(player);
                if (netrunnerNight != null) netrunnerNight.tick(player);
            }
        }, 10L, 10L);

        getLogger().info("BydloClasses enabled — races in config.yml, /раса | /class reload");
    }

    @Override
    public void onDisable() {
        if (effectTask != null) effectTask.cancel();
        if (traitTask != null) traitTask.cancel();
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearAttachments(player);
        }
        if (service != null) service.savePlayers();
    }

    public void reloadAll() {
        service.load();
        for (Player player : Bukkit.getOnlinePlayers()) {
            service.applyCurrent(player);
        }
    }

    public ClassService service() {
        return service;
    }

    public SamuraiGearListener samuraiGear() {
        return samuraiGear;
    }

    public void attachPermission(Player player, String node) {
        PermissionAttachment att = attachments.computeIfAbsent(player.getUniqueId(),
                id -> player.addAttachment(this));
        att.setPermission(node, true);
    }

    public void clearAttachments(Player player) {
        PermissionAttachment att = attachments.remove(player.getUniqueId());
        if (att != null) {
            try {
                player.removeAttachment(att);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
