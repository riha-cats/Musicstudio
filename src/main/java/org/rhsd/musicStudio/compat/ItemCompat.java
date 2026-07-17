package org.rhsd.musicStudio.compat;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.Locale;

// =================================================================
// ItemMeta 버전 호환 셈 (shim)
// =================================================================
// setEnchantmentGlintOverride(Boolean)은 Paper 1.20.5+ 에만 존재한다
// 1.20~1.20.4 에는 메서드가 없어 직접 호출하면 그 버전용 빌드가 컴파일조차 안 된다
// 그래서 리플렉션으로 우회 — 단일 소스가 1.20~26.x 전 paper-api 에 대해 컴파일되고,
// 런타임에 메서드가 없으면(1.20.5 미만) 조용히 생략한다. 글린트만 안 보일 뿐 동작엔 영향 없음
// GLINT_OVERRIDE 는 클래스 로드 시 서버의 ItemMeta 인터페이스를 보고 한 번만 해석되므로,
// 같은 JAR 이 구버전/신버전 서버 양쪽에서 각각 알맞게 동작한다
public final class ItemCompat {

    private static final Method GLINT_OVERRIDE = resolveGlint();
    private static final ItemFlag EXTRA_TOOLTIP = resolveExtraTooltipFlag();
    // 1.21+ 에만 있는 jukebox_playable 접근자. 1.20.x paper-api 엔 없어 null 로 남는다
    private static final Method JUKEBOX_GET = resolveMetaMethod("getJukeboxPlayable", 0);
    private static final Method JUKEBOX_SET = resolveMetaMethod("setJukeboxPlayable", 1);

    private ItemCompat() {
    }

    private static Method resolveMetaMethod(String name, int params) {
        for (Method m : ItemMeta.class.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == params) {
                return m;
            }
        }
        return null;
    }

    // 아이템 종류가 스스로 붙이는 부가 툴팁을 숨기는 플래그를 찾는다.
    // 예 :: written_book 은 우리가 넣지 않아도 "원본"(generation)을 제 툴팁으로 그린다
    // 플래그 이름이 1.20.5 에서 HIDE_POTION_EFFECTS → HIDE_ADDITIONAL_TOOLTIP 으로 갈렸다.
    // enum 상수를 코드에 박으면 없는 쪽 버전에서 컴파일이 깨지므로 이름으로 찾는다
    private static ItemFlag resolveExtraTooltipFlag() {
        for (String name : new String[]{"HIDE_ADDITIONAL_TOOLTIP", "HIDE_POTION_EFFECTS"}) {
            try {
                return ItemFlag.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                // 이 버전엔 없는 이름. 다음 후보로
            }
        }
        return null;
    }

    // 바닐라가 자동으로 붙이는 부가 툴팁 숨기기. 미지원 버전에서는 무시된다
    public static void hideExtraTooltip(ItemMeta meta) {
        if (meta == null || EXTRA_TOOLTIP == null) {
            return;
        }
        meta.addItemFlags(EXTRA_TOOLTIP);
    }

    // 뮤직디스크가 제 곡명("C418 - strad")을 회색 툴팁으로 그린다.
    // 1.20.x 는 위 hideExtraTooltip 플래그로 꺼지지만, 1.21+ 는 그 줄을 jukebox_playable
    // 컴포넌트가 담당하고 HIDE_ADDITIONAL_TOOLTIP 로는 안 꺼진다 (Paper #11141).
    // 그래서 show_in_tooltip 을 끈 명시적 컴포넌트로 덮는다.
    // 기본 디스크는 이 컴포넌트가 override 패치에 없어 getJukeboxPlayable() 이 엉뚱한
    // 폴백 곡을 주므로, 곡 키도 디스크 종류에 맞춰 다시 박는다 (주크박스에 넣어도 제 곡이 나오게).
    // 1.20.x paper-api 엔 이 API 가 아예 없어 메서드가 안 잡히고 조용히 생략된다
    public static void hideJukeboxSong(ItemMeta meta, Material disc) {
        if (meta == null || JUKEBOX_GET == null || JUKEBOX_SET == null) {
            return;
        }
        String song = jukeboxSongName(disc);
        if (song == null) {
            return;
        }
        try {
            Object component = JUKEBOX_GET.invoke(meta);
            if (component == null) {
                return;
            }
            // [1] :: 곡명 숨김이 본질이라 먼저 건다. 아래 곡 키 교체가 실패해도 이건 남는다
            component.getClass().getMethod("setShowInTooltip", boolean.class).invoke(component, false);
            // [2] :: 곡 키를 디스크에 맞추기. 레지스트리에 없는 이름이면 조용히 넘어간다
            try {
                component.getClass().getMethod("setSongKey", NamespacedKey.class)
                        .invoke(component, NamespacedKey.minecraft(song));
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                // 이 서버 빌드엔 없는 곡. 곡명은 이미 숨겼으니 그대로 둔다
            }
            JUKEBOX_SET.invoke(meta, component);
        } catch (ReflectiveOperationException ignored) {
            // 시그니처가 다르거나 없는 서버. 곡명 툴팁만 남을 뿐 치명적이지 않다
        }
    }

    // MUSIC_DISC_STRAD -> "strad". 뮤직디스크가 아니면 null 을 돌려 jukebox 컴포넌트를 안 건드리게 한다
    static String jukeboxSongName(Material disc) {
        if (disc == null) {
            return null;
        }
        String prefix = "MUSIC_DISC_";
        String name = disc.name();
        if (!name.startsWith(prefix)) {
            return null;
        }
        return name.substring(prefix.length()).toLowerCase(Locale.ROOT);
    }

    private static Method resolveGlint() {
        try {
            return ItemMeta.class.getMethod("setEnchantmentGlintOverride", Boolean.class);
        } catch (NoSuchMethodException e) {
            // 1.20.5 미만
            return null;
        }
    }

    // 아이템에 마법부여 글린트 강제. 미지원 버전(1.20.5 미만)에서는 무시된다
    public static void setGlint(ItemMeta meta, boolean glint) {
        if (meta == null || GLINT_OVERRIDE == null) {
            return;
        }
        try {
            GLINT_OVERRIDE.invoke(meta, glint);
        } catch (ReflectiveOperationException ignored) {
            // 런타임 호출 실패 시 글린트만 생략. 치명적이지 않음
        }
    }
}

// 컴플리트
