package ru.bydloland.discs.download;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.bydloland.discs.BydloDiscsPlugin;
import ru.bydloland.discs.item.DiscItems;
import ru.bydloland.discs.store.PlayerTracks;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class DownloadPipeline {
    private final BydloDiscsPlugin plugin;
    private final PlayerTracks tracks;
    private final DiscItems items;
    private final Semaphore slots;
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

    public DownloadPipeline(BydloDiscsPlugin plugin, PlayerTracks tracks, DiscItems items) {
        this.plugin = plugin;
        this.tracks = tracks;
        this.items = items;
        this.slots = new Semaphore(Math.max(1, plugin.getConfig().getInt("download-slots", 1)));
    }

    public boolean isBusy(UUID id) {
        return busy.contains(id);
    }

    public boolean isAllowedUrl(String url) {
        try {
            URI uri = URI.create(normalizeUrl(url));
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            List<String> allowed = plugin.getConfig().getStringList("allowed-hosts");
            for (String a : allowed) {
                if (host.equals(a) || host.endsWith("." + a)) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Strip chat junk: <url>, trailing punctuation, playlist junk. */
    private static String normalizeUrl(String raw) {
        String u = raw == null ? "" : raw.trim();
        if (u.startsWith("<") && u.endsWith(">")) u = u.substring(1, u.length() - 1).trim();
        while (!u.isEmpty() && ".,);]>\"'".indexOf(u.charAt(u.length() - 1)) >= 0) {
            u = u.substring(0, u.length() - 1);
        }
        // keep only the video id for youtube watch URLs with &list=...
        int list = u.indexOf("&list=");
        if (list > 0 && u.contains("youtube.com/watch")) u = u.substring(0, list);
        int start = u.indexOf("&start_radio=");
        if (start > 0) u = u.substring(0, start);
        return u;
    }

    public void start(Player player, String url) {
        UUID id = player.getUniqueId();
        if (busy.contains(id)) {
            player.sendMessage(plugin.prefix() + plugin.msg("busy"));
            return;
        }
        if (!isAllowedUrl(url)) {
            player.sendMessage(plugin.prefix() + plugin.msg("bad-url"));
            return;
        }
        if (tracks.onCooldown(id) && !player.hasPermission("bydlo.disc.bypasscooldown")) {
            player.sendMessage(plugin.prefix() + plugin.msg("cooldown")
                    .replace("%time%", PlayerTracks.formatLeft(tracks.cooldownLeftSec(id))));
            return;
        }
        busy.add(id);
        final String cleanUrl = normalizeUrl(url);
        player.sendMessage(plugin.prefix() + plugin.msg("downloading"));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                slots.acquire();
                Result result = download(id, cleanUrl);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    busy.remove(id);
                    Player online = Bukkit.getPlayer(id);
                    if (result == null) {
                        if (online != null) online.sendMessage(plugin.prefix() + plugin.msg("failed"));
                        return;
                    }
                    tracks.saveTrack(id, result.title, url, result.pcm, result.ogg);
                    if (online != null) {
                        online.getInventory().addItem(items.create(id, online.getName(), result.title));
                        online.sendMessage(plugin.prefix() + plugin.msg("done").replace("%title%", result.title));
                        online.sendMessage(plugin.prefix() + plugin.msg("no-voice"));
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("download failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    busy.remove(id);
                    Player online = Bukkit.getPlayer(id);
                    if (online != null) online.sendMessage(plugin.prefix() + plugin.msg("failed"));
                });
            } finally {
                slots.release();
            }
        });
    }

    private record Result(String title, Path pcm, Path ogg) {}

    private Result download(UUID id, String url) throws Exception {
        Path work = abs(tracks.tracksDir().resolve("tmp_" + id));
        Files.createDirectories(work);
        Path outTpl = work.resolve("audio.%(ext)s");
        int maxSec = plugin.getConfig().getInt("max-duration-seconds", 360);

        Path ytdlp = resolveTool("yt-dlp", "yt-dlp", "yt-dlp.exe");
        Path ffmpeg = resolveTool("ffmpeg", "ffmpeg", "ffmpeg.exe");
        if (ytdlp == null || ffmpeg == null) {
            plugin.getLogger().severe("yt-dlp or ffmpeg not found in plugins/BydloDiscs/bin or PATH");
            return null;
        }

        Path deno = abs(plugin.getDataFolder().toPath().resolve("bin").resolve("deno"));
        boolean hasDeno = Files.isRegularFile(deno);
        if (hasDeno) deno.toFile().setExecutable(true);

        // YouTube 2026: android_vr + bestaudio (251/webm) → HTTP 403 without PO token.
        // Format 18 (360p mp4) still works without JS. ffmpeg then takes only audio.
        String[][] attempts = hasDeno
                ? new String[][]{
                    {"android", "18/140/bestaudio/best"},
                    {"tv", "18/140/bestaudio/best"},
                    {"", "18/140/bestaudio/best"}
                }
                : new String[][]{
                    {"android", "18/140/bestaudio/best"},
                    {"android_vr", "18"},
                    {"tv", "18"},
                    {"ios", "140/18/bestaudio/best"}
                };

        String title = "Трек";
        boolean downloadedOk = false;
        for (String[] attempt : attempts) {
            java.util.ArrayList<String> base = ytdlpBase(ytdlp, ffmpeg, deno, hasDeno, attempt[0]);
            if ("Трек".equals(title)) {
                java.util.ArrayList<String> titleCmd = new java.util.ArrayList<>(base);
                titleCmd.add("--print");
                titleCmd.add("%(title).80s");
                titleCmd.add("--skip-download");
                titleCmd.add(url);
                String got = runCapture(titleCmd, 90);
                if (got != null && !got.isBlank() && !got.toLowerCase(Locale.ROOT).contains("error")) {
                    title = got.replace('\n', ' ').trim();
                }
            }
            java.util.ArrayList<String> dlCmd = new java.util.ArrayList<>(base);
            dlCmd.add("-f");
            dlCmd.add(attempt[1]);
            dlCmd.add("--max-filesize");
            dlCmd.add("40M");
            dlCmd.add("-o");
            dlCmd.add(outTpl.toString());
            dlCmd.add("--force-overwrites");
            dlCmd.add(url);
            plugin.getLogger().info("yt-dlp try client=" + (attempt[0].isBlank() ? "default" : attempt[0])
                    + " fmt=" + attempt[1]);
            int code = run(dlCmd, 240);
            if (code == 0) {
                downloadedOk = true;
                break;
            }
            plugin.getLogger().warning("yt-dlp exit " + code + " client=" + attempt[0]);
        }
        if (!downloadedOk) {
            plugin.getLogger().warning("yt-dlp failed all clients for " + url);
            return null;
        }

        Path downloaded = Files.list(work)
                .filter(p -> !p.getFileName().toString().startsWith("audio.%"))
                .filter(p -> {
                    String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    return n.startsWith("audio.") || (!n.endsWith(".pcm") && !n.endsWith(".ogg"));
                })
                .findFirst()
                .orElse(null);
        // find newest non-dir file
        downloaded = Files.list(work)
                .filter(Files::isRegularFile)
                .filter(p -> !p.toString().endsWith(".pcm") && !p.toString().endsWith(".ogg"))
                .findFirst()
                .orElse(null);
        if (downloaded == null || !Files.isRegularFile(downloaded)) return null;

        Path pcm = abs(tracks.tracksDir().resolve(id + ".pcm"));
        Path ogg = abs(tracks.tracksDir().resolve(id + ".ogg"));
        Files.deleteIfExists(pcm);
        Files.deleteIfExists(ogg);

        int ff = run(List.of(
                ffmpeg.toString(), "-y",
                "-i", downloaded.toString(),
                "-t", String.valueOf(maxSec),
                "-ac", "1",
                "-ar", "48000",
                "-f", "s16le",
                pcm.toString()
        ), 120);
        if (ff != 0 || !Files.isRegularFile(pcm)) return null;

        run(List.of(
                ffmpeg.toString(), "-y",
                "-f", "s16le", "-ar", "48000", "-ac", "1",
                "-i", pcm.toString(),
                "-c:a", "libvorbis", "-q:a", "4",
                ogg.toString()
        ), 120);

        // cleanup tmp
        try {
            Files.walk(work).sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}

        return new Result(title, pcm, ogg);
    }

    private java.util.ArrayList<String> ytdlpBase(Path ytdlp, Path ffmpeg, Path deno, boolean hasDeno, String client) {
        java.util.ArrayList<String> base = new java.util.ArrayList<>();
        base.add(ytdlp.toString());
        base.add("--no-playlist");
        base.add("--no-warnings");
        base.add("--force-ipv4");
        base.add("--retries");
        base.add("3");
        base.add("--ffmpeg-location");
        base.add(ffmpeg.getParent().toString());
        if (client != null && !client.isBlank()) {
            base.add("--extractor-args");
            base.add("youtube:player_client=" + client);
        }
        if (hasDeno && (client == null || client.isBlank() || client.startsWith("web") || "tv".equals(client))) {
            base.add("--js-runtimes");
            base.add("deno:" + deno);
        }
        return base;
    }

    private Path resolveTool(String configKey, String unixName, String winName) {
        String conf = plugin.getConfig().getString("tools." + configKey, "");
        if (conf != null && !conf.isBlank()) {
            Path p = Path.of(conf);
            if (Files.isRegularFile(p)) return abs(p);
            Path rel = plugin.getDataFolder().toPath().resolve(conf);
            if (Files.isRegularFile(rel)) return abs(rel);
        }
        Path bin = plugin.getDataFolder().toPath().resolve("bin");
        Path a = bin.resolve(winName);
        Path b = bin.resolve(unixName);
        if (Files.isRegularFile(a)) {
            a.toFile().setExecutable(true);
            return abs(a);
        }
        if (Files.isRegularFile(b)) {
            b.toFile().setExecutable(true);
            return abs(b);
        }
        // PATH
        String path = System.getenv("PATH");
        if (path != null) {
            for (String part : path.split(java.io.File.pathSeparator)) {
                Path pa = Path.of(part, winName);
                Path pb = Path.of(part, unixName);
                if (Files.isRegularFile(pa)) return abs(pa);
                if (Files.isRegularFile(pb)) return abs(pb);
            }
        }
        return null;
    }

    private static Path abs(Path p) {
        return p.toAbsolutePath().normalize();
    }

    private int run(List<String> cmd, int timeoutSec) throws Exception {
        plugin.getLogger().info("exec: " + String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.directory(plugin.getDataFolder().getAbsoluteFile());
        Process p = pb.start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.length() < 200) plugin.getLogger().info("[tool] " + line);
            }
        }
        boolean ok = p.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!ok) {
            p.destroyForcibly();
            return -1;
        }
        return p.exitValue();
    }

    private String runCapture(List<String> cmd, int timeoutSec) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.directory(plugin.getDataFolder().getAbsoluteFile());
        Process p = pb.start();
        String out;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            out = br.lines().collect(Collectors.joining("\n")).trim();
        }
        boolean ok = p.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!ok) {
            p.destroyForcibly();
            return null;
        }
        return p.exitValue() == 0 ? out : null;
    }
}
