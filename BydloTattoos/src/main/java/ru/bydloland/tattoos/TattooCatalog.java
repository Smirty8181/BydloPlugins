package ru.bydloland.tattoos;

import org.bukkit.configuration.ConfigurationSection;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TattooCatalog {
    private final Map<String, TattooDef> byId = new LinkedHashMap<>();

    public TattooCatalog(BydloTattoosPlugin plugin) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("tattoos");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            String file = sec.getString("file", id + ".png");
            BufferedImage stamp = load(plugin, file);
            if (stamp == null) {
                plugin.getLogger().warning("Нет текстуры тату: " + file);
                continue;
            }
            List<TattooSlot> slots = new ArrayList<>();
            for (String raw : sec.getStringList("slots")) {
                TattooSlot slot = TattooSlot.fromConfig(raw);
                if (slot != null) slots.add(slot);
            }
            if (slots.isEmpty()) continue;
            byId.put(id, new TattooDef(id, sec.getString("name", id), stamp, List.copyOf(slots)));
        }
    }

    private static BufferedImage load(BydloTattoosPlugin plugin, String file) {
        try (InputStream in = plugin.getResource("tattoos/" + file)) {
            if (in == null) return null;
            return Pngs.ensureArgb(ImageIO.read(in));
        } catch (Exception ex) {
            return null;
        }
    }

    public TattooDef get(String id) {
        return id == null ? null : byId.get(id);
    }

    public List<TattooDef> all() {
        return List.copyOf(byId.values());
    }
}
