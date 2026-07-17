package org.rhsd.musicStudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.rhsd.musicStudio.model.Instrument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// =================================================================
// GUI 텍스트 (language 파일의 gui 섹션)
// =================================================================
// 인벤토리 제목, 아이템 이름, lore
// 이름/lore 는 기본적으로 기울임을 끈다 (<i>로 감싼 부분만 기울임). 제목은 서식 그대로
// 문구는 LanguageStore 가 3단으로 겹쳐 들고 있고, 여기서는 gui 섹션만 떼어 본다
public final class GuiConfig {

    private static final String PREFIX = "gui.";

    private final LanguageStore store;

    // 플레이스홀더 없는 문구는 리로드 전까지 값이 같다. 렌더가 클릭마다 같은 문자열을
    // 다시 MiniMessage 파싱하지 않도록 파싱 결과를 재사용한다 (미리듣기 중 눈금 갱신처럼
    // 틱 루프에 얹힌 경로에서 특히 이득). 리로드로 문구가 바뀌면 세대가 올라 통째로 비운다.
    // GUI 는 메인 스레드에서만 도므로 동기화는 필요 없다
    private final Map<String, Component> nameCache = new HashMap<>();
    private final Map<String, List<Component>> loreCache = new HashMap<>();
    private int cacheGen = -1;

    public GuiConfig(LanguageStore store) {
        this.store = store;
    }

    // 렌더 쪽이 만들어 둔 정적 ItemStack 을 언제 버릴지 판단하는 데 쓴다
    public int generation() {
        return store.generation();
    }

    private String raw(String path) {
        return store.string(PREFIX + path, "<red>[" + path + "]");
    }

    // 문구가 바뀌었으면(리로드) 캐시를 버린다. 안 그러면 옛 문구를 붙든다
    private void ensureFresh() {
        int gen = store.generation();
        if (gen != cacheGen) {
            nameCache.clear();
            loreCache.clear();
            cacheGen = gen;
        }
    }

    // 아이템 이름/단일 라인 (기울임 꺼짐)
    public Component name(String path, String... kv) {
        // 플레이스홀더가 있으면 매번 값이 달라 캐시하지 않는다
        if (kv.length > 0) {
            return deserializeName(path, store.resolver(kv));
        }
        ensureFresh();
        Component cached = nameCache.get(path);
        if (cached == null) {
            cached = deserializeName(path, TagResolver.empty());
            nameCache.put(path, cached);
        }
        return cached;
    }

    private Component deserializeName(String path, TagResolver resolver) {
        return LanguageStore.MM.deserialize(raw(path), resolver)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    // 인벤토리 제목 (서식 그대로). GUI 를 열 때 한 번만 그리므로 캐시하지 않는다
    public Component title(String path, String... kv) {
        return LanguageStore.MM.deserialize(raw(path), store.resolver(kv));
    }

    // lore 각 줄 (기울임 꺼짐). 빈 문자열은 빈 줄로 유지
    public List<Component> lore(String path, String... kv) {
        if (kv.length > 0) {
            return deserializeLore(path, store.resolver(kv));
        }
        ensureFresh();
        List<Component> cached = loreCache.get(path);
        if (cached == null) {
            // 캐시본은 밖에서 못 고치게 잠근다. meta.lore(List) 는 어차피 복사하므로 문제없다
            cached = Collections.unmodifiableList(deserializeLore(path, TagResolver.empty()));
            loreCache.put(path, cached);
        }
        return cached;
    }

    private List<Component> deserializeLore(String path, TagResolver resolver) {
        List<String> raws = store.stringList(PREFIX + path);
        List<Component> out = new ArrayList<>(raws.size());
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
