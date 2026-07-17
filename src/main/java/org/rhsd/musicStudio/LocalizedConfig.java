package org.rhsd.musicStudio;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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
// 로케일 텍스트 로더 (공통 토대)
// =================================================================
// messages/gui 설정이 같은 plumbing 을 공유한다
// 활성 언어는 config 의 language 키로 고른다 (ko_kr, en_us)
// 실제 파일은 <base>_<locale>.yml 이며, 번들된 ko_kr 을 기본값으로 깔아
// 번역이 빠진 키는 자동으로 한국어로 폴백 — 일부만 번역된 로케일도 깨지지 않는다
public abstract class LocalizedConfig {

    protected static final MiniMessage MM = MiniMessage.miniMessage();

    // 번역 누락 시 기준이 되는 정본 로케일
    private static final String CANONICAL = "ko_kr";

    private final JavaPlugin plugin;
    // "messages" 또는 "gui"
    private final String base;
    private YamlConfiguration text;

    protected LocalizedConfig(JavaPlugin plugin, String base) {
        this.plugin = plugin;
        this.base = base;
        reload();
    }

    public final void reload() {
        String locale = plugin.getConfig().getString("language", CANONICAL).toLowerCase(Locale.ROOT);
        text = load(locale);
    }

    private YamlConfiguration load(String locale) {
        // [1] :: 로케일 파일이 없다면? 번들 리소스를 꺼내 깐다
        String fileName = base + "_" + locale + ".yml";
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists() && plugin.getResource(fileName) != null) {
            plugin.saveResource(fileName, false);
        }

        YamlConfiguration loaded = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        // 정본을 깔기 전에 읽어야 파일 자체 버전을 본다 (없으면 0)
        int savedVer = loaded.getInt("config-version", 0);

        // [2] :: 번들 정본(ko_kr)을 defaults 로 깐다. 없다면(개발 중 등) 있는 그대로 사용
        YamlConfiguration canonical = bundled(base + "_" + CANONICAL + ".yml");
        if (canonical == null) {
            return loaded;
        }
        // 정본 버전은 덮어쓰기 전에 읽어둔다 (로케일 파일 버전이 아니라 정본이 기준이다)
        int bundledVer = canonical.getInt("config-version", 1);
        // 같은 로케일의 번들 파일이 있다면 정본 위에 덮어써서 defaults 를 만든다.
        // 정본만 defaults 로 두면 migrate 가 지운 en_us 키가 한국어로 채워진다
        overlayBundledLocale(canonical, locale);
        loaded.setDefaults(canonical);

        // [3] :: 파일 버전이 낮다면? 누락 키를 파일에 실제로 복사해 관리자가 편집할 수 있게 한다
        if (savedVer < bundledVer) {
            migrate(loaded, savedVer, bundledVer);
            loaded.options().copyDefaults(true);
            loaded.set("config-version", bundledVer);
            try {
                loaded.save(file);
                plugin.getLogger().info(fileName + " 갱신 (v" + savedVer + " → v" + bundledVer + ")");
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, fileName + " 저장 실패", e);
            }
        }
        return loaded;
        // [STOP] :: 로케일 로드 끝
    }

    // 번들 로케일 값을 정본 위에 덮어 단일 defaults 를 만든다. config-version 은 정본 것을 지킨다
    private void overlayBundledLocale(YamlConfiguration canonical, String locale) {
        if (locale.equals(CANONICAL)) {
            return;
        }
        YamlConfiguration localeBundle = bundled(base + "_" + locale + ".yml");
        if (localeBundle == null) {
            return;
        }
        for (String key : localeBundle.getKeys(true)) {
            if (!localeBundle.isConfigurationSection(key) && !key.equals("config-version")) {
                canonical.set(key, localeBundle.get(key));
            }
        }
        // [STOP] :: 덮어쓰기 끝
    }

    private void migrate(YamlConfiguration loaded, int fromVersion, int toVersion) {
        if (base.equals("gui") && fromVersion < 3 && toVersion >= 3) {
            for (String path : List.of(
                    "editor.header.name", "editor.header.name-muted",
                    "editor.header.name-moving", "editor.header.lore",
                    "editor.header.lore-moving")) {
                loaded.set(path, null);
            }
        }
        // v4 :: 숨은 키(F/1/2/3/Q) 조작을 버튼과 Shift+좌클릭으로 갈아엎었다.
        // 아래 키들은 문구의 뜻 자체가 바뀌었거나(눈금·셀 안내) 사라져서(도움말 책) 지워야 한다
        if (base.equals("gui") && fromVersion < 4 && toVersion >= 4) {
            for (String path : List.of(
                    "editor.ruler", "editor.ruler-cursor", "editor.ruler-anchor",
                    "editor.buttons.range-help", "editor.buttons.edit-help",
                    "editor.buttons.play", "editor.buttons.stop", "editor.buttons.settings",
                    "editor.empty-cell", "editor.note")) {
                loaded.set(path, null);
            }
        }
        // v5 :: 붙여넣기가 "선택 구간의 첫 틱"에서 "배너로 찍은 자리"로 바뀌었다.
        // 눈금과 붙여넣기 버튼 문구가 그에 맞춰 통째로 갈렸다
        if (base.equals("gui") && fromVersion < 5 && toVersion >= 5) {
            for (String path : List.of("editor.ruler", "editor.buttons.paste")) {
                loaded.set(path, null);
            }
        }
        if (base.equals("messages") && fromVersion < 5 && toVersion >= 5) {
            // copy-success 는 단일 틱에서 범위 복사로 뜻이 바뀌어 플레이스홀더가 통째로 교체됐다.
            // copyDefaults 는 없는 키만 채우므로, 뜻이 바뀐 키는 여기서 지워야 새 문장이 들어온다
            for (String path : List.of(
                    "editor.layer-move-selected", "editor.layer-move-cancelled",
                    "editor.layer-moved", "editor.layer-move-invalid",
                    "editor.copy-success")) {
                loaded.set(path, null);
            }
        }
        // v6 :: F 키 안내가 Shift+좌클릭으로 바뀌었다. 죽은 키 5개는 목록에서 지워 파일에서도 걷어낸다
        if (base.equals("messages") && fromVersion < 6 && toVersion >= 6) {
            for (String path : List.of(
                    "editor.copy-no-selection", "editor.range-start", "editor.range-cancelled",
                    "editor.copy-empty", "editor.copy-range-success", "editor.copy-range-empty",
                    "editor.paste-success", "editor.paste-partial")) {
                loaded.set(path, null);
            }
        }
        // [STOP] :: 마이그레이션 끝
    }

    private YamlConfiguration bundled(String fileName) {
        try (InputStream in = plugin.getResource(fileName)) {
            if (in == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    // 값이 없다면(로케일·정본 모두) def. 1-인자 조회라 defaults 까지 본다
    protected final String string(String path, String def) {
        String value = text.getString(path);
        return value != null ? value : def;
    }

    protected final List<String> stringList(String path) {
        return text.getStringList(path);
    }

    protected final TagResolver resolver(String... kv) {
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
