package ru.bydloland.mage;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Reflection bridge to BydloClassesAPI. */
public final class ClassesBridge {

    private ClassesBridge() {}

    public static boolean canUseMageGear(Player player) {
        return call("canUseMageGear", player);
    }

    public static boolean canUseMagicTower(Player player) {
        return call("canUseMagicTower", player);
    }

    public static boolean canUseEtherium(Player player) {
        return call("canUseEtherium", player);
    }

    private static boolean call(String method, Player player) {
        try {
            Class<?> api = Class.forName("ru.bydloland.classes.BydloClassesAPI");
            Object r = api.getMethod(method, Player.class).invoke(null, player);
            return r instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean classesPresent() {
        return Bukkit.getPluginManager().isPluginEnabled("BydloClasses");
    }
}
