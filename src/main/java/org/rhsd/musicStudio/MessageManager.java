package org.rhsd.musicStudio;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

// =================================================================
// 채팅 메시지 (language 파일의 messages 섹션)
// =================================================================
// MiniMessage 문법과 <key> 플레이스홀더 지원
// 값에 태그가 섞여 있어도 Placeholder.unparsed 로 리터럴 삽입되므로 인젝션 위험 없음
// 문구는 LanguageStore 가 3단으로 겹쳐 들고 있고, 여기서는 messages 섹션만 떼어 본다
public final class MessageManager {

    private static final String PREFIX = "messages.";

    private final LanguageStore store;

    public MessageManager(LanguageStore store) {
        this.store = store;
    }

    private String text(String key) {
        return store.string(PREFIX + key, "<red>[Missing: " + key + "]");
    }

    // 접두사(prefix)를 붙인 메시지
    public Component get(String key, String... kv) {
        String prefix = store.string(PREFIX + "prefix", "");
        return LanguageStore.MM.deserialize(prefix + text(key), store.resolver(kv));
    }

    // 접두사 없는 메시지 (목록 항목 등)
    public Component raw(String key, String... kv) {
        return LanguageStore.MM.deserialize(text(key), store.resolver(kv));
    }

    public void send(CommandSender sender, String key, String... kv) {
        sender.sendMessage(get(key, kv));
    }

    public void sendRaw(CommandSender sender, String key, String... kv) {
        sender.sendMessage(raw(key, kv));
    }
}

// 컴플리트
