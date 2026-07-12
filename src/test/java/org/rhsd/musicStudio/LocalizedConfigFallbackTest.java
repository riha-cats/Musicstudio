package org.rhsd.musicStudio;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// LocalizedConfig 가 기대는 YamlConfiguration 동작을 실제 실행으로 검증
// 정본(ko_kr)을 defaults 로 깔고 copyDefaults 후 저장하면 누락 키가 파일에 물리적으로 채워지고,
// 미리 번역해 둔 키는 보존되어야 한다 (en_us 를 버전 0으로 두는 이유)
class LocalizedConfigFallbackTest {

    @Test
    void copyDefaultsFillsMissingKeysAndKeepsTranslations() throws InvalidConfigurationException {
        // [0] :: 정본(ko_kr) 구성. 중첩 키도 채워지는지 보려고 command.* 포함
        YamlConfiguration canonical = new YamlConfiguration();
        canonical.set("config-version", 2);
        canonical.set("prefix", "[KO] ");
        canonical.set("no-permission", "권한이 없습니다");
        canonical.set("command.create-success", "생성 완료");

        // [1] :: en_us 스텁. 일부만 번역, 버전 0
        YamlConfiguration locale = new YamlConfiguration();
        locale.set("config-version", 0);
        locale.set("no-permission", "No permission");

        // defaults 깔기 전 파일 버전을 먼저 읽는다
        int savedVer = locale.getInt("config-version", 0);
        assertEquals(0, savedVer);

        locale.setDefaults(canonical);
        locale.options().copyDefaults(true);
        locale.set("config-version", canonical.getInt("config-version", 1));

        // [2] :: 저장→재로드 (파일 저장 경로와 동일). defaults 없는 상태에서 조회해 '물리적 채움' 확인
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(locale.saveToString());

        // [3] :: 결과 도출 — 번역 보존 / 누락은 정본 채움 / 중첩도 채움
        assertEquals("No permission", reloaded.getString("no-permission"));
        assertEquals("[KO] ", reloaded.getString("prefix"));
        assertEquals("생성 완료", reloaded.getString("command.create-success"));
        assertEquals(2, reloaded.getInt("config-version"));
    }
}
