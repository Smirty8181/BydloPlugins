package ru.bydloland.tattoos;

import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.connections.model.MineSkinResponse;
import net.skinsrestorer.api.property.SkinApplier;
import net.skinsrestorer.api.property.SkinIdentifier;
import net.skinsrestorer.api.property.SkinProperty;
import net.skinsrestorer.api.property.SkinVariant;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

public final class SkinBridge {
    private final BydloTattoosPlugin plugin;
    private final MineSkinClient mineskin;
    private SkinsRestorer api;

    public SkinBridge(BydloTattoosPlugin plugin) {
        this.plugin = plugin;
        this.mineskin = new MineSkinClient(plugin);
        if (Bukkit.getPluginManager().getPlugin("SkinsRestorer") != null) {
            try {
                api = SkinsRestorerProvider.get();
            } catch (Exception ex) {
                plugin.getLogger().warning("SkinsRestorer API не поднялся: " + ex.getMessage());
            }
        } else {
            plugin.getLogger().warning("SkinsRestorer не найден — тату не наложатся на скин");
        }
    }

    public boolean ready() {
        return api != null;
    }

    public SkinSnapshot current(Player player) {
        if (api == null) return null;
        try {
            Optional<SkinProperty> opt = api.getPlayerStorage()
                    .getSkinForPlayer(player.getUniqueId(), player.getName());
            if (opt.isEmpty()) return null;
            SkinProperty prop = opt.get();
            String json = new String(Base64.getDecoder().decode(prop.getValue()), StandardCharsets.UTF_8);
            String url = extractUrl(json);
            SkinVariant variant = json.contains("\"slim\"") ? SkinVariant.SLIM : SkinVariant.CLASSIC;
            return new SkinSnapshot(prop.getValue(), prop.getSignature(), url, variant);
        } catch (Exception ex) {
            plugin.getLogger().warning("Не прочитал скин " + player.getName() + ": " + ex.getMessage());
            return null;
        }
    }

    public BufferedImage download(String url) throws Exception {
        try (InputStream in = URI.create(url).toURL().openStream()) {
            return Pngs.ensureArgb(ImageIO.read(in));
        }
    }

    public SkinProperty upload(BufferedImage image, SkinVariant variant) throws Exception {
        try {
            return mineskin.upload(image, variant);
        } catch (Exception first) {
            plugin.getLogger().warning("Прямой MineSkin не вышел: " + first.getMessage());
            try {
                MineSkinResponse resp = api.getMineSkinAPI().genSkin(image, variant);
                return resp.getProperty();
            } catch (Exception second) {
                throw new IllegalStateException(first.getMessage() + " / SR: " + second.getMessage(), first);
            }
        }
    }

    public void apply(Player player, SkinProperty property) {
        String key = "bydlo-tattoo-" + player.getUniqueId();
        api.getSkinStorage().setCustomSkinData(key, property);
        SkinIdentifier id = SkinIdentifier.ofCustom(key);
        api.getPlayerStorage().setSkinIdOfPlayer(player.getUniqueId(), id);
        SkinApplier<Player> applier = api.getSkinApplier(Player.class);
        applier.applySkin(player, property);
    }

    public void restore(Player player, String value, String signature) {
        if (value == null || signature == null) {
            api.getPlayerStorage().removeSkinIdOfPlayer(player.getUniqueId());
            try {
                api.getSkinApplier(Player.class).applySkin(player);
            } catch (Exception ignored) {
            }
            return;
        }
        SkinProperty prop = SkinProperty.of(value, signature);
        String key = "bydlo-tattoo-base-" + player.getUniqueId();
        api.getSkinStorage().setCustomSkinData(key, prop);
        api.getPlayerStorage().setSkinIdOfPlayer(player.getUniqueId(), SkinIdentifier.ofCustom(key));
        api.getSkinApplier(Player.class).applySkin(player, prop);
    }

    private static String extractUrl(String json) {
        int i = json.indexOf("\"url\"");
        if (i < 0) return null;
        int q1 = json.indexOf('"', i + 5);
        int q2 = json.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) return null;
        return json.substring(q1 + 1, q2).replace("\\/", "/");
    }

    public record SkinSnapshot(String value, String signature, String url, SkinVariant variant) {
    }

    public UUID unused() {
        return UUID.randomUUID();
    }
}
