package org.rhsd.musicStudio.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// 추출 메뉴 슬롯 배치 고정. 27칸 안에서 2행에만 버튼이 놓여야 한다
class OutputMenuLayoutTest {

    @Test void slotsMatchTheSpec() {
        assertEquals(10, OutputMenu.SLOT_PRICE);
        assertEquals(13, OutputMenu.SLOT_BUY_START);
        assertEquals(15, OutputMenu.SLOT_BUY_END);
        assertEquals(16, OutputMenu.SLOT_BACK);
    }

    @Test void buttonsSitOnTheSecondRow() {
        for (int slot : new int[]{OutputMenu.SLOT_PRICE, OutputMenu.SLOT_BUY_START,
                OutputMenu.SLOT_BUY_END, OutputMenu.SLOT_BACK}) {
            assertEquals(1, slot / 9, "2행이 아님: " + slot);
        }
    }

    @Test void buySlotsCoverThreeCellsAndNothingElse() {
        assertTrue(OutputMenu.isBuySlot(13));
        assertTrue(OutputMenu.isBuySlot(14));
        assertTrue(OutputMenu.isBuySlot(15));
        // 가격·돌아가기 칸이 결제로 잡히면 실수로 돈이 나간다
        assertFalse(OutputMenu.isBuySlot(OutputMenu.SLOT_PRICE));
        assertFalse(OutputMenu.isBuySlot(OutputMenu.SLOT_BACK));
        assertFalse(OutputMenu.isBuySlot(12));
        assertFalse(OutputMenu.isBuySlot(0));
    }
}
