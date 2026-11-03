package ru.bydloland.prison.hook;

import ru.bydloland.prison.BydloPrisonPlugin;
import ru.bydloland.prison.service.PrisonService;

/**
 * Заключённые могут говорить в войсе.
 * Мут голоса — только через BydloMod /vmute.
 */
public final class VoiceChatHook {

    private VoiceChatHook() {}

    public static void tryRegister(BydloPrisonPlugin plugin, PrisonService prison) {
        plugin.getLogger().info("Prison voice: NOT muted (use /vmute for voice mute)");
    }
}
