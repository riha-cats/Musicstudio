package org.rhsd.musicStudio.command;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    // 탭에 제안하는 영문 서브커맨드는 전부 실제 처리되는 표준 서브커맨드여야 한다.
    // normalizeSub 는 미인식 토큰을 그대로 돌려주므로 그걸로는 오타를 못 잡는다 — 표준 집합과 대조한다.
    // 오타("creaet")나 없는 서브를 제안하면 여기서 잡힌다 (서버에서만 안 먹는 죽은 제안 방지)
    @Test
    void everyTabSuggestionIsARealSubcommand() {
        Set<String> canonical = Set.of("create", "list", "open", "rename", "disc", "delete", "admin", "help");
        for (String sub : MsTabCompleter.USER_SUBS) {
            assertTrue(canonical.contains(sub), "탭 제안이 표준 서브커맨드가 아님: " + sub);
        }
    }
}
