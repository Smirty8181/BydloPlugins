package ru.bydloland.fishing;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

public enum RodType {
    VANILLA("vanilla", "обычная удочка"),
    OAK("oak", "дубовая удочка"),
    STEEL("steel", "стальная удочка"),
    NETHER("nether", "адская удочка"),
    ABYSS("abyss", "удочка бездны");

    public final String id;
    public final String title;

    RodType(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public boolean lava() {
        return cfg().getBoolean("lava", this == NETHER || this == ABYSS);
    }

    public int window() {
        return Math.max(3, cfg().getInt("window", cfg().getInt("green", 6)));
    }

    public double fill() {
        return Math.max(0.004, cfg().getDouble("fill", 0.016));
    }

    public double drain() {
        return Math.max(0.004, cfg().getDouble("drain", 0.014));
    }

    public double fishSpeed() {
        return Math.max(0.15, cfg().getDouble("fish-speed", 0.4));
    }

    public double dashChance() {
        return Math.max(0.02, cfg().getDouble("dash", 0.08));
    }

    public double lift() {
        return Math.max(0.15, cfg().getDouble("lift", 0.38));
    }

    public double fall() {
        return Math.max(0.12, cfg().getDouble("fall", 0.32));
    }

    public int waitMin() {
        return Math.max(10, cfg().getInt("wait-min", 40));
    }

    public int waitMax() {
        return Math.max(waitMin() + 1, cfg().getInt("wait-max", 120));
    }

    public String ceId() {
        return this == VANILLA ? null : "bydlo:rod_" + id;
    }

    private ConfigurationSection cfg() {
        ConfigurationSection s = BydloFishingPlugin.get().getConfig().getConfigurationSection("rods." + id);
        return s != null ? s : BydloFishingPlugin.get().getConfig().createSection("rods." + id);
    }

    public static RodType of(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD) return null;
        String ce = CeItems.ceId(item);
        if (ce != null) {
            String low = ce.toLowerCase();
            if (low.endsWith("rod_abyss") || low.contains("rod_abyss")) return ABYSS;
            if (low.endsWith("rod_nether") || low.contains("rod_nether")) return NETHER;
            if (low.endsWith("rod_steel") || low.contains("rod_steel")) return STEEL;
            if (low.endsWith("rod_oak") || low.contains("rod_oak")) return OAK;
        }
        return VANILLA;
    }

    public static RodType byName(String raw) {
        if (raw == null) return null;
        String s = raw.toLowerCase();
        return switch (s) {
            case "oak", "дуб", "дубовая" -> OAK;
            case "steel", "сталь", "стальная" -> STEEL;
            case "nether", "ад", "адская", "lava" -> NETHER;
            case "abyss", "бездна", "бездны" -> ABYSS;
            case "vanilla", "обычная" -> VANILLA;
            default -> null;
        };
    }
}
