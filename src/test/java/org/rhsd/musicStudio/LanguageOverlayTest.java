package org.rhsd.musicStudio;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// LanguageStore 가 기대는 겹치기 동작을 실제 YamlConfiguration 으로 검증한다
// 우선순위 :: messages.yml(관리자) > language/<언어>.yml > language/ko_kr.yml(정본)
class LanguageOverlayTest {

    // LanguageStore.overlay 와 같은 규칙. 잎사귀만 덮고 섹션은 안 건드린다
    private static void overlay(YamlConfiguration base, ConfigurationSection src) {
        for (String key : src.getKeys(true)) {
            if (!src.isConfigurationSection(key)) {
                base.set(key, src.get(key));
            }
        }
    }

    @Test
    void overrideBeatsLocaleAndLocaleBeatsCanonical() {
        // [0] :: 정본(ko_kr) — 모든 키를 가진다
        YamlConfiguration canonical = new YamlConfiguration();
        canonical.set("messages.prefix", "[음악] ");
        canonical.set("messages.no-permission", "권한이 없습니다");
        canonical.set("gui.editor.buttons.copy.name", "복사");

        // [1] :: 현재 언어(en_us) — 일부만 번역
        YamlConfiguration locale = new YamlConfiguration();
        locale.set("messages.no-permission", "No permission");

        // [2] :: 관리자 override — 바꾸고 싶은 키만
        YamlConfiguration override = new YamlConfiguration();
        override.set("messages.prefix", "<gold>[MyServer] ");

        YamlConfiguration merged = new YamlConfiguration();
        overlay(merged, canonical);
        overlay(merged, locale);
        overlay(merged, override);

        // [3] :: 결과 도출
        assertEquals("<gold>[MyServer] ", merged.getString("messages.prefix"),
                "override 가 가장 세야 한다");
        assertEquals("No permission", merged.getString("messages.no-permission"),
                "번역된 키는 로케일 값이어야 한다");
        assertEquals("복사", merged.getString("gui.editor.buttons.copy.name"),
                "번역이 없는 키는 정본으로 떨어져야 한다");
    }

    @Test
    void overlayKeepsSiblingsWhenOverridingOneLeaf() throws InvalidConfigurationException {
        // 섹션째 덮으면 안 건드린 형제 키가 사라진다. 잎사귀만 덮는 이유다
        YamlConfiguration canonical = new YamlConfiguration();
        canonical.set("gui.editor.buttons.copy.name", "복사");
        canonical.set("gui.editor.buttons.copy.lore", java.util.List.of("줄1", "줄2"));
        canonical.set("gui.editor.buttons.paste.name", "붙여넣기");

        YamlConfiguration override = new YamlConfiguration();
        override.set("gui.editor.buttons.copy.name", "복사하기");

        YamlConfiguration merged = new YamlConfiguration();
        overlay(merged, canonical);
        overlay(merged, override);

        assertEquals("복사하기", merged.getString("gui.editor.buttons.copy.name"));
        assertEquals(java.util.List.of("줄1", "줄2"), merged.getStringList("gui.editor.buttons.copy.lore"),
                "override 하지 않은 형제 lore 가 살아 있어야 한다");
        assertEquals("붙여넣기", merged.getString("gui.editor.buttons.paste.name"),
                "override 하지 않은 형제 버튼이 살아 있어야 한다");

        // 저장 후 다시 읽어도 같아야 한다 (실제 파일 경로와 동일)
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(merged.saveToString());
        assertEquals("복사하기", reloaded.getString("gui.editor.buttons.copy.name"));
        assertEquals("붙여넣기", reloaded.getString("gui.editor.buttons.paste.name"));
    }

    @Test
    void emptyOverrideChangesNothing() {
        YamlConfiguration canonical = new YamlConfiguration();
        canonical.set("messages.prefix", "[음악] ");

        YamlConfiguration merged = new YamlConfiguration();
        overlay(merged, canonical);
        overlay(merged, new YamlConfiguration());

        assertEquals("[음악] ", merged.getString("messages.prefix"));
    }
}
