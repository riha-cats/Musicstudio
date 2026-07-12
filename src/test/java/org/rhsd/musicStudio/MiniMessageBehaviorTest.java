package org.rhsd.musicStudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// GuiConfig/MessageManager 가 의존하는 MiniMessage 동작을 실제 실행으로 검증
// 색상 파싱, italic 기본 꺼짐 + 명시 유지, 플레이스홀더 인젝션 방지, 한글
class MiniMessageBehaviorTest {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();

    @Test
    void colorIsParsed() {
        Component c = mm.deserialize("<red>음악");
        assertEquals(NamedTextColor.RED, c.color());
        assertEquals("음악", plain.serialize(c));
    }

    @Test
    void italicOffWhenNotSpecified() {
        // GuiConfig.name()/lore()와 동일: 명시 안 하면 ITALIC=FALSE 강제
        Component c = mm.deserialize("<red>음악")
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        assertEquals(TextDecoration.State.FALSE, c.decoration(TextDecoration.ITALIC));
    }

    @Test
    void explicitItalicSurvivesDecorationIfAbsent() {
        // 루트에서 <i>를 명시하면 decorationIfAbsent가 건드리지 않음(absent 아님)
        Component c = mm.deserialize("<i>강조")
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        assertEquals(TextDecoration.State.TRUE, c.decoration(TextDecoration.ITALIC));
    }

    @Test
    void placeholderValueIsNotInjected() {
        // 곡명에 MiniMessage 태그가 있어도 리터럴로 삽입되어야 안전(Placeholder.unparsed)
        Component c = mm.deserialize("<name>", Placeholder.unparsed("name", "<bold><red>해킹"));
        // 태그가 적용 안 되고 그대로 나와야 하고, red 도 적용되면 안 된다
        assertEquals("<bold><red>해킹", plain.serialize(c));
        assertNull(c.color());
    }

    @Test
    void koreanPlaceholderText() {
        Component c = mm.deserialize("<green>레이어 <n>: <layer_name>",
                Placeholder.unparsed("n", "2"),
                Placeholder.unparsed("layer_name", "베이스"));
        assertEquals("레이어 2: 베이스", plain.serialize(c));
        assertEquals(NamedTextColor.GREEN, c.color());
    }

    @Test
    void unknownPlaceholderStaysLiteral() {
        // resolver에 없는 키는 그대로 출력(예외 없음)
        Component c = mm.deserialize("<gray>틱 <tick>");
        assertEquals("틱 <tick>", plain.serialize(c));
    }
}
