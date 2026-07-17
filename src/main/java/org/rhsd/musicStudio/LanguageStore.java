package org.rhsd.musicStudio;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

// =================================================================
// 언어 저장소
// =================================================================
// 문구를 3단으로 겹쳐 읽는다 :: messages.yml(관리자) > language/<언어>.yml > language/ko_kr.yml(정본)
//
// language/ 는 켤 때마다 번들로 덮어쓰는 배포본이라 관리자가 고쳐도 사라진다.
// 대신 messages.yml 은 플러그인이 절대 안 건드리고, 관리자는 바꿀 키만 적는다.
// 이렇게 하면 새 버전의 문구가 저절로 들어오면서 관리자 수정도 안 밟는다
// (EssentialsX 의 messages_custom, Towny 의 lang/override 와 같은 발상)
//
// 이 구조 덕에 config-version 과 키 마이그레이션이 통째로 필요 없어졌다.
// 뜻이 바뀐 키를 일일이 지워줄 이유가 없다 — 배포본은 늘 최신이다
public final class LanguageStore {

    public static final MiniMessage MM = MiniMessage.miniMessage();

    // 번역이 빠졌을 때 기대는 정본 로케일
    private static final String CANONICAL = "ko_kr";
    private static final String LANG_DIR = "language";
    private static final String OVERRIDE_FILE = "messages.yml";

    private final JavaPlugin plugin;
    // 3단을 하나로 눌러 담은 결과. 조회는 여기서만 한다
    private YamlConfiguration merged = new YamlConfiguration();

    public LanguageStore(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        // [0] :: 옛 구조(messages_ko_kr.yml 등)를 쓰던 서버라면 먼저 치운다
        relocateLegacyFiles();

        // [1] :: 배포본을 번들로 덮어쓴다. 여기가 "고치면 사라지는" 자리다
        exportBundledLanguages();

        String locale = plugin.getConfig().getString("language", CANONICAL).toLowerCase(Locale.ROOT);

        // [2] :: 정본을 깔고 그 위에 현재 언어를 덮는다. 번역이 빠진 키는 정본이 메운다
        YamlConfiguration result = readLanguage(CANONICAL);
        if (!locale.equals(CANONICAL)) {
            overlay(result, readLanguage(locale));
        }

        // [3] :: 마지막으로 관리자 override. 이게 가장 세다
        File override = new File(plugin.getDataFolder(), OVERRIDE_FILE);
        if (override.exists()) {
            overlay(result, YamlConfiguration.loadConfiguration(override));
        } else {
            writeOverrideTemplate(override);
        }

        merged = result;
        // [STOP] :: 로드 끝
    }

    // 번들 language/*.yml 을 디스크로 꺼낸다. 매번 덮어써야 새 문구가 들어온다
    private void exportBundledLanguages() {
        for (String locale : new String[]{CANONICAL, "en_us"}) {
            String path = LANG_DIR + "/" + locale + ".yml";
            if (plugin.getResource(path) != null) {
                plugin.saveResource(path, true);
            }
        }
    }

    private YamlConfiguration readLanguage(String locale) {
        File file = new File(plugin.getDataFolder(), LANG_DIR + "/" + locale + ".yml");
        // 디스크에 있다면 그걸 읽는다. 방금 번들로 덮었으니 내용은 같다
        if (file.exists()) {
            return YamlConfiguration.loadConfiguration(file);
        }
        // 없는 언어를 config 에 적었다면? 빈 설정을 돌려 정본으로 떨어지게 한다
        return bundled(LANG_DIR + "/" + locale + ".yml");
    }

    // src 의 잎사귀 값만 base 에 덮어쓴다. 섹션째 덮으면 안 건드린 형제 키가 날아간다
    private static void overlay(YamlConfiguration base, ConfigurationSection src) {
        if (src == null) {
            return;
        }
        for (String key : src.getKeys(true)) {
            if (!src.isConfigurationSection(key)) {
                base.set(key, src.get(key));
            }
        }
    }

