package org.rhsd.musicStudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.rhsd.musicStudio.model.Instrument;

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

    // 악기 이름. <instrument> 플레이스홀더로 꽂히는 자리라 Component 가 아니라 평문이다
    // (unparsed 로 들어가므로 여기에 MiniMessage 를 적어도 색이 아니라 글자로 보인다)
    // 키가 없으면 enum 이름을 그대로 내보내 [Missing] 대신 최소한 뭐가 빠졌는지는 읽히게 한다
    public String instrumentName(Instrument instrument) {
        return store.string(PREFIX + "instrument.names." + instrument.key(), instrument.name());
    }
}

// 컴플리트
