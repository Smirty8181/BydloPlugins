package ru.bydloland.prefixes;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Мастер создания префикса через чат:
 * 1) ID  2) текст (с цветами)
 */
public final class PrefixCreateListener implements Listener {

    private enum Step { ID, TEXT }

    private record Session(Step step, String id) {}

    private final BydloPrefixesPlugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public PrefixCreateListener(BydloPrefixesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(Player admin) {
        sessions.put(admin.getUniqueId(), new Session(Step.ID, null));
        admin.sendMessage("§d[Префикс] §fСоздание. Напиши в чат §eID §7(латиница), пример: §fkorol");
        admin.sendMessage("§7Отмена: §fcancel");
    }

    public boolean isCreating(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void cancel(Player player) {
        sessions.remove(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        event.setCancelled(true);
        String msg = event.getMessage().trim();
        if (msg.equalsIgnoreCase("cancel") || msg.equalsIgnoreCase("отмена")) {
            sessions.remove(player.getUniqueId());
            player.sendMessage("§7Создание отменено");
            return;
        }

        if (session.step() == Step.ID) {
            String id = msg.toLowerCase().replace(' ', '_');
            if (!PrefixService.isValidId(id)) {
                player.sendMessage("§cПлохой ID. Только a-z 0-9 _ , 2–24 символа");
                return;
            }
            if (plugin.service().get(id) != null) {
                player.sendMessage("§eТакой ID уже есть — будет перезаписан текстом на следующем шаге");
            }
            sessions.put(player.getUniqueId(), new Session(Step.TEXT, id));
            player.sendMessage("§d[Префикс] §fТеперь напиши §eтекст §fпрефикса");
            player.sendMessage("§7Цвета: §f&d &a &b §7или §f<#F5D0FE>§7, жирный: §f&l");
            player.sendMessage("§7Пример: §d&l<#F5D0FE>&lКороль");
            return;
        }

        // TEXT — сохранение только на главном потоке
        String id = session.id();
        String text = msg;
        sessions.remove(player.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            String err = plugin.service().createOrUpdate(id, text, null, null);
            if (err != null) {
                player.sendMessage("§cОшибка: " + err);
                return;
            }
            PrefixDefinition def = plugin.service().get(id);
            player.sendMessage("§aПрефикс создан: §f" + id + " §7→ " + ColorUtil.legacy(def.display()));
            player.sendMessage("§7Выдать: §f/префикс §7(меню) или §f/префикс set <ник> " + id);
        });
    }
}
