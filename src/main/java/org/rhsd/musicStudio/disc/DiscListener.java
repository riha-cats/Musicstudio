package org.rhsd.musicStudio.disc;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.rhsd.musicStudio.MessageManager;
import org.rhsd.musicStudio.model.Song;
import org.rhsd.musicStudio.playback.PlaybackManager;
import org.rhsd.musicStudio.storage.SongStorage;

// =================================================================
// 음반 우클릭 리스너
// =================================================================
// 음반을 소지한 누구나 재생/정지 가능 (소유권 무관)
public final class DiscListener implements Listener {

    private final DiscManager discManager;
    private final PlaybackManager playback;
    private final SongStorage storage;
    private final MessageManager msg;

    public DiscListener(DiscManager discManager, PlaybackManager playback,
                        SongStorage storage, MessageManager msg) {
        this.discManager = discManager;
        this.playback = playback;
        this.storage = storage;
        this.msg = msg;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // [1] :: 주 손 우클릭인가?
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        // [2] :: 우리 음반인가?
        String songId = discManager.getSongId(event.getItem());
        if (songId == null) return;

        // 주크박스 삽입 등 바닐라 동작 차단
        event.setCancelled(true);

        // [3] :: 곡 데이터가 남아있는가? (삭제됐을 수 있음)
        Player player = event.getPlayer();
        Song song = storage.getById(songId);
        if (song == null) {
            msg.send(player, "disc.not-found");
            return;
        }

        // [4] :: 이미 재생 중이라면? 정지 토글
        if (playback.isPlaying(player)) {
            playback.stop(player);
            msg.send(player, "disc.play-stop", "name", song.name());
            return;
        }

        // [5] :: 빈 곡인가?
        if (song.maxTick() < 0) {
            msg.send(player, "disc.empty");
            return;
        }

        // [6] PASSED :: 재생 시작
        playback.start(player, song);
        msg.send(player, "disc.play-start", "name", song.name());
        // [STOP] :: 우클릭 처리 끝
    }
}

// 컴플리트
