package org.rhsd.musicStudio.playback;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.rhsd.musicStudio.model.Layer;
import org.rhsd.musicStudio.model.Note;
import org.rhsd.musicStudio.model.Song;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// =================================================================
// 음반 재생 엔진
// =================================================================
// 플레이어별로 1개의 재생을 추적. 매 셀마다 해당 플레이어의 현재 위치에서
// RECORDS 카테고리로 주변 반경에 노트를 재생한다 (들고 다니는 라디오처럼 위치 추종)
// 곡 끝(maxTick)에 도달하면 1회 재생 후 자동 정지
public final class PlaybackManager {

    private final JavaPlugin plugin;
    private final Map<UUID, BukkitTask> playbacks = new ConcurrentHashMap<>();

    public PlaybackManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isPlaying(Player player) {
        return playbacks.containsKey(player.getUniqueId());
    }

    // 재생 중이면 정지, 아니면 시작. true = 시작됨, false = 정지됨
    public boolean toggle(Player player, Song song) {
        if (isPlaying(player)) {
            stop(player);
            return false;
        }
        return start(player, song);
    }

    // 재생 시작. 빈 곡이라면? false
    public boolean start(Player player, Song song) {
        stop(player);
        int maxTick = song.maxTick();
        if (maxTick < 0) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        int period = Math.max(1, song.ticksPerCell());
        BukkitTask task = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                // [1] :: 플레이어가 나갔는가? 재생 종료
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) {
                    finish();
                    return;
                }
                // [2] :: 곡 끝까지 재생했는가?
                if (tick > maxTick) {
                    finish();
                    return;
                }
                // [3] :: 현재 틱의 노트를 플레이어 위치에서 재생 (음소거 레이어 제외)
                Location loc = p.getLocation();
                for (Note note : song.notesAtTick(tick)) {
                    Layer layer = song.layer(note.layer());
                    if (layer == null || layer.muted()) {
                        continue;
                    }
                    p.getWorld().playSound(loc, layer.instrument().sound(),
                            SoundCategory.RECORDS, layer.volume(), note.pitch());
                }
                tick++;
            }

            private void finish() {
                playbacks.remove(uuid);
                cancel();
            }
        }.runTaskTimer(plugin, 0L, period);
        playbacks.put(uuid, task);
        return true;
        // [STOP] :: 재생 시작 끝
    }

    public void stop(Player player) {
        BukkitTask task = playbacks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    public void shutdownAll() {
        for (BukkitTask task : playbacks.values()) {
            task.cancel();
        }
        playbacks.clear();
    }
}

// 컴플리트
