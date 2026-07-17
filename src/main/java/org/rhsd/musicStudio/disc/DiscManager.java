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
import org.rhsd.musicStudio.integration.VaultHook;
import org.rhsd.musicStudio.model.Song;

import java.util.Locale;
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

    // 비용을 무엇으로 받는가
    public enum CostType { ITEM, ECONOMY }

    // 비용 처리 결과. 부족과 프로바이더 없음을 구분해야 안내 문구가 갈린다
    public enum CostResult { OK, INSUFFICIENT, PROVIDER_MISSING }

    private final JavaPlugin plugin;
    private final NamespacedKey songIdKey;
    private final GuiConfig gui;
    private final ItemsAdderHook itemsAdder;
    private final VaultHook vault;

    // config 캐시. 리로드로 갱신되므로 final 아님
    private Material discMaterial;
    private String itemsAdderId;
    private boolean glint;
    private boolean unbreakable;
    private int customModelData;
    private boolean costEnabled;
    private CostType costType;
    private Material costItem;
    // ITEM 이면 개수, ECONOMY 면 금액. Vault 가 double 을 쓰므로 double 로 통일한다
    private double costAmount;

    public DiscManager(JavaPlugin plugin, GuiConfig gui, ItemsAdderHook itemsAdder, VaultHook vault) {
        this.plugin = plugin;
        this.songIdKey = new NamespacedKey(plugin, "song_id");
        this.gui = gui;
        this.itemsAdder = itemsAdder;
        this.vault = vault;
        reload();
    }

    // config 에서 음반 관련 설정을 다시 읽는다. 리로드 시 호출
    public void reload() {
        FileConfiguration config = plugin.getConfig();
        this.discMaterial = parseMaterial(plugin, config.getString("disc.material", "MUSIC_DISC_STRAD"),
                Material.MUSIC_DISC_STRAD, "disc.material");
        this.itemsAdderId = config.getString("disc.itemsadder-id", "");
        this.glint = config.getBoolean("disc.glint", false);
        this.unbreakable = config.getBoolean("disc.unbreakable", false);
        this.customModelData = config.getInt("disc.custom-model-data", 0);
        this.costEnabled = config.getBoolean("disc.cost.enabled", false);
        this.costType = parseCostType(config.getString("disc.cost.type", "ITEM"));
        this.costItem = costEnabled
                ? parseMaterial(plugin, config.getString("disc.cost.item", "EMERALD"),
                        Material.EMERALD, "disc.cost.item")
                : Material.EMERALD;
        this.costAmount = Math.max(0, config.getDouble("disc.cost.amount", 5));
    }

    // 잘못된 type 이면 ITEM 으로 폴백하고 경고. 서버가 오설정해도 추출은 굴러가야 한다
    private CostType parseCostType(String raw) {
        if (raw == null) {
            return CostType.ITEM;
        }
        try {
            return CostType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("disc.cost.type 값이 잘못됐습니다: " + raw + " → ITEM 사용");
            return CostType.ITEM;
        }
    }

    // ITEM 비용의 개수. amount 를 정수로 내린다 (최소 1)
    private int itemAmount() {
        return (int) Math.max(1, Math.round(costAmount));
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

    public CostResult takeCost(Player player) {
        // [1] :: 비용 기능이 꺼져있는가? 무료 통과
        if (!costEnabled) {
            return CostResult.OK;
        }
        // [2] :: economy 비용인가?
        if (costType == CostType.ECONOMY) {
            // [A] :: economy 를 못 쓰는데(Vault/프로바이더 없음) 무료로 주면 안 된다. 막고 알린다
            if (!vault.isAvailable()) {
                return CostResult.PROVIDER_MISSING;
            }
            if (!vault.has(player, costAmount)) {
                return CostResult.INSUFFICIENT;
            }
            // 차감이 거부되면(경합 등) 부족으로 본다. 돈을 못 뺐으니 지급하면 안 된다
            return vault.withdraw(player, costAmount) ? CostResult.OK : CostResult.INSUFFICIENT;
        }
        // [3] :: 아이템 비용. 재료가 부족한가?
        int need = itemAmount();
        if (countItems(player, costItem) < need) {
            return CostResult.INSUFFICIENT;
        }
        // [4] PASSED :: 재료 차감
        removeItems(player, costItem, need);
        return CostResult.OK;
    }

    public boolean isCostEnabled() {
        return costEnabled;
    }

    // 가격 표기 한 줄. ITEM 은 "EMERALD x5", ECONOMY 는 서버 통화 표기("$5.00" 등)
    public String costLabel() {
        if (costType == CostType.ECONOMY) {
            return vault.format(costAmount);
        }
        return costItem.name() + " x" + itemAmount();
    }

    // 가격/부족 메시지에 넣을 플레이스홀더 (cost)
    public String[] costPlaceholders() {
        return new String[]{"cost", costLabel()};
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

            // [3] :: 베이스 뮤직디스크가 제 곡명을 툴팁에 그린다. 우리 lore 만 남기려면 지운다.
            // 플래그는 1.20.x, jukebox 컴포넌트는 1.21+ 를 덮는다. ItemsAdder 커스텀 등
            // 뮤직디스크가 아닌 베이스에는 hideJukeboxSong 이 알아서 손대지 않는다
            ItemCompat.hideExtraTooltip(meta);
            ItemCompat.hideJukeboxSong(meta, disc.getType());

            // [4] :: 바닐라 속성 (config)
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
