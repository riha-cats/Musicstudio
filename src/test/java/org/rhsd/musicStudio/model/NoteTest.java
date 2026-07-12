package org.rhsd.musicStudio.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// 노트블록 음높이 매핑(pitch/키이름/클램프) 검증. Bukkit 비의존 순수 로직
class NoteTest {

    @Test
    void pitchCenterIsOne() {
        // key 12 = F#4 = pitch 1.0 (노트블록 중앙음)
        assertEquals(1.0f, Note.pitch(12), 1e-4);
    }

    @Test
    void pitchRangeIsHalfToTwo() {
        // key 0 = F#3, key 24 = F#5
        assertEquals(0.5f, Note.pitch(0), 1e-4);
        assertEquals(2.0f, Note.pitch(24), 1e-4);
    }

    @Test
    void pitchClampsOutOfRange() {
        // 범위를 벗어난 key는 클램프되어 0.5~2.0 유지
        assertEquals(0.5f, Note.pitch(-10), 1e-4);
        assertEquals(2.0f, Note.pitch(100), 1e-4);
    }

    @Test
    void clampKeyBounds() {
        assertEquals(0, Note.clampKey(-5));
        assertEquals(24, Note.clampKey(99));
        assertEquals(12, Note.clampKey(12));
    }

    @Test
    void keyNamesAcrossRange() {
        assertEquals("F#3", Note.keyName(0));
        assertEquals("C4", Note.keyName(6));
        assertEquals("F#4", Note.keyName(12));
        assertEquals("C5", Note.keyName(18));
        assertEquals("F#5", Note.keyName(24));
    }

    @Test
    void shiftKeyClampsAtBounds() {
        Note high = new Note(0, 0, 24);
        high.shiftKey(5);
        assertEquals(24, high.key());

        Note low = new Note(0, 0, 0);
        low.shiftKey(-5);
        assertEquals(0, low.key());
    }

    @Test
    void noteCoordinatesPreserved() {
        Note n = new Note(7, 3, 10);
        assertEquals(7, n.tick());
        assertEquals(3, n.layer());
        assertEquals(10, n.key());
    }
}
