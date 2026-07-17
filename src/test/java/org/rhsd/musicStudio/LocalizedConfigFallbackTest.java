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

    // 뜻이 바뀐 키는 copyDefaults 로는 못 고친다. migrate 가 먼저 지워줘야 새 문장이 들어온다
    @Test
    void migrateClearsChangedKeySoDefaultsCanRefill() throws InvalidConfigurationException {
        String stale = "틱 <white><tick></white>의 노트 <white><count></white>개를 복사했습니다.";
        String fresh = "선택한 <white><ticks></white>틱에서 노트 <white><notes></white>개를 복사했습니다. (<from>~<to>)";

        // [0] :: 정본(ko_kr) — copy-success 가 단일 틱에서 범위 복사로 바뀐 상태
        YamlConfiguration canonical = new YamlConfiguration();
        canonical.set("config-version", 5);
        canonical.set("editor.copy-success", fresh);

        // [1] :: 기존 서버에 깔려 있는 파일 — 버전 4, 단일 틱 시절 문장이 그대로다
        YamlConfiguration onDisk = new YamlConfiguration();
        onDisk.set("config-version", 4);
        onDisk.set("editor.copy-success", stale);

        // [2] :: migrate 없이 copyDefaults 만? 키가 이미 있으니 옛 문장이 살아남는다 (이 버그의 정체)
        YamlConfiguration without = new YamlConfiguration();
        without.loadFromString(onDisk.saveToString());
        without.setDefaults(canonical);
        without.options().copyDefaults(true);
        assertEquals(stale, without.getString("editor.copy-success"));

        // [3] :: migrate 가 키를 지운 뒤라면? 정본의 새 문장이 채워진다
        YamlConfiguration with = new YamlConfiguration();
        with.loadFromString(onDisk.saveToString());
        with.set("editor.copy-success", null);
        with.setDefaults(canonical);
        with.options().copyDefaults(true);
        assertEquals(fresh, with.getString("editor.copy-success"));
    }
}
