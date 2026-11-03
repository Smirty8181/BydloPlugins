package ru.bydloland.tattoos;

import net.skinsrestorer.api.property.SkinProperty;
import net.skinsrestorer.api.property.SkinVariant;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class TattooService {
    private final BydloTattoosPlugin plugin;
    private final TattooStorage storage;
    private final TattooCatalog catalog;
    private final SkinBridge skins;

    public TattooService(BydloTattoosPlugin plugin, TattooStorage storage, TattooCatalog catalog, SkinBridge skins) {
        this.plugin = plugin;
        this.storage = storage;
        this.catalog = catalog;
        this.skins = skins;
    }

    public boolean isArtist(Player player) {
        if (player.hasPermission("bydlo.tattoo.admin")) return true;
        if (player.hasPermission("bydlo.tattoo.artist")) return true;
        return storage.isListedArtist(player);
    }

    public void setArtist(Player target, boolean on) {
        storage.setArtist(target, on);
        if (on) {
            target.getInventory().addItem(MachineItem.create(plugin));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + target.getName() + " permission set bydlo.tattoo.artist true");
            syncPrefix(target, true);
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + target.getName() + " permission unset bydlo.tattoo.artist");
            syncPrefix(target, false);
        }
    }

    public void setArtistName(String name, boolean on) {
        Player online = Players.byName(name);
        if (online != null) {
            setArtist(online, on);
            return;
        }
        storage.setArtistName(name, on);
        String nick = name.trim();
        if (on) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + nick + " permission set bydlo.tattoo.artist true");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "префикс set " + nick + " tattoo_master");
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + nick + " permission unset bydlo.tattoo.artist");
        }
    }

    public void apply(Player artist, Player client, TattooDef def, TattooSlot slot, List<Integer> colors, Consumer<String> done) {
        if (!skins.ready()) {
            done.accept("SkinsRestorer не работает — тату на скин не поставить.");
            return;
        }
        if (!def.slots().contains(slot)) {
            done.accept("Эту наколку сюда не бьют.");
            return;
        }
        if (!takeCost(artist, "cost")) {
            done.accept("Нужны чернила (чернильный мешок).");
            return;
        }
        PlayerTattoos data = storage.get(client.getUniqueId());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ensureOriginal(client, data);
                data.add(def.id(), slot, artist.getUniqueId(), null, colors);
                SkinProperty prop = composeAndUpload(data);
                data.composeRev = PlayerTattoos.CURRENT_REV;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    skins.apply(client, prop);
                    storage.save();
                    done.accept(null);
                });
            } catch (Exception ex) {
                plugin.getLogger().warning("Тату не наложилась: " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> done.accept("MineSkin не подписал скин. Подожди 10 сек и ещё раз."));
            }
        });
    }

    public void applyCustom(Player artist, Player client, TattooSlot slot, BufferedImage stamp, Consumer<String> done) {
        if (!skins.ready()) {
            done.accept("SkinsRestorer не работает — тату на скин не поставить.");
            return;
        }
        if (stamp == null) {
            done.accept("Пустой рисунок.");
            return;
        }
        if (!takeCost(artist, "cost")) {
            done.accept("Нужны чернила (чернильный мешок).");
            return;
        }
        boolean slim = "SLIM".equalsIgnoreCase(storage.get(client.getUniqueId()).variant);
        BufferedImage inked = SkinCompositor.inkify(stamp, slot.width(slim), slot.h);
        String b64 = Pngs.encode(inked);
        if (b64 == null) {
            done.accept("Не сохранил рисунок.");
            return;
        }
        PlayerTattoos data = storage.get(client.getUniqueId());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ensureOriginal(client, data);
                data.addCustom(slot, artist.getUniqueId(), b64);
                SkinProperty prop = composeAndUpload(data);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    skins.apply(client, prop);
                    storage.save();
                    done.accept(null);
                });
            } catch (Exception ex) {
                plugin.getLogger().warning("Свой рисунок не наложился: " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> done.accept("MineSkin не подписал скин. Подожди 10 сек и ещё раз."));
            }
        });
    }

    public void remove(Player artist, Player client, TattooSlot slot, boolean all, Consumer<String> done) {
        if (!skins.ready()) {
            done.accept("SkinsRestorer не работает.");
            return;
        }
        if (!takeCost(artist, "remove-cost") && !artist.hasPermission("bydlo.tattoo.admin")) {
            done.accept("Чтобы свести, нужны золотые слитки.");
            return;
        }
        PlayerTattoos data = storage.get(client.getUniqueId());
        if (all) data.tattoos.clear();
        else if (slot != null) data.removeSlot(slot);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (data.tattoos.isEmpty()) {
                    String v = data.originalValue;
                    String s = data.originalSignature;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        skins.restore(client, v, s);
                        storage.save();
                        done.accept(null);
                    });
                    return;
                }
                SkinProperty prop = composeAndUpload(data);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    skins.apply(client, prop);
                    storage.save();
                    done.accept(null);
                });
            } catch (Exception ex) {
                plugin.getLogger().warning("Не снял тату: " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> done.accept("Не вышло свести. Попробуй ещё раз."));
            }
        });
    }

    private void ensureOriginal(Player client, PlayerTattoos data) throws Exception {
        if (data.originalValue != null && data.originalSignature != null) return;
        SkinBridge.SkinSnapshot snap = skins.current(client);
        if (snap == null || snap.url() == null) {
            throw new IllegalStateException("нет скина");
        }
        data.originalValue = snap.value();
        data.originalSignature = snap.signature();
        data.variant = snap.variant().name();
    }

    private SkinProperty composeAndUpload(PlayerTattoos data) throws Exception {
        String json = new String(java.util.Base64.getDecoder().decode(data.originalValue), java.nio.charset.StandardCharsets.UTF_8);
        String url = null;
        int i = json.indexOf("\"url\"");
        if (i >= 0) {
            int q1 = json.indexOf('"', i + 5);
            int q2 = json.indexOf('"', q1 + 1);
            if (q1 > 0 && q2 > q1) url = json.substring(q1 + 1, q2).replace("\\/", "/");
        }
        if (url == null) throw new IllegalStateException("нет url скина");
        BufferedImage base = skins.download(url);
        boolean slim = "SLIM".equalsIgnoreCase(data.variant);
        List<SkinCompositor.Stamp> stamps = new ArrayList<>();
        for (TattooRecord rec : data.tattoos) {
            BufferedImage img = null;
            if (rec.custom()) {
                img = Pngs.decode(rec.pngB64());
            } else {
                TattooDef def = catalog.get(rec.id());
                if (def != null) img = InkPalette.paint(def.stamp(), rec.colors());
            }
            if (img == null) continue;
            stamps.add(new SkinCompositor.Stamp(rec.slot(), img));
        }
        BufferedImage out = SkinCompositor.apply(base, stamps, slim);
        SkinVariant variant;
        try {
            variant = SkinVariant.valueOf(data.variant);
        } catch (Exception ex) {
            variant = SkinVariant.CLASSIC;
        }
        return skins.upload(out, variant);
    }

    private boolean takeCost(Player player, String path) {
        if (player.hasPermission("bydlo.tattoo.admin")) return true;
        String matName = plugin.getConfig().getString(path + ".material", "INK_SAC");
        int amount = plugin.getConfig().getInt(path + ".amount", 1);
        Material mat;
        try {
            mat = Material.valueOf(matName);
        } catch (IllegalArgumentException ex) {
            return true;
        }
        if (!player.getInventory().containsAtLeast(new ItemStack(mat), amount)) return false;
        player.getInventory().removeItem(new ItemStack(mat, amount));
        return true;
    }

    private void syncPrefix(Player target, boolean on) {
        Plugin prefixes = Bukkit.getPluginManager().getPlugin("BydloPrefixes");
        if (prefixes == null || !prefixes.isEnabled()) {
            if (on) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "префикс set " + target.getName() + " tattoo_master");
            }
            return;
        }
        try {
            Object service = prefixes.getClass().getMethod("service").invoke(prefixes);
            if (on) {
                try {
                    service.getClass().getMethod("setPrefix", Player.class, String.class)
                            .invoke(service, target, "tattoo_master");
                } catch (NoSuchMethodException ex) {
                    service.getClass().getMethod("setPrefix", UUID.class, String.class)
                            .invoke(service, target.getUniqueId(), "tattoo_master");
                }
                return;
            }
            Object def = service.getClass().getMethod("getPlayerPrefix", Player.class).invoke(service, target);
            if (def == null) return;
            String id = String.valueOf(def.getClass().getMethod("id").invoke(def));
            if ("tattoo_master".equalsIgnoreCase(id)) {
                service.getClass().getMethod("clearPrefix", UUID.class).invoke(service, target.getUniqueId());
                try {
                    service.getClass().getMethod("clearPrefixByName", String.class)
                            .invoke(service, target.getName());
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable ex) {
            plugin.getLogger().warning("Префикс мастера: " + ex.getMessage());
        }
    }

    public TattooCatalog catalog() {
        return catalog;
    }

    public TattooStorage storage() {
        return storage;
    }

    public void refresh(Player client, Consumer<String> done) {
        if (!skins.ready()) {
            done.accept("SkinsRestorer не работает.");
            return;
        }
        PlayerTattoos data = storage.get(client.getUniqueId());
        if (data.tattoos.isEmpty() || data.originalValue == null) {
            done.accept(null);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                SkinProperty prop = composeAndUpload(data);
                data.composeRev = PlayerTattoos.CURRENT_REV;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    skins.apply(client, prop);
                    storage.save();
                    done.accept(null);
                });
            } catch (Exception ex) {
                plugin.getLogger().warning("Не обновил тату " + client.getName() + ": " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> done.accept("Не вышло обновить скин."));
            }
        });
    }

    public UUID idOf(Player p) {
        return p.getUniqueId();
    }
}
