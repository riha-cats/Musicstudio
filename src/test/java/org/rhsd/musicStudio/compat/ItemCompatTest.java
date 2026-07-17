package org.rhsd.musicStudio.compat;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// jukebox 곡 키 도출만 순수 로직으로 검증한다.
// 실제 툴팁 숨김은 서버 컴포넌트가 필요해 여기서 못 돌린다
class ItemCompatTest {

    @Test
    void derivesSongNameFromDiscMaterial() {
        assertEquals("strad", ItemCompat.jukeboxSongName(Material.MUSIC_DISC_STRAD));
        assertEquals("cat", ItemCompat.jukeboxSongName(Material.MUSIC_DISC_CAT));
        assertEquals("11", ItemCompat.jukeboxSongName(Material.MUSIC_DISC_11));
        assertEquals("pigstep", ItemCompat.jukeboxSongName(Material.MUSIC_DISC_PIGSTEP));
    }

    // 뮤직디스크가 아닌 베이스(ItemsAdder 커스텀 등)에는 jukebox 컴포넌트를 손대면 안 된다
    @Test
    void nonDiscMaterialsYieldNull() {
        assertNull(ItemCompat.jukeboxSongName(Material.PAPER));
        assertNull(ItemCompat.jukeboxSongName(Material.STONE));
        assertNull(ItemCompat.jukeboxSongName(null));
    }
}
