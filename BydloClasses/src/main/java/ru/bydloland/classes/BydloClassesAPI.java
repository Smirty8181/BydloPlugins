package ru.bydloland.classes;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Публичный API для других плагинов (BydloSmeltery и т.д.).
 * Расовые проверки — только по текущей расе/флагам класса, без permission-bypass
 * (иначе OP / LuckPerms * обходят всё).
 */
public final class BydloClassesAPI {

    private BydloClassesAPI() {}

    public static boolean isEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("BydloClasses");
    }

    public static BydloClassesPlugin plugin() {
        var p = Bukkit.getPluginManager().getPlugin("BydloClasses");
        return p instanceof BydloClassesPlugin bc ? bc : null;
    }

    public static String getClassId(Player player) {
        BydloClassesPlugin p = plugin();
        if (p == null) return null;
        return p.service().getPlayerClassId(player.getUniqueId());
    }

    public static String getDisplay(Player player) {
        BydloClassesPlugin p = plugin();
        if (p == null) return "";
        ClassDefinition def = p.service().getPlayerClass(player);
        return def == null ? "" : ColorUtil.strip(def.display());
    }

    public static boolean hasFlag(Player player, String flag) {
        BydloClassesPlugin p = plugin();
        if (p == null) return false;
        return p.service().hasFlag(player, flag);
    }

    /** Мифриловая плавильня — только дворфы. */
    public static boolean canUseMithrilSmeltery(Player player) {
        String id = getClassId(player);
        return "dwarf".equalsIgnoreCase(id) || hasFlag(player, "mithril_smeltery");
    }

    /** Крафт самурайского снаряжения — только самураи. */
    public static boolean canUseSamuraiGear(Player player) {
        String id = getClassId(player);
        return "samurai".equalsIgnoreCase(id) || hasFlag(player, "samurai_gear");
    }

    /** Плавка/крафт стали — только самураи. */
    public static boolean canSmeltSteel(Player player) {
        String id = getClassId(player);
        return "samurai".equalsIgnoreCase(id) || hasFlag(player, "steel_smelting");
    }

    /** Киберпечь / титан — только нетранеры. */
    public static boolean canUseTitanSmeltery(Player player) {
        String id = getClassId(player);
        return "netrunner".equalsIgnoreCase(id) || hasFlag(player, "titan_smeltery");
    }

    /** Импланты — только нетранеры. */
    public static boolean canUseImplants(Player player) {
        String id = getClassId(player);
        return "netrunner".equalsIgnoreCase(id)
                || hasFlag(player, "implants")
                || hasFlag(player, "titan_smeltery");
    }

    /** Кастомные зачары ExcellentEnchants — только маги. */
    public static boolean canUseCustomEnchants(Player player) {
        return canUseMageGear(player);
    }

    /** Магическое снаряжение / крафт — только маги. */
    public static boolean canUseMageGear(Player player) {
        String id = getClassId(player);
        return "mage".equalsIgnoreCase(id) || hasFlag(player, "mage_gear");
    }

    /** Башня магии — только маги. */
    public static boolean canUseMagicTower(Player player) {
        String id = getClassId(player);
        return "mage".equalsIgnoreCase(id)
                || hasFlag(player, "magic_tower")
                || hasFlag(player, "mage_gear");
    }

    /** Эфириум / плавка эфириума — только маги. */
    public static boolean canUseEtherium(Player player) {
        String id = getClassId(player);
        return "mage".equalsIgnoreCase(id)
                || hasFlag(player, "etherium_smeltery")
                || hasFlag(player, "mage_gear");
    }
}
