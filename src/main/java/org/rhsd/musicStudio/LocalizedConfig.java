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
        loaded.setDefaults(canonical);

        // [3] :: 파일 버전이 낮다면? 누락 키를 파일에 실제로 복사해 관리자가 편집할 수 있게 한다
        int bundledVer = canonical.getInt("config-version", 1);
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

    private void migrate(YamlConfiguration loaded, int fromVersion, int toVersion) {
        if (base.equals("gui") && fromVersion < 3 && toVersion >= 3) {
            for (String path : List.of(
                    "editor.header.name", "editor.header.name-muted",
                    "editor.header.name-moving", "editor.header.lore",
                    "editor.header.lore-moving")) {
                loaded.set(path, null);
            }
        }
        if (base.equals("messages") && fromVersion < 5 && toVersion >= 5) {
            for (String path : List.of(
                    "editor.layer-move-selected", "editor.layer-move-cancelled",
                    "editor.layer-moved", "editor.layer-move-invalid")) {
                loaded.set(path, null);
            }
        }
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
