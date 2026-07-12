package org.rhsd.musicStudio.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 버전 비교 + 릴리스 JSON 태그 추출 검증. 네트워크/Bukkit 비의존 순수 로직
class UpdateCheckerTest {

    @Test
    void newerPatchVersionDetected() {
        assertTrue(UpdateChecker.isNewer("1.0.4", "1.0.3"));
        assertTrue(UpdateChecker.isNewer("v1.0.4", "1.0.3"));
    }

    @Test
    void sameOrOlderIsNotNewer() {
        assertFalse(UpdateChecker.isNewer("1.0.3", "1.0.3"));
        assertFalse(UpdateChecker.isNewer("v1.0.3", "1.0.3"));
        assertFalse(UpdateChecker.isNewer("1.0.2", "1.0.3"));
    }

    @Test
    void numericCompareNotLexicographic() {
        // 문자열 비교라면 1.0.10 < 1.0.9 로 잘못 판정된다
        assertTrue(UpdateChecker.isNewer("1.0.10", "1.0.9"));
        assertTrue(UpdateChecker.isNewer("2.0", "1.9.9"));
    }

    @Test
    void shorterVersionTreatedAsZeroPadded() {
        assertTrue(UpdateChecker.isNewer("1.0.3.1", "1.0.3"));
        assertFalse(UpdateChecker.isNewer("1.0.3", "1.0.3.0"));
    }

    @Test
    void garbageVersionsNeverNotify() {
        assertFalse(UpdateChecker.isNewer(null, "1.0.3"));
        assertFalse(UpdateChecker.isNewer("1.0.4", null));
        assertFalse(UpdateChecker.isNewer("latest", "1.0.3"));
    }

    @Test
    void tagExtractedFromReleaseJson() {
        String json = "{\"url\":\"x\",\"tag_name\": \"v1.0.4\",\"name\":\"MusicStudio 1.0.4\"}";
        assertEquals("v1.0.4", UpdateChecker.extractTag(json));
    }

    @Test
    void missingTagReturnsNull() {
        assertNull(UpdateChecker.extractTag("{\"message\":\"Not Found\"}"));
        assertNull(UpdateChecker.extractTag(null));
    }
}