    private YamlConfiguration bundled(String path) {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new YamlConfiguration();
        }
    }

    // 관리자용 빈 override 파일. 쓰는 법을 파일 안에 적어둬야 위키를 안 찾는다
    private void writeOverrideTemplate(File file) {
        String template = """
                # =================================================================
                # MusicStudio 문구 덮어쓰기
                # =================================================================
                # 바꾸고 싶은 키만 여기에 적으세요. 플러그인은 이 파일을 건드리지 않습니다
                # 적지 않은 키는 language/<언어>.yml 의 값을 그대로 씁니다
                #
                # 키 경로는 language/ko_kr.yml 을 열어 그대로 베끼면 됩니다
                # 예시 ::
                #
                # messages:
                #   prefix: "<gold>[음악] "
                # gui:
                #   editor:
                #     buttons:
                #       copy:
                #         name: "<white>복사하기"
                #
                # 전부 MiniMessage 문법입니다
                """;
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            java.nio.file.Files.writeString(file.toPath(), template, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, OVERRIDE_FILE + " 생성 실패", e);
        }
    }

    // 옛 구조 파일을 language/legacy/ 로 옮긴다.
    // 어느 키가 관리자 수정인지 알 방법이 없어 자동 이관은 하지 않는다.
    // 조용히 지우면 남의 번역이 날아가므로 보관하고 안내만 한다
    //
    // 대상은 두 세대다 ::
    //   1세대 gui.yml (로케일 이전. messages.yml 도 같은 세대지만 아래 [1] 참고)
    //   2세대 messages_ko_kr.yml, gui_ko_kr.yml (로케일별 파일)
    private void relocateLegacyFiles() {
        File dir = plugin.getDataFolder();
        File[] legacy = dir.listFiles((d, n) ->
                n.matches("^(messages|gui)_[a-z]{2}_[a-z]{2}\\.yml$") || n.equals("gui.yml"));
        if (legacy == null || legacy.length == 0) {
            relocateLegacyOverride(dir);
            return;
        }
        File legacyDir = new File(dir, LANG_DIR + "/legacy");
        if (!legacyDir.exists() && !legacyDir.mkdirs()) {
            plugin.getLogger().warning("language/legacy 생성 실패. 옛 언어 파일을 그대로 둡니다");
            return;
        }
        int moved = 0;
        for (File file : legacy) {
            if (file.renameTo(new File(legacyDir, file.getName()))) {
                moved++;
            }
        }
        moved += relocateLegacyOverride(dir) ? 1 : 0;
        if (moved > 0) {
            plugin.getLogger().info("언어 파일 구조가 바뀌었습니다. 기존 파일 "
                    + moved + "개를 language/legacy/ 로 옮겨 보관했습니다.");
            plugin.getLogger().info("직접 고치신 문구가 있다면 messages.yml 에 옮겨 주세요.");
        }
    }

    // [1] :: 로케일 이전 세대에도 messages.yml 이 있었는데, 그때는 "전체 설정"이었고
    // 지금은 "관리자 override" 라 뜻이 정반대다. 옛 파일을 그대로 두면 override 로 잘못 읽혀
    // 관리자는 "고쳐도 안 먹는다" 를 겪는다. config-version 이 박혀 있으면 옛 세대로 본다
    private boolean relocateLegacyOverride(File dir) {
        File override = new File(dir, OVERRIDE_FILE);
        if (!override.exists()) {
            return false;
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(override);
        // 새 override 는 messages/gui 두 섹션만 쓰고 config-version 을 안 쓴다
        if (loaded.get("config-version") == null) {
            return false;
        }
        File legacyDir = new File(dir, LANG_DIR + "/legacy");
        if (!legacyDir.exists() && !legacyDir.mkdirs()) {
            return false;
        }
        return override.renameTo(new File(legacyDir, OVERRIDE_FILE));
    }

    // =================================================================
    // 조회
    // =================================================================

    // 값이 없다면 def. 3단이 이미 하나로 눌려 있어 여기서 폴백을 더 볼 게 없다
    public String string(String path, String def) {
        String value = merged.getString(path);
        return value != null ? value : def;
    }

    public List<String> stringList(String path) {
        return merged.getStringList(path);
    }

    // 값에 태그가 섞여 있어도 unparsed 로 리터럴 삽입되므로 인젝션 위험은 없다
    public TagResolver resolver(String... kv) {
        if (kv.length == 0) {
            return TagResolver.empty();
        }
        TagResolver.Builder builder = TagResolver.builder();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            builder.resolver(Placeholder.unparsed(kv[i], kv[i + 1] == null ? "" : kv[i + 1]));
        }
        return builder.build();
    }
}

// 컴플리트
