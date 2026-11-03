package ru.bydloland.prefixes;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.List;

public final class PrefixDefinition {
    private final String id;
    private final String tab;
    private final String display;
    private final Material icon;
    private final List<String> lore;
    /** LuckPerms group id, или null */
    private final String luckPermsGroup;

    public PrefixDefinition(String id, String tab, String display, Material icon, List<String> lore, String luckPermsGroup) {
        this.id = id;
        this.tab = tab;
        this.display = display;
        this.icon = icon;
        this.lore = lore;
        this.luckPermsGroup = luckPermsGroup == null || luckPermsGroup.isBlank() ? null : luckPermsGroup.trim();
    }

    public static PrefixDefinition from(String id, ConfigurationSection c) {
        Material icon = Material.matchMaterial(c.getString("icon", "NAME_TAG"));
        if (icon == null) icon = Material.NAME_TAG;
        String group = c.getString("luckperms-group", c.getString("group", ""));
        return new PrefixDefinition(
                id,
                c.getString("tab", id),
                c.getString("display", c.getString("tab", id)),
                icon,
                c.getStringList("lore"),
                group
        );
    }

    public String id() { return id; }
    public String tab() { return tab; }
    public String display() { return display; }
    public Material icon() { return icon; }
    public List<String> lore() { return Collections.unmodifiableList(lore); }
    public String luckPermsGroup() { return luckPermsGroup; }
}
