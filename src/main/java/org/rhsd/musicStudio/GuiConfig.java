package org.rhsd.musicStudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.ArrayList;
import java.util.List;

// =================================================================
// GUI 텍스트 (language 파일의 gui 섹션)
// =================================================================
// 인벤토리 제목, 아이템 이름, lore
// 이름/lore 는 기본적으로 기울임을 끈다 (<i>로 감싼 부분만 기울임). 제목은 서식 그대로
// 문구는 LanguageStore 가 3단으로 겹쳐 들고 있고, 여기서는 gui 섹션만 떼어 본다
public final class GuiConfig {

    private static final String PREFIX = "gui.";

    private final LanguageStore store;

    public GuiConfig(LanguageStore store) {
        this.store = store;
    }

    private String raw(String path) {
        return store.string(PREFIX + path, "<red>[" + path + "]");
    }

    // 아이템 이름/단일 라인 (기울임 꺼짐)
    public Component name(String path, String... kv) {
        return LanguageStore.MM.deserialize(raw(path), store.resolver(kv))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    // 인벤토리 제목 (서식 그대로)
    public Component title(String path, String... kv) {
        return LanguageStore.MM.deserialize(raw(path), store.resolver(kv));
    }

    // lore 각 줄 (기울임 꺼짐). 빈 문자열은 빈 줄로 유지
    public List<Component> lore(String path, String... kv) {
        List<String> raws = store.stringList(PREFIX + path);
        List<Component> out = new ArrayList<>(raws.size());
        TagResolver resolver = store.resolver(kv);
        for (String line : raws) {
            out.add(LanguageStore.MM.deserialize(line, resolver)
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        return out;
    }
}

// 컴플리트
