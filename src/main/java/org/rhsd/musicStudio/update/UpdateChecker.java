package org.rhsd.musicStudio.update;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.rhsd.musicStudio.MessageManager;
import org.rhsd.musicStudio.command.MsCommand;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// =================================================================
// 업데이트 체커
// =================================================================
// GitHub 최신 릴리스(tag)와 현재 플러그인 버전을 비교한다
// 새 버전이 있으면 콘솔에 알리고, 관리자(musicstudio.admin)가 접속할 때마다 채팅으로 알린다
// HTTP 요청은 비동기 스레드에서만 수행 — 메인 스레드 블로킹 없음
// 릴리스가 아직 없거나(404) 네트워크가 막혀 있으면 조용히 넘어간다
public final class UpdateChecker implements Listener {

    private static final String REPO = "riha-cats/Musicstudio";
    private static final String API_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String RELEASES_URL = "https://github.com/" + REPO + "/releases/latest";

    private static final Pattern TAG_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");

    private final JavaPlugin plugin;
    private final MessageManager msg;
    // 새 버전 태그. null = 최신이거나 아직 미확인 (비동기 스레드가 쓰고 메인이 읽는다)
    private volatile String latestVersion = null;

    public UpdateChecker(JavaPlugin plugin, MessageManager msg) {
        this.plugin = plugin;
        this.msg = msg;
    }

    // 비동기로 최신 릴리스를 확인한다. onEnable/reload 에서 호출
    public void check() {
        // [1] :: 기능이 꺼져있는가?
        if (!plugin.getConfig().getBoolean("update-check.enabled", true)) {
            return;
        }
        String current = plugin.getDescription().getVersion();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // [2] :: GitHub API 조회. 실패(릴리스 없음/네트워크)라면 조용히 종료
            String tag = fetchLatestTag();
            if (tag == null) {
                return;
            }
            // [3] :: 더 새로운 버전인가?
            if (isNewer(tag, current)) {
                latestVersion = tag;
                plugin.getLogger().info("새 버전이 나왔습니다: " + tag
                        + " (현재 " + current + ") — " + RELEASES_URL);
            }
        });
        // [STOP] :: 업데이트 확인 끝
    }

    public String latestVersion() {
        return latestVersion;
    }

    // 관리자가 접속하면 새 버전을 안내한다
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String latest = latestVersion;
        if (latest == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(MsCommand.PERM_ADMIN)) {
            return;
        }
        msg.send(player, "update.available",
                "latest", latest,
                "current", plugin.getDescription().getVersion(),
                "url", RELEASES_URL);
    }

    // GitHub releases/latest 의 tag_name. 실패라면? null
    private String fetchLatestTag() {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(API_URL).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            // GitHub API 는 User-Agent 필수
            conn.setRequestProperty("User-Agent", "MusicStudio-UpdateChecker");
            if (conn.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                return extractTag(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // 오프라인 서버 등. 알림만 못 띄울 뿐 치명적이지 않음
            return null;
        }
    }

    // 릴리스 JSON 에서 tag_name 만 뽑는다. 의존성 없이 정규식으로 충분
    public static String extractTag(String json) {
        if (json == null) {
            return null;
        }
        Matcher m = TAG_PATTERN.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    // remote 가 local 보다 새 버전인가? "v1.0.4" 처럼 v 접두사가 붙어도 된다
    // 숫자 마디를 앞에서부터 비교. 마디가 모자라면 0으로 취급 (1.0.3.1 > 1.0.3)
    public static boolean isNewer(String remote, String local) {
        if (remote == null || local == null) {
            return false;
        }
        int[] r = parseParts(remote);
        int[] l = parseParts(local);
        // 숫자가 하나도 없는 태그라면? 비교 불가 — 알림하지 않는다
        if (r.length == 0 || l.length == 0) {
            return false;
        }
        int len = Math.max(r.length, l.length);
        for (int i = 0; i < len; i++) {
            int rv = i < r.length ? r[i] : 0;
            int lv = i < l.length ? l[i] : 0;
            if (rv != lv) {
                return rv > lv;
            }
        }
        return false;
    }

    private static int[] parseParts(String version) {
        String[] tokens = version.trim().split("\\D+");
        int count = 0;
        int[] parts = new int[tokens.length];
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            try {
                parts[count++] = Integer.parseInt(token);
            } catch (NumberFormatException ignored) {
                // int 범위를 넘는 마디는 버린다
            }
        }
        int[] out = new int[count];
        System.arraycopy(parts, 0, out, 0, count);
        return out;
    }
}

// 컴플리트
