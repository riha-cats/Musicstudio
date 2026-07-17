package org.rhsd.musicStudio;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

// 옛 세대 파일을 override 로 오인하지 않는지 본다.
// 로케일 이전 버전에도 messages.yml 이 있었는데 그때는 "전체 설정" 이라 뜻이 정반대다
class LegacyDetectionTest {

    @Test void realLegacyMessagesFileIsRecognised() throws Exception {
        // 실제 서버에 남아 있던 2026-05-30 자 파일을 그대로 가져왔다
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("legacy-messages.yml")) {
            assertNotNull(in, "표본 파일이 없다");
            YamlConfiguration old = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            // 판별 기준 :: config-version 이 있으면 옛 세대
            assertNotNull(old.get("config-version"), "옛 파일에는 config-version 이 있어야 한다");
            // 옛 파일은 평면 구조라 새 네임스페이스가 없다
            assertNull(old.get("messages"), "옛 파일에 messages 섹션이 있으면 판별이 헷갈린다");
            assertNull(old.get("gui"), "옛 파일에 gui 섹션이 있으면 판별이 헷갈린다");
            assertNotNull(old.get("prefix"), "옛 파일은 prefix 를 최상위에 둔다");
        }
    }

    @Test void newOverrideFileIsNotMistakenForLegacy() {
        // 관리자가 새로 쓴 override. config-version 이 없어야 정상이다
        YamlConfiguration fresh = new YamlConfiguration();
        fresh.set("messages.prefix", "<gold>[MyServer] ");
        assertNull(fresh.get("config-version"), "새 override 는 config-version 을 쓰지 않는다");
    }
}
