package ru.bydloland.prison.pig;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import ru.bydloland.prison.BydloPrisonPlugin;
import ru.bydloland.prison.model.PrisonRecord;

import java.util.UUID;

public final class PrisonPigManager {

    public static final String PDC_KEY = "prisoner";

    private final BydloPrisonPlugin plugin;
    private final NamespacedKey key;

    public PrisonPigManager(BydloPrisonPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, PDC_KEY);
    }

    public NamespacedKey key() {
        return key;
    }

    public Location prisonLocation() {
        var cfg = plugin.getConfig();
        String worldName = cfg.getString("prison.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            throw new IllegalStateException("No worlds loaded");
        }
        if (cfg.get("prison.x") == null || cfg.get("prison.y") == null || cfg.get("prison.z") == null) {
            Location spawn = world.getSpawnLocation().clone().add(0.5, 0, 0.5);
            spawn.setYaw((float) cfg.getDouble("prison.yaw", 0));
            spawn.setPitch((float) cfg.getDouble("prison.pitch", 0));
            return spawn;
        }
        return new Location(
                world,
                cfg.getDouble("prison.x") + 0.5,
                cfg.getDouble("prison.y"),
                cfg.getDouble("prison.z") + 0.5,
                (float) cfg.getDouble("prison.yaw", 0),
                (float) cfg.getDouble("prison.pitch", 0)
        );
    }

    public Location releaseLocation() {
        var cfg = plugin.getConfig();
        String worldName = cfg.getString("prison.release-world", cfg.getString("prison.world", "world"));
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            return prisonLocation();
        }
        if (cfg.get("prison.release-x") == null) {
            return world.getSpawnLocation().clone().add(0.5, 0, 0.5);
        }
        return new Location(
                world,
                cfg.getDouble("prison.release-x") + 0.5,
                cfg.getDouble("prison.release-y"),
                cfg.getDouble("prison.release-z") + 0.5
        );
    }

    public Pig spawnOrLoad(PrisonRecord record, Player player) {
        removeExisting(record);

        Location loc = prisonLocation();
        World world = loc.getWorld();
        if (world == null) return null;

        // Keep chunk loaded
        world.addPluginChunkTicket(loc.getBlockX() >> 4, loc.getBlockZ() >> 4, plugin);

        Pig pig = world.spawn(loc, Pig.class, p -> {
            applyPigState(p, player.getName(), record.uuid());
        });

        record.setPigUuid(pig.getUniqueId());
        record.setLocation(world.getName(), loc.getX(), loc.getY(), loc.getZ());
        return pig;
    }

    public void applyPigState(Pig pig, String playerName, UUID prisonerUuid) {
        pig.setAI(false);
        pig.setAware(false);
        pig.setGravity(false);
        pig.setSilent(true);
        pig.setInvulnerable(true);
        pig.setCollidable(false);
        pig.setPersistent(true);
        pig.setRemoveWhenFarAway(false);
        pig.setAdult();
        pig.setBreed(false);
        pig.setAgeLock(true);
        String name = plugin.messages().apply("pig-name", playerName, "", "");
        pig.setCustomName(name);
        pig.setCustomNameVisible(true);

        var maxHealth = pig.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(20);
            pig.setHealth(20);
        }

        pig.getPersistentDataContainer().set(key, PersistentDataType.STRING, prisonerUuid.toString());
        pig.setVelocity(pig.getVelocity().zero());
    }

    public Pig findPig(PrisonRecord record) {
        if (record.pigUuid() == null) return null;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getUniqueId().equals(record.pigUuid()) && e instanceof Pig pig) {
                    return pig;
                }
            }
        }
        // Also search by PDC near prison location
        Location loc = prisonLocation();
        World w = loc.getWorld();
        if (w == null) return null;
        for (Entity e : w.getNearbyEntities(loc, 8, 8, 8)) {
            if (!(e instanceof Pig pig)) continue;
            String stored = pig.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (stored != null && stored.equals(record.uuid().toString())) {
                record.setPigUuid(pig.getUniqueId());
                return pig;
            }
        }
        return null;
    }

    public void removeExisting(PrisonRecord record) {
        Pig pig = findPig(record);
        if (pig != null) {
            Location loc = pig.getLocation();
            World world = loc.getWorld();
            pig.remove();
            if (world != null) {
                world.removePluginChunkTicket(loc.getBlockX() >> 4, loc.getBlockZ() >> 4, plugin);
            }
        }
        // Sweep orphans for this prisoner
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntitiesByClass(Pig.class)) {
                String stored = e.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                if (stored != null && stored.equals(record.uuid().toString())) {
                    e.remove();
                }
            }
        }
        record.setPigUuid(null);
    }

    public boolean isPrisonPig(Entity entity) {
        if (!(entity instanceof Pig)) return false;
        return entity.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }
}
