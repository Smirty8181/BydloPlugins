package ru.bydloland.chatguard;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

public final class BydloChatGuard extends JavaPlugin {

    @Override
    public void onEnable() {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                this,
                ListenerPriority.HIGHEST,
                PacketType.Play.Server.SYSTEM_CHAT,
                PacketType.Play.Server.DISGUISED_CHAT
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (shouldHide(dump(event.getPacket()))) {
                    event.setCancelled(true);
                }
            }
        });
        getLogger().info("Plugin nags hidden from chat.");
    }

    static boolean shouldHide(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String text = raw.toLowerCase(Locale.ROOT);
        if (text.contains("tlauncher") || text.contains("sklauncher")) {
            return true;
        }
        if (text.contains("пиратский minecraft") || text.contains("offline_mode_skin_warning")) {
            return true;
        }
        if (text.contains("your version of") && text.contains("is outdated")) {
            return true;
        }
        if (text.contains("betterstructures") && text.contains("outdated")) {
            return true;
        }
        if (text.contains("craftengine") && (text.contains("outdated") || text.contains("new version") || text.contains("update available"))) {
            return true;
        }
        return false;
    }

    private static String dump(PacketContainer packet) {
        StringBuilder out = new StringBuilder();
        try {
            List<WrappedChatComponent> chats = packet.getChatComponents().getValues();
            if (chats != null) {
                for (WrappedChatComponent chat : chats) {
                    if (chat != null) {
                        out.append(chat.getJson()).append('\n');
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            for (Object value : packet.getModifier().getValues()) {
                if (value != null) {
                    out.append(value).append('\n');
                }
            }
        } catch (Throwable ignored) {
        }
        return out.toString();
    }
}
