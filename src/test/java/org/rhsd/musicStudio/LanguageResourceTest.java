package org.rhsd.musicStudio;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

// 번들 language/*.yml 이 코드가 찾는 경로와 실제로 맞는지 본다.
// 어긋나면 서버에서 전부 [Missing] 으로 뜨는데 컴파일은 통과하므로 여기서 잡는다
class LanguageResourceTest {

    private YamlConfiguration bundled(String path) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "번들 리소스가 없다: " + path);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test void canonicalHasBothNamespaces() throws Exception {
        YamlConfiguration ko = bundled("language/ko_kr.yml");
        assertTrue(ko.isConfigurationSection("messages"), "messages 섹션이 없다");
        assertTrue(ko.isConfigurationSection("gui"), "gui 섹션이 없다");
    }

    // MessageManager / GuiConfig 가 실제로 던지는 키 몇 개를 정본에서 찾아본다
    @Test void keysTheCodeAsksForExistInCanonical() throws Exception {
        YamlConfiguration ko = bundled("language/ko_kr.yml");
        String[] paths = {
                "messages.prefix",
                "messages.no-permission",
                "messages.editor.copy-success",
                "messages.editor.paste-no-target",
                "messages.command.not-found",
                "gui.editor.title",
                "gui.editor.buttons.copy.name",
                "gui.editor.buttons.paste.name",
                "gui.editor.ruler-paste.name",
                "gui.settings.tempo-reset.name",
                "gui.output.buy.name",
                "gui.song-list.entry.name",
                "gui.song-list.page-info.name",
                "gui.disc.name",
        };
        for (String path : paths) {
            assertNotNull(ko.get(path), "정본에 없는 키: " + path);
        }
    }

    // 옛 구조의 흔적이 남아 있으면 안 된다
    @Test void oldPerFileLayoutIsGone() throws Exception {
        for (String gone : new String[]{"messages_ko_kr.yml", "gui_ko_kr.yml",
                "messages_en_us.yml", "gui_en_us.yml"}) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(gone)) {
                assertNull(in, "옛 언어 파일이 아직 번들에 있다: " + gone);
            }
        }
    }

    // config-version 은 override 구조로 오면서 필요 없어졌다. 남아 있으면 설계가 덜 정리된 것
    @Test void configVersionIsNoLongerPartOfLanguageFiles() throws Exception {
        for (String locale : new String[]{"ko_kr", "en_us"}) {
            YamlConfiguration lang = bundled("language/" + locale + ".yml");
            assertNull(lang.get("config-version"), locale + " 에 config-version 이 남아 있다");
        }
    }

    // 자바가 실제로 던지는 키를 전부 긁어 정본과 대조한다.
    // 오타나 이름이 바뀐 키는 컴파일도 통과하고 서버에서만 [Missing] 으로 터지므로 여기서 잡는다
    @Test void everyKeyTheCodeAsksForExists() throws Exception {
        YamlConfiguration ko = bundled("language/ko_kr.yml");
        Path srcRoot = Path.of("src/main/java");
        assertTrue(Files.isDirectory(srcRoot), "소스 경로를 못 찾음: " + srcRoot.toAbsolutePath());

        Pattern guiPat = Pattern.compile("\\bgui\\.(?:name|lore|title)\\(\\s*\"([^\"]+)\"");
        Pattern msgPat = Pattern.compile("\\bmsg\\.(?:send|sendRaw|get|raw)\\(\\s*\\w+\\s*,\\s*\"([^\"]+)\"");
        List<String> missing = new ArrayList<>();

        try (Stream<Path> files = Files.walk(srcRoot)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(file, StandardCharsets.UTF_8);
                collectMissing(ko, guiPat.matcher(src), "gui.", missing, file);
                collectMissing(ko, msgPat.matcher(src), "messages.", missing, file);
            }
        }
        assertTrue(missing.isEmpty(), "정본에 없는 키를 코드가 찾고 있다:\n  " + String.join("\n  ", missing));
    }

    private void collectMissing(YamlConfiguration ko, Matcher m, String prefix,
                                List<String> missing, Path file) {
        while (m.find()) {
            String path = prefix + m.group(1);
            // lore 는 리스트, name 은 문자열. 섹션 자체를 가리키는 경우도 있어 get 으로 본다
            if (ko.get(path) == null) {
                missing.add(path + "  (" + file.getFileName() + ")");
            }
        }
    }

    // 번역이 빠진 키는 정본으로 떨어져야 하므로, en_us 의 키는 정본의 부분집합이라야 한다
    @Test void englishKeysAreASubsetOfCanonical() throws Exception {
        YamlConfiguration ko = bundled("language/ko_kr.yml");
        YamlConfiguration en = bundled("language/en_us.yml");
        for (String key : en.getKeys(true)) {
            if (en.isConfigurationSection(key)) {
                continue;
            }
            assertNotNull(ko.get(key), "정본에 없는 키를 en_us 가 들고 있다: " + key);
        }
    }
}
