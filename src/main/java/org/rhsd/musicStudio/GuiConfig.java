package org.rhsd.musicStudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

// =================================================================
// GUI 텍스트 (gui_<locale>.yml)
// =================================================================
// 인벤토리 제목, 아이템 이름, lore
// 이름/lore 는 기본적으로 기울임을 끈다 (<i>로 감싼 부분만 기울임). 제목은 서식 그대로
public final class GuiConfig extends LocalizedConfig {

    public GuiConfig(JavaPlugin plugin) {
        super(plugin, "gui");
    }

    // 아이템 이름/단일 라인 (기울임 꺼짐)
    public Component name(String path, String... kv) {
        return MM.deserialize(string(path, "<red>[" + path + "]"), resolver(kv))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    // 인벤토리 제목 (서식 그대로)
    public Component title(String path, String... kv) {
        return MM.deserialize(string(path, "<red>[" + path + "]"), resolver(kv));
    }

    // lore 각 줄 (기울임 꺼짐). 빈 문자열은 빈 줄로 유지
    public List<Component> lore(String path, String... kv) {
        List<String> raws = stringList(path);
        List<Component> out = new ArrayList<>(raws.size());
        TagResolver resolver = resolver(kv);
        for (String raw : raws) {
            out.add(MM.deserialize(raw, resolver)
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        return out;
    }
}

// 컴플리트
