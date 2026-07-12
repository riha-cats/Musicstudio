package org.rhsd.musicStudio.model;

import org.bukkit.Material;
import org.bukkit.Sound;

// =================================================================
// 악기 (바닐라 노트블록 16종)
// =================================================================
// 각 값은 재생용 Sound, GUI 표시용 Material(NBS 의 악기 결정 블록과 같은 매핑),
// 한글 표시명을 가진다. 음역은 key 0~24 (F#3~F#5, 2옥타브) — pitch 매핑은 Note.pitch 참고
public enum Instrument {
    HARP(Sound.BLOCK_NOTE_BLOCK_HARP, Material.DIRT, "하프"),
    BASS(Sound.BLOCK_NOTE_BLOCK_BASS, Material.OAK_PLANKS, "베이스"),
    BASEDRUM(Sound.BLOCK_NOTE_BLOCK_BASEDRUM, Material.STONE, "베이스드럼"),
    SNARE(Sound.BLOCK_NOTE_BLOCK_SNARE, Material.SAND, "스네어"),
    HAT(Sound.BLOCK_NOTE_BLOCK_HAT, Material.GLASS, "하이햇"),
    GUITAR(Sound.BLOCK_NOTE_BLOCK_GUITAR, Material.WHITE_WOOL, "기타"),
    FLUTE(Sound.BLOCK_NOTE_BLOCK_FLUTE, Material.CLAY, "플루트"),
    BELL(Sound.BLOCK_NOTE_BLOCK_BELL, Material.GOLD_BLOCK, "벨"),
    CHIME(Sound.BLOCK_NOTE_BLOCK_CHIME, Material.PACKED_ICE, "차임"),
    XYLOPHONE(Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, Material.BONE_BLOCK, "실로폰"),
    IRON_XYLOPHONE(Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, Material.IRON_BLOCK, "철실로폰"),
    COW_BELL(Sound.BLOCK_NOTE_BLOCK_COW_BELL, Material.SOUL_SAND, "카우벨"),
    DIDGERIDOO(Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, Material.PUMPKIN, "디저리두"),
    BIT(Sound.BLOCK_NOTE_BLOCK_BIT, Material.EMERALD_BLOCK, "비트"),
    BANJO(Sound.BLOCK_NOTE_BLOCK_BANJO, Material.HAY_BLOCK, "밴조"),
    PLING(Sound.BLOCK_NOTE_BLOCK_PLING, Material.GLOWSTONE, "플링");

    private final Sound sound;
    private final Material icon;
    private final String displayName;

    Instrument(Sound sound, Material icon, String displayName) {
        this.sound = sound;
        this.icon = icon;
        this.displayName = displayName;
    }

    public Sound sound() {
        return sound;
    }

    public Material icon() {
        return icon;
    }

    public String displayName() {
        return displayName;
    }

    // 저장된 이름 파싱. 알 수 없는 이름이라면? HARP 로 폴백
    public static Instrument fromString(String name) {
        if (name == null) {
            return HARP;
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return HARP;
        }
    }

    // 순환 전환 (다음 악기)
    public Instrument next() {
        Instrument[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    // 순환 전환 (이전 악기)
    public Instrument prev() {
        Instrument[] v = values();
        return v[(ordinal() - 1 + v.length) % v.length];
    }
}

// 컴플리트
