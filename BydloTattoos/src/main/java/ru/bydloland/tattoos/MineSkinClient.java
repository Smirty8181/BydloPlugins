package ru.bydloland.tattoos;

import net.skinsrestorer.api.property.SkinProperty;
import net.skinsrestorer.api.property.SkinVariant;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class MineSkinClient {
    private static final URI GENERATE = URI.create("https://api.mineskin.org/v2/generate");
    private static final String AGENT = "BydloTattoos/1.2.5 (SkinsRestorer/MineSkinAPI)";
    private static final Object LOCK = new Object();
    private static long nextAllowedAt;
    private static final Map<String, SkinProperty> CACHE = new ConcurrentHashMap<>();

    private final BydloTattoosPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    MineSkinClient(BydloTattoosPlugin plugin) {
        this.plugin = plugin;
    }

    SkinProperty upload(BufferedImage image, SkinVariant variant) throws Exception {
        byte[] png = toPng(SkinCompositor.to64(image));
        if (png.length < 80) {
            throw new IllegalStateException("пустой png скина");
        }
        String cacheKey = sha256(png) + ":" + (variant == null ? "classic" : variant.name());
        SkinProperty cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        String key = plugin.getConfig().getString("mineskin-api-key", "");
        if (key != null) key = key.trim();
        if ("key".equalsIgnoreCase(key)) key = "";

        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            waitTurn();
            HttpResponse<String> resp;
            try {
                resp = postPng(png, variant, key);
            } catch (Exception ex) {
                last = ex;
                plugin.getLogger().warning("MineSkin сеть (" + attempt + "/5): " + ex.getMessage());
                Thread.sleep(2000L * attempt);
                continue;
            }
            String body = resp.body() == null ? "" : resp.body();
            int status = resp.statusCode();
            long delay = parseDelayMs(body);
            if (status == 429 || body.contains("\"rate_limit\"")) {
                plugin.getLogger().info("MineSkin лимит, жду " + Math.max(delay, 1200) + "мс");
                markWait(Math.max(delay, 1200));
                last = new IllegalStateException("rate_limit");
                continue;
            }
            if (status >= 200 && status < 300) {
                String value = jsonString(body, "value");
                String signature = jsonString(body, "signature");
                if (value != null && signature != null && value.length() > 20) {
                    markWait(Math.max(delay, hasKey(key) ? 400 : 1500));
                    SkinProperty prop = SkinProperty.of(value, signature);
                    CACHE.put(cacheKey, prop);
                    return prop;
                }
            }
            String err = jsonString(body, "code");
            String msg = jsonString(body, "message");
            last = new IllegalStateException("HTTP " + status + " " + (err == null ? "" : err)
                    + " " + (msg == null ? snippet(body) : msg));
            plugin.getLogger().warning("MineSkin отказ (" + attempt + "/5): " + last.getMessage());
            if (status >= 400 && status < 500 && status != 429) break;
            Thread.sleep(2000L * attempt);
        }
        throw last != null ? last : new IllegalStateException("MineSkin не ответил");
    }

    private HttpResponse<String> postPng(byte[] png, SkinVariant variant, String key) throws Exception {
        String boundary = "----bydlo" + UUID.randomUUID().toString().replace("-", "");
        String varName = variant == SkinVariant.SLIM ? "slim" : "classic";
        byte[] body = multipart(boundary, png, varName);
        HttpRequest.Builder b = HttpRequest.newBuilder(GENERATE)
                .timeout(Duration.ofSeconds(45))
                .header("User-Agent", AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (key != null && !key.isEmpty()) {
            b.header("Authorization", "Bearer " + key);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static byte[] multipart(String boundary, byte[] png, String variant) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String dash = "--" + boundary + "\r\n";
        out.write((dash + "Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(png);
        out.write(("\r\n" + dash + "Content-Disposition: form-data; name=\"variant\"\r\n\r\n"
                + variant + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write((dash + "Content-Disposition: form-data; name=\"visibility\"\r\n\r\n"
                + "unlisted\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static byte[] toPng(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static String sha256(byte[] png) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(png);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static boolean hasKey(String key) {
        return key != null && !key.isBlank();
    }

    private static void waitTurn() throws InterruptedException {
        long wait;
        synchronized (LOCK) {
            wait = Math.max(0, nextAllowedAt - System.currentTimeMillis());
        }
        if (wait > 0) Thread.sleep(wait);
    }

    private static void markWait(long extraMs) {
        synchronized (LOCK) {
            nextAllowedAt = Math.max(nextAllowedAt, System.currentTimeMillis() + extraMs);
        }
    }

    private static long parseDelayMs(String body) {
        if (body == null) return 6500;
        int i = body.indexOf("\"relative\"");
        if (i < 0) return 6500;
        int colon = body.indexOf(':', i);
        if (colon < 0) return 6500;
        int end = colon + 1;
        while (end < body.length() && Character.isDigit(body.charAt(end))) end++;
        try {
            long v = Long.parseLong(body.substring(colon + 1, end).trim());
            return v < 1000 ? v * 1000 : v;
        } catch (Exception ex) {
            return 6500;
        }
    }

    private static String jsonString(String json, String key) {
        String a = "\"" + key + "\":\"";
        String b = "\"" + key + "\": \"";
        int i = json.indexOf(a);
        int skip = a.length();
        if (i < 0) {
            i = json.indexOf(b);
            skip = b.length();
        }
        if (i < 0) return null;
        int from = i + skip;
        int to = json.indexOf('"', from);
        if (to < 0) return null;
        return json.substring(from, to);
    }

    private static String snippet(String body) {
        if (body == null) return "";
        String t = body.replace('\n', ' ');
        return t.length() > 180 ? t.substring(0, 180) : t;
    }
}
