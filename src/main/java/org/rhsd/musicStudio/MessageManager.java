package org.rhsd.musicStudio;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

// =================================================================
// 채팅 메시지 (messages_<locale>.yml)
// =================================================================
// MiniMessage 문법과 <key> 플레이스홀더 지원
// 값에 태그가 섞여 있어도 Placeholder.unparsed 로 리터럴 삽입되므로 인젝션 위험 없음
public final class MessageManager extends LocalizedConfig {

    public MessageManager(JavaPlugin plugin) {
        super(plugin, "messages");
    }

    // 접두사(prefix)를 붙인 메시지
    public Component get(String key, String... kv) {
        String prefix = string("prefix", "");
        return MM.deserialize(prefix + string(key, "<red>[Missing: " + key + "]"), resolver(kv));
    }

    // 접두사 없는 메시지 (목록 항목 등)
    public Component raw(String key, String... kv) {
        return MM.deserialize(string(key, "<red>[Missing: " + key + "]"), resolver(kv));
    }

    public void send(CommandSender sender, String key, String... kv) {
        sender.sendMessage(get(key, kv));
    }

    public void sendRaw(CommandSender sender, String key, String... kv) {
        sender.sendMessage(raw(key, kv));
    }
}

// 컴플리트
