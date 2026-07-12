package org.rhsd.musicStudio.integration;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

// =================================================================
// ItemsAdder 선택 연동
// =================================================================
// 컴파일 의존성 없이 리플렉션으로 호출 — ItemsAdder 가 없어도 NoClassDefFoundError 없이 동작
// 호출마다 플러그인 활성 여부를 확인하고(IA 가 꺼지거나 언로드돼도 안전),
// 리플렉션 메서드는 한 번만 찾아 캐시한다
// 한 번이라도 실패하면 연동을 영구 비활성화하고 바닐라 아이템으로 폴백
// 음반 추출은 서버 가동 후에만 일어나고 plugin.yml 의 softdepend 로 로드 순서를 보장하므로,
// 이 시점엔 IA 데이터가 항상 준비돼 있다
public final class ItemsAdderHook {

    private final JavaPlugin plugin;
    private final boolean installed;
    // CustomStack.getInstance(String) / CustomStack#getItemStack()
    private Method getInstanceMethod;
    private Method getItemStackMethod;
    private boolean reflectionFailed = false;

    public ItemsAdderHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.installed = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
        if (installed) {
            plugin.getLogger().info("ItemsAdder 감지됨 — 커스텀 음반 아이템 연동 가능.");
        }
    }

    // ItemsAdder 가 설치되어 있고 현재 활성 상태인가?
    public boolean isAvailable() {
        return installed && !reflectionFailed
                && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
    }

    // ItemsAdder 커스텀 아이템 가져오기. 사용 불가/없는 id/오류라면? null (호출측이 폴백)
    public ItemStack getCustomItem(String id) {
        // [1] :: id 가 비었거나 IA 를 쓸 수 없는가?
        if (id == null || id.isBlank() || !isAvailable()) {
            return null;
        }
        try {
            // [2] :: 리플렉션 메서드 캐시. 처음 한 번만 찾는다
            if (getInstanceMethod == null || getItemStackMethod == null) {
                Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
                getInstanceMethod = customStack.getMethod("getInstance", String.class);
                getItemStackMethod = customStack.getMethod("getItemStack");
            }
            // [3] :: id 로 커스텀 아이템 조회
            Object stack = getInstanceMethod.invoke(null, id);
            if (stack == null) {
                plugin.getLogger().warning("ItemsAdder 아이템을 찾을 수 없습니다: " + id + " → 바닐라로 폴백");
                return null;
            }
            Object item = getItemStackMethod.invoke(stack);
            return (item instanceof ItemStack itemStack) ? itemStack.clone() : null;
        } catch (Throwable t) {
            // API 변경/예외 시 연동을 영구 비활성화하고 폴백
            reflectionFailed = true;
            plugin.getLogger().warning("ItemsAdder 연동 실패 — 바닐라 아이템으로 폴백합니다: " + t.getMessage());
            return null;
        }
        // [STOP] :: 커스텀 아이템 조회 끝
    }
}

// 컴플리트
