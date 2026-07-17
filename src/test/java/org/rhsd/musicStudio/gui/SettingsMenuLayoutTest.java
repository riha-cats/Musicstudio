package org.rhsd.musicStudio.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// 설정 메뉴 슬롯 배치 고정. 27칸 안에서 2행에만 버튼이 놓여야 한다
class SettingsMenuLayoutTest {

    @Test void buttonsSitOnTheSecondRow() {
        // 27칸 = 3행. 2행은 슬롯 9~17
        for (int slot : new int[]{SettingsMenu.SLOT_INFO, SettingsMenu.SLOT_TEMPO_DOWN,
                SettingsMenu.SLOT_TEMPO_UP, SettingsMenu.SLOT_TEMPO_RESET, SettingsMenu.SLOT_BACK}) {
            assertTrue(slot >= 9 && slot <= 17, "2행(9~17) 밖의 슬롯: " + slot);
            assertEquals(1, slot / 9, "2행이 아님: " + slot);
        }
    }

    @Test void slotsMatchTheSpecAndDoNotCollide() {
        assertEquals(10, SettingsMenu.SLOT_INFO);
        assertEquals(13, SettingsMenu.SLOT_TEMPO_DOWN);
        assertEquals(14, SettingsMenu.SLOT_TEMPO_UP);
        assertEquals(15, SettingsMenu.SLOT_TEMPO_RESET);
        assertEquals(16, SettingsMenu.SLOT_BACK);

        long distinct = java.util.stream.IntStream.of(SettingsMenu.SLOT_INFO,
                SettingsMenu.SLOT_TEMPO_DOWN, SettingsMenu.SLOT_TEMPO_UP,
                SettingsMenu.SLOT_TEMPO_RESET, SettingsMenu.SLOT_BACK).distinct().count();
        assertEquals(5, distinct, "슬롯이 겹친다");
    }
}
