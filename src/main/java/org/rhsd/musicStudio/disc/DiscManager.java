package org.rhsd.musicStudio.disc;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.rhsd.musicStudio.GuiConfig;
import org.rhsd.musicStudio.compat.ItemCompat;
import org.rhsd.musicStudio.integration.ItemsAdderHook;
import org.rhsd.musicStudio.model.Song;

import java.util.Map;
import java.util.logging.Level;

// =================================================================
// 음반 매니저
// =================================================================
// MusicStudio 음반 아이템의 생성/식별과 추출 비용 처리 담당
// 베이스 아이템은 config 의 disc.itemsadder-id 가 있고 ItemsAdder 를 쓸 수 있으면
// IA 커스텀 아이템, 아니면 disc.material 바닐라 뮤직디스크. 어느 쪽이든 표시 이름/lore
// (GuiConfig)와 곡 id(PDC)를 덧입힌다. 재생은 소지자 누구나 가능하고,
// 추출 권한 확인은 명령/메뉴 쪽 책임이다
public final class DiscManager {

    private final JavaPlugin plugin;
    private final NamespacedKey songIdKey;
    private final GuiConfig gui;
    private final ItemsAdderHook itemsAdder;

    // config 캐시. 리로드로 갱신되므로 final 아님
    private Material discMaterial;
    private String itemsAdderId;
    private boolean glint;
    private boolean unbreakable;
    private int customModelData;
    private boolean costEnabled;
    private Material costItem;
    private int costAmount;

    public DiscManager(JavaPlugin plugin, GuiConfig gui, ItemsAdderHook itemsAdder) {
        this.plugin = plugin;
        this.songIdKey = new NamespacedKey(plugin, "song_id");
        this.gui = gui;
        this.itemsAdder = itemsAdder;
        reload();
    }

    // config 에서 음반 관련 설정을 다시 읽는다. 리로드 시 호출
    public void reload() {
        FileConfiguration config = plugin.getConfig();
        this.discMaterial = parseMaterial(plugin, config.getString("disc.material", "MUSIC_DISC_5"),
                Material.MUSIC_DISC_5, "disc.material");
        this.itemsAdderId = config.getString("disc.itemsadder-id", "");
        this.glint = config.getBoolean("disc.glint", false);
        this.unbreakable = config.getBoolean("disc.unbreakable", false);
        this.customModelData = config.getInt("disc.custom-model-data", 0);
        this.costEnabled = config.getBoolean("disc.cost.enabled", false);
        this.costItem = costEnabled
                ? parseMaterial(plugin, config.getString("disc.cost.item", "EMERALD"),
                        Material.EMERALD, "disc.cost.item")
                : Material.EMERALD;
        this.costAmount = Math.max(1, config.getInt("disc.cost.amount", 5));
    }

    // Material 이름 파싱. 올바르지 않다면? 경고 후 폴백
    private static Material parseMaterial(JavaPlugin plugin, String name, Material fallback, String key) {
        if (name == null) {
            return fallback;
        }
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING,
                    key + " 값이 잘못됐습니다: " + name + " → " + fallback + " 사용");
            return fallback;
        }
    }

    // =================================================================
    // 추출 비용
    // =================================================================

    public boolean takeCost(Player player) {
        // [1] :: 비용 기능이 꺼져있는가? 무료 통과
        if (!costEnabled) {
            return true;
        }
        // [2] :: 재료가 부족한가?
        if (countItems(player, costItem) < costAmount) {
            return false;
        }
        // [3] PASSED :: 재료 차감
        removeItems(player, costItem, costAmount);
        return true;
    }

    public boolean isCostEnabled() {
        return costEnabled;
    }

    public Material costItemMaterial() {
        return costItem;
    }

    public int costAmount() {
        return costAmount;
    }

    // 비용 부족 메시지에 넣을 플레이스홀더 (item, amount)
    public String[] costPlaceholders() {
        return new String[]{"item", costItem.name(), "amount", String.valueOf(costAmount)};
    }

    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeItems(Player player, Material material, int amount) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                if (item.getAmount() <= amount) {
                    amount -= item.getAmount();
                    contents[i] = null;
                } else {
                    item.setAmount(item.getAmount() - amount);
                    amount = 0;
                }
            }
        }
        player.getInventory().setContents(contents);
    }

    // =================================================================
    // 음반 생성 / 식별
    // =================================================================

    public ItemStack createDisc(Song song) {
        // [1] :: 베이스 아이템. ItemsAdder 우선, 실패 시 바닐라 폴백
        ItemStack disc = null;
        if (itemsAdderId != null && !itemsAdderId.isBlank()) {
            // null 가능 (미설치/없는 id/오류)
            disc = itemsAdder.getCustomItem(itemsAdderId);
        }
        if (disc == null) {
            disc = new ItemStack(discMaterial);
        }

        // [2] :: 이름/lore/PDC 덧입히기
        ItemMeta meta = disc.getItemMeta();
        if (meta != null) {
            String[] ph = {
                    "song", song.name(),
                    "layers", String.valueOf(song.layerCount()),
                    "notes", String.valueOf(song.noteCount()),
                    "cells", String.valueOf(song.maxTick() + 1)
            };
            meta.displayName(gui.name("disc.name", ph));
            meta.lore(gui.lore("disc.lore", ph));
            meta.getPersistentDataContainer().set(songIdKey, PersistentDataType.STRING, song.id());

            // [3] :: 바닐라 속성 (config)
            if (glint) {
                ItemCompat.setGlint(meta, true);
            }
            if (unbreakable) {
                meta.setUnbreakable(true);
            }
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }

            disc.setItemMeta(meta);
        }
        return disc;
        // [STOP] :: 음반 생성 끝
    }

    public String getSongId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(songIdKey, PersistentDataType.STRING);
    }

    public boolean isDisc(ItemStack item) {
        return getSongId(item) != null;
    }

    // 곡을 음반으로 만들어 지급. 인벤토리가 꽉 찼다면? 남는 분량은 발 밑에 떨군다
    public void giveDisc(Player player, Song song) {
        ItemStack disc = createDisc(song);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(disc);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(item ->
                    player.getWorld().dropItem(player.getLocation(), item));
        }
    }
}

// 컴플리트
