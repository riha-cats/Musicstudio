package org.rhsd.musicStudio.model;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.rhsd.musicStudio.gui.InstrumentMenu;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// 악기 목록이 지켜야 할 약속을 못박는다.
// 순서와 이름 키는 코드만 봐서는 안 깨진 티가 안 나고 서버에서만 드러나므로 여기서 잡는다
class InstrumentTest {

    private YamlConfiguration bundled(String locale) throws Exception {
        String path = "language/" + locale + ".yml";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "번들 리소스가 없다: " + path);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    // NbsImporter 가 악기 id 를 ordinal 로 그대로 쓴다.
    // 앞 16개 순서가 밀리면 임포트한 곡의 악기가 전부 엉뚱해지는데 예외 하나 안 난다
    @Test
    void firstSixteenMatchNbsInstrumentIds() {
        String[] nbsOrder = {
                "HARP", "BASS", "BASEDRUM", "SNARE", "HAT", "GUITAR", "FLUTE", "BELL",
                "CHIME", "XYLOPHONE", "IRON_XYLOPHONE", "COW_BELL", "DIDGERIDOO", "BIT", "BANJO", "PLING"
        };
        assertEquals(nbsOrder.length, Instrument.VANILLA_COUNT, "VANILLA_COUNT 가 바닐라 악기 수와 다르다");
        for (int i = 0; i < nbsOrder.length; i++) {
            assertEquals(nbsOrder[i], Instrument.values()[i].name(),
                    "NBS 악기 id " + i + " 자리가 밀렸다. 새 악기는 enum 맨 뒤에 붙여야 한다");
        }
    }

    // 머리 악기는 반드시 바닐라 16개 뒤다. 앞으로 오면 NBS 커스텀 악기 id 와 겹친다
    @Test
    void headInstrumentsComeAfterTheVanillaBlock() {
        for (Instrument i : new Instrument[]{Instrument.CREEPER, Instrument.ENDER_DRAGON,
                Instrument.PIGLIN, Instrument.SKELETON, Instrument.WITHER_SKELETON, Instrument.ZOMBIE}) {
            assertTrue(i.ordinal() >= Instrument.VANILLA_COUNT,
                    i + " 가 바닐라 구간 안에 있다 (ordinal " + i.ordinal() + ")");
        }
    }

    // 모든 악기가 두 로케일 모두에서 이름을 가져야 한다.
    // 빠지면 GUI 에 "WITHER_SKELETON" 같은 enum 이름이 그대로 뜬다
    @Test
    void everyInstrumentHasANameInBothLocales() throws Exception {
        for (String locale : new String[]{"ko_kr", "en_us"}) {
            YamlConfiguration lang = bundled(locale);
            List<String> missing = new ArrayList<>();
            for (Instrument i : Instrument.values()) {
                if (lang.getString("gui.instrument.names." + i.key()) == null) {
                    missing.add(i.key());
                }
            }
            assertTrue(missing.isEmpty(), locale + " 에 악기 이름이 없다: " + missing);
        }
    }

    // 반대 방향. 악기를 지웠는데 언어 파일에 이름만 남는 걸 막는다
    @Test
    void noOrphanNamesInLanguageFiles() throws Exception {
        for (String locale : new String[]{"ko_kr", "en_us"}) {
            YamlConfiguration lang = bundled(locale);
            var section = lang.getConfigurationSection("gui.instrument.names");
            assertNotNull(section, locale + " 에 gui.instrument.names 섹션이 없다");
            for (String key : section.getKeys(false)) {
                assertDoesNotThrow(() -> Instrument.valueOf(key.toUpperCase(java.util.Locale.ROOT)),
                        locale + " 에 없는 악기의 이름이 남아 있다: " + key);
            }
        }
    }

    // 악기 선택 메뉴는 앞칸부터 순서대로 채운다. 칸수를 넘기면 런타임에 터진다
    @Test
    void allInstrumentsFitInTheMenu() {
        assertTrue(Instrument.values().length <= InstrumentMenu.SIZE,
                "악기 " + Instrument.values().length + "개가 " + InstrumentMenu.SIZE + "칸 메뉴에 안 들어간다");
    }

    // 아이콘이 겹치면 에디터에서 레이어를 눈으로 구분할 수 없다
    @Test
    void iconsAreDistinct() {
        var seen = new java.util.HashMap<org.bukkit.Material, Instrument>();
        for (Instrument i : Instrument.values()) {
            Instrument prev = seen.put(i.icon(), i);
            assertNull(prev, "아이콘이 겹친다: " + prev + " 와 " + i + " 둘 다 " + i.icon());
        }
    }

    @Test
    void unknownNameFallsBackToHarp() {
        assertEquals(Instrument.HARP, Instrument.fromString(null));
        assertEquals(Instrument.HARP, Instrument.fromString("NOT_AN_INSTRUMENT"));
        // 저장 파일은 name() 으로 적히므로 왕복이 되어야 한다
        for (Instrument i : Instrument.values()) {
            assertEquals(i, Instrument.fromString(i.name()));
        }
    }
}
