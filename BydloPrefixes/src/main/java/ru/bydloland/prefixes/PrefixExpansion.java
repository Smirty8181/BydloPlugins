package ru.bydloland.prefixes;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public final class PrefixExpansion extends PlaceholderExpansion {

    private final BydloPrefixesPlugin plugin;

    public PrefixExpansion(BydloPrefixesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "bydloprefixes";
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
        if (player == null) return "";
        String key = params == null ? "" : params.toLowerCase();
        PrefixDefinition def = plugin.service().getPlayerPrefix(player);
        return switch (key) {
            case "tab", "prefix" -> plugin.service().tabText(player);
            case "id" -> def == null ? "" : def.id();
            case "display", "name" -> def == null ? "" : ColorUtil.legacy(def.display());
            case "has" -> def == null ? "no" : "yes";
            default -> plugin.service().tabText(player);
        };
    }
}
