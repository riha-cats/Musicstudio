package org.rhsd.musicStudio.model;

import org.bukkit.Material;
import org.bukkit.Sound;

import java.util.function.Supplier;

public enum Instrument {
    HARP(() -> Sound.BLOCK_NOTE_BLOCK_HARP, Material.DIRT, "하프"),
    BASS(() -> Sound.BLOCK_NOTE_BLOCK_BASS, Material.OAK_PLANKS, "베이스"),
    BASEDRUM(() -> Sound.BLOCK_NOTE_BLOCK_BASEDRUM, Material.STONE, "베이스드럼"),
    SNARE(() -> Sound.BLOCK_NOTE_BLOCK_SNARE, Material.SAND, "스네어"),
    HAT(() -> Sound.BLOCK_NOTE_BLOCK_HAT, Material.GLASS, "하이햇"),
    GUITAR(() -> Sound.BLOCK_NOTE_BLOCK_GUITAR, Material.WHITE_WOOL, "기타"),
    FLUTE(() -> Sound.BLOCK_NOTE_BLOCK_FLUTE, Material.CLAY, "플루트"),
    BELL(() -> Sound.BLOCK_NOTE_BLOCK_BELL, Material.GOLD_BLOCK, "벨"),
    CHIME(() -> Sound.BLOCK_NOTE_BLOCK_CHIME, Material.PACKED_ICE, "차임"),
    XYLOPHONE(() -> Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, Material.BONE_BLOCK, "실로폰"),
    IRON_XYLOPHONE(
            () -> Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE,
            Material.IRON_BLOCK,
            "철실로폰"
    ),
    COW_BELL(() -> Sound.BLOCK_NOTE_BLOCK_COW_BELL, Material.SOUL_SAND, "카우벨"),
    DIDGERIDOO(() -> Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, Material.PUMPKIN, "디저리두"),
    BIT(() -> Sound.BLOCK_NOTE_BLOCK_BIT, Material.EMERALD_BLOCK, "비트"),
    BANJO(() -> Sound.BLOCK_NOTE_BLOCK_BANJO, Material.HAY_BLOCK, "밴조"),
    PLING(() -> Sound.BLOCK_NOTE_BLOCK_PLING, Material.GLOWSTONE, "플링");

    private final Supplier<Sound> soundSupplier;
    private final Material icon;
    private final String displayName;

    Instrument(
            Supplier<Sound> soundSupplier,
            Material icon,
            String displayName
    ) {
        this.soundSupplier = soundSupplier;
        this.icon = icon;
        this.displayName = displayName;
    }

    public Sound sound() {
        return soundSupplier.get();
    }

    public Material icon() {
        return icon;
    }

    public String displayName() {
        return displayName;
    }

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

    public Instrument next() {
        Instrument[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public Instrument prev() {
        Instrument[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }
}