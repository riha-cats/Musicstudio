package org.rhsd.musicStudio.command;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 한국어/영문 서브커맨드 정규화 검증. 순수 static 로직, Bukkit 비의존
class CommandRoutingTest {

    @Test
    void koreanSubcommandsMapToStandard() {
        assertEquals("create", MsCommand.normalizeSub("생성"));
        assertEquals("create", MsCommand.normalizeSub("만들기"));
        assertEquals("list", MsCommand.normalizeSub("목록"));
        assertEquals("open", MsCommand.normalizeSub("열기"));
        assertEquals("rename", MsCommand.normalizeSub("이름변경"));
        assertEquals("rename", MsCommand.normalizeSub("이름"));
        assertEquals("disc", MsCommand.normalizeSub("음반"));
        assertEquals("delete", MsCommand.normalizeSub("삭제"));
        assertEquals("admin", MsCommand.normalizeSub("관리자"));
        assertEquals("help", MsCommand.normalizeSub("도움말"));
    }

    @Test
    void englishSubcommandsStillWork() {
        assertEquals("create", MsCommand.normalizeSub("create"));
        assertEquals("list", MsCommand.normalizeSub("list"));
        assertEquals("open", MsCommand.normalizeSub("open"));
        assertEquals("disc", MsCommand.normalizeSub("disc"));
    }

    @Test
    void caseInsensitive() {
        assertEquals("list", MsCommand.normalizeSub("LIST"));
        assertEquals("create", MsCommand.normalizeSub("Create"));
    }

    @Test
    void unknownPassesThroughLowercased() {
        assertEquals("xyz", MsCommand.normalizeSub("XYZ"));
    }

    // 탭 제안(한/영 양쪽)은 전부 실제 처리되는 표준 서브커맨드로 매핑돼야 한다.
    // normalizeSub 로 정규화한 값이 표준 집합 안에 있는지 본다 — 오타("creaet"), 없는 서브,
    // 잘못된 한글 별칭을 넣으면 여기서 잡힌다 (서버에서만 안 먹는 죽은 제안 방지)
    @Test
    void everyTabSuggestionMapsToARealSubcommand() {
        Set<String> canonical = Set.of("create", "list", "open", "rename", "disc", "delete", "admin", "help");
        for (String sub : MsTabCompleter.USER_SUBS) {
            assertTrue(canonical.contains(MsCommand.normalizeSub(sub)), "영문 탭 제안이 표준이 아님: " + sub);
        }
        for (String sub : MsTabCompleter.USER_SUBS_KO) {
            assertTrue(canonical.contains(MsCommand.normalizeSub(sub)), "한글 탭 제안이 표준이 아님: " + sub);
        }
    }

    // 두 목록은 같은 인덱스가 같은 기능이라야 한다(한 쪽만 늘어나는 실수 방지)
    @Test
    void koreanAndEnglishSuggestionListsAlign() {
        assertEquals(MsTabCompleter.USER_SUBS.size(), MsTabCompleter.USER_SUBS_KO.size());
        for (int i = 0; i < MsTabCompleter.USER_SUBS.size(); i++) {
            assertEquals(MsCommand.normalizeSub(MsTabCompleter.USER_SUBS.get(i)),
                    MsCommand.normalizeSub(MsTabCompleter.USER_SUBS_KO.get(i)),
                    "같은 인덱스인데 기능이 다름: " + i);
        }
    }

    // 별칭이 한글이면 한국어 제안으로 가야 한다
    @Test
    void koreanAliasesAreDetected() {
        assertTrue(MsTabCompleter.isKoreanLabel("음악스튜디오"));
        assertTrue(MsTabCompleter.isKoreanLabel("음스"));
        assertFalse(MsTabCompleter.isKoreanLabel("ms"));
        assertFalse(MsTabCompleter.isKoreanLabel("musicstudio"));
    }
}
