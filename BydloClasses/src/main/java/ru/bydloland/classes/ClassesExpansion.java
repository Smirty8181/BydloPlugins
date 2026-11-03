package ru.bydloland.classes;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class ClassesExpansion extends PlaceholderExpansion {

    private final BydloClassesPlugin plugin;

    public ClassesExpansion(BydloClassesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "bydloclasses";
    }

    @Override
    public String getAuthor() {
        return "Smirty_228";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        String key = params == null ? "" : params.toLowerCase();
        // Счётчики миров — без привязки к игроку (для TAB header)
        String worldCount = switch (key) {
            case "online_world", "online_surv", "surv" -> countIn("world");
            case "online_event", "event" -> countIn("event");
            case "online_lobby", "lobby" -> countIn("lobby");
            default -> null;
        };
        if (worldCount != null) return worldCount;

        if (player == null) return "";
        ClassDefinition def = plugin.service().getPlayerClass(player);
        return switch (key) {
            case "class", "id" -> def == null ? "" : def.id();
            case "display", "name" -> def == null ? "" : ColorUtil.legacy(def.display());
            // §r после расы — иначе цвет красит иконку префикса в TAB/чате
            case "tab" -> def == null || def.tab().isEmpty() ? "" : ColorUtil.legacy(def.tab()) + "§r ";
            case "scale" -> def == null ? "1.0" : String.valueOf(def.scale());
            case "has_smeltery", "mithril_smeltery" ->
                    BydloClassesAPI.canUseMithrilSmeltery(player) ? "yes" : "no";
            default -> "";
        };
    }

    private static String countIn(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return "0";
        return String.valueOf(world.getPlayers().size());
    }
}
