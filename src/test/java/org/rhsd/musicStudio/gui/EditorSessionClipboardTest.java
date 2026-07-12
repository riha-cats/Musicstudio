package org.rhsd.musicStudio.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 클립보드 → 붙여넣기 좌표 변환 검증. Bukkit 비의존 순수 로직
class EditorSessionClipboardTest {

    @Test
    void pasteShiftsByBaseTick() {
        List<int[]> clipboard = List.of(new int[]{0, 0, 5}, new int[]{3, 1, 7});
        List<int[]> out = EditorSession.resolvePaste(clipboard, 10, 4096, 2);
        assertEquals(2, out.size());
        assertArrayEquals(new int[]{10, 0, 5}, out.get(0));
        assertArrayEquals(new int[]{13, 1, 7}, out.get(1));
    }

    @Test
    void pasteDropsTicksAtOrBeyondLimit() {
        // 4090+7=4097, 4090+6=4096 → 최대 길이(4096) 이상은 잘리고 그 앞까지만 남는다
        List<int[]> clipboard = List.of(
                new int[]{0, 0, 5}, new int[]{5, 0, 6}, new int[]{6, 0, 7}, new int[]{7, 0, 8});
        List<int[]> out = EditorSession.resolvePaste(clipboard, 4090, 4096, 1);
        assertEquals(2, out.size());
        assertArrayEquals(new int[]{4090, 0, 5}, out.get(0));
        assertArrayEquals(new int[]{4095, 0, 6}, out.get(1));
    }

    @Test
    void pasteDropsLayersSongDoesNotHave() {
        List<int[]> clipboard = List.of(new int[]{0, 0, 5}, new int[]{0, 3, 7});
        List<int[]> out = EditorSession.resolvePaste(clipboard, 0, 4096, 2);
        assertEquals(1, out.size());
        assertArrayEquals(new int[]{0, 0, 5}, out.get(0));
    }

    @Test
    void emptyClipboardResolvesToNothing() {
        assertTrue(EditorSession.resolvePaste(List.of(), 0, 4096, 9).isEmpty());
    }
}
