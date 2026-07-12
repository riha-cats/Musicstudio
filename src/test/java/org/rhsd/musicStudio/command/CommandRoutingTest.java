package org.rhsd.musicStudio.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
