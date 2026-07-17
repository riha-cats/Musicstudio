package org.rhsd.musicStudio.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// 목록 GUI 의 배치·페이지 계산·크기 표기 검증. Bukkit 없이 순수 로직만 본다
class SongListMenuTest {

    @Test void contentAreaIsTheInsideOfTheBorder() {
        // 테두리(0행·5행·0열·8열)를 뺀 4행x7열 = 28칸
        assertEquals(28, SongListMenu.PER_PAGE);
        for (int slot : SongListMenu.CONTENT_SLOTS) {
            int row = slot / 9;
            int col = slot % 9;
            assertTrue(row >= 1 && row <= 4, "테두리 행을 침범: " + slot);
            assertTrue(col >= 1 && col <= 7, "테두리 열을 침범: " + slot);
        }
        assertEquals(10, SongListMenu.CONTENT_SLOTS[0]);
        assertEquals(43, SongListMenu.CONTENT_SLOTS[27]);
    }

    @Test void pageControlsSitOnTheBottomRowAndDoNotOverlapContent() {
        for (int slot : new int[]{SongListMenu.SLOT_PREV_PAGE,
                SongListMenu.SLOT_PAGE_INFO, SongListMenu.SLOT_NEXT_PAGE}) {
            assertEquals(5, slot / 9, "하단 행이 아님: " + slot);
            for (int content : SongListMenu.CONTENT_SLOTS) {
                assertNotEquals(content, slot, "곡 칸과 겹침: " + slot);
            }
        }
    }

    // 시계(현재 페이지)는 하단 행 45~53 의 정중앙(49)에 와야 한다.
    // 예전엔 50 이라 한 칸 오른쪽으로 밀려 보였다. 이전/다음은 그 좌우로 대칭
    @Test void pageInfoIsCenteredOnTheBottomRow() {
        assertEquals(49, SongListMenu.SLOT_PAGE_INFO, "시계가 하단 행 정중앙이 아님");
        assertEquals(SongListMenu.SLOT_PAGE_INFO - 1, SongListMenu.SLOT_PREV_PAGE, "이전이 시계 왼쪽이 아님");
        assertEquals(SongListMenu.SLOT_PAGE_INFO + 1, SongListMenu.SLOT_NEXT_PAGE, "다음이 시계 오른쪽이 아님");
    }

    @Test void sizeFormatPicksAUnitThatIsActuallyReadable() {
        // 값이 1 이상이 되는 가장 큰 단위를 쓴다. 작은 곡이 "0.00MiB" 로 보이면 정보가 아니다
        assertEquals("0B", SongListMenu.formatSize(0));
        assertEquals("512B", SongListMenu.formatSize(512));
        assertEquals("1.00KiB", SongListMenu.formatSize(1024));
        assertEquals("3.00KiB", SongListMenu.formatSize(3072));
        assertEquals("1.00MiB", SongListMenu.formatSize(1024 * 1024));
        assertEquals("2.50MiB", SongListMenu.formatSize((long) (1024 * 1024 * 2.5)));
    }

    @Test void subMegabyteStaysInKibibytes() {
        // 스펙 예시는 "0.99MiB" 였지만 그 크기는 1MiB 미만이라 KiB 로 내려간다.
        // 항상-MiB 로 가면 대부분의 곡이 "0.00MiB" 가 되므로 이 쪽을 택했다
        assertEquals("1013.76KiB", SongListMenu.formatSize((long) (1024 * 1024 * 0.99)));
    }
}
