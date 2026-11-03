package ru.bydloland.classes;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ClassDefinition {

    private final String id;
    private final String display;
    private final String tab;
    private final double scale;
    private final List<EffectSpec> effects;
    private final Set<String> flags;
    private final Material icon;
    /** CraftEngine item id, e.g. bydlo:race_icon_dwarf */
    private final String iconCe;
    private final List<String> lore;
    private final boolean selectable;

    public ClassDefinition(String id, String display, String tab, double scale,
                           List<EffectSpec> effects, Set<String> flags, Material icon,
                           String iconCe, List<String> lore, boolean selectable) {
        this.id = id;
        this.display = display;
        this.tab = tab;
        this.scale = scale;
        this.effects = effects;
        this.flags = flags;
        this.icon = icon;
        this.iconCe = iconCe;
        this.lore = lore;
        this.selectable = selectable;
    }

    public static ClassDefinition from(String id, ConfigurationSection c) {
        String display = c.getString("display", id);
        String tab = c.getString("tab", "");
        double scale = c.getDouble("scale", 1.0);
        Material icon = Material.matchMaterial(c.getString("icon", "PAPER"));
        if (icon == null) icon = Material.PAPER;
        String iconCe = c.getString("icon-ce", null);
        if (iconCe != null && iconCe.isBlank()) iconCe = null;
        List<String> lore = c.getStringList("lore");
        boolean selectable = c.getBoolean("selectable", true);

        Set<String> flags = new LinkedHashSet<>();
        for (String f : c.getStringList("flags")) {
            flags.add(f.toLowerCase(Locale.ROOT));
        }

        List<EffectSpec> effects = new ArrayList<>();
        for (java.util.Map<?, ?> map : c.getMapList("effects")) {
            String type = String.valueOf(map.get("type"));
            int amp = map.get("amplifier") instanceof Number n ? n.intValue() : 0;
            boolean ambient = !(map.get("ambient") instanceof Boolean b) || b;
            boolean particles = map.get("particles") instanceof Boolean pb && pb;
            boolean iconFlag = map.get("icon") instanceof Boolean ib && ib;
            effects.add(new EffectSpec(type, amp, ambient, particles, iconFlag));
        }

        return new ClassDefinition(id, display, tab, scale, effects, flags, icon, iconCe, lore, selectable);
    }

    public String id() { return id; }
    public String display() { return display; }
    public String tab() { return tab; }
    public double scale() { return scale; }
    public List<EffectSpec> effects() { return Collections.unmodifiableList(effects); }
    public Set<String> flags() { return Collections.unmodifiableSet(flags); }
    public Material icon() { return icon; }
    public String iconCe() { return iconCe; }
    public List<String> lore() { return Collections.unmodifiableList(lore); }
    public boolean selectable() { return selectable; }

    public record EffectSpec(String typeName, int amplifier, boolean ambient, boolean particles, boolean icon) {
        PotionEffectType type() {
            if (typeName == null) return null;
            String n = typeName.trim().toUpperCase(Locale.ROOT);
            if ("HASTE".equals(n) || "FAST_DIGGING".equals(n)) {
                PotionEffectType t = PotionEffectType.getByName("HASTE");
                if (t == null) t = PotionEffectType.getByName("FAST_DIGGING");
                return t;
            }
            if ("SPEED".equals(n) || "SWIFTNESS".equals(n)) {
                PotionEffectType t = PotionEffectType.getByName("SPEED");
                if (t == null) t = PotionEffectType.getByName("SWIFTNESS");
                return t;
            }
            if ("SLOW_FALLING".equals(n) || "SLOWFALLING".equals(n)) {
                PotionEffectType t = PotionEffectType.getByName("SLOW_FALLING");
                if (t == null) t = PotionEffectType.getByName("SLOWFALL");
                return t;
            }
            return PotionEffectType.getByName(n);
        }
    }
}
