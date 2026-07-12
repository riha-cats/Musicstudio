package org.rhsd.musicStudio.compat;

import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;

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

    private ItemCompat() {
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
