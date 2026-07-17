package org.rhsd.musicStudio.model;

import org.bukkit.Material;
import org.bukkit.Sound;

import java.util.Locale;
import java.util.function.Supplier;

// =================================================================
// 악기
// =================================================================
// 두 묶음으로 나뉜다 ::
//   앞 16개 = 바닐라 노트블록 악기. 순서가 NBS 악기 id 0~15 와 그대로 맞물린다
//   뒤 6개  = 노트블록에 머리를 얹었을 때 나는 소리 (1.20 에서 추가)
//
// 순서를 절대 건드리지 않는다. 중간에 끼워넣으면 NbsImporter 의 id 매핑이 통째로 밀린다.
// 새 악기는 늘 맨 뒤에 붙인다 (저장은 name() 기반이라 기존 곡은 영향 없음)
//
// 머리 소리는 바닐라에선 음이 안 변하지만, playSound 는 pitch 를 그냥 받으므로
// 여기서는 다른 악기와 똑같이 음을 태운다. 에디터 격자가 모든 레이어에서 같은 뜻을 갖는 편이
// 낫고, "이 악기만 음이 안 바뀜" 같은 예외를 안 만들 수 있다
public enum Instrument {
    HARP(() -> Sound.BLOCK_NOTE_BLOCK_HARP, Material.DIRT),
    BASS(() -> Sound.BLOCK_NOTE_BLOCK_BASS, Material.OAK_PLANKS),
    BASEDRUM(() -> Sound.BLOCK_NOTE_BLOCK_BASEDRUM, Material.STONE),
    SNARE(() -> Sound.BLOCK_NOTE_BLOCK_SNARE, Material.SAND),
    HAT(() -> Sound.BLOCK_NOTE_BLOCK_HAT, Material.GLASS),
    GUITAR(() -> Sound.BLOCK_NOTE_BLOCK_GUITAR, Material.WHITE_WOOL),
    FLUTE(() -> Sound.BLOCK_NOTE_BLOCK_FLUTE, Material.CLAY),
    BELL(() -> Sound.BLOCK_NOTE_BLOCK_BELL, Material.GOLD_BLOCK),
    CHIME(() -> Sound.BLOCK_NOTE_BLOCK_CHIME, Material.PACKED_ICE),
    XYLOPHONE(() -> Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, Material.BONE_BLOCK),
    IRON_XYLOPHONE(() -> Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, Material.IRON_BLOCK),
    COW_BELL(() -> Sound.BLOCK_NOTE_BLOCK_COW_BELL, Material.SOUL_SAND),
    DIDGERIDOO(() -> Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, Material.PUMPKIN),
    BIT(() -> Sound.BLOCK_NOTE_BLOCK_BIT, Material.EMERALD_BLOCK),
    BANJO(() -> Sound.BLOCK_NOTE_BLOCK_BANJO, Material.HAY_BLOCK),
    PLING(() -> Sound.BLOCK_NOTE_BLOCK_PLING, Material.GLOWSTONE),

    // 여기부터 머리 소리. 아이콘은 노트블록에 얹는 바로 그 머리를 쓴다
    CREEPER(() -> Sound.BLOCK_NOTE_BLOCK_IMITATE_CREEPER, Material.CREEPER_HEAD),
    ENDER_DRAGON(() -> Sound.BLOCK_NOTE_BLOCK_IMITATE_ENDER_DRAGON, Material.DRAGON_HEAD),
    PIGLIN(() -> Sound.BLOCK_NOTE_BLOCK_IMITATE_PIGLIN, Material.PIGLIN_HEAD),
    SKELETON(() -> Sound.BLOCK_NOTE_BLOCK_IMITATE_SKELETON, Material.SKELETON_SKULL),
    WITHER_SKELETON(() -> Sound.BLOCK_NOTE_BLOCK_IMITATE_WITHER_SKELETON, Material.WITHER_SKELETON_SKULL),
    ZOMBIE(() -> Sound.BLOCK_NOTE_BLOCK_IMITATE_ZOMBIE, Material.ZOMBIE_HEAD);

    // NBS 파일의 악기 id 0~15 가 바닐라 악기다. 16 이상은 그 파일이 자체 정의한 커스텀 사운드라
    // 우리 악기로 옮길 수 없다. values().length 로 재면 머리 악기가 커스텀 자리를 먹으므로 못 쓴다
    public static final int VANILLA_COUNT = 16;

    private final Supplier<Sound> soundSupplier;
    private final Material icon;

    Instrument(Supplier<Sound> soundSupplier, Material icon) {
        this.soundSupplier = soundSupplier;
        this.icon = icon;
    }

    public Sound sound() {
        return soundSupplier.get();
    }

    public Material icon() {
        return icon;
    }

    // language 파일에서 이름을 찾을 때 쓰는 키. gui.instrument.names.<key> 를 본다
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Instrument fromString(String name) {
        if (name == null) {
            return HARP;
        }

        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return HARP;
        }
    }

    public Instrument next() {
        Instrument[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public Instrument prev() {
        Instrument[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }
}
